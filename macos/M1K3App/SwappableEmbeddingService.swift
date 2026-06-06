//
//  SwappableEmbeddingService.swift
//  M1K3App
//
//  An EmbeddingService façade whose backing embedder can change at runtime, so
//  the app can switch Hashing (offline fallback) ↔ MLX (semantic) without
//  rebuilding the RAGResponder / DocumentIngester that hold it. Both the query
//  path (RAGResponder) and the ingest path (DocumentIngester) see the swap at
//  once — and a reindex of the store keeps the stored vectors in the same space.
//
//  Mirrors RuntimeInferenceProvider: a lock-protected swap so this Sendable type
//  reads safely off the main actor while the @Observable UI drives the change.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import Foundation
import M1K3Knowledge

final class SwappableEmbeddingService: EmbeddingService, @unchecked Sendable {
    private let lock = NSLock()
    private var current: any EmbeddingService

    init(_ initial: any EmbeddingService) {
        current = initial
    }

    var active: any EmbeddingService {
        lock.withLock { current }
    }

    func setEmbedder(_ embedder: any EmbeddingService) {
        lock.withLock { current = embedder }
    }

    var dimension: Int {
        active.dimension
    }

    func embed(_ text: String) async throws -> [Float] {
        try await active.embed(text)
    }

    func embedBatch(_ texts: [String]) async throws -> [[Float]] {
        try await active.embedBatch(texts)
    }

    func isAvailable() async -> Bool {
        await active.isAvailable()
    }
}
