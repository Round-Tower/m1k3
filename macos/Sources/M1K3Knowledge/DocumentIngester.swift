//
//  DocumentIngester.swift
//  M1K3Knowledge
//
//  Orchestrates the document path: pages → DocumentChunker → (optional embed via
//  EmbeddingService) → KnowledgeStore as a `.document` KnowledgeItem. The single
//  entry point Phase 4 ingest needs, sitting on top of the pure chunker + the
//  store. Embedder is optional: pass one (HashingEmbeddingService now, MLX later)
//  for vector/hybrid search, or omit it for FTS-only ingest with embeddings
//  backfilled afterwards.
//
//  Dedupe: if `sourceRef` is already indexed, ingest is a no-op and returns the
//  existing item id (the prior knowledge-server project's sha256-dedupe pattern, generalised).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation

public struct DocumentIngester: Sendable {
    private let store: KnowledgeStore
    private let embedder: (any EmbeddingService)?

    public init(store: KnowledgeStore, embedder: (any EmbeddingService)? = nil) {
        self.store = store
        self.embedder = embedder
    }

    public struct IngestResult: Sendable, Equatable {
        public let itemID: UUID
        public let chunkCount: Int
        /// True when a matching `sourceRef` was already indexed (no new work).
        public let wasDeduped: Bool
    }

    /// Ingest extracted pages as a document. Returns the item id and chunk count.
    @discardableResult
    public func ingest(
        title: String,
        pages: [DocumentPage],
        sourceRef: String? = nil
    ) async throws -> IngestResult {
        if let sourceRef, let existing = try store.itemID(forSourceRef: sourceRef) {
            let existingCount = try store.chunks(forItem: existing).count
            return IngestResult(itemID: existing, chunkCount: existingCount, wasDeduped: true)
        }

        let documentChunks = DocumentChunker.chunk(pages: pages)
        let itemID = UUID()
        let item = KnowledgeItem(id: itemID, kind: .document, title: title, sourceRef: sourceRef)
        let knowledgeChunks = documentChunks.map { chunk in
            KnowledgeChunk(
                itemID: itemID,
                ordinal: chunk.chunkIndex,
                heading: chunk.heading,
                content: chunk.content
            )
        }

        let embeddings: [[Float]]?
        if let embedder, !knowledgeChunks.isEmpty {
            embeddings = try await embedder.embedBatch(knowledgeChunks.map(\.content))
        } else {
            embeddings = nil
        }

        try store.index(item: item, chunks: knowledgeChunks, embeddings: embeddings)
        return IngestResult(itemID: itemID, chunkCount: knowledgeChunks.count, wasDeduped: false)
    }

    /// Ingest a single block of raw text as a one-page document.
    @discardableResult
    public func ingest(
        title: String,
        text: String,
        sourceRef: String? = nil
    ) async throws -> IngestResult {
        try await ingest(
            title: title,
            pages: [DocumentPage(pageNumber: 1, text: text)],
            sourceRef: sourceRef
        )
    }

    /// Ingest PDF bytes: extract pages, then chunk + embed + store.
    @discardableResult
    public func ingestPDF(
        title: String,
        data: Data,
        sourceRef: String? = nil
    ) async throws -> IngestResult {
        let pages = try PDFTextExtractor.extract(data: data)
        return try await ingest(title: title, pages: pages, sourceRef: sourceRef)
    }
}
