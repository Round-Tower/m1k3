//
//  MLXEmbeddingService.swift
//  M1K3MLX
//
//  On-device embeddings via Apple's MLX framework — Metal GPU, no server. The
//  real semantic embedder that replaces the dependency-free HashingEmbeddingService
//  fallback behind the EmbeddingService seam, so hybrid search becomes genuinely
//  semantic with zero change to KnowledgeStore / RAGResponder.
//
//  Model auto-downloads from HuggingFace on first use and caches in
//  ~/Library/Caches/huggingface/. Subsequent loads are instant.
//
//  Default = bge_small (384-dim, BERT). NOT nomic-embed-text-v1.5: the prior knowledge-server project hit a
//  weight-key mismatch in MLXEmbedders' nomic loader (optional position_embeddings
//  vs RoPE) and shipped bge_small instead. We inherit that hard-won default rather
//  than rediscover the crash — flip to .nomic_text_v1_5 only once upstream fixes it.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.75,
//  Prior: the prior knowledge-server project MLXEmbeddingService (Kev)
//  Context: Ported to M1K3's [Float] EmbeddingService protocol (the prior knowledge-server project returned
//  [Double]); dimension is carried explicitly so callers can size buffers.

import Foundation
import M1K3Knowledge
import MLX
import MLXEmbedders
import MLXNN

/// `@unchecked Sendable`: `modelContainer` is an MLX actor; all model access is
/// actor-isolated inside `perform`. Same guarantee the prior knowledge-server project's service relies on.
public final class MLXEmbeddingService: EmbeddingService, @unchecked Sendable {
    private var modelContainer: ModelContainer?
    private let configuration: ModelConfiguration

    public let dimension: Int

    /// - Parameters:
    ///   - configuration: MLXEmbedders model. Defaults to `.bge_small` (the
    ///     known-good path); pass `.nomic_text_v1_5` only when its loader is fixed.
    ///   - dimension: vector width of `configuration` (bge_small = 384).
    public init(configuration: ModelConfiguration = .bge_small, dimension: Int = 384) {
        self.configuration = configuration
        self.dimension = dimension
    }

    public func embed(_ text: String) async throws -> [Float] {
        let container = try await ensureLoaded()

        return await container.perform { model, tokenizer, pooler in
            let tokenIds = tokenizer.encode(text: text)
            let inputIds = MLXArray(tokenIds.map { Int32($0) }).expandedDimensions(axis: 0)
            let mask = MLXArray([Int32](repeating: 1, count: tokenIds.count)).expandedDimensions(axis: 0)

            let output = model(inputIds, positionIds: nil, tokenTypeIds: nil, attentionMask: mask)
            let pooled = pooler(output, mask: mask, normalize: true)

            // Must eval before leaving perform — MLXArray is not Sendable.
            let result = pooled.squeezed()
            eval(result)
            return result.asArray(Float.self)
        }
    }

    public func isAvailable() async -> Bool {
        do {
            _ = try await ensureLoaded()
            return true
        } catch {
            return false
        }
    }

    private func ensureLoaded() async throws -> ModelContainer {
        if let modelContainer { return modelContainer }
        let container = try await loadModelContainer(
            configuration: configuration,
            progressHandler: { _ in }
        )
        modelContainer = container
        return container
    }
}
