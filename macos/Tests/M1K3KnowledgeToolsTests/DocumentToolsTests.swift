//
//  DocumentToolsTests.swift
//  M1K3KnowledgeToolsTests
//
//  ListDocumentsTool + GetDocumentTool over a real store.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Knowledge
@testable import M1K3KnowledgeTools
import Testing

private func seededStore() throws -> KnowledgeStore {
    let store = try KnowledgeStore()
    let doc = UUID()
    try store.index(
        item: KnowledgeItem(id: doc, kind: .document, title: "Plant Notes"),
        chunks: [
            KnowledgeChunk(itemID: doc, ordinal: 0, content: "The hydraulic seal failed."),
            KnowledgeChunk(itemID: doc, ordinal: 1, content: "Replace before next shift."),
        ],
        embeddings: nil
    )
    let call = UUID()
    try store.index(
        item: KnowledgeItem(id: call, kind: .call, title: "Vendor call"),
        chunks: [KnowledgeChunk(itemID: call, ordinal: 0, content: "Discussed delivery dates.")],
        embeddings: nil
    )
    return store
}

struct ListDocumentsToolTests {
    @Test("lists every stored item with its kind")
    func lists() async throws {
        let tool = try ListDocumentsTool(store: seededStore())
        let out = try await tool.execute(input: [:]).output
        #expect(out.contains("Plant Notes [document]"))
        #expect(out.contains("Vendor call [call]"))
    }

    @Test("reports an empty store cleanly")
    func empty() async throws {
        let tool = try ListDocumentsTool(store: KnowledgeStore())
        #expect(try await tool.execute(input: [:]).output.contains("No stored knowledge"))
    }
}

struct GetDocumentToolTests {
    @Test("fetches a document's full text by partial title")
    func fetches() async throws {
        let tool = try GetDocumentTool(store: seededStore())
        let out = try await tool.execute(input: ["title": "plant"]).output
        #expect(out.contains("# Plant Notes"))
        #expect(out.contains("hydraulic seal failed"))
        #expect(out.contains("Replace before next shift"))
    }

    @Test("reports when no document matches")
    func noMatch() async throws {
        let tool = try GetDocumentTool(store: seededStore())
        #expect(try await tool.execute(input: ["title": "spaceship"]).output.contains("No document matching"))
    }

    @Test("empty title is an error")
    func emptyTitle() async throws {
        let tool = try GetDocumentTool(store: seededStore())
        #expect(try await tool.execute(input: ["title": " "]).output.hasPrefix("Error:"))
    }

    @Test("pages very long documents with a resume-offset footer")
    func pagesLongDocuments() async throws {
        let store = try KnowledgeStore()
        let id = UUID()
        let big = String(repeating: "A", count: 150) + String(repeating: "B", count: 150)
        try store.index(
            item: KnowledgeItem(id: id, kind: .document, title: "Big"),
            chunks: [KnowledgeChunk(itemID: id, ordinal: 0, content: big)],
            embeddings: nil
        )
        let tool = GetDocumentTool(store: store, maxChars: 100)

        // First page: capped, with the exact resume offset — never a silent cut.
        let page1 = try await tool.execute(input: ["title": "Big"]).output
        #expect(page1.contains("200 more characters"))
        #expect(page1.contains("offset:100"))
        #expect(!page1.contains("BBBBB"))

        // Resuming reaches the tail and says so.
        let page2 = try await tool.execute(input: ["title": "Big", "offset": "200"]).output
        #expect(page2.contains("BBBBB"))
        #expect(page2.contains("end of document"))
    }

    @Test("explains a title-only item instead of returning a bare header")
    func chunklessItem() async throws {
        let store = try KnowledgeStore()
        try store.index(
            item: KnowledgeItem(kind: .document, title: "Title Only Doc"),
            chunks: []
        )
        let tool = GetDocumentTool(store: store)
        let out = try await tool.execute(input: ["title": "Title Only"]).output
        #expect(out.contains("# Title Only Doc"))
        #expect(out.lowercased().contains("no readable text"))
    }
}
