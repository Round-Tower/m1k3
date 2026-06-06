//
//  KnowledgeMCPToolsTests.swift
//  M1K3MCPKitTests
//
//  The MCP tool handlers over a real in-memory store. The stdio transport is
//  glue; this is where the behaviour Claude will see is pinned.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Knowledge
@testable import M1K3MCPKit
import Testing

private func seededStore() async throws -> KnowledgeStore {
    let store = try KnowledgeStore()
    let ingester = DocumentIngester(store: store, embedder: HashingEmbeddingService())
    try await ingester.ingest(
        title: "Plant Notes",
        text: "3.2 Seals\nThe hydraulic seal on the conveyor failed under load."
    )
    try await ingester.ingest(
        title: "Safety",
        text: "4.1 PPE\nOperators must wear gloves near the press."
    )
    return store
}

struct KnowledgeMCPToolsTests {
    @Test("search_knowledge returns ranked matching chunks")
    func search() async throws {
        let tools = try await KnowledgeMCPTools(store: seededStore())
        let out = try tools.searchKnowledge(query: "hydraulic seal", limit: 5)
        #expect(out.contains("Plant Notes"))
        #expect(out.contains("hydraulic seal"))
    }

    @Test("search_knowledge handles empty query + no matches")
    func searchEdges() async throws {
        let tools = try await KnowledgeMCPTools(store: seededStore())
        #expect(try tools.searchKnowledge(query: "   ").contains("empty"))
        #expect(try tools.searchKnowledge(query: "zzzznotpresent").contains("No results"))
    }

    @Test("list_documents lists every indexed item with its id")
    func list() async throws {
        let store = try await seededStore()
        let tools = KnowledgeMCPTools(store: store)
        let out = try tools.listDocuments()
        #expect(out.contains("Plant Notes"))
        #expect(out.contains("Safety"))
        // ids present so get_document can be called.
        let firstID = try #require(store.allItems().first?.id.uuidString)
        #expect(out.contains(firstID))
    }

    @Test("get_document returns the full text by id, and errors cleanly")
    func get() async throws {
        let store = try await seededStore()
        let tools = KnowledgeMCPTools(store: store)
        let id = try #require(store.allItems().first { $0.title == "Plant Notes" }?.id)
        let out = try tools.getDocument(idString: id.uuidString)
        #expect(out.contains("# Plant Notes"))
        #expect(out.contains("hydraulic seal"))

        #expect(try tools.getDocument(idString: "not-a-uuid").contains("not a valid"))
        #expect(try tools.getDocument(idString: UUID().uuidString).contains("No document found"))
    }

    @Test("resolveStorePath honours the env override")
    func storePathOverride() {
        let path = resolveStorePath(environment: ["M1K3_STORE_PATH": "/tmp/custom.sqlite"])
        #expect(path == "/tmp/custom.sqlite")
    }
}
