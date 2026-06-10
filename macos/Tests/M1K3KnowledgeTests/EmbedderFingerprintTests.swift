//
//  EmbedderFingerprintTests.swift
//  M1K3KnowledgeTests
//
//  The embedder fingerprint is what makes vector-space migrations safe: stored
//  vectors are only comparable to query vectors from the SAME embedder + kernel
//  generation (an mlx-swift bump changes the kernels → all stored vectors go
//  stale). The store remembers which fingerprint produced its vectors; on
//  mismatch the app re-embeds everything. Pure policy + meta persistence here.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

struct EmbedderReindexPolicyTests {
    @Test("matching fingerprint never reindexes")
    func matchNeverReindexes() {
        #expect(!EmbedderReindexPolicy.needsReindex(stored: "hashing/v1", current: "hashing/v1", embeddingCount: 5))
        #expect(!EmbedderReindexPolicy.needsReindex(stored: "hashing/v1", current: "hashing/v1", embeddingCount: 0))
    }

    @Test("an empty store adopts the current fingerprint without reindexing")
    func emptyStoreAdopts() {
        #expect(!EmbedderReindexPolicy.needsReindex(stored: nil, current: "hashing/v1", embeddingCount: 0))
        #expect(!EmbedderReindexPolicy.needsReindex(stored: "old/v0", current: "hashing/v1", embeddingCount: 0))
    }

    @Test("vectors with a different or unknown provenance reindex")
    func mismatchReindexes() {
        #expect(EmbedderReindexPolicy.needsReindex(stored: "old/v0", current: "hashing/v1", embeddingCount: 3))
        // nil marker + existing vectors = unknown provenance → re-embed. A
        // pre-marker store upgrading straight across an mlx-swift kernel bump
        // MUST re-embed; adopting silently would compare incompatible spaces.
        #expect(EmbedderReindexPolicy.needsReindex(stored: nil, current: "hashing/v1", embeddingCount: 3))
    }
}

struct EmbedderFingerprintConformanceTests {
    @Test("hashing embedder carries a stable fingerprint")
    func hashingFingerprint() {
        #expect(HashingEmbeddingService().fingerprint == "hashing/v1")
    }
}

struct KnowledgeStoreMetaTests {
    @Test("meta round-trips and overwrites")
    func metaRoundTrip() throws {
        let store = try KnowledgeStore()
        #expect(try store.meta(key: "embedder.fingerprint") == nil)

        try store.setMeta(key: "embedder.fingerprint", value: "hashing/v1")
        #expect(try store.meta(key: "embedder.fingerprint") == "hashing/v1")

        try store.setMeta(key: "embedder.fingerprint", value: "mlx/bge/0.31")
        #expect(try store.meta(key: "embedder.fingerprint") == "mlx/bge/0.31")
    }

    @Test("reindex records the fingerprint with the new vectors")
    func reindexWritesFingerprint() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let ingester = DocumentIngester(store: store, embedder: nil) // FTS-only
        try await ingester.ingest(title: "Seals", text: "The hydraulic seal failed under load.")

        let count = try await store.reindexEmbeddings(using: embedder, fingerprint: embedder.fingerprint)
        #expect(count == 1)
        #expect(try store.meta(key: KnowledgeStore.embedderFingerprintKey) == "hashing/v1")
    }

    @Test("reindex without a fingerprint leaves the marker untouched")
    func reindexWithoutFingerprintLeavesMarker() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let ingester = DocumentIngester(store: store, embedder: embedder)
        try await ingester.ingest(title: "Seals", text: "The hydraulic seal failed under load.")
        try store.setMeta(key: KnowledgeStore.embedderFingerprintKey, value: "previous/v0")

        try await store.reindexEmbeddings(using: embedder)
        #expect(try store.meta(key: KnowledgeStore.embedderFingerprintKey) == "previous/v0")
    }
}
