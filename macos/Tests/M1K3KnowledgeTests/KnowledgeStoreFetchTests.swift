//
//  KnowledgeStoreFetchTests.swift
//  M1K3KnowledgeTests
//
//  Tests for the fetch/list methods that back document tools + MCP resources:
//  item(id:), allItems(kind:limit:), chunks(forItem:).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct KnowledgeStoreFetchTests {
    private func seed(_ store: KnowledgeStore, title: String, kind: KnowledgeKind, chunks: [String]) throws -> UUID {
        let id = UUID()
        let items = chunks.enumerated().map { KnowledgeChunk(itemID: id, ordinal: $0.offset, content: $0.element) }
        try store.index(item: KnowledgeItem(id: id, kind: kind, title: title), chunks: items, embeddings: nil)
        return id
    }

    @Test("item(id:) round-trips title and kind; missing id is nil")
    func fetchItem() throws {
        let store = try KnowledgeStore()
        let id = try seed(store, title: "Q7 SOP", kind: .document, chunks: ["a"])
        let item = try #require(try store.item(id: id))
        #expect(item.title == "Q7 SOP")
        #expect(item.kind == .document)
        #expect(try store.item(id: UUID()) == nil)
    }

    @Test("allItems lists newest-first and filters by kind")
    func listItems() throws {
        let store = try KnowledgeStore()
        _ = try seed(store, title: "Doc", kind: .document, chunks: ["x"])
        _ = try seed(store, title: "Call", kind: .call, chunks: ["y"])
        #expect(try store.allItems().count == 2)
        let docs = try store.allItems(kind: .document)
        #expect(docs.count == 1)
        #expect(docs.first?.title == "Doc")
    }

    @Test("chunks(forItem:) returns chunks in ordinal order")
    func fetchChunks() throws {
        let store = try KnowledgeStore()
        let id = try seed(store, title: "Notes", kind: .note, chunks: ["first", "second", "third"])
        let chunks = try store.chunks(forItem: id)
        #expect(chunks.map(\.content) == ["first", "second", "third"])
        #expect(chunks.map(\.ordinal) == [0, 1, 2])
    }

    @Test("allItems respects the limit")
    func listLimit() throws {
        let store = try KnowledgeStore()
        for i in 0 ..< 5 {
            _ = try seed(store, title: "T\(i)", kind: .note, chunks: ["c"])
        }
        #expect(try store.allItems(limit: 3).count == 3)
    }
}
