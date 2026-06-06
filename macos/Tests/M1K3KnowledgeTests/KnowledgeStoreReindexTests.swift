//
//  KnowledgeStoreReindexTests.swift
//  M1K3KnowledgeTests
//
//  Re-embedding the whole store. This is the primitive that makes switching
//  embedders safe (e.g. Hashing fallback → MLX bge_small): the stored vectors
//  define the search space, so a half-migrated store would compare incompatible
//  vectors. Reindex replaces every chunk's embedding in one pass.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct KnowledgeStoreReindexTests {
    /// Ingest text-only (no embeddings), then reindex and confirm the chunks
    /// become vector-searchable.
    @Test("reindex populates embeddings on a text-only store")
    func reindexPopulates() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let ingester = DocumentIngester(store: store, embedder: nil) // FTS-only

        try await ingester.ingest(
            title: "Seals",
            text: "The hydraulic seal on the conveyor failed under load."
        )
        try await ingester.ingest(
            title: "Safety",
            text: "Operators must wear gloves near the press."
        )

        // Text-only ingest → no vectors yet.
        #expect(try store.embeddingCount() == 0)

        let reindexed = try await store.reindexEmbeddings(using: embedder)
        #expect(reindexed == 2) // one chunk per short doc
        #expect(try store.embeddingCount() == 2) // vectors now populated

        // And the store still searches post-reindex.
        let queryVector = try await embedder.embed("hydraulic seal conveyor")
        let hits = try store.searchHybrid(
            query: "hydraulic seal conveyor",
            queryVector: queryVector,
            limit: 5
        )
        #expect(hits.first?.content.contains("hydraulic seal") == true)
    }

    @Test("reindex is idempotent and returns the chunk count each time")
    func reindexIdempotent() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let ingester = DocumentIngester(store: store, embedder: embedder)
        try await ingester.ingest(title: "A", text: "alpha bravo charlie")

        let first = try await store.reindexEmbeddings(using: embedder)
        let second = try await store.reindexEmbeddings(using: embedder)
        #expect(first == second)
        #expect(first >= 1)
    }

    @Test("reindex of an empty store is a no-op returning zero")
    func reindexEmpty() async throws {
        let store = try KnowledgeStore()
        let count = try await store.reindexEmbeddings(using: HashingEmbeddingService())
        #expect(count == 0)
    }
}
