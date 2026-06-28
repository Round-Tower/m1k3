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
//  it decides when to search and what to ask. Results pass the SAME relevance
//  floor as implicit grounding (GroundingGate, kind-aware): top-K always
//  returns *something*, and a small model treats whatever comes back as ground
//  truth — ungated nearest-neighbour garbage was the confabulation fuel for
//  off-store questions. When nothing clears the bar the tool says so honestly
//  instead. The hits the model actually saw are reported through `onHits` so
//  they join the turn's sources and the citation validator's allow-list.
//  Without an embedder it degrades to FTS (self-test).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-10, Confidence 0.85 — hybrid search +
//  hit collection for the model-decides retrieval redesign.
//  Review: Kev + claude-fable-5, 2026-06-12, Confidence 0.85 — relevance floor
//  + honest abstention; replaces the deliberately-ungated stance (sound for a
//  strong model, wrong for a confabulating 2B).

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
        // Same floor as implicit grounding, against the MODEL's query instead
        // of the user's: a better-crafted query earns better cosines, but
        // sub-floor nearest neighbours are noise no matter who asked. When
        // nothing clears the bar, abstain honestly — handing a small model
        // top-K garbage is how off-store questions become confabulation.
        let hits: [ChunkHit]
        if let embedder {
            let queryVector = try await embedder.embed(query)
            // Two-lane retrieval (documents + memories get SEPARATE top-K budgets),
            // the same path implicit grounding uses — so a large document corpus can't
            // crowd short memory facts out of one ranking BEFORE the floor is applied.
            // Was a single searchHybrid top-K, which is how a memory like "Ada is a
            // scientist" went missing here while ask_m1k3 (two-lane) still found it.
            let (knowledge, memories) = try GroundingGate.partition(
                store.searchGrounding(
                    query: query, queryVector: queryVector,
                    documentLimit: limit, memoryLimit: limit
                )
            )
            hits = knowledge + memories
            guard !hits.isEmpty else {
                return ToolResult(output: "Nothing relevant in stored knowledge for \"\(query)\" "
                    + "— the user's documents don't cover this. Do not search again for it; "
                    + "if the answer isn't already in this conversation, say it isn't in "
                    + "the stored knowledge.")
            }
        } else {
            // FTS-only fallback (no embedder, self-test): no vector scores
            // exist, so no floor can apply.
            hits = try store.searchFTS(query: query, limit: limit)
            guard !hits.isEmpty else {
                return ToolResult(output: "No results for \"\(query)\".")
            }
        }
        onHits?(hits)
        let body = hits.enumerated().map { index, hit -> String in
            let heading = hit.heading.map { " §\($0)" } ?? ""
            return "\(index + 1). [\(hit.itemTitle)\(heading)] \(hit.content)"
        }.joined(separator: "\n")
        return ToolResult(output: body)
    }
}
