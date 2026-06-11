//
//  AppEnvironment+ChatHistory.swift
//  M1K3App
//
//  Chat-history store factory — its own file because AppEnvironment.swift
//  sits at the 1000-line file-length lint ceiling (the established split
//  pattern: VoiceMode, MCP, now ChatHistory).
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (store + migrator
//  behaviour test-pinned in M1K3Chat; this is wiring). Prior: Unknown.
//

import Foundation
import M1K3Chat

extension AppEnvironment {
    /// Build the conversation store and run the one-shot legacy-transcript
    /// migration BEFORE ChatSession init reads it (resume-most-recent must see
    /// the import). nil on store failure → chat degrades to non-persistent,
    /// exactly like the old optional transcript.
    static func makeChatHistoryStore(in dir: URL) -> (any ChatHistoryPersisting)? {
        guard let store = try? GRDBChatHistoryStore(
            path: dir.appendingPathComponent("chat-history.sqlite").path
        ) else { return nil }
        try? TranscriptMigrator.migrateIfNeeded(
            legacyURL: dir.appendingPathComponent("transcript.json"),
            into: store
        )
        return store
    }
}
