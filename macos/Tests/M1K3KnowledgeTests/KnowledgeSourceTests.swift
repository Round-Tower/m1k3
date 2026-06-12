//
//  KnowledgeSourceTests.swift
//  M1K3KnowledgeTests
//
//  Memory as a first-class kind: KnowledgeKind.memory plus KnowledgeSource
//  provenance ("user" told us vs "distilled" by the background loop). Pins the
//  v3-source migration round-trip and that legacy items stay source-nil.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct KnowledgeSourceTests {
    @Test("raw values are pinned — they live in the database")
    func rawValuesPinned() {
        #expect(KnowledgeKind.memory.rawValue == "memory")
        #expect(KnowledgeSource.user.rawValue == "user")
        #expect(KnowledgeSource.distilled.rawValue == "distilled")
    }

    @Test("memory item with source round-trips through index → fetch")
    func memorySourceRoundTrips() throws {
        let store = try KnowledgeStore()
        let id = UUID()
        let item = KnowledgeItem(
            id: id, kind: .memory, title: "Kev's sister", source: .user
        )
        let chunk = KnowledgeChunk(itemID: id, ordinal: 0, content: "Kev's sister is called Aoife.")
        try store.index(item: item, chunks: [chunk])

        let fetched = try #require(try store.item(id: id))
        #expect(fetched.kind == .memory)
        #expect(fetched.source == .user)

        let listed = try store.allItems(kind: .memory)
        #expect(listed.count == 1)
        #expect(listed.first?.source == .user)
    }

    @Test("distilled source round-trips distinctly from user")
    func distilledSourceRoundTrips() throws {
        let store = try KnowledgeStore()
        let id = UUID()
        let item = KnowledgeItem(id: id, kind: .memory, title: "Units", source: .distilled)
        try store.index(item: item, chunks: [KnowledgeChunk(itemID: id, ordinal: 0, content: "Prefers metric units.")])
        #expect(try store.item(id: id)?.source == .distilled)
    }

    @Test("legacy items (no source) fetch with source nil")
    func legacySourceIsNil() throws {
        let store = try KnowledgeStore()
        let id = UUID()
        let item = KnowledgeItem(id: id, kind: .document, title: "Plant Notes")
        try store.index(item: item, chunks: [KnowledgeChunk(itemID: id, ordinal: 0, content: "The seal failed.")])

        let fetched = try #require(try store.item(id: id))
        #expect(fetched.source == nil)
        #expect(try store.allItems().first?.source == nil)
    }

    @Test("allItems(kind: .memory) excludes documents and calls")
    func kindFilterExcludesOthers() throws {
        let store = try KnowledgeStore()
        let memoryID = UUID()
        try store.index(
            item: KnowledgeItem(id: memoryID, kind: .memory, title: "A fact", source: .user),
            chunks: [KnowledgeChunk(itemID: memoryID, ordinal: 0, content: "A durable fact.")]
        )
        let docID = UUID()
        try store.index(
            item: KnowledgeItem(id: docID, kind: .document, title: "A doc"),
            chunks: [KnowledgeChunk(itemID: docID, ordinal: 0, content: "Some document text.")]
        )

        let memories = try store.allItems(kind: .memory)
        #expect(memories.map(\.id) == [memoryID])
    }
}
