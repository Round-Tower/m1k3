//
//  TranscriptMigratorTests.swift
//  M1K3ChatTests
//
//  One-shot migration of the legacy single-transcript JSON into the
//  conversation store. Rename-not-delete (recoverable), idempotent, and
//  never imports into a store that already has conversations.
//

import Foundation
@testable import M1K3Chat
import Testing

private func tempDir() throws -> URL {
    let dir = FileManager.default.temporaryDirectory
        .appendingPathComponent("migrator-tests-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir
}

private func writeLegacy(_ messages: [ChatMessage], at url: URL) throws {
    try JSONEncoder().encode(messages).write(to: url)
}

private func legacyMessages() -> [ChatMessage] {
    [
        ChatMessage(role: .user, text: "hello", status: .complete),
        ChatMessage(role: .assistant, text: "Hi! What are we making today?", status: .complete),
    ]
}

struct TranscriptMigratorTests {
    @Test("a legacy transcript imports as one conversation and the file is renamed")
    func happyPath() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let legacy = dir.appendingPathComponent("transcript.json")
        let original = legacyMessages() // capture ONCE — ids are minted per call
        try writeLegacy(original, at: legacy)
        let store = try GRDBChatHistoryStore()

        let imported = try TranscriptMigrator.migrateIfNeeded(legacyURL: legacy, into: store)

        let id = try #require(imported)
        #expect(try store.loadMessages(id: id) == original)
        #expect(try store.list().count == 1)
        #expect(try store.list().first?.title == nil)
        #expect(!FileManager.default.fileExists(atPath: legacy.path))
        #expect(FileManager.default.fileExists(atPath: legacy.path + ".migrated"))
    }

    @Test("a store that already has conversations never imports — but the file is still retired")
    func storeNotEmpty() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let legacy = dir.appendingPathComponent("transcript.json")
        try writeLegacy(legacyMessages(), at: legacy)
        let store = try GRDBChatHistoryStore()
        try store.save(id: UUID(), messages: legacyMessages(), updatedAt: Date())

        let imported = try TranscriptMigrator.migrateIfNeeded(legacyURL: legacy, into: store)

        #expect(imported == nil)
        #expect(try store.list().count == 1)
        #expect(!FileManager.default.fileExists(atPath: legacy.path))
    }

    @Test("no legacy file is a quiet no-op")
    func noFile() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let store = try GRDBChatHistoryStore()
        let imported = try TranscriptMigrator.migrateIfNeeded(
            legacyURL: dir.appendingPathComponent("transcript.json"), into: store
        )
        #expect(imported == nil)
        #expect(try store.list().isEmpty)
    }

    @Test("corrupt or empty legacy files create nothing but are still retired")
    func corruptAndEmpty() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let corrupt = dir.appendingPathComponent("corrupt.json")
        try Data("not json".utf8).write(to: corrupt)
        let empty = dir.appendingPathComponent("empty.json")
        try writeLegacy([], at: empty)
        let store = try GRDBChatHistoryStore()

        #expect(try TranscriptMigrator.migrateIfNeeded(legacyURL: corrupt, into: store) == nil)
        #expect(try TranscriptMigrator.migrateIfNeeded(legacyURL: empty, into: store) == nil)
        #expect(try store.list().isEmpty)
        #expect(!FileManager.default.fileExists(atPath: corrupt.path))
        #expect(!FileManager.default.fileExists(atPath: empty.path))
    }

    @Test("running twice is idempotent")
    func idempotent() throws {
        let dir = try tempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let legacy = dir.appendingPathComponent("transcript.json")
        try writeLegacy(legacyMessages(), at: legacy)
        let store = try GRDBChatHistoryStore()

        let first = try TranscriptMigrator.migrateIfNeeded(legacyURL: legacy, into: store)
        let second = try TranscriptMigrator.migrateIfNeeded(legacyURL: legacy, into: store)

        #expect(first != nil)
        #expect(second == nil)
        #expect(try store.list().count == 1)
    }
}
