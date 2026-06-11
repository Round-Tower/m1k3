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
    func save(id: UUID, messages: [ChatMessage], updatedAt: Date) throws
    /// No-op for unknown ids — titling races conversation deletion.
    func setTitle(id: UUID, title: String) throws
    @discardableResult
    func delete(id: UUID) throws -> Bool
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
            try db.create(table: "conversations") { t in
                t.column("id", .text).primaryKey()
                t.column("title", .text) // NULL until auto-titled
                t.column("created_at", .double).notNull()
                t.column("updated_at", .double).notNull().indexed()
                t.column("payload", .blob).notNull() // JSONEncoder([ChatMessage])
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
}
