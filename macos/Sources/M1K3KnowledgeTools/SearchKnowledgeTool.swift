//
//  SearchKnowledgeTool.swift
//  M1K3KnowledgeTools
//
//  The bridge that lets the LocalAgent actually search M1K3's memory. Wraps a
//  KnowledgeStore as an AgentTool — the agent emits `ACTION: search_knowledge(q)`
//  and gets back the top matching chunks as an observation.
//
//  MVP uses FTS (text) search so it needs no embedder; a hybrid variant taking
//  an EmbeddingService lands with the MLX session. This is the same seam the prior call-pipeline's
//  VectorStoreTool occupies, pointed at M1K3's generalised knowledge store.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

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
    private let limit: Int

    public init(store: KnowledgeStore, limit: Int = 5) {
        self.store = store
        self.limit = limit
    }

    public func execute(input: [String: String]) async throws -> ToolResult {
        let query = (input["query"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else {
            return ToolResult(output: "Error: empty query.")
        }
        let hits = try store.searchFTS(query: query, limit: limit)
        guard !hits.isEmpty else {
            return ToolResult(output: "No results for \"\(query)\".")
        }
        let body = hits.enumerated().map { index, hit -> String in
            let heading = hit.heading.map { " §\($0)" } ?? ""
            return "\(index + 1). [\(hit.itemTitle)\(heading)] \(hit.content)"
        }.joined(separator: "\n")
        return ToolResult(output: body)
    }
}
