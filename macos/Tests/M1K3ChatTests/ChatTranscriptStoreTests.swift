//
//  ChatTranscriptStoreTests.swift
//  M1K3ChatTests
//
//  Persisting the transcript so a relaunch doesn't lose the conversation. The
//  store is pure file I/O over Codable messages; ChatSession loads on init and
//  saves after each turn.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

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

    @Test("ChatSession loads prior history on init and appends to it")
    func sessionLoadsHistory() async {
        let url = tempURL()
        defer { try? FileManager.default.removeItem(at: url) }
        let store = ChatTranscriptStore(url: url)
        store.save([ChatMessage(role: .user, text: "earlier", status: .complete)])

        let responder = StubResponder()
        let session = ChatSession(responder: responder, transcript: store)
        #expect(session.messages.count == 1) // loaded the prior turn

        await session.send("now")
        #expect(session.messages.count == 3) // earlier + user + assistant

        // A fresh session reading the same file sees the persisted transcript.
        let reloaded = ChatSession(responder: responder, transcript: store)
        #expect(reloaded.messages.count == 3)
        #expect(reloaded.messages.first?.text == "earlier")
    }
}

private struct StubResponder: RAGResponding {
    func answerStreaming(
        _: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        ([], AsyncStream { $0.yield("ok"); $0.finish() })
    }
}
