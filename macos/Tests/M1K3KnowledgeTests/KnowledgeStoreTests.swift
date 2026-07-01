//
//  KnowledgeStoreTests.swift
//  M1K3KnowledgeTests
//
//  End-to-end of the knowledge-core mechanism against an in-memory store and a
//  deterministic hashing embedder: ingest → embed → FTS / vector / hybrid
//  search returns it. Swapping the embedder for the real MLX one (Phase 1b)
//  changes the vectors, not this contract.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

// The deterministic embedder used to live here as a private fixture; it's now a
// first-class fallback in the library (`HashingEmbeddingService`), reused below.

// MARK: - Fixture

private struct Fixture {
    let store: KnowledgeStore
    let embedder = HashingEmbeddingService()
    let docID = UUID()

    /// Three chunks with distinct vocabularies so search has something to rank.
    static let chunkTexts = [
        "The hydraulic seal on the conveyor failed under load.",
        "Quarterly revenue grew after the new pricing model shipped.",
        "Operators should wear gloves near the press at all times.",
    ]

    init() throws {
        store = try KnowledgeStore()
    }

    func ingest() async throws {
        let item = KnowledgeItem(id: docID, kind: .document, title: "Plant Notes", sourceRef: "sha:abc")
        let chunks = Self.chunkTexts.enumerated().map { idx, text in
            KnowledgeChunk(itemID: docID, ordinal: idx, content: text)
        }
        let embeddings = try await embedder.embedBatch(Self.chunkTexts)
        try store.index(item: item, chunks: chunks, embeddings: embeddings)
    }
}

// MARK: - Tests

struct KnowledgeStoreTests {
    @Test("ingest persists the item and its chunks")
    func ingestCounts() async throws {
        let f = try Fixture()
        try await f.ingest()
        #expect(try f.store.itemCount() == 1)
        #expect(try f.store.chunkCount() == 3)
    }

    @Test("FTS finds the chunk containing a query word")
    func ftsFindsWord() async throws {
        let f = try Fixture()
        try await f.ingest()
        let hits = try f.store.searchFTS(query: "hydraulic")
        #expect(hits.count == 1)
        #expect(hits.first?.content.contains("hydraulic") == true)
        #expect(hits.first?.itemTitle == "Plant Notes")
    }

    @Test("FTS sanitisation neutralises operator characters")
    func ftsSanitises() async throws {
        let f = try Fixture()
        try await f.ingest()
        // Bare FTS5 would choke on the colon / asterisk; sanitiser quotes tokens.
        let hits = try f.store.searchFTS(query: "gloves: press*")
        #expect(hits.contains { $0.content.contains("gloves") })
    }

    @Test("vector search ranks the chunk sharing query words highest")
    func vectorRanks() async throws {
        let f = try Fixture()
        try await f.ingest()
        let q = try await f.embedder.embed("hydraulic seal load")
        let hits = try f.store.searchVector(queryVector: q)
        #expect(hits.first?.content.contains("hydraulic") == true)
        // similarity is populated and ordered descending
        #expect(hits.first?.similarity != nil)
        if hits.count >= 2 {
            #expect((hits[0].similarity ?? 0) >= (hits[1].similarity ?? 0))
        }
    }

    @Test("hybrid search fuses FTS + vector and returns the relevant chunk first")
    func hybridFuses() async throws {
        let f = try Fixture()
        try await f.ingest()
        let q = try await f.embedder.embed("revenue pricing")
        let hits = try f.store.searchHybrid(query: "revenue pricing", queryVector: q)
        #expect(!hits.isEmpty)
        #expect(hits.first?.content.contains("revenue") == true)
    }

    @Test("hybrid hits carry the vector similarity for relevance gating")
    func hybridCarriesSimilarity() async throws {
        let f = try Fixture()
        try await f.ingest()
        let q = try await f.embedder.embed("hydraulic seal load")
        let hits = try f.store.searchHybrid(query: "hydraulic seal", queryVector: q)
        // The top hit scored in BOTH signals; RRF keeps the FTS instance, so
        // the similarity must be backfilled from the vector ranking.
        let top = try #require(hits.first)
        #expect(top.similarity != nil)
        #expect(top.rrfScore != nil)
    }

    @Test("text-only index (no embeddings) still serves FTS")
    func textOnly() throws {
        let store = try KnowledgeStore()
        let id = UUID()
        let item = KnowledgeItem(id: id, kind: .note, title: "Note")
        let chunk = KnowledgeChunk(itemID: id, ordinal: 0, content: "remember the milk")
        try store.index(item: item, chunks: [chunk], embeddings: nil)
        #expect(try store.searchFTS(query: "milk").count == 1)
        #expect(try store.searchVector(queryVector: [0, 0, 0]).isEmpty)
    }

    @Test("sourceRef dedupe locates a previously indexed item")
    func sourceRefDedupe() async throws {
        let f = try Fixture()
        try await f.ingest()
        #expect(try f.store.itemID(forSourceRef: "sha:abc") == f.docID)
        #expect(try f.store.itemID(forSourceRef: "sha:missing") == nil)
    }

    @Test("delete removes the item, its chunks, and its search rows")
    func deleteCascades() async throws {
        let f = try Fixture()
        try await f.ingest()
        #expect(try f.store.deleteItem(id: f.docID) == true)
        #expect(try f.store.itemCount() == 0)
        #expect(try f.store.chunkCount() == 0)
        #expect(try f.store.searchFTS(query: "hydraulic").isEmpty)
        #expect(try f.store.searchVector(queryVector: [1, 1, 1]).isEmpty)
        // deleting again is a no-op
        #expect(try f.store.deleteItem(id: f.docID) == false)
    }

    @Test("embedding count must match chunk count")
    func embeddingMismatchThrows() throws {
        let store = try KnowledgeStore()
        let id = UUID()
        let item = KnowledgeItem(id: id, kind: .note, title: "X")
        let chunks = [KnowledgeChunk(itemID: id, ordinal: 0, content: "a")]
        #expect(throws: KnowledgeStoreError.embeddingCountMismatch(chunks: 1, embeddings: 2)) {
            try store.index(item: item, chunks: chunks, embeddings: [[1], [2]])
        }
    }

    @Test("setEmbedding backfills, making a text-only chunk vector-searchable")
    func backfillEmbedding() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let id = UUID()
        let chunk = KnowledgeChunk(itemID: id, ordinal: 0, content: "conveyor belt motor")
        try store.index(
            item: KnowledgeItem(id: id, kind: .document, title: "M"),
            chunks: [chunk],
            embeddings: nil
        )
        #expect(try store.searchVector(queryVector: [1]).isEmpty)
        let vec = try await embedder.embed("conveyor belt motor")
        try store.setEmbedding(chunkID: chunk.id, itemID: id, vector: vec)
        let hits = try store.searchVector(queryVector: vec)
        #expect(hits.first?.chunkID == chunk.id)
    }

    // MARK: - FTS relaxation (live repro 2026-07-02: a stored memory was listed

    // but invisible to a natural multi-term search — FTS5's implicit AND demands
    // every token in one chunk, so long queries starve retrieval to zero)

    @Test("over-constrained multi-term query falls back to best-coverage ranking")
    func overConstrainedQueryRelaxes() async throws {
        let f = try Fixture()
        try await f.ingest()
        // "milestone" appears in no chunk → strict AND is zero; the relaxed
        // fallback must still surface the chunk covering most query terms.
        let hits = try f.store.searchFTS(query: "hydraulic seal milestone conveyor failed")
        #expect(hits.isEmpty == false)
        #expect(hits.first?.content.contains("hydraulic") == true)
    }

    @Test("relaxation never fires when the strict AND query has hits")
    func strictAndPrecisionPreserved() async throws {
        let f = try Fixture()
        try await f.ingest()
        // Both terms live in chunk 1 → strict AND matches exactly one chunk; the
        // OR fallback (which would also match the pricing/gloves chunks on
        // neither term) must not run.
        let hits = try f.store.searchFTS(query: "hydraulic conveyor")
        #expect(hits.count == 1)
        #expect(hits.first?.content.contains("hydraulic") == true)
    }

    @Test("query with no matching terms stays empty even after relaxation")
    func garbageQueryStaysEmpty() async throws {
        let f = try Fixture()
        try await f.ingest()
        #expect(try f.store.searchFTS(query: "xyzzy plugh zork").isEmpty)
    }

    @Test("relaxedFTSQuery OR-joins quoted tokens, nil below two tokens")
    func relaxedQueryConstruction() {
        #expect(KnowledgeStore.relaxedFTSQuery("Golden Gate milestone")
            == "\"Golden\" OR \"Gate\" OR \"milestone\"")
        // A single token relaxes to nothing new — strict already covered it.
        #expect(KnowledgeStore.relaxedFTSQuery("hydraulic") == nil)
        #expect(KnowledgeStore.relaxedFTSQuery("   ") == nil)
        // Embedded quotes are stripped, same as the strict sanitiser.
        #expect(KnowledgeStore.relaxedFTSQuery("say \"cheese\" now")
            == "\"say\" OR \"cheese\" OR \"now\"")
    }
}
