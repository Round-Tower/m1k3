//
//  M1K3ModelExecutor.swift
//  M1K3Agent
//
//  The real-provider executor for the WWDC26 LanguageModel bridge (ADR 0001).
//  Conforms M1K3's actual token-streaming seam (`ToolTurnSession`) to the
//  `LanguageModelExecuting` mirror: it runs a turn, feeds every token through the
//  LIVE `ThinkStreamGate`, and routes reasoning / answer / tool-calls to the
//  channel. This is the brick that proves the bridge carries M1K3's real engine —
//  with the reasoning/answer split a generic adapter (which leaks `<think>`) lacks.
//
//  On the macOS 27 SDK, `LanguageModelExecuting`/`GenerationChannel` retarget to
//  the real `FoundationModels` types; this body is unchanged (it only touches the
//  mirror's surface, which mirrors Apple's).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.85 (gate routing TDD'd
//  against a scripted session incl. split close-tags; real MLX session is the
//  verify-at-launch edge — it's the same `ToolTurnSession` the agent already uses).
//  Prior: Kev + claude-opus-4-8
//

import Foundation
import M1K3Inference
import M1K3LanguageModel
import Synchronization

/// Wraps a `ToolTurnSession` (M1K3's streaming generate seam) as a
/// `LanguageModelExecuting`. Stateless across calls beyond the session it holds,
/// so a caller can reuse one executor for a multi-turn conversation (the session
/// keeps the KV cache). `respond` runs exactly one turn.
public struct M1K3ModelExecutor: LanguageModelExecuting {
    private let session: any ToolTurnSession
    /// Optional standing system prompt prepended to the turn (persona, etc.). Nil
    /// keeps `respond` a bare single-user-turn — the caller owns transcript policy.
    private let systemPrompt: String?

    public init(session: any ToolTurnSession, systemPrompt: String? = nil) {
        self.session = session
        self.systemPrompt = systemPrompt
    }

    public func respond(to prompt: String, into channel: GenerationChannel) async throws {
        var messages: [ToolMessage] = []
        if let systemPrompt { messages.append(.system(systemPrompt)) }
        messages.append(.user(prompt))
        channel.updateUsage(inputTokens: estimatedTokens(messages))

        // The gate is a value type; the @Sendable onToken sink mutates it, so guard
        // it with a Mutex. Mirror the lock-safety rule from sendThroughGate: collect
        // answer chunks UNDER the lock, emit to the channel OUTSIDE it (the gate's
        // lock is non-reentrant; the channel must never be touched while it's held).
        let gate = Mutex(ThinkStreamGate())
        let turn = try await session.send(messages) { token in
            var answerChunk = ""
            let reasoning = gate.withLock { $0.feed(token, onAnswerToken: { answerChunk += $0 }) }
            if !answerChunk.isEmpty { channel.appendText(answerChunk) }
            if !reasoning.isEmpty { channel.appendReasoning(reasoning) }
        }

        switch turn {
        case .text:
            // The answer already streamed LIVE via onAnswerToken (the gate buffers
            // the same bytes for flushRemainder, so re-appending would double it —
            // mirrors LocalAgent, which never re-emits the remainder). Only fall back
            // to the remainder when nothing streamed live (unclosed-`<think>` or a
            // truncated turn), so a degenerate turn still yields its text once.
            if channel.answer.isEmpty {
                let remainder = gate.withLock { $0.flushRemainder() }
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !remainder.isEmpty { channel.appendText(remainder) }
            }
        case let .toolCalls(calls):
            // The model asked for tools — surface them as channel tool-call events
            // (Apple's `.toolCallDelta`). Execution + re-invocation stays the agent
            // loop's job; the executor only reports what was requested.
            for call in calls {
                channel.appendToolCall(.init(name: call.name, arguments: call.stringArguments))
            }
        }
    }

    /// Cheap input-token estimate for usage accounting (~4 chars/token). The real
    /// session reports exact counts on the macOS 27 path; this is the floor.
    private func estimatedTokens(_ messages: [ToolMessage]) -> Int {
        let chars = messages.reduce(0) { sum, message in
            switch message {
            case let .system(s), let .user(s): return sum + s.count
            case let .assistant(text, _): return sum + (text?.count ?? 0)
            case let .toolResult(_, output): return sum + output.count
            }
        }
        return chars / 4
    }
}
