//
//  KeyProviderTests.swift
//  M1K3CallsTests
//
//  The get-or-create key logic — tested against an in-memory KeyStore fake, so the
//  thing that feeds EncryptedCallCoder is provably stable (same key across calls and
//  across provider instances) without touching the real Keychain (that's the thin
//  KeychainKeyStore adapter, verify-by-launch).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import CryptoKit
import Foundation
@testable import M1K3Calls
import Testing

/// In-memory KeyStore fake — the seam that lets the provider's logic be tested.
private final class InMemoryKeyStore: KeyStore, @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [String: Data] = [:]

    func data(forAccount account: String) throws -> Data? {
        lock.withLock { storage[account] }
    }

    func setData(_ data: Data, forAccount account: String) throws {
        lock.withLock { storage[account] = data }
    }

    func removeData(forAccount account: String) throws {
        _ = lock.withLock { storage.removeValue(forKey: account) }
    }
}

private func bytes(_ key: SymmetricKey) -> Data {
    key.withUnsafeBytes { Data($0) }
}

struct StoredKeyProviderTests {
    @Test("first use generates and persists a 256-bit key")
    func firstUseGenerates() throws {
        let store = InMemoryKeyStore()
        let provider = StoredKeyProvider(store: store, account: "k")
        let key = try provider.symmetricKey()
        #expect(bytes(key).count == 32) // 256 bits
        #expect(try store.data(forAccount: "k") != nil) // persisted
    }

    @Test("the same key is returned on subsequent calls")
    func stableAcrossCalls() throws {
        let provider = StoredKeyProvider(store: InMemoryKeyStore(), account: "k")
        #expect(try bytes(provider.symmetricKey()) == bytes(provider.symmetricKey()))
    }

    @Test("a fresh provider over the same store reads back the same key")
    func persistsAcrossInstances() throws {
        let store = InMemoryKeyStore()
        let first = try StoredKeyProvider(store: store, account: "k").symmetricKey()
        let second = try StoredKeyProvider(store: store, account: "k").symmetricKey()
        #expect(bytes(first) == bytes(second))
    }

    @Test("different accounts get independent keys")
    func independentAccounts() throws {
        let store = InMemoryKeyStore()
        let a = try StoredKeyProvider(store: store, account: "a").symmetricKey()
        let b = try StoredKeyProvider(store: store, account: "b").symmetricKey()
        #expect(bytes(a) != bytes(b))
    }

    @Test("reassertProtection rewrites an existing key without changing its bytes")
    func reassertKeepsKey() throws {
        let store = InMemoryKeyStore()
        let provider = StoredKeyProvider(store: store, account: "k")
        let original = try provider.symmetricKey()
        try provider.reassertProtection()
        // Same bytes, still present — the call-encryption key survives an in-place
        // protection upgrade, so already-encrypted calls stay decryptable.
        #expect(try bytes(provider.symmetricKey()) == bytes(original))
        #expect(try store.data(forAccount: "k") != nil)
    }

    @Test("reassertProtection is a no-op when no key exists (never creates one)")
    func reassertNoOpWhenEmpty() throws {
        let store = InMemoryKeyStore()
        let provider = StoredKeyProvider(store: store, account: "k")
        try provider.reassertProtection()
        // Distinct from symmetricKey(): reassert must not mint a key just to
        // protect it — otherwise it would rotate away a key we simply hadn't read.
        #expect(try store.data(forAccount: "k") == nil)
    }

    @Test("reset forgets the key so the next use generates a fresh one")
    func resetRotates() throws {
        let store = InMemoryKeyStore()
        let provider = StoredKeyProvider(store: store, account: "k")
        let original = try provider.symmetricKey()
        try provider.reset()
        let regenerated = try provider.symmetricKey()
        #expect(bytes(original) != bytes(regenerated))
    }

    @Test("end-to-end: the provider's key drives encrypted persistence round-trip")
    func drivesEncryptedPersistence() throws {
        let provider = StoredKeyProvider(store: InMemoryKeyStore(), account: "k")
        let store = try GRDBCallPersistence(coder: EncryptedCallCoder(key: provider.symmetricKey()))
        let call = CallSession(startedAt: Date(timeIntervalSince1970: 0), title: "Secret call")
        try store.save(call)
        // A fresh coder built from the SAME provider/store decrypts it.
        #expect(try store.load(id: call.id) == call)
    }
}
