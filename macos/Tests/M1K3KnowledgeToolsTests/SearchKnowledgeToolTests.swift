//
//  SearchKnowledgeToolTests.swift
//  M1K3KnowledgeToolsTests
//
//  Integration of the whole pure stack: ingest into a real KnowledgeStore →
//  the LocalAgent emits ACTION: search_knowledge(...) → the tool runs FTS →
//  the agent concludes from the real observation. Plus direct tool unit tests.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Agent
import M1K3Inference
import M1K3Knowledge
@testable import M1K3KnowledgeTools
import Testing

// MARK: - Scripted provider (mirrors the agent test double)

private final class ScriptedProvider: InferenceProvider, @unchecked Sendable {
    let name = "scripted"
    let isAvailable = true
    private let lock = NSLock()
    private var responses: [String]
    private var index = 0
    init(_ responses: [String]) {
        self.responses = responses
    }

    func generate(prompt _: String) async throws -> String {
        lock.withLock {
            defer { index += 1 }
            return index < responses.count ? responses[index] : "CONCLUSION: (fallback)"
        }
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { $0.finish() }
    }
}

// MARK: - Fixture

private func storeWithNotes() throws -> KnowledgeStore {
    let store = try KnowledgeStore()
    let id = UUID()
    let item = KnowledgeItem(id: id, kind: .document, title: "Plant Notes")
    let chunks = [
        KnowledgeChunk(itemID: id, ordinal: 0, heading: "3.2 Seals", content: "The hydraulic seal on the conveyor failed under load."),
        KnowledgeChunk(itemID: id, ordinal: 1, heading: "4.1 Safety", content: "Operators must wear gloves near the press."),
    ]
    try store.index(item: item, chunks: chunks, embeddings: nil)
    return store
}

// MARK: - Tests

struct SearchKnowledgeToolTests {
    @Test("returns matching chunks formatted with title and heading")
    func returnsMatches() async throws {
        let tool = try SearchKnowledgeTool(store: storeWithNotes())
        let result = try await tool.execute(input: ["query": "hydraulic"])
        #expect(result.output.contains("Plant Notes"))
        #expect(result.output.contains("§3.2 Seals"))
        #expect(result.output.contains("failed under load"))
    }

    @Test("reports no results cleanly")
    func noResults() async throws {
        let tool = try SearchKnowledgeTool(store: storeWithNotes())
        let result = try await tool.execute(input: ["query": "spaceship"])
        #expect(result.output.contains("No results"))
    }

    @Test("empty query is an error observation")
    func emptyQuery() async throws {
        let tool = try SearchKnowledgeTool(store: storeWithNotes())
        let result = try await tool.execute(input: ["query": "   "])
        #expect(result.output.hasPrefix("Error:"))
    }

    @Test("respects the result limit")
    func respectsLimit() async throws {
        let store = try KnowledgeStore()
        let id = UUID()
        let chunks = (0 ..< 5).map {
            KnowledgeChunk(itemID: id, ordinal: $0, content: "seal report number \($0)")
        }
        try store.index(item: KnowledgeItem(id: id, kind: .note, title: "Reports"), chunks: chunks, embeddings: nil)
        let tool = SearchKnowledgeTool(store: store, limit: 2)
        let result = try await tool.execute(input: ["query": "seal"])
        // Two numbered lines only.
        #expect(result.output.split(separator: "\n").count == 2)
    }

    @Test("END TO END: agent searches the real store and concludes from it")
    func agentEndToEnd() async throws {
        let store = try storeWithNotes()
        let tool = SearchKnowledgeTool(store: store)
        // The agent decides to search, gets the real observation, then concludes.
        let provider = ScriptedProvider([
            "I should check the notes. ACTION: search_knowledge(hydraulic seal)",
            "CONCLUSION: The hydraulic seal failed under load.",
        ])
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])
        let result = try await agent.run(goal: "What failed on the conveyor?")

        #expect(result.toolsUsed == ["search_knowledge"])
        #expect(result.conclusion == "The hydraulic seal failed under load.")
        // The observation in the trace is the REAL store content, not a mock.
        #expect(result.reasoningTrace[0].observation?.contains("failed under load") == true)
    }
}
