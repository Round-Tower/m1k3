//
//  KnowledgeStoreGroundingTests.swift
//  M1K3KnowledgeTests
//
//  Pins the two-lane grounding retrieval: documents and memories get SEPARATE
//  top-K budgets so the larger document corpus can't crowd short memory facts
//  out of a single ranking. Live regression (2026-06-13): "where am I based and
//  what pet do I have" returned 5 document chunks and 0 memories — M1K3 fell
//  back to persona chat and couldn't name the user's own city.
//

import Foundation
@testable import M1K3Knowledge
import Testing

private struct CrowdingFixture {
    let store: KnowledgeStore
    let embedder = HashingEmbeddingService()

    /// Six document chunks that all share the full query vocabulary (so they
    /// dominate any single ranking) + one memory sharing most of it. Under a
    /// shared top-5 the memory ranks 7th and is lost; its own lane rescues it.
    static let docChunks = (1 ... 6).map { "alpha beta gamma delta doc\($0)" }
    static let memoryText = "alpha beta gamma"

    init() throws {
        store = try KnowledgeStore()
    }

    func ingest() async throws {
        let docID = UUID()
        let docItem = KnowledgeItem(id: docID, kind: .document, title: "Papers", sourceRef: "sha:docs")
        let docChunkModels = Self.docChunks.enumerated().map { idx, text in
            KnowledgeChunk(itemID: docID, ordinal: idx, content: text)
        }
        try await store.index(
            item: docItem, chunks: docChunkModels,
            embeddings: embedder.embedBatch(Self.docChunks)
        )

        let memID = UUID()
        let memItem = KnowledgeItem(id: memID, kind: .memory, title: Self.memoryText, sourceRef: "sha:mem")
        let memChunk = KnowledgeChunk(itemID: memID, ordinal: 0, content: Self.memoryText)
        try await store.index(
            item: memItem, chunks: [memChunk],
            embeddings: embedder.embedBatch([Self.memoryText])
        )
    }

    func query() async throws -> [Float] {
        try await embedder.embed("alpha beta gamma delta")
    }
}

struct KnowledgeStoreGroundingTests {
    @Test("a single top-K lets documents crowd the memory out entirely")
    func sharedTopKLosesMemory() async throws {
        let f = try CrowdingFixture()
        try await f.ingest()
        let q = try await f.query()
        let hits = try f.store.searchHybrid(
            query: "alpha beta gamma delta", queryVector: q, limit: 5
        )
        #expect(hits.count == 5)
        #expect(!hits.contains { $0.kind == .memory }) // the bug we're fixing
    }

    @Test("searchGrounding rescues the memory with its own budget")
    func groundingKeepsMemory() async throws {
        let f = try CrowdingFixture()
        try await f.ingest()
        let q = try await f.query()
        let hits = try f.store.searchGrounding(
            query: "alpha beta gamma delta", queryVector: q,
            documentLimit: 5, memoryLimit: 5
        )
        #expect(hits.contains { $0.kind == .memory })
        #expect(hits.contains { $0.content == CrowdingFixture.memoryText })
        // The document lane is still full and still excludes memories.
        let docs = hits.filter { $0.kind == .document }
        #expect(docs.count == 5)
        #expect(docs.allSatisfy { $0.kind != .memory })
    }

    @Test("the kinds filter restricts a hybrid search to those kinds only")
    func kindsFilterRestricts() async throws {
        let f = try CrowdingFixture()
        try await f.ingest()
        let q = try await f.query()
        let memOnly = try f.store.searchHybrid(
            query: "alpha beta gamma delta", queryVector: q,
            limit: 10, kinds: [.memory]
        )
        #expect(!memOnly.isEmpty)
        #expect(memOnly.allSatisfy { $0.kind == .memory })

        let docsOnly = try f.store.searchHybrid(
            query: "alpha beta gamma delta", queryVector: q,
            limit: 10, kinds: [.document]
        )
        #expect(!docsOnly.isEmpty)
        #expect(docsOnly.allSatisfy { $0.kind == .document })
    }
}
