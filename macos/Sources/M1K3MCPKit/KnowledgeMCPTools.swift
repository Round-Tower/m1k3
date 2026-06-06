//
//  KnowledgeMCPTools.swift
//  M1K3MCP
//
//  The substance behind the MCP server: query the KnowledgeStore and format the
//  results as text an LLM can read. Pure of the MCP transport (no swift-sdk
//  import) so it's testable against an in-memory store — the stdio wiring in
//  main.swift just calls these and wraps the strings in MCP content.
//
//  Search is FTS-only: the server is a plain CLI with no embedder (and MLX won't
//  load outside an .app bundle anyway), so it uses BM25 text search, which needs
//  no query vector.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import Foundation
import M1K3Knowledge

struct KnowledgeMCPTools {
    let store: KnowledgeStore

    /// Full-text search over stored knowledge. Returns ranked chunks as text.
    func searchKnowledge(query: String, limit: Int = 5) throws -> String {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "Error: empty query." }
        let hits = try store.searchFTS(query: trimmed, limit: limit)
        guard !hits.isEmpty else { return "No results for “\(trimmed)”." }
        return hits.enumerated().map { index, hit -> String in
            let heading: String = hit.heading.map { " §\($0)" } ?? ""
            return "\(index + 1). [\(hit.itemTitle)\(heading)] (\(hit.kind.rawValue))\n\(hit.content)"
        }.joined(separator: "\n\n")
    }

    /// List indexed items (documents, calls, notes) with their ids.
    func listDocuments(limit: Int = 100) throws -> String {
        let items = try store.allItems(limit: limit)
        guard !items.isEmpty else { return "No documents indexed yet." }
        return items.map { item in
            "\(item.id.uuidString)  [\(item.kind.rawValue)]  \(item.title)"
        }.joined(separator: "\n")
    }

    /// Full text of one item by id, chunk by chunk.
    func getDocument(idString: String) throws -> String {
        guard let id = UUID(uuidString: idString.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            return "Error: “\(idString)” is not a valid document id."
        }
        guard let item = try store.item(id: id) else {
            return "No document found with id \(id.uuidString)."
        }
        let chunks = try store.chunks(forItem: id)
        let body: String = chunks.map { chunk -> String in
            let heading: String = chunk.heading.map { "## \($0)\n" } ?? ""
            return "\(heading)\(chunk.content)"
        }.joined(separator: "\n\n")
        return "# \(item.title)  [\(item.kind.rawValue)]\n\n\(body)"
    }
}
