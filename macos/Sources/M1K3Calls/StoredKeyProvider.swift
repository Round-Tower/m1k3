//
//  StoredKeyProvider.swift
//  M1K3Calls
//
//  Get-or-create the call-encryption key: on first use, generate a random 256-bit
//  key and persist it to a KeyStore (Keychain in the app); thereafter, read it
//  back so encrypted call data stays decryptable across launches. This is the
//  missing half of the encryption story — `EncryptedCallCoder` needs a stable key,
//  and this is what produces one. Pure logic over the KeyStore seam, so it's fully
//  unit-tested with no Keychain.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import CryptoKit
import Foundation

public struct StoredKeyProvider: Sendable {
    private let store: any KeyStore
    private let account: String

    public init(store: any KeyStore, account: String = "dev.murphysig.M1K3.call-encryption-key") {
        self.store = store
        self.account = account
    }

    /// The persisted 256-bit key, generating + storing one on first use. Stable
    /// across calls and launches as long as the store keeps the bytes.
    public func symmetricKey() throws -> SymmetricKey {
        if let data = try store.data(forAccount: account) {
            return SymmetricKey(data: data)
        }
        let key = SymmetricKey(size: .bits256)
        try store.setData(key.withUnsafeBytes { Data($0) }, forAccount: account)
        return key
    }

    /// Rewrite the stored key under the store's CURRENT protection policy, without
    /// changing its bytes. This upgrades a key written before biometric protection
    /// was added (plain `kSecAttrAccessible`) to a Touch-ID-gated item in place, so
    /// existing encrypted calls stay decryptable. A no-op when no key exists — it
    /// must never mint a key just to protect it (that would rotate one we simply
    /// hadn't read yet).
    ///
    /// The key bytes never change, so this is safe to call more than once — but it
    /// is NOT free to repeat: against a biometric store every call reads (one Touch
    /// ID prompt) and rewrites the item. Run it ONCE, behind a migration flag, not
    /// on every launch.
    public func reassertProtection() throws {
        guard let data = try store.data(forAccount: account) else { return }
        try store.setData(data, forAccount: account)
    }

    /// Forget the stored key. The next `symmetricKey()` generates a fresh one —
    /// which makes any data encrypted under the old key unrecoverable, so this is
    /// a deliberate "burn it" / rotation primitive, not routine.
    public func reset() throws {
        try store.removeData(forAccount: account)
    }
}
