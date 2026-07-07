//
//  SwappableEmbeddingService.swift
//  M1K3Knowledge
//
//  An EmbeddingService façade whose backing embedder can change at runtime, so
//  the app can switch Hashing (offline fallback) ↔ MLX (semantic) without
//  rebuilding the RAGResponder / DocumentIngester that hold it. Both the query
//  path (RAGResponder) and the ingest path (DocumentIngester) see the swap at
//  once — and a reindex of the store keeps the stored vectors in the same space.
//
//  Mirrors SwappableInferenceProvider / SwappableSpeechProvider: a lock-protected
//  swap so this Sendable type reads safely off the main actor while the
//  @Observable UI drives the change.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown
//
//  Review: Kev + claude-opus-4-8, 2026-07-07 — relocated from M1K3App into
//  M1K3Knowledge (beside the EmbeddingService protocol it implements) so BOTH the
//  macOS app and the iOS/visionOS shell reuse ONE swap façade. It was the last
//  stranded member of the Swappable* family; its siblings already live in the
//  package. Made `public` (+ public init); logic byte-identical to the app copy.
//

import Foundation

public final class SwappableEmbeddingService: EmbeddingService, @unchecked Sendable {
    private let lock = NSLock()
    private var current: any EmbeddingService

    public init(_ initial: any EmbeddingService) {
        current = initial
    }

    public var active: any EmbeddingService {
        lock.withLock { current }
    }

    public func setEmbedder(_ embedder: any EmbeddingService) {
        lock.withLock { current = embedder }
    }

    public var dimension: Int {
        active.dimension
    }

    public var fingerprint: String {
        active.fingerprint
    }

    public func embed(_ text: String) async throws -> [Float] {
        try await active.embed(text)
    }

    public func embedBatch(_ texts: [String]) async throws -> [[Float]] {
        try await active.embedBatch(texts)
    }

    public func isAvailable() async -> Bool {
        await active.isAvailable()
    }
}
