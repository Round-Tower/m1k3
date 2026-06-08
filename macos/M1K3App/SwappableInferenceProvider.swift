//
//  SwappableInferenceProvider.swift
//  M1K3App
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

import Foundation
import M1K3Inference

final class SwappableInferenceProvider: InferenceProvider, @unchecked Sendable {
    let name = "swappable-mlx"

    private let lock = NSLock()
    private var current: any InferenceProvider

    init(_ initial: any InferenceProvider) {
        current = initial
    }

    var active: any InferenceProvider {
        lock.withLock { current }
    }

    func setProvider(_ provider: any InferenceProvider) {
        lock.withLock { current = provider }
    }

    var isAvailable: Bool {
        active.isAvailable
    }

    func generate(prompt: String) async throws -> String {
        try await active.generate(prompt: prompt)
    }

    func generateStreaming(prompt: String) -> AsyncStream<String> {
        active.generateStreaming(prompt: prompt)
    }
}
