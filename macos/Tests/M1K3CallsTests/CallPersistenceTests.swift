//
//  CallPersistenceTests.swift
//  M1K3CallsTests
//
//  Round-trip + the privacy guarantee that matters: with an EncryptedCallCoder,
//  nothing sensitive is readable in the database file (verified by reopening the
//  raw SQLite and scanning the stored bytes for plaintext).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import CryptoKit
import Foundation
import GRDB
@testable import M1K3Calls
import Testing

private func sampleCall(title: String = "Billing call", at seconds: TimeInterval = 0) -> CallSession {
    CallSession(
        startedAt: Date(timeIntervalSince1970: seconds),
        title: title,
        segments: [
            CallTranscriptSegment(text: "I was double charged for the hydraulic pump", startTime: 0, speaker: "Customer"),
            CallTranscriptSegment(text: "Issuing a refund now", startTime: 4, speaker: "Agent"),
        ],
        speakers: [SpeakerSegment(speakerId: "Customer", startTime: 0, endTime: 3)],
        fullSummary: CallSummary(overview: "Double charge; refund issued.", actionItems: ["Refund"])
    )
}

struct CallPersistenceTests {
    @Test("save then load round-trips the full session")
    func roundTrip() throws {
        let store = try GRDBCallPersistence()
        let call = sampleCall()
        try store.save(call)
        let loaded = try store.load(id: call.id)
        #expect(loaded == call)
    }

    @Test("loading an absent id returns nil, not an error")
    func missingIsNil() throws {
        let store = try GRDBCallPersistence()
        #expect(try store.load(id: UUID()) == nil)
    }

    @Test("loadAll returns calls newest-first")
    func loadAllOrdered() throws {
        let store = try GRDBCallPersistence()
        let older = sampleCall(title: "older", at: 100)
        let newer = sampleCall(title: "newer", at: 200)
        try store.save(older)
        try store.save(newer)
        let all = try store.loadAll()
        #expect(all.map(\.title) == ["newer", "older"])
    }

    @Test("save is upsert — re-saving the same id replaces, not duplicates")
    func upsert() throws {
        let store = try GRDBCallPersistence()
        let call = sampleCall()
        try store.save(call)
        try store.save(call)
        #expect(try store.loadAll().count == 1)
    }

    @Test("delete removes the call and reports it")
    func delete() throws {
        let store = try GRDBCallPersistence()
        let call = sampleCall()
        try store.save(call)
        #expect(try store.delete(id: call.id) == true)
        #expect(try store.load(id: call.id) == nil)
        #expect(try store.delete(id: call.id) == false) // already gone
    }

    @Test("encrypted round-trip works through the store")
    func encryptedRoundTrip() throws {
        let store = try GRDBCallPersistence(coder: EncryptedCallCoder(key: SymmetricKey(size: .bits256)))
        let call = sampleCall()
        try store.save(call)
        #expect(try store.load(id: call.id) == call)
    }

    @Test("ENCRYPTION AT REST: the database file holds no plaintext title or transcript")
    func encryptedAtRest() throws {
        let path = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".sqlite").path
        defer { try? FileManager.default.removeItem(atPath: path) }

        let store = try GRDBCallPersistence(path: path, coder: EncryptedCallCoder(key: SymmetricKey(size: .bits256)))
        try store.save(sampleCall())

        // Reopen the raw database — a thief with the file but not the key.
        let raw = try DatabaseQueue(path: path)
        let payload: Data = try raw.read { db in
            try Row.fetchOne(db, sql: "SELECT payload FROM call_sessions")!["payload"]
        }
        #expect(payload.range(of: Data("Billing call".utf8)) == nil)
        #expect(payload.range(of: Data("hydraulic pump".utf8)) == nil)
        #expect(payload.range(of: Data("refund".utf8)) == nil)
    }

    @Test("positive control: plain JSON storage DOES leak the title (proves the scan works)")
    func plainLeaks() throws {
        let path = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".sqlite").path
        defer { try? FileManager.default.removeItem(atPath: path) }

        let store = try GRDBCallPersistence(path: path) // JSON coder
        try store.save(sampleCall())
        let raw = try DatabaseQueue(path: path)
        let payload: Data = try raw.read { db in
            try Row.fetchOne(db, sql: "SELECT payload FROM call_sessions")!["payload"]
        }
        #expect(payload.range(of: Data("Billing call".utf8)) != nil)
    }
}

struct CallSessionCoderTests {
    @Test("a wrong key fails to decode (authenticated encryption)")
    func wrongKeyFails() throws {
        let coder = EncryptedCallCoder(key: SymmetricKey(size: .bits256))
        let bytes = try coder.encode(sampleCall())
        let attacker = EncryptedCallCoder(key: SymmetricKey(size: .bits256))
        #expect(throws: CallPersistenceError.self) { _ = try attacker.decode(bytes) }
    }

    @Test("ciphertext does not equal the plaintext JSON")
    func ciphertextDiffers() throws {
        let call = sampleCall()
        let plain = try JSONCallCoder().encode(call)
        let cipher = try EncryptedCallCoder(key: SymmetricKey(size: .bits256)).encode(call)
        #expect(cipher != plain)
    }
}
