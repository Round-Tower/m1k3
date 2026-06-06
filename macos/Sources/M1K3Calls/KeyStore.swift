//
//  KeyStore.swift
//  M1K3Calls
//
//  The low-level secret-storage seam: bytes in/out by account. A protocol so the
//  key-management LOGIC (StoredKeyProvider) is testable against a fake, while the
//  real Keychain access lives in one thin, verify-by-launch adapter. Mirrors the
//  pattern used everywhere in M1K3 — isolate the OS dependency behind a seam.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
import Security

public protocol KeyStore: Sendable {
    func data(forAccount account: String) throws -> Data?
    func setData(_ data: Data, forAccount account: String) throws
    func removeData(forAccount account: String) throws
}

public enum KeyStoreError: Error, Sendable, Equatable {
    case unexpectedStatus(OSStatus)
}

/// Keychain-backed secret storage (generic-password items). Device-only and only
/// readable after first unlock — never synced to iCloud — matching M1K3's
/// privacy-first stance. Thin OS adapter: verified by launching the app, not by
/// `swift test` (the logic that uses it is tested via the in-memory fake).
public struct KeychainKeyStore: KeyStore {
    private let service: String

    public init(service: String = "dev.murphysig.M1K3") {
        self.service = service
    }

    public func data(forAccount account: String) throws -> Data? {
        var query = baseQuery(account)
        query[kSecReturnData] = kCFBooleanTrue
        query[kSecMatchLimit] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess: return item as? Data
        case errSecItemNotFound: return nil
        default: throw KeyStoreError.unexpectedStatus(status)
        }
    }

    public func setData(_ data: Data, forAccount account: String) throws {
        // Upsert: update an existing item, else add a new one.
        let update = SecItemUpdate(
            baseQuery(account) as CFDictionary,
            [kSecValueData: data] as CFDictionary
        )
        if update == errSecSuccess { return }
        guard update == errSecItemNotFound else { throw KeyStoreError.unexpectedStatus(update) }

        var add = baseQuery(account)
        add[kSecValueData] = data
        add[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let addStatus = SecItemAdd(add as CFDictionary, nil)
        guard addStatus == errSecSuccess else { throw KeyStoreError.unexpectedStatus(addStatus) }
    }

    public func removeData(forAccount account: String) throws {
        let status = SecItemDelete(baseQuery(account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeyStoreError.unexpectedStatus(status)
        }
    }

    private func baseQuery(_ account: String) -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
        ]
    }
}
