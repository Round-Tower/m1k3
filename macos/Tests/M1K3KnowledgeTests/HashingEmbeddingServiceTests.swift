//
//  HashingEmbeddingServiceTests.swift
//  M1K3KnowledgeTests
//
//  The dependency-free fallback embedder: deterministic, correct dimension,
//  shared-word texts score higher than unrelated ones under cosine. (It is NOT
//  semantic — synonyms are orthogonal; that's expected, MLX covers meaning.)
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct HashingEmbeddingServiceTests {
    @Test("produces a vector of the configured dimension")
    func dimension() async throws {
        let e = HashingEmbeddingService(dimension: 64)
        let v = try await e.embed("hello world")
        #expect(v.count == 64)
    }

    @Test("is deterministic for the same input")
    func deterministic() async throws {
        let e = HashingEmbeddingService()
        #expect(try await e.embed("hydraulic seal") == (await e.embed("hydraulic seal")))
    }

    @Test("is case- and punctuation-insensitive (same bag of words)")
    func normalisation() async throws {
        let e = HashingEmbeddingService()
        #expect(try await e.embed("Hydraulic, Seal!") == (await e.embed("hydraulic seal")))
    }

    @Test("texts sharing words score higher than unrelated texts")
    func cosineSignal() async throws {
        let e = HashingEmbeddingService()
        let query = try await e.embed("hydraulic seal failure")
        let related = try await e.embed("the hydraulic seal failed")
        let unrelated = try await e.embed("quarterly revenue growth")
        #expect(VectorMath.cosineSimilarity(query, related) > VectorMath.cosineSimilarity(query, unrelated))
    }

    @Test("is always available")
    func available() async {
        #expect(await HashingEmbeddingService().isAvailable())
    }

    @Test("backs hybrid search end-to-end in the store")
    func drivesHybridSearch() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let id = UUID()
        let texts = ["hydraulic seal failed under load", "operators wear gloves near the press"]
        let chunks = texts.enumerated().map { KnowledgeChunk(itemID: id, ordinal: $0.offset, content: $0.element) }
        let embeddings = try await embedder.embedBatch(texts)
        try store.index(item: KnowledgeItem(id: id, kind: .document, title: "Notes"), chunks: chunks, embeddings: embeddings)

        let q = try await embedder.embed("hydraulic seal")
        let hits = try store.searchHybrid(query: "hydraulic seal", queryVector: q)
        #expect(hits.first?.content.contains("hydraulic seal") == true)
    }
}
