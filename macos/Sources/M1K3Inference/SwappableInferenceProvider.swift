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
