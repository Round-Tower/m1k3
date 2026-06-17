//
//  MemoryStore.swift
//  M1K3Memory
//
//  Memory as a first-class store, SEPARATE from the RAG document corpus.
//  Today `remember` ingests through DocumentIngester into KnowledgeStore —
//  i.e. a memory IS a document. This store splits them, because the two have
//  different physics:
//
//    RAG docs (KnowledgeStore)          Memories (this store)
//    ─────────────────────────          ─────────────────────
//    big, chunked, immutable            small, atomic, single-row
//    ingested wholesale                 written one fact at a time
//    deleted per-document               deleted per-fact (consent surface)
//    no relations between chunks        edges between memories matter
//    cited via § tokens                 surfaced as "you told me…"
//    relevance is the only signal       recency + supersession also signal
//
//  This is the temporal memory GRAPH: atomic facts (nodes) + typed edges +
//  supersession-over-time, traversed by a recursive CTE. It is the artifact a
//  later persona/"knows-me" LoRA distils — facts stay here (queryable, editable,
//  deletable), the *shape* of how they relate over time is what a prior learns.
//
//  DESIGN DECISIONS (vs the generic advice):
//
//  1. NO sqlite-vec, NO extension loading, NO SQLCipher build change.
//     The house pattern (KnowledgeStore.swift) is already BLOB embedding
//     columns + brute-force cosine in Swift + RRF hybrid — proven on-device,
//     zero dependencies, and fine to ~10k rows. A lifetime of atomic personal
//     memories is thousands of rows, not millions. Revisit only if recall
//     latency shows up in TTFT logs.
//
//  2. NO graph database. Edges are a table; traversal is a recursive CTE,
//     which SQLite runs natively and GRDB passes through untouched.
//
//  3. Memories are SINGLE-ROW — no chunking. A memory is an atomic fact
//     ("Kev's sister is Aoife", "decided RRF over learned fusion on 06-11").
//     If text arrives too big to be one fact, that's an ingest-policy problem
//     (summarise or split at write time), not a storage problem. This is what
//     makes per-row deletion an honest consent surface.
//
//  4. Same EmbedderReindexPolicy doctrine as KnowledgeStore: vectors carry an
//     embedder fingerprint in a meta table; kernel bump → one-time re-index.
//
//  5. Retrieval cutoff: reuse GroundingGate.chunkThreshold. FTS-only hits must
//     NOT bypass the bar when recall is implicit — the exact leak
//     GroundingGate.filter fixed.
//
//  6. SEPARATE DATABASE FILE (memory.sqlite), not new tables in knowledge.db.
//     Different lifecycle (sync/export/forget-all), different consent story,
//     and CloudKit row-wise sync later wants a clean record boundary.
//
//  iOS NOTE: this file works unchanged on iOS — GRDB, FTS5, recursive CTEs,
//  BLOB cosine all ship in the system SQLite. The only platform delta is where
//  embeddings come from and the Data Protection class on the file.
//
//  ── Review ───────────────────────────────────────────────────────────────
//  Graduated from scratch/memory-store-sketch into a real, tested target. The
//  design is Fable's; this pass added the test contract (M1K3MemoryTests) that
//  pins recall-cutoff, supersession-over-time, undirected traversal, and the
//  forget consent cascade, and wired the M1K3Memory target into Package.swift.
//  Logic is unchanged from the sketch except where a test demanded a guarantee
//  the sketch only implied. The duplicated `sanitizeFTSQuery` is carried as a
//  documented follow-up: lift it (and KnowledgeStore's twin) into one shared
//  home in M1K3Knowledge rather than widen this feature PR's blast radius.
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.88 (idioms proven in
//  KnowledgeStore; graph + supersession now TDD'd green; live remember-path
//  migration deliberately deferred to its own PR). Prior: Kev + claude-fable-5
//  (scratch/memory-store-sketch/MemoryStore.swift) → Kev + claude-opus-4-8
//  (KnowledgeStore.swift).

import Foundation
import GRDB
import M1K3Knowledge // VectorMath, ReciprocalRankFusion, GroundingGate, EmbeddingService

// MARK: - Domain model

/// What kind of fact this is. Open string-backed enum, same pattern as
/// KnowledgeKind — new kinds need no schema change.
public struct MemoryKind: RawRepresentable, Hashable, Sendable, Codable {
    public let rawValue: String
    public init(rawValue: String) {
        self.rawValue = rawValue
    }

    /// A fact about the user ("sister is Aoife"). Feeds the persona About-block.
    public static let profile = MemoryKind(rawValue: "profile")
    /// A standing preference ("prefers en-GB spellings", "no emoji in commits").
    public static let preference = MemoryKind(rawValue: "preference")
    /// A decision + rationale ("RRF over learned fusion, 06-11, because…").
    public static let decision = MemoryKind(rawValue: "decision")
    /// An event that happened ("shipped Gecko companion on 06-11").
    public static let episode = MemoryKind(rawValue: "episode")
    /// Anything else worth keeping.
    public static let note = MemoryKind(rawValue: "note")
}

/// One atomic memory. Small, deletable, supersedable.
public struct Memory: Identifiable, Equatable, Sendable, Codable {
    public var id: UUID
    public var kind: MemoryKind
    /// The fact itself. Atomic — one idea per row. Soft cap at write time
    /// (~500 chars); bigger inputs should be split/summarised by the caller.
    public var text: String
    /// Who/what wrote it: "mcp:remember", "chat:auto-distill", "user:settings".
    /// The provenance half of the consent story — Settings can show it.
    public var source: String
    public var createdAt: Date
    /// Set when a newer memory replaces this one. Superseded rows are kept
    /// (history, undo) but excluded from recall by default. Forgetting is
    /// still hard-delete — supersession is correction, not deletion.
    public var supersededBy: UUID?

    public init(
        id: UUID = UUID(),
        kind: MemoryKind,
        text: String,
        source: String,
        createdAt: Date = Date(),
        supersededBy: UUID? = nil
    ) {
        self.id = id
        self.kind = kind
        self.text = text
        self.source = source
        self.createdAt = createdAt
        self.supersededBy = supersededBy
    }
}

/// A typed, directed relation between two memories.
/// The "graph" is this table + recursive CTEs — nothing more.
public struct MemoryEdge: Equatable, Sendable, Codable {
    public var fromID: UUID
    public var toID: UUID
    /// Open vocabulary: "supersedes", "about-person", "caused-by", "part-of".
    public var relation: String
    public var createdAt: Date

    public init(fromID: UUID, toID: UUID, relation: String, createdAt: Date = Date()) {
        self.fromID = fromID
        self.toID = toID
        self.relation = relation
        self.createdAt = createdAt
    }
}

/// A cheap, pollable change-signature of the store. Equatable so a UI (the
/// constellation) can gate a relayout on "did anything change?" without
/// re-reading every row each tick.
public struct MemoryRevision: Equatable, Sendable {
    public let memoryCount: Int
    public let edgeCount: Int
    public let latestCreatedAt: Double

    public init(memoryCount: Int, edgeCount: Int, latestCreatedAt: Double) {
        self.memoryCount = memoryCount
        self.edgeCount = edgeCount
        self.latestCreatedAt = latestCreatedAt
    }
}

/// A recall result: the memory plus how it scored.
public struct MemoryHit: Identifiable, Equatable, Sendable {
    public var id: UUID {
        memory.id
    }

    public var memory: Memory
    public var similarity: Float?
    public var rrfScore: Double?

    public init(memory: Memory, similarity: Float? = nil, rrfScore: Double? = nil) {
        self.memory = memory
        self.similarity = similarity
        self.rrfScore = rrfScore
    }
}

// MARK: - Store

/// GRDB-backed memory store. Same concurrency stance as KnowledgeStore:
/// DatabaseQueue is internally serialized → @unchecked Sendable.
public final class MemoryStore: @unchecked Sendable {
    private let dbQueue: DatabaseQueue

    /// `nil` path → in-memory store (tests).
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
            try db.create(table: "memories") { t in
                t.column("id", .text).primaryKey()
                t.column("kind", .text).notNull().indexed()
                t.column("text", .text).notNull()
                t.column("source", .text).notNull()
                t.column("created_at", .double).notNull()
                t.column("superseded_by", .text) // nil = live
            }
            // FTS5 mirror — id carried UNINDEXED, same as knowledge_chunk_fts.
            try db.execute(sql: """
            CREATE VIRTUAL TABLE memory_fts USING fts5(id UNINDEXED, text)
            """)
            // One vector per memory (it's atomic — no chunk table needed).
            try db.create(table: "memory_embeddings") { t in
                t.column("memory_id", .text).primaryKey()
                t.column("embedding", .blob).notNull()
            }
            // The graph. Composite PK = one edge per (from, to, relation).
            try db.create(table: "memory_edges") { t in
                t.column("from_id", .text).notNull().indexed()
                t.column("to_id", .text).notNull().indexed()
                t.column("relation", .text).notNull()
                t.column("created_at", .double).notNull()
                t.primaryKey(["from_id", "to_id", "relation"])
            }
            // Embedder fingerprint + future markers (knowledge_meta pattern).
            try db.create(table: "memory_meta") { t in
                t.column("key", .text).primaryKey()
                t.column("value", .text).notNull()
            }
        }
        try migrator.migrate(dbQueue)
    }

    // MARK: - Write

    /// Store a memory and its embedding atomically.
    /// `supersedes` marks an older memory as corrected by this one and records
    /// the edge — recall stops returning the old row, history keeps it.
    ///
    /// Supersession can CHAIN (A←B←C: C supersedes B which superseded A). Only
    /// the latest is live; each older row points at its immediate corrector.
    /// `forget` undoes exactly one link: deleting B revives A (B's predecessor)
    /// and leaves C live — it does not walk the chain. That's deliberate: undo
    /// is one step, not a cascade.
    public func remember(
        _ memory: Memory,
        embedding: [Float],
        supersedes oldID: UUID? = nil
    ) throws {
        try dbQueue.write { db in
            try db.execute(
                sql: """
                INSERT INTO memories (id, kind, text, source, created_at, superseded_by)
                VALUES (?, ?, ?, ?, ?, NULL)
                """,
                arguments: [
                    memory.id.uuidString, memory.kind.rawValue, memory.text,
                    memory.source, memory.createdAt.timeIntervalSince1970,
                ]
            )
            try db.execute(
                sql: "INSERT INTO memory_fts (id, text) VALUES (?, ?)",
                arguments: [memory.id.uuidString, memory.text]
            )
            try db.execute(
                sql: "INSERT INTO memory_embeddings (memory_id, embedding) VALUES (?, ?)",
                arguments: [memory.id.uuidString, VectorMath.serialize(embedding)]
            )
            if let oldID {
                try db.execute(
                    sql: "UPDATE memories SET superseded_by = ? WHERE id = ?",
                    arguments: [memory.id.uuidString, oldID.uuidString]
                )
                try db.execute(
                    sql: """
                    INSERT OR IGNORE INTO memory_edges (from_id, to_id, relation, created_at)
                    VALUES (?, ?, 'supersedes', ?)
                    """,
                    arguments: [
                        memory.id.uuidString, oldID.uuidString,
                        memory.createdAt.timeIntervalSince1970,
                    ]
                )
            }
        }
    }

    /// Record a typed, directed edge between two memories. Idempotent on the
    /// (from, to, relation) composite key.
    public func link(_ edge: MemoryEdge) throws {
        try dbQueue.write { db in
            try db.execute(
                sql: """
                INSERT OR IGNORE INTO memory_edges (from_id, to_id, relation, created_at)
                VALUES (?, ?, ?, ?)
                """,
                arguments: [
                    edge.fromID.uuidString, edge.toID.uuidString,
                    edge.relation, edge.createdAt.timeIntervalSince1970,
                ]
            )
        }
    }

    /// Hard-delete: the row, its vector, its FTS mirror, every edge touching
    /// it. THE consent primitive — "forget that" must leave no residue.
    /// Manual cascade because FTS5 ignores ON DELETE CASCADE (KnowledgeStore
    /// precedent).
    @discardableResult
    public func forget(id: UUID) throws -> Bool {
        try dbQueue.write { db in
            let mid = id.uuidString
            let exists = try Int.fetchOne(
                db, sql: "SELECT 1 FROM memories WHERE id = ?", arguments: [mid]
            ) != nil
            guard exists else { return false }
            try db.execute(sql: "DELETE FROM memory_fts WHERE id = ?", arguments: [mid])
            try db.execute(sql: "DELETE FROM memory_embeddings WHERE memory_id = ?", arguments: [mid])
            try db.execute(
                sql: "DELETE FROM memory_edges WHERE from_id = ? OR to_id = ?",
                arguments: [mid, mid]
            )
            // Anything this row superseded becomes live again (undo semantics).
            try db.execute(
                sql: "UPDATE memories SET superseded_by = NULL WHERE superseded_by = ?",
                arguments: [mid]
            )
            try db.execute(sql: "DELETE FROM memories WHERE id = ?", arguments: [mid])
            return true
        }
    }

    // MARK: - Recall (the retrieval-with-cutoff query)

    /// Hybrid recall: FTS5 BM25 + brute-force cosine, fused with RRF, then
    /// EVERY hit must clear the cosine bar individually — keyword-only hits
    /// never sneak past (the GroundingGate.filter lesson, applied at source).
    /// Superseded memories are excluded. Ties broken newest-first so a recent
    /// fact beats an old near-duplicate.
    public func recall(
        query: String,
        queryVector: [Float],
        limit: Int = 5,
        threshold: Float = GroundingGate.chunkThreshold
    ) throws -> [MemoryHit] {
        let ftsHits = try recallFTS(query: query, limit: limit * 2)
        let vectorHits = try recallVector(queryVector: queryVector, limit: limit * 2)

        let fused = ReciprocalRankFusion.fuseScored(
            rankings: [ftsHits, vectorHits],
            key: { $0.memory.id }
        )
        // Backfill similarity onto FTS-first instances (KnowledgeStore
        // searchHybrid pattern) so the cutoff sees both signals on every hit.
        let similarityByID = Dictionary(
            vectorHits.compactMap { hit in hit.similarity.map { (hit.memory.id, $0) } },
            uniquingKeysWith: { first, _ in first }
        )
        return fused
            .map { item, score -> MemoryHit in
                var hit = item
                hit.rrfScore = score
                if hit.similarity == nil { hit.similarity = similarityByID[hit.memory.id] }
                return hit
            }
            .filter { ($0.similarity ?? 0) >= threshold } // the cutoff — no exceptions
            .sorted {
                if $0.rrfScore != $1.rrfScore { return ($0.rrfScore ?? 0) > ($1.rrfScore ?? 0) }
                return $0.memory.createdAt > $1.memory.createdAt
            }
            .prefix(limit)
            .map { $0 }
    }

    private func recallFTS(query: String, limit: Int) throws -> [MemoryHit] {
        guard let sanitized = Self.sanitizeFTSQuery(query) else { return [] }
        return try dbQueue.read { db in
            let rows = try Row.fetchAll(
                db,
                sql: """
                SELECT m.* FROM memory_fts fts
                JOIN memories m ON m.id = fts.id
                WHERE memory_fts MATCH ? AND m.superseded_by IS NULL
                ORDER BY bm25(memory_fts) ASC
                LIMIT ?
                """,
                arguments: [sanitized, limit]
            )
            return rows.compactMap { Self.memory(from: $0).map { MemoryHit(memory: $0) } }
        }
    }

    private func recallVector(queryVector: [Float], limit: Int) throws -> [MemoryHit] {
        let rows = try dbQueue.read { db in
            try Row.fetchAll(
                db,
                sql: """
                SELECT m.*, e.embedding FROM memory_embeddings e
                JOIN memories m ON m.id = e.memory_id
                WHERE m.superseded_by IS NULL
                """
            )
        }
        var scored: [MemoryHit] = []
        scored.reserveCapacity(rows.count)
        for row in rows {
            guard let blob: Data = row["embedding"],
                  let memory = Self.memory(from: row) else { continue }
            scored.append(MemoryHit(
                memory: memory,
                similarity: VectorMath.cosineSimilarity(queryVector, VectorMath.deserialize(blob))
            ))
        }
        scored.sort { ($0.similarity ?? 0) > ($1.similarity ?? 0) }
        return Array(scored.prefix(limit))
    }

    // MARK: - Graph traversal (the recursive CTE)

    /// Everything connected to a memory within `maxHops`, undirected, nearest
    /// hops first. This IS the graph layer — one query, no graph engine.
    ///
    /// `UNION` (not `UNION ALL`) is deliberate: it dedupes whole `(id, hops)`
    /// rows so the recursion can't loop forever on a cycle. SQLite has no native
    /// cycle guard, so a cycle still re-visits a node at increasing hop counts
    /// until `r.hops < maxHops` bottoms out — bounded, never infinite. The outer
    /// `GROUP BY m.id` + `MIN(hops)` then collapses those revisits to one row
    /// per node at its nearest distance, so the RESULT is correct; the only cost
    /// is some redundant intermediate rows on dense cyclic graphs (negligible at
    /// the default maxHops 2 over a personal store). Pinned by relatedWithCycle.
    public func related(to id: UUID, maxHops: Int = 2, limit: Int = 20) throws -> [Memory] {
        try dbQueue.read { db in
            let rows = try Row.fetchAll(
                db,
                sql: """
                WITH RECURSIVE reachable(id, hops) AS (
                    SELECT ?, 0
                    UNION
                    SELECT CASE WHEN e.from_id = r.id THEN e.to_id ELSE e.from_id END,
                           r.hops + 1
                    FROM memory_edges e
                    JOIN reachable r ON r.id IN (e.from_id, e.to_id)
                    WHERE r.hops < ?
                )
                SELECT m.*, MIN(r.hops) AS hops FROM reachable r
                JOIN memories m ON m.id = r.id
                WHERE r.id != ?
                GROUP BY m.id
                ORDER BY hops ASC, m.created_at DESC
                LIMIT ?
                """,
                arguments: [id.uuidString, maxHops, id.uuidString, limit]
            )
            return rows.compactMap { Self.memory(from: $0) }
        }
    }

    // MARK: - Maintenance

    public static let embedderFingerprintKey = "embedder.fingerprint"

    /// Re-embed everything when the embedder changes — same atomic
    /// vectors+fingerprint contract as KnowledgeStore.reindexEmbeddings.
    ///
    /// Re-embeds SUPERSEDED rows too, not just live ones: `forget`-the-corrector
    /// can revive a superseded memory (undo), so its vector must stay valid
    /// across an embedder change. Skipping them would save a little work at the
    /// cost of a revived memory carrying a stale-dimension vector that silently
    /// fails the cosine bar. Correctness over the saving.
    ///
    /// TOCTOU: the read → async embed → write spans three steps. A `forget` that
    /// interleaves between the read and the write leaves an orphan embedding (no
    /// matching `memories` row) — invisible to recall via the JOIN, harmless but
    /// for a little space. The serialized DatabaseQueue makes each step atomic,
    /// not the whole sequence.
    @discardableResult
    public func reindexEmbeddings(
        using embedder: any EmbeddingService,
        fingerprint: String? = nil
    ) async throws -> Int {
        let rows: [(id: String, text: String)] = try await dbQueue.read { db in
            try Row.fetchAll(db, sql: "SELECT id, text FROM memories").compactMap { row in
                guard let id: String = row["id"] else { return nil }
                return (id, row["text"] ?? "")
            }
        }
        guard !rows.isEmpty else { return 0 }
        let vectors = try await embedder.embedBatch(rows.map(\.text))
        try await dbQueue.write { db in
            for (row, vector) in zip(rows, vectors) {
                try db.execute(
                    sql: """
                    INSERT INTO memory_embeddings (memory_id, embedding) VALUES (?, ?)
                    ON CONFLICT(memory_id) DO UPDATE SET embedding = excluded.embedding
                    """,
                    arguments: [row.id, VectorMath.serialize(vector)]
                )
            }
            if let fingerprint {
                try db.execute(
                    sql: """
                    INSERT INTO memory_meta (key, value) VALUES (?, ?)
                    ON CONFLICT(key) DO UPDATE SET value = excluded.value
                    """,
                    arguments: [Self.embedderFingerprintKey, fingerprint]
                )
            }
        }
        return rows.count
    }

    /// Live (non-superseded) memory count — the Settings "M1K3 remembers
    /// N things about you" number.
    public func liveCount() throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(
                db, sql: "SELECT COUNT(*) FROM memories WHERE superseded_by IS NULL"
            ) ?? 0
        }
    }

    /// A cheap change-signal for polling UIs. `liveCount` alone MISSES a
    /// supersession (an old fact exits live as its corrector enters → net delta
    /// zero), so we fold in the edge count (a supersedes/link always writes an
    /// edge) and the newest live timestamp. One aggregate read, no row scan —
    /// the constellation gates its relayout on this so a correction redraws.
    public func revision() throws -> MemoryRevision {
        try dbQueue.read { db in
            let memoryCount = try Int.fetchOne(
                db, sql: "SELECT COUNT(*) FROM memories WHERE superseded_by IS NULL"
            ) ?? 0
            let edgeCount = try Int.fetchOne(
                db, sql: "SELECT COUNT(*) FROM memory_edges"
            ) ?? 0
            let latest = try Double.fetchOne(
                db, sql: "SELECT COALESCE(MAX(created_at), 0) FROM memories WHERE superseded_by IS NULL"
            ) ?? 0
            return MemoryRevision(memoryCount: memoryCount, edgeCount: edgeCount, latestCreatedAt: latest)
        }
    }

    /// Everything, newest first, for the Settings review/delete surface.
    /// Visibility is the other half of consent.
    public func allMemories(includeSuperseded: Bool = false, limit: Int = 500) throws -> [Memory] {
        // Two whole literals, not interpolation — there's no user input here, but
        // building SQL by string concat is a pattern not worth seeding.
        let sql = includeSuperseded
            ? "SELECT * FROM memories ORDER BY created_at DESC LIMIT ?"
            : "SELECT * FROM memories WHERE superseded_by IS NULL ORDER BY created_at DESC LIMIT ?"
        return try dbQueue.read { db in
            let rows = try Row.fetchAll(db, sql: sql, arguments: [limit])
            return rows.compactMap { Self.memory(from: $0) }
        }
    }

    /// Fetch a single memory by id, live or superseded. The app layer needs this
    /// to surface a just-written fact (e.g. echo back an MCP `remember`) without
    /// scanning the whole store.
    public func memory(id: UUID) throws -> Memory? {
        try dbQueue.read { db in
            let row = try Row.fetchOne(
                db, sql: "SELECT * FROM memories WHERE id = ?", arguments: [id.uuidString]
            )
            return row.flatMap { Self.memory(from: $0) }
        }
    }

    /// Every edge in the graph, oldest-first — the bulk read the constellation
    /// view needs to draw threads (recall/`related` only walk from a seed).
    public func allEdges(limit: Int = 5000) throws -> [MemoryEdge] {
        try dbQueue.read { db in
            let rows = try Row.fetchAll(
                db,
                sql: """
                SELECT from_id, to_id, relation, created_at FROM memory_edges
                ORDER BY created_at ASC LIMIT ?
                """,
                arguments: [limit]
            )
            return rows.compactMap { row -> MemoryEdge? in
                guard let from: String = row["from_id"], let fromID = UUID(uuidString: from),
                      let to: String = row["to_id"], let toID = UUID(uuidString: to)
                else { return nil }
                return MemoryEdge(
                    fromID: fromID, toID: toID,
                    relation: row["relation"] ?? "",
                    createdAt: Date(timeIntervalSince1970: row["created_at"] ?? 0)
                )
            }
        }
    }

    // MARK: - Row mapping

    private static func memory(from row: Row) -> Memory? {
        guard let raw: String = row["id"], let id = UUID(uuidString: raw) else { return nil }
        return Memory(
            id: id,
            kind: MemoryKind(rawValue: row["kind"] ?? "note"),
            text: row["text"] ?? "",
            source: row["source"] ?? "unknown",
            createdAt: Date(timeIntervalSince1970: row["created_at"] ?? 0),
            supersededBy: (row["superseded_by"] as String?).flatMap(UUID.init(uuidString:))
        )
    }

    /// Same double-quoting sanitiser as KnowledgeStore (would be shared if
    /// this graduates — lift sanitizeFTSQuery into a common home).
    private static func sanitizeFTSQuery(_ query: String) -> String? {
        let tokens = query
            .components(separatedBy: .whitespacesAndNewlines)
            .map { $0.replacingOccurrences(of: "\"", with: "") }
            .filter { !$0.isEmpty }
        guard !tokens.isEmpty else { return nil }
        return tokens.map { "\"\($0)\"" }.joined(separator: " ")
    }
}
