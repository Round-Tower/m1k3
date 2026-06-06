//
//  CallSessionCoder.swift
//  M1K3Calls
//
//  Serialises a CallSession to/from bytes for storage. Two impls: JSON (plain) and
//  AES-256-GCM encryption wrapping any inner coder. The encrypted coder is the
//  privacy-by-default path — the GRDB store persists whatever bytes the coder
//  produces, so swapping JSON → Encrypted encrypts calls at rest with zero store
//  changes. The key is injected (the app fetches/generates a Keychain key; tests
//  pass a fixed key), so the crypto is deterministically testable headless.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project EncryptedCallPersistence (Kev, concept) — refactored to
//  a pluggable coder rather than a persistence decorator.

import CryptoKit
import Foundation

public protocol CallSessionCoder: Sendable {
    func encode(_ session: CallSession) throws -> Data
    func decode(_ data: Data) throws -> CallSession
}

/// Plain JSON — readable on disk. Fine for non-sensitive use; wrap in
/// `EncryptedCallCoder` for at-rest privacy.
public struct JSONCallCoder: CallSessionCoder {
    public init() {}

    public func encode(_ session: CallSession) throws -> Data {
        try JSONEncoder().encode(session)
    }

    public func decode(_ data: Data) throws -> CallSession {
        do { return try JSONDecoder().decode(CallSession.self, from: data) }
        catch { throw CallPersistenceError.decodingFailed }
    }
}

/// AES-256-GCM over an inner coder's bytes (default JSON). Authenticated: a wrong
/// key or tampered ciphertext fails to decode rather than returning garbage.
public struct EncryptedCallCoder: CallSessionCoder {
    private let key: SymmetricKey
    private let inner: CallSessionCoder

    public init(key: SymmetricKey, inner: CallSessionCoder = JSONCallCoder()) {
        self.key = key
        self.inner = inner
    }

    public func encode(_ session: CallSession) throws -> Data {
        let plaintext = try inner.encode(session)
        let sealed = try AES.GCM.seal(plaintext, using: key)
        guard let combined = sealed.combined else { throw CallPersistenceError.encryptionFailed }
        return combined
    }

    public func decode(_ data: Data) throws -> CallSession {
        do {
            let box = try AES.GCM.SealedBox(combined: data)
            let plaintext = try AES.GCM.open(box, using: key)
            return try inner.decode(plaintext)
        } catch let error as CallPersistenceError {
            throw error
        } catch {
            throw CallPersistenceError.decodingFailed
        }
    }
}
