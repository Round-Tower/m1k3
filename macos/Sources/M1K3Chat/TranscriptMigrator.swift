//
//  TranscriptMigrator.swift
//  M1K3Chat
//
//  One-shot bridge from the legacy single-transcript JSON (transcript.json)
//  to the conversation store. Rename-not-delete: the original survives as
//  `transcript.json.migrated` so nothing is ever lost to a migration bug.
//  Renaming ALSO happens when no import occurs (non-empty store, corrupt
//  file) so the question never re-arises on the next launch.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (all paths
//  test-pinned with temp dirs + in-memory stores). Prior: Unknown.
//

import Foundation

public enum TranscriptMigrator {
    /// Returns the imported conversation's id, or nil when nothing imported.
    @discardableResult
    public static func migrateIfNeeded(
        legacyURL: URL,
        into store: any ChatHistoryPersisting
    ) throws -> UUID? {
        let fileManager = FileManager.default
        guard fileManager.fileExists(atPath: legacyURL.path) else { return nil }
        defer { retire(legacyURL, fileManager: fileManager) }

        guard try store.list().isEmpty else { return nil }
        let messages = ChatTranscriptStore(url: legacyURL).load()
        guard !messages.isEmpty else { return nil }

        // The file's mtime is the closest thing to "when this chat last moved".
        let modified = (try? fileManager.attributesOfItem(atPath: legacyURL.path)[.modificationDate] as? Date)
            .flatMap { $0 } ?? Date()
        let id = UUID()
        try store.save(id: id, messages: messages, updatedAt: modified)
        return id
    }

    private static func retire(_ url: URL, fileManager: FileManager) {
        let retired = url.appendingPathExtension("migrated")
        try? fileManager.removeItem(at: retired) // stale leftover from a crash
        try? fileManager.moveItem(at: url, to: retired)
    }
}
