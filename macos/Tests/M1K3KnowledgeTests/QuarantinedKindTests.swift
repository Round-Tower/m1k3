//
//  QuarantinedKindTests.swift
//  M1K3KnowledgeTests
//
//  Index segregation (prompt-hardening v2, code-side ticket 2): a
//  `.quarantined` item is stored and embedded like anything else but is
//  invisible to every retrieval surface unless a caller asks for the kind
//  BY NAME. The prompt cannot stop retrieval surfacing a doc that is in the
//  index — only exclusion can — so internal QA/diagnostic notes (and canary
//  honeypot docs) live under this kind and never reach a model's context.
//
//  Signed: Kev + claude-fable-5, 2026-07-12, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

// MARK: - Fixture

private struct Fixture {
    let store: KnowledgeStore
    let embedder = HashingEmbeddingService()
    let publicID = UUID()
    let quarantinedID = UUID()

    init() throws {
        store = try KnowledgeStore()
    }

    /// A public doc and a quarantined doc SHARING distinctive vocabulary —
    /// if the exclusion leaks, the quarantined chunk outranks or rides along.
    func ingest() async throws {
        let publicText = "The hydraulic seal on the conveyor failed under load."
        let internalText = "QA diagnostic: the hydraulic seal test rig failed — internal triage notes."
        try store.index(
            item: KnowledgeItem(id: publicID, kind: .document, title: "Plant Notes"),
            chunks: [KnowledgeChunk(itemID: publicID, ordinal: 0, content: publicText)],
            embeddings: await embedder.embedBatch([publicText])
        )
        try store.index(
            item: KnowledgeItem(id: quarantinedID, kind: .quarantined, title: "Internal QA Notes"),
            chunks: [KnowledgeChunk(itemID: quarantinedID, ordinal: 0, content: internalText)],
            embeddings: await embedder.embedBatch([internalText])
        )
    }
}

// MARK: - Tests

struct QuarantinedKindTests {
    @Test("FTS: nil kinds means every retrievable kind — quarantined stays out")
    func ftsExcludesQuarantined() async throws {
        let f = try Fixture()
        try await f.ingest()
        let hits = try f.store.searchFTS(query: "hydraulic seal failed")
        #expect(!hits.isEmpty)
        #expect(hits.allSatisfy { $0.kind != .quarantined })
    }

    @Test("vector: nil kinds means every retrievable kind — quarantined stays out")
    func vectorExcludesQuarantined() async throws {
        let f = try Fixture()
        try await f.ingest()
        let q = try await f.embedder.embed("QA diagnostic hydraulic seal triage")
        let hits = try f.store.searchVector(queryVector: q)
        #expect(!hits.isEmpty)
        #expect(hits.allSatisfy { $0.kind != .quarantined })
    }

    @Test("hybrid: quarantined never rides the fusion in")
    func hybridExcludesQuarantined() async throws {
        let f = try Fixture()
        try await f.ingest()
        let q = try await f.embedder.embed("hydraulic seal failed")
        let hits = try f.store.searchHybrid(query: "hydraulic seal failed", queryVector: q)
        #expect(!hits.isEmpty)
        #expect(hits.allSatisfy { $0.kind != .quarantined })
    }

    @Test("asking for the kind BY NAME still works — the owner surface opt-in")
    func explicitKindOptsIn() async throws {
        let f = try Fixture()
        try await f.ingest()
        let hits = try f.store.searchFTS(query: "triage", kinds: [.quarantined])
        #expect(hits.count == 1)
        #expect(hits.first?.kind == .quarantined)
    }

    @Test("allItems: default listing hides quarantined; explicit kind reveals it")
    func allItemsSplit() async throws {
        let f = try Fixture()
        try await f.ingest()
        let listed = try f.store.allItems()
        #expect(listed.count == 1)
        #expect(listed.allSatisfy { $0.kind != .quarantined })
        let quarantined = try f.store.allItems(kind: .quarantined)
        #expect(quarantined.count == 1)
        #expect(quarantined.first?.title == "Internal QA Notes")
    }

    @Test("grounding: the two-lane retrieval never sees quarantined")
    func groundingExcludesQuarantined() async throws {
        let f = try Fixture()
        try await f.ingest()
        // The allowlist is the pin — quarantined must never join it.
        #expect(!KnowledgeStore.groundingDocumentKinds.contains(.quarantined))
        let q = try await f.embedder.embed("QA diagnostic hydraulic seal")
        let hits = try f.store.searchGrounding(
            query: "QA diagnostic hydraulic seal", queryVector: q,
            documentLimit: 5, memoryLimit: 5
        )
        #expect(hits.allSatisfy { $0.kind != .quarantined })
    }
}
