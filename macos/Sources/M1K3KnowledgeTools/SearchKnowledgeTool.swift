//
//  SearchKnowledgeTool.swift
//  M1K3KnowledgeTools
//
//  The bridge that lets the LocalAgent actually search M1K3's memory. Wraps a
//  KnowledgeStore as an AgentTool — the agent emits `ACTION: search_knowledge(q)`
//  and gets back the top matching chunks as an observation.
//
//  With an embedder this is full HYBRID search (FTS + vector, RRF-fused) —
//  the same retrieval the implicit grounding uses, but on the MODEL's terms:
//  it decides when to search and what to ask. Results are deliberately NOT
//  relevance-gated (the model judges what it asked for); the hits it saw are
//  reported through `onHits` so they join the turn's sources and the citation
//  validator's allow-list. Without an embedder it degrades to FTS (self-test).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-10, Confidence 0.85 — hybrid search +
//  hit collection for the model-decides retrieval redesign.

import Foundation
import M1K3Agent
import M1K3Knowledge

public struct SearchKnowledgeTool: AgentTool {
    public let name = "search_knowledge"
    public let description =
        "Search M1K3's stored knowledge (documents, calls, notes) by text. Argument: the search query."
    public let parameters = [
        ToolParameter(name: "query", description: "the text to search for"),
    ]

    private let store: KnowledgeStore
    private let embedder: (any EmbeddingService)?
    private let limit: Int
    private let onHits: (@Sendable ([ChunkHit]) -> Void)?

    public init(
        store: KnowledgeStore,
        embedder: (any EmbeddingService)? = nil,
        limit: Int = 5,
        onHits: (@Sendable ([ChunkHit]) -> Void)? = nil
    ) {
        self.store = store
        self.embedder = embedder
        self.limit = limit
        self.onHits = onHits
    }

    public func execute(input: [String: String]) async throws -> ToolResult {
        let query = (input["query"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else {
            return ToolResult(output: "Error: empty query.")
        }
        // Deliberately UNGATED (asymmetric with GroundingGate's 0.62 cosine
        // bar on implicit injection): the model chose to search with its own
        // query, so it judges the relevance of what comes back — the gate
        // exists to police what the model never asked for.
        let hits: [ChunkHit]
        if let embedder {
            let queryVector = try await embedder.embed(query)
            hits = try store.searchHybrid(query: query, queryVector: queryVector, limit: limit)
        } else {
            hits = try store.searchFTS(query: query, limit: limit)
        }
        guard !hits.isEmpty else {
            return ToolResult(output: "No results for \"\(query)\".")
        }
        onHits?(hits)
        let body = hits.enumerated().map { index, hit -> String in
            let heading = hit.heading.map { " §\($0)" } ?? ""
            return "\(index + 1). [\(hit.itemTitle)\(heading)] \(hit.content)"
        }.joined(separator: "\n")
        return ToolResult(output: body)
    }
}
