//
//  ChatTranscriptStoreTests.swift
//  M1K3ChatTests
//
//  The LEGACY single-transcript JSON store — now migration-only input for
//  TranscriptMigrator (ChatSession persists via ChatHistoryPersisting since
//  the multi-conversation refactor). Load/save round-trips stay pinned
//  because the migrator decodes through this exact type.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-11 — ChatSession integration test
//  superseded by ChatSessionConversationsTests (session no longer takes a
//  transcript store); file round-trip tests unchanged.

import Foundation
@testable import M1K3Chat
import M1K3Knowledge
import Testing

private func tempURL() -> URL {
    FileManager.default.temporaryDirectory
        .appendingPathComponent("m1k3-transcript-\(UUID().uuidString).json")
}

@MainActor
struct ChatTranscriptStoreTests {
    @Test("ChunkHit round-trips through Codable")
    func chunkHitCodable() throws {
        let hit = ChunkHit(
            chunkID: UUID(), itemID: UUID(), itemTitle: "Plant Notes",
            kind: .document, heading: "3.2 Seals", content: "the seal failed",
            similarity: 0.9, rrfScore: 0.5
        )
        let data = try JSONEncoder().encode(hit)
        let decoded = try JSONDecoder().decode(ChunkHit.self, from: data)
        #expect(decoded == hit)
    }

    @Test("save then load round-trips messages (incl. sources + failed status)")
    func roundTrip() {
        let url = tempURL()
        defer { try? FileManager.default.removeItem(at: url) }
        let store = ChatTranscriptStore(url: url)

        let source = ChunkHit(
            chunkID: UUID(), itemID: UUID(), itemTitle: "Doc",
            kind: .document, heading: nil, content: "body"
        )
        let messages = [
            ChatMessage(role: .user, text: "hi", status: .complete),
            ChatMessage(role: .assistant, text: "hello", sources: [source], status: .complete),
            ChatMessage(role: .assistant, text: "oops", status: .failed("boom")),
        ]
        store.save(messages)
        #expect(store.load() == messages)
    }

    @Test("loading a missing file returns empty, not a crash")
    func loadMissing() {
        let store = ChatTranscriptStore(url: tempURL())
        #expect(store.load().isEmpty)
    }
}
