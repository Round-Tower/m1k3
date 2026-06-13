//
//  KnowledgeToolDefinitionsTests.swift
//  M1K3MCPKitTests
//
//  Pins the knowledge tool surface the stdio server has always exposed — the
//  registry refactor must not change what Claude sees.
//

import Foundation
import M1K3Knowledge
@testable import M1K3MCPKit
import MCP
import Testing

struct KnowledgeToolDefinitionsTests {
    @Test("the registry exposes exactly the original three knowledge tools")
    func toolSurface() throws {
        let store = try KnowledgeStore()
        let registry = MCPToolRegistry(makeKnowledgeToolDefinitions(store: store))
        #expect(registry.tools.map(\.name) == ["search_knowledge", "list_documents", "get_document"])
    }

    @Test("search_knowledge round-trips through the registry against a real store")
    func searchRoundTrip() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store, embedder: HashingEmbeddingService())
        try await ingester.ingest(
            title: "Plant Notes",
            text: "3.2 Seals\nThe hydraulic seal on the conveyor failed under load."
        )
        let registry = MCPToolRegistry(makeKnowledgeToolDefinitions(store: store))
        let result = await registry.call(name: "search_knowledge", arguments: ["query": .string("hydraulic seal")])
        #expect(result.isError != true)
        if case let .text(text, _, _) = result.content.first {
            #expect(text.contains("Plant Notes"))
        } else {
            Issue.record("expected text content")
        }
    }

    @Test("missing required argument degrades to the handler's default, not a crash")
    func missingArgument() async throws {
        let store = try KnowledgeStore()
        let registry = MCPToolRegistry(makeKnowledgeToolDefinitions(store: store))
        let result = await registry.call(name: "get_document", arguments: nil)
        // Empty id → handler reports not-found/error text; the call itself survives.
        #expect(result.content.isEmpty == false)
    }
}
