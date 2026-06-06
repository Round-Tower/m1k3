//
//  CallIngester.swift
//  M1K3Calls
//
//  The M1K3 twist — what makes this more than the prior call pipeline on Mac. A finished CallSession
//  is chunked, embedded, and stored as a `.call` KnowledgeItem in the SAME index as
//  documents, so calls are searchable via hybrid RAG, the local agent's tools, and
//  the MCP server. Mirrors DocumentIngester exactly (chunk → embed → store, dedupe
//  on a stable sourceRef) — different source, one knowledge graph.
//
//  Dedupe: the call's own UUID is its sourceRef, so re-ingesting the same call is a
//  no-op.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Knowledge

public struct CallIngester: Sendable {
    private let store: KnowledgeStore
    private let embedder: (any EmbeddingService)?

    public init(store: KnowledgeStore, embedder: (any EmbeddingService)? = nil) {
        self.store = store
        self.embedder = embedder
    }

    public struct IngestResult: Sendable, Equatable {
        public let itemID: UUID
        public let chunkCount: Int
        /// True when this call was already indexed (no new work).
        public let wasDeduped: Bool
    }

    /// Index a finished call into the knowledge graph. Returns the item id + chunk count.
    @discardableResult
    public func ingest(_ session: CallSession) async throws -> IngestResult {
        let sourceRef = session.id.uuidString
        if let existing = try store.itemID(forSourceRef: sourceRef) {
            let existingCount = try store.chunks(forItem: existing).count
            return IngestResult(itemID: existing, chunkCount: existingCount, wasDeduped: true)
        }

        let callChunks = CallChunker.chunk(session: session)
        let item = KnowledgeItem(
            id: session.id,
            kind: .call,
            title: session.title,
            sourceRef: sourceRef,
            createdAt: session.startedAt
        )
        let knowledgeChunks = callChunks.enumerated().map { ordinal, chunk in
            KnowledgeChunk(itemID: session.id, ordinal: ordinal, heading: chunk.heading, content: chunk.content)
        }

        let embeddings: [[Float]]?
        if let embedder, !knowledgeChunks.isEmpty {
            embeddings = try await embedder.embedBatch(knowledgeChunks.map(\.content))
        } else {
            embeddings = nil
        }

        try store.index(item: item, chunks: knowledgeChunks, embeddings: embeddings)
        return IngestResult(itemID: session.id, chunkCount: knowledgeChunks.count, wasDeduped: false)
    }
}
