//
//  KnowledgeStore.swift
//  M1K3Knowledge
//
//  GRDB-backed semantic store: knowledge items + chunks + FTS5 + vector
//  embeddings + RRF hybrid search. The reusable mechanism is lifted from
//  the prior knowledge-server project's SemanticStore (FTS5 BM25 + brute-force cosine KNN + Reciprocal
//  Rank Fusion); the schema is generalised to M1K3's domain-neutral model.
//
//  Embeddings are computed by the caller via an EmbeddingService and passed in
//  aligned with chunks — the store knows persistence and search, not how to
//  embed, so it carries no MLX dependency.
//
//  Concurrency: GRDB's DatabaseQueue is internally serialized; the class is
//  @unchecked Sendable for the same reason the prior knowledge-server project's SemanticStore is.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8,
//  Prior: the prior knowledge-server project SemanticStore + SemanticStore+Documents (Kev)

import Foundation
import GRDB

public final class KnowledgeStore: @unchecked Sendable {
    private let dbQueue: DatabaseQueue

    // MARK: - Init

    /// Open (or create) a store at `path`. Pass `nil` for an in-memory store
    /// (used by tests — no disk, fresh per instance).
    public init(path: String? = nil) throws {
        if let path {
            dbQueue = try DatabaseQueue(path: path)
        } else {
            dbQueue = try DatabaseQueue()
        }
        try migrate()
    }

    private func migrate() throws {
        var migrator = DatabaseMigrator()
        migrator.registerMigration("v1") { db in
            try db.create(table: "knowledge_items") { t in
                t.column("id", .text).primaryKey()
                t.column("kind", .text).notNull()
                t.column("title", .text).notNull()
                t.column("source_ref", .text)
                t.column("created_at", .double).notNull()
            }
            try db.create(table: "knowledge_chunks") { t in
                t.column("id", .text).primaryKey()
                t.column("item_id", .text).notNull().indexed()
                t.column("ordinal", .integer).notNull()
                t.column("heading", .text)
                t.column("content", .text).notNull()
            }
            // FTS5 mirror for BM25 full-text search. id/item_id carried
            // UNINDEXED so they round-trip without participating in matching.
            try db.execute(sql: """
            CREATE VIRTUAL TABLE knowledge_chunk_fts USING fts5(
                id UNINDEXED, item_id UNINDEXED, heading, content
            )
            """)
            try db.create(table: "knowledge_chunk_embeddings") { t in
                t.column("chunk_id", .text).primaryKey()
                t.column("item_id", .text).notNull().indexed()
                t.column("embedding", .blob).notNull()
            }
        }
        try migrator.migrate(dbQueue)
    }

    // MARK: - Indexing

    /// Index an item and its chunks atomically. `embeddings`, when provided,
    /// must align 1:1 with `chunks`; pass `nil` to index text-only (FTS-only,
    /// embeddings can be backfilled later via `setEmbedding`).
    public func index(
        item: KnowledgeItem,
        chunks: [KnowledgeChunk],
        embeddings: [[Float]]? = nil
    ) throws {
        if let embeddings, embeddings.count != chunks.count {
            throw KnowledgeStoreError.embeddingCountMismatch(
                chunks: chunks.count, embeddings: embeddings.count
            )
        }
        try dbQueue.write { db in
            try ItemRecord(item).insert(db)
            for (offset, chunk) in chunks.enumerated() {
                try ChunkRecord(chunk).insert(db)
                try db.execute(
                    sql: """
                    INSERT INTO knowledge_chunk_fts (id, item_id, heading, content)
                    VALUES (?, ?, ?, ?)
                    """,
                    arguments: [chunk.id.uuidString, chunk.itemID.uuidString, chunk.heading, chunk.content]
                )
                if let embeddings {
                    try db.execute(
                        sql: """
                        INSERT INTO knowledge_chunk_embeddings (chunk_id, item_id, embedding)
                        VALUES (?, ?, ?)
                        """,
                        arguments: [
                            chunk.id.uuidString,
                            chunk.itemID.uuidString,
                            VectorMath.serialize(embeddings[offset]),
                        ]
                    )
                }
            }
        }
    }

    /// Backfill or replace a single chunk's embedding.
    public func setEmbedding(chunkID: UUID, itemID: UUID, vector: [Float]) throws {
        try dbQueue.write { db in
            try db.execute(
                sql: """
                INSERT INTO knowledge_chunk_embeddings (chunk_id, item_id, embedding)
                VALUES (?, ?, ?)
                ON CONFLICT(chunk_id) DO UPDATE SET embedding = excluded.embedding
                """,
                arguments: [chunkID.uuidString, itemID.uuidString, VectorMath.serialize(vector)]
            )
        }
    }

    /// Re-embed every stored chunk with `embedder`, replacing existing vectors
    /// in one pass. Use when switching embedders (e.g. Hashing fallback → MLX):
    /// the stored vectors define the search space, so a half-migrated store would
    /// compare incompatible vectors. Returns the number of chunks re-embedded.
    @discardableResult
    public func reindexEmbeddings(using embedder: any EmbeddingService) async throws -> Int {
        // Snapshot (chunkID, itemID, content) for every chunk first, so the async
        // embed happens outside any DB transaction.
        let rows: [(chunkID: UUID, itemID: UUID, content: String)] = try await dbQueue.read { db in
            try Row.fetchAll(db, sql: "SELECT id, item_id, content FROM knowledge_chunks")
                .compactMap { row -> (chunkID: UUID, itemID: UUID, content: String)? in
                    guard let cid: String = row["id"], let chunkID = UUID(uuidString: cid),
                          let iid: String = row["item_id"], let itemID = UUID(uuidString: iid)
                    else { return nil }
                    let content: String = row["content"] ?? ""
                    return (chunkID, itemID, content)
                }
        }
        guard !rows.isEmpty else { return 0 }

        let vectors = try await embedder.embedBatch(rows.map(\.content))
        guard vectors.count == rows.count else {
            throw KnowledgeStoreError.embeddingCountMismatch(chunks: rows.count, embeddings: vectors.count)
        }

        try await dbQueue.write { db in
            for (row, vector) in zip(rows, vectors) {
                try db.execute(
                    sql: """
                    INSERT INTO knowledge_chunk_embeddings (chunk_id, item_id, embedding)
                    VALUES (?, ?, ?)
                    ON CONFLICT(chunk_id) DO UPDATE SET embedding = excluded.embedding
                    """,
                    arguments: [row.chunkID.uuidString, row.itemID.uuidString, VectorMath.serialize(vector)]
                )
            }
        }
        return rows.count
    }

    /// The existing item id if one with this `sourceRef` is already indexed.
    public func itemID(forSourceRef sourceRef: String) throws -> UUID? {
        try dbQueue.read { db in
            let raw = try String.fetchOne(
                db,
                sql: "SELECT id FROM knowledge_items WHERE source_ref = ?",
                arguments: [sourceRef]
            )
            return raw.flatMap(UUID.init(uuidString:))
        }
    }

    public func itemCount() throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM knowledge_items") ?? 0
        }
    }

    public func chunkCount() throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM knowledge_chunks") ?? 0
        }
    }

    /// Number of chunks that currently carry an embedding vector. Lets callers
    /// see how much of the store is vector-searchable (vs FTS-only).
    public func embeddingCount() throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM knowledge_chunk_embeddings") ?? 0
        }
    }

    /// Hard-delete an item and every row it owns (chunks, FTS mirror,
    /// embeddings). Manual cascade — FTS5 doesn't honour ON DELETE CASCADE.
    @discardableResult
    public func deleteItem(id: UUID) throws -> Bool {
        try dbQueue.write { db in
            let iid = id.uuidString
            let exists = try Int.fetchOne(
                db, sql: "SELECT 1 FROM knowledge_items WHERE id = ?", arguments: [iid]
            ) != nil
            guard exists else { return false }
            try db.execute(sql: "DELETE FROM knowledge_chunk_fts WHERE item_id = ?", arguments: [iid])
            try db.execute(sql: "DELETE FROM knowledge_chunk_embeddings WHERE item_id = ?", arguments: [iid])
            try db.execute(sql: "DELETE FROM knowledge_chunks WHERE item_id = ?", arguments: [iid])
            try db.execute(sql: "DELETE FROM knowledge_items WHERE id = ?", arguments: [iid])
            return true
        }
    }

    // MARK: - Fetch (for document tools + MCP resources)

    /// Fetch a single item by id, or nil if absent.
    public func item(id: UUID) throws -> KnowledgeItem? {
        try dbQueue.read { db in
            guard let row = try Row.fetchOne(
                db, sql: "SELECT * FROM knowledge_items WHERE id = ?", arguments: [id.uuidString]
            ) else { return nil }
            return Self.item(from: row)
        }
    }

    /// List items, newest first. Optionally filter by kind.
    public func allItems(kind: KnowledgeKind? = nil, limit: Int = 200) throws -> [KnowledgeItem] {
        try dbQueue.read { db in
            let rows: [Row]
            if let kind {
                rows = try Row.fetchAll(
                    db,
                    sql: "SELECT * FROM knowledge_items WHERE kind = ? ORDER BY created_at DESC LIMIT ?",
                    arguments: [kind.rawValue, limit]
                )
            } else {
                rows = try Row.fetchAll(
                    db,
                    sql: "SELECT * FROM knowledge_items ORDER BY created_at DESC LIMIT ?",
                    arguments: [limit]
                )
            }
            return rows.compactMap { Self.item(from: $0) }
        }
    }

    /// Fetch an item's chunks in order.
    public func chunks(forItem itemID: UUID) throws -> [KnowledgeChunk] {
        try dbQueue.read { db in
            let rows = try Row.fetchAll(
                db,
                sql: "SELECT * FROM knowledge_chunks WHERE item_id = ? ORDER BY ordinal",
                arguments: [itemID.uuidString]
            )
            return rows.compactMap { row -> KnowledgeChunk? in
                guard let cid: String = row["id"], let chunkID = UUID(uuidString: cid),
                      let iid: String = row["item_id"], let parsedItemID = UUID(uuidString: iid)
                else { return nil }
                return KnowledgeChunk(
                    id: chunkID,
                    itemID: parsedItemID,
                    ordinal: row["ordinal"] ?? 0,
                    heading: row["heading"],
                    content: row["content"] ?? ""
                )
            }
        }
    }

    private static func item(from row: Row) -> KnowledgeItem? {
        guard let raw: String = row["id"], let id = UUID(uuidString: raw) else { return nil }
        return KnowledgeItem(
            id: id,
            kind: KnowledgeKind(rawValue: row["kind"] ?? "note"),
            title: row["title"] ?? "",
            sourceRef: row["source_ref"],
            createdAt: Date(timeIntervalSince1970: row["created_at"] ?? 0)
        )
    }

    // MARK: - Search

    /// FTS5 BM25 search over chunk text. Each query word is double-quoted to
    /// neutralise FTS5 operators (same sanitisation as the prior knowledge-server project).
    public func searchFTS(query: String, limit: Int = 10) throws -> [ChunkHit] {
        guard let sanitized = Self.sanitizeFTSQuery(query) else { return [] }
        return try dbQueue.read { db in
            let rows = try Row.fetchAll(
                db,
                sql: """
                SELECT c.id AS chunk_id, c.item_id, c.heading, c.content,
                       i.title, i.kind
                FROM knowledge_chunk_fts fts
                JOIN knowledge_chunks c ON c.id = fts.id
                JOIN knowledge_items i ON i.id = c.item_id
                WHERE knowledge_chunk_fts MATCH ?
                ORDER BY bm25(knowledge_chunk_fts) ASC
                LIMIT ?
                """,
                arguments: [sanitized, limit]
            )
            return rows.compactMap { Self.hit(from: $0) }
        }
    }

    /// Brute-force cosine KNN over stored embeddings. Single read, in-memory
    /// score + sort + prefix — fine for MVP volumes; revisit past ~10k chunks.
    public func searchVector(queryVector: [Float], limit: Int = 10) throws -> [ChunkHit] {
        let rows = try dbQueue.read { db in
            try Row.fetchAll(
                db,
                sql: """
                SELECT e.chunk_id, e.embedding, c.item_id, c.heading, c.content,
                       i.title, i.kind
                FROM knowledge_chunk_embeddings e
                JOIN knowledge_chunks c ON c.id = e.chunk_id
                JOIN knowledge_items i ON i.id = c.item_id
                """
            )
        }
        guard !rows.isEmpty else { return [] }

        var scored: [ChunkHit] = []
        scored.reserveCapacity(rows.count)
        for row in rows {
            guard let blob: Data = row["embedding"],
                  var hit = Self.hit(from: row, chunkIDColumn: "chunk_id")
            else { continue }
            hit.similarity = VectorMath.cosineSimilarity(queryVector, VectorMath.deserialize(blob))
            scored.append(hit)
        }
        scored.sort { ($0.similarity ?? 0) > ($1.similarity ?? 0) }
        return Array(scored.prefix(limit))
    }

    /// Hybrid search: fuse FTS and vector rankings with Reciprocal Rank Fusion.
    /// An item strong in both signals ranks above one strong in either alone.
    public func searchHybrid(
        query: String,
        queryVector: [Float],
        limit: Int = 10
    ) throws -> [ChunkHit] {
        // Pull a wider candidate set from each signal before fusing.
        let ftsHits = try searchFTS(query: query, limit: limit * 2)
        let vectorHits = try searchVector(queryVector: queryVector, limit: limit * 2)
        let fused = ReciprocalRankFusion.fuse(
            rankings: [ftsHits, vectorHits],
            key: { $0.chunkID }
        )
        return Array(fused.prefix(limit))
    }

    // MARK: - Row mapping

    private static func hit(from row: Row, chunkIDColumn: String = "chunk_id") -> ChunkHit? {
        guard let chunkRaw: String = row[chunkIDColumn],
              let chunkID = UUID(uuidString: chunkRaw),
              let itemRaw: String = row["item_id"],
              let itemID = UUID(uuidString: itemRaw)
        else { return nil }
        return ChunkHit(
            chunkID: chunkID,
            itemID: itemID,
            itemTitle: row["title"] ?? "",
            kind: KnowledgeKind(rawValue: row["kind"] ?? "note"),
            heading: row["heading"],
            content: row["content"] ?? ""
        )
    }

    // MARK: - FTS sanitisation

    /// Double-quote each whitespace-separated token so FTS5 treats them as
    /// literals (neutralises `*`, `:`, `"`, `-` etc.). Returns nil if the query
    /// has no usable tokens. Ported from the prior knowledge-server project's sanitizeFTSQuery.
    static func sanitizeFTSQuery(_ query: String) -> String? {
        let tokens = query
            .components(separatedBy: .whitespacesAndNewlines)
            .map { $0.replacingOccurrences(of: "\"", with: "") }
            .filter { !$0.isEmpty }
        guard !tokens.isEmpty else { return nil }
        return tokens.map { "\"\($0)\"" }.joined(separator: " ")
    }
}

public enum KnowledgeStoreError: Error, Sendable, Equatable {
    case embeddingCountMismatch(chunks: Int, embeddings: Int)
}
