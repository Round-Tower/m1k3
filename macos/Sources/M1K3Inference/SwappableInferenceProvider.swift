//
//  SwappableInferenceProvider.swift
//  M1K3Inference
//
//  An InferenceProvider façade whose backing provider can change at runtime, so
//  switching the chosen brain's MLX model (Lil = Qwen ↔ Big = Gemma) re-points the
//  generation backend without rebuilding the RuntimeInferenceProvider / RAGResponder
//  that hold it. Lil and Big both route through RuntimeOption.mlxGemma, so this is
//  the single MLX slot behind that key; AppEnvironment sets the concrete model.
//
//  Mirrors SwappableEmbeddingService: a lock-protected swap so this Sendable type
//  reads safely off the main actor while the @Observable UI drives the change.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.8, Prior: Unknown
//  Review: claude-opus-4-8, 2026-06-09 (PR #10 follow-up, issue #11) — promoted from
//  the M1K3App target into M1K3Inference so the swap logic is `swift test`-covered,
//  matching its siblings SwappableSpeechProvider/SwappableEmbeddingService. Behaviour
//  unchanged; members made `public`.

import Foundation

public final class SwappableInferenceProvider: InferenceProvider, @unchecked Sendable {
    public let name = "swappable-mlx"

    private let lock = NSLock()
    private var current: any InferenceProvider

    public init(_ initial: any InferenceProvider) {
        current = initial
    }

    public var active: any InferenceProvider {
        lock.withLock { current }
    }

    public func setProvider(_ provider: any InferenceProvider) {
        lock.withLock { current = provider }
    }

    public var isAvailable: Bool {
        active.isAvailable
    }

    public func generate(prompt: String) async throws -> String {
        try await active.generate(prompt: prompt)
    }

    public func generateStreaming(prompt: String) -> AsyncStream<String> {
        active.generateStreaming(prompt: prompt)
    }
}

// MARK: - Native tool calling (Phase 12c)

/// Forwards the tool-calling capability to the current backing provider. Both
/// MLX slots (Lil = Qwen, Big = Gemma) conform, so when the swappable backs a
/// tool-capable model the agent gets the native loop; the runtime flag tracks
/// whichever model is currently set (a swap re-points it transparently).
extension SwappableInferenceProvider: ToolCallingProvider {
    public var supportsToolCalls: Bool {
        (active as? ToolCallingProvider)?.supportsToolCalls ?? false
    }

    public func continueToolTurn(messages: [ToolMessage], tools: [ToolDefinition]) async throws -> ToolTurn {
        guard let toolProvider = active as? ToolCallingProvider else {
            // Unreachable in practice: LocalAgent only calls this after reading
            // supportsToolCalls == true. Defensive — surface rather than hang.
            throw InferenceError.generationFailed("active backend does not support tool calls")
        }
        return try await toolProvider.continueToolTurn(messages: messages, tools: tools)
    }

    /// Forward session creation to the ACTIVE provider so its real session
    /// (e.g. MLX's KV-cache session) is reached — falling through to the
    /// stateless default here would silently lose the per-turn cache reuse.
    public func makeToolTurnSession(
        tools: [ToolDefinition],
        options: ToolTurnOptions
    ) async throws -> any ToolTurnSession {
        guard let toolProvider = active as? ToolCallingProvider else {
            throw InferenceError.generationFailed("active backend does not support tool calls")
        }
        return try await toolProvider.makeToolTurnSession(tools: tools, options: options)
    }
}
