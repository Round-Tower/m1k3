//
//  ChatHistoryStore.swift
//  M1K3Chat
//
//  Multi-conversation chat persistence: GRDB, blob-per-conversation (the
//  GRDBCallPersistence idiom — ChatSession already saves the whole message
//  array once per completed turn, conversations are small, and the UI never
//  queries individual messages). Plaintext columns are exactly what the
//  history drawer lists without decoding payloads: title + timestamps.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (round-trips,
//  upsert, ordering, file persistence all test-pinned).
//  Prior: GRDBCallPersistence idiom (Kev + claude-opus-4-8).
//

import Foundation
import GRDB

/// What the history drawer shows per conversation — no message payloads.
public struct ConversationSummary: Identifiable, Sendable, Equatable {
    public let id: UUID
    /// nil until auto-titled; the UI shows "New chat".
    public let title: String?
    public let createdAt: Date
    public let updatedAt: Date

    public init(id: UUID, title: String?, createdAt: Date, updatedAt: Date) {
        self.id = id
        self.title = title
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

/// The seam ChatSession persists through — in-memory fakes keep session tests
/// store-free; GRDBChatHistoryStore is the production conformer.
public protocol ChatHistoryPersisting: Sendable {
    /// Summaries ordered by recency (updatedAt DESC).
    func list() throws -> [ConversationSummary]
    /// nil when the conversation doesn't exist (≠ an existing empty one).
    func loadMessages(id: UUID) throws -> [ChatMessage]?
    /// Upsert: a new id creates the row (createdAt = updatedAt); an existing
    /// id replaces the payload and advances updatedAt, preserving createdAt.
    /// Synchronous — kept for the one-time launch migration (`TranscriptMigrator`
    /// inside `AppEnvironment`'s SYNCHRONOUS init) and the direct store tests.
    func save(id: UUID, messages: [ChatMessage], updatedAt: Date) throws
    /// Off-the-caller-actor upsert for the per-turn hot path (ChatSession is
    /// `@MainActor` — the O(conversation) encode + write must not land on it).
    /// Default hops the synchronous `save` onto a utility task; a store with a
    /// native async writer (GRDBChatHistoryStore → GRDB's `dbQueue.write { }`)
    /// overrides this so it suspends on the DB's own queue instead of parking a
    /// cooperative-pool thread. Same upsert semantics as `save`.
    func saveAsync(id: UUID, messages: [ChatMessage], updatedAt: Date) async throws
    /// No-op for unknown ids — titling races conversation deletion.
    func setTitle(id: UUID, title: String) throws
    @discardableResult
    func delete(id: UUID) throws -> Bool
    /// How many leading transcript messages the memory distiller has already
    /// processed (0 = never distilled; unknown id reads 0). NO protocol
    /// default on purpose: a silent no-op conformer would invisibly break
    /// double-distill protection.
    func distilledWatermark(id: UUID) throws -> Int
    /// Advance the watermark after a successful distillation. No-op for
    /// unknown ids (the setTitle precedent — distillation races deletion).
    func setDistilledWatermark(id: UUID, count: Int) throws
}

public extension ChatHistoryPersisting {
    /// Default `saveAsync`: run the synchronous `save` on a detached utility task
    /// so it never executes on the caller's actor (the MainActor, for ChatSession).
    /// Awaited by the caller, so ordering and post-write invariants are preserved.
    /// GRDBChatHistoryStore overrides this with a native async DB write; this
    /// default keeps every other conformer (the in-memory test fakes, any future
    /// non-GRDB store) honestly off-main without each re-implementing the hop.
    func saveAsync(id: UUID, messages: [ChatMessage], updatedAt: Date) async throws {
        try await Task.detached(priority: .utility) {
            try self.save(id: id, messages: messages, updatedAt: updatedAt)
        }.value
    }
}

public final class GRDBChatHistoryStore: ChatHistoryPersisting, @unchecked Sendable {
    private let dbQueue: DatabaseQueue

    /// - Parameter path: file path, or nil for an in-memory store (tests).
    public init(path: String? = nil) throws {
        dbQueue = try path.map { try DatabaseQueue(path: $0) } ?? DatabaseQueue()
        try migrate()
    }

    private func migrate() throws {
        var migrator = DatabaseMigrator()
        migrator.registerMigration("v1") { db in
            try db.create(table: "conversations") { table in
                table.column("id", .text).primaryKey()
                table.column("title", .text) // NULL until auto-titled
                table.column("created_at", .double).notNull()
                table.column("updated_at", .double).notNull().indexed()
                table.column("payload", .blob).notNull() // JSONEncoder([ChatMessage])
            }
        }
        // Memory-distillation watermark: leading messages already distilled.
        // Per-conversation state lives here (not knowledge_meta) so it dies
        // with its row on delete. DEFAULT 0 = legacy conversations distill
        // their history once at first exit (bootstraps memory).
        migrator.registerMigration("v2-distilled") { db in
            try db.alter(table: "conversations") { table in
                table.add(column: "distilled_count", .integer).notNull().defaults(to: 0)
            }
        }
        try migrator.migrate(dbQueue)
    }

    public func list() throws -> [ConversationSummary] {
        try dbQueue.read { db in
            let rows = try Row.fetchAll(
                db, sql: "SELECT id, title, created_at, updated_at FROM conversations ORDER BY updated_at DESC"
            )
            return rows.compactMap { row in
                guard let id = UUID(uuidString: row["id"]) else { return nil }
                return ConversationSummary(
                    id: id,
                    title: row["title"],
                    createdAt: Date(timeIntervalSince1970: row["created_at"]),
                    updatedAt: Date(timeIntervalSince1970: row["updated_at"])
                )
            }
        }
    }

    public func loadMessages(id: UUID) throws -> [ChatMessage]? {
        try dbQueue.read { db in
            guard let row = try Row.fetchOne(
                db, sql: "SELECT payload FROM conversations WHERE id = ?", arguments: [id.uuidString]
            ) else { return nil }
            let payload: Data = row["payload"]
            return try JSONDecoder().decode([ChatMessage].self, from: payload)
        }
    }

    public func save(id: UUID, messages: [ChatMessage], updatedAt: Date) throws {
        let payload = try JSONEncoder().encode(messages)
        try dbQueue.write { db in
            try db.execute(
                sql: """
                INSERT INTO conversations (id, title, created_at, updated_at, payload)
                VALUES (?, NULL, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET payload = excluded.payload, updated_at = excluded.updated_at
                """,
                arguments: [
                    id.uuidString,
                    updatedAt.timeIntervalSince1970,
                    updatedAt.timeIntervalSince1970,
                    payload,
                ]
            )
        }
    }

    /// Native async override of `saveAsync`: GRDB's `dbQueue.write { }` suspends
    /// the caller on the DB's own writer queue (no parked cooperative-pool thread,
    /// unlike the protocol default), and the O(conversation) JSON encode runs
    /// INSIDE the write closure so it too executes off the calling MainActor —
    /// the whole point of finding 25/30. Same upsert SQL as the sync `save`.
    public func saveAsync(id: UUID, messages: [ChatMessage], updatedAt: Date) async throws {
        try await dbQueue.write { db in
            let payload = try JSONEncoder().encode(messages)
            try db.execute(
                sql: """
                INSERT INTO conversations (id, title, created_at, updated_at, payload)
                VALUES (?, NULL, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET payload = excluded.payload, updated_at = excluded.updated_at
                """,
                arguments: [
                    id.uuidString,
                    updatedAt.timeIntervalSince1970,
                    updatedAt.timeIntervalSince1970,
                    payload,
                ]
            )
        }
    }

    public func setTitle(id: UUID, title: String) throws {
        try dbQueue.write { db in
            try db.execute(
                sql: "UPDATE conversations SET title = ? WHERE id = ?",
                arguments: [title, id.uuidString]
            )
        }
    }

    @discardableResult
    public func delete(id: UUID) throws -> Bool {
        try dbQueue.write { db in
            try db.execute(sql: "DELETE FROM conversations WHERE id = ?", arguments: [id.uuidString])
            return db.changesCount > 0
        }
    }

    public func distilledWatermark(id: UUID) throws -> Int {
        try dbQueue.read { db in
            try Int.fetchOne(
                db,
                sql: "SELECT distilled_count FROM conversations WHERE id = ?",
                arguments: [id.uuidString]
            ) ?? 0
        }
    }

    public func setDistilledWatermark(id: UUID, count: Int) throws {
        try dbQueue.write { db in
            try db.execute(
                sql: "UPDATE conversations SET distilled_count = ? WHERE id = ?",
                arguments: [count, id.uuidString]
            )
        }
    }
}
