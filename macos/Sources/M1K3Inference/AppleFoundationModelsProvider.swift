//
//  AppleFoundationModelsProvider.swift
//  M1K3Inference
//
//  InferenceProvider backed by Apple's on-device Foundation Models. M1K3's
//  cheap/fast tier — short turns, the Tier-1 call summary, anything that
//  doesn't need Gemma 4's depth. Thin OS adapter: the testable routing logic
//  lives in ProviderRouter, so this file is verified by compiling against the
//  macOS 26 SDK + a name check, not by invoking the model (which needs Apple
//  Intelligence hardware).
//
//  Mirrors the internal call-pipeline project's AppleFoundationModelsProvider.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.75,
//  Prior: the internal call-pipeline project AppleFoundationModelsProvider (Kev)

import Foundation
import FoundationModels

public struct AppleFoundationModelsProvider: InferenceProvider {
    public let name = "apple-foundation-models"

    /// System instructions for every session this provider opens, evaluated
    /// fresh per call (the persona tracks profile edits). Defaults to the
    /// persona; secondary jobs (the memory distiller, future judges) pass
    /// neutral instructions so they don't speak as M1K3.
    private let instructions: @Sendable () -> String

    /// Opt-in for the Phase-15 AFM-native tool-calling path. Default OFF: the
    /// provider reports `supportsToolCalls == false`, so `LocalAgent` keeps the
    /// prompt-ReAct floor and launch routing is unchanged. Flipped on only by the
    /// eval harness (and, later, a Settings toggle) to exercise the spike.
    private let nativeToolCalling: Bool

    public init(
        instructions: @escaping @Sendable () -> String = { M1K3Persona.systemPrompt },
        nativeToolCalling: Bool = false
    ) {
        self.instructions = instructions
        self.nativeToolCalling = nativeToolCalling
    }

    public var isAvailable: Bool {
        switch SystemLanguageModel.default.availability {
        case .available:
            return true
        default:
            return false
        }
    }

    public func generate(prompt: String) async throws -> String {
        let session = LanguageModelSession(instructions: instructions())
        let response = try await session.respond(to: prompt)
        return response.content
    }

    public func generateStreaming(prompt: String) -> AsyncStream<String> {
        AsyncStream { continuation in
            let task = Task { [instructions] in
                do {
                    let session = LanguageModelSession(instructions: instructions())
                    let stream = session.streamResponse(to: prompt)
                    for try await snapshot in stream {
                        continuation.yield(snapshot.content)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish()
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}

// MARK: - AFM-native tool calling (Phase 15 spike)

/// The structured decision AFM is FORCED to emit each agent turn. AFM speaks no
/// per-model tool dialect, so `respond(generating:)` does constrained decoding
/// against this schema — the model can only return well-formed `{isFinal,
/// toolName, toolInput, finalAnswer}`. The provider extracts the scalars and
/// hands them to the pure `AFMToolMapping`; OUR `LocalAgent` keeps the loop
/// (iteration cap, repeat-guard, unknown-tool steering), so AFM never auto-loops
/// to the context-overflow melt the Apple-driven `LanguageModelSession(tools:)`
/// path does.
@Generable
private struct AFMToolDecision {
    @Guide(description: "True ONLY if you can fully answer now without calling any tool.")
    var isFinal: Bool
    @Guide(description: "Exact name of the single tool to call. Leave empty when isFinal is true.")
    var toolName: String
    @Guide(description: "The input/query to pass to that tool. Leave empty when isFinal is true.")
    var toolInput: String
    @Guide(description: "Your complete final answer to the user. Fill only when isFinal is true.")
    var finalAnswer: String
}

/// Same-file extension so the conformance keeps reading the provider's `private`
/// `instructions` + `nativeToolCalling` without widening their visibility.
extension AppleFoundationModelsProvider: ToolCallingProvider {
    /// Runtime capability: only when the spike is opted IN *and* the on-device
    /// model is actually available. Default-OFF flag ⇒ ReAct floor ⇒ launch
    /// routing unchanged.
    public var supportsToolCalls: Bool {
        nativeToolCalling && isAvailable
    }

    /// Spike-scoped costs to retire before any production wiring (review
    /// 2026-06-15): (1) a FRESH `LanguageModelSession` per call + the default
    /// `StatelessToolTurnSession` re-sending the whole transcript ⇒ no KV reuse,
    /// iteration ≥2 re-prefills the persona (a chunk of the ~20–30s/call). A real
    /// `ToolTurnSession` holding one AFM session across the turn would cut it. (2)
    /// the cap-reached `synthesizeNativeConclusion` turn is a plain `.user`, but
    /// this path still forces the `AFMToolDecision` schema — the `isFinal=true`
    /// branch absorbs it (toolName/toolInput wasted), a non-obvious coupling.
    /// Both are acceptable for a spike whose verdict is "don't route agentic to
    /// AFM" regardless; named so they aren't inherited silently.
    public func continueToolTurn(messages: [ToolMessage], tools: [ToolDefinition]) async throws -> ToolTurn {
        let body = AFMToolPrompt.render(messages: messages, tools: tools)
        let standing = AFMToolPrompt.systemInstructions(from: messages) ?? instructions()
        let session = LanguageModelSession(instructions: standing)
        do {
            let decision = try await session.respond(to: body, generating: AFMToolDecision.self).content
            return AFMToolMapping.toolTurn(
                isFinal: decision.isFinal,
                toolName: decision.toolName,
                toolInput: decision.toolInput,
                finalAnswer: decision.finalAnswer
            )
        } catch is CancellationError {
            // A cancelled turn MUST propagate — the native loop's
            // `catch is CancellationError { throw }` (LocalAgent+Native) depends on
            // it reaching up. Swallowing it here would silently conclude with an
            // empty answer instead of honouring Cancel (the `try? Task.sleep`
            // family of bug). Re-throw before the catch-all backstop.
            throw CancellationError()
        } catch {
            // Non-melt backstop: a guardrail / decode / context-overflow throw
            // becomes a fast, empty text conclusion — LocalAgent ends the turn
            // immediately rather than thrashing. The latency band proves the
            // difference from the 337s Apple-driven auto-loop.
            return .text("")
        }
    }
}
