//
//  CallPersistence.swift
//  M1K3Calls
//
//  The storage seam for call sessions — save / load / list / delete. A protocol so
//  the store is swappable and testable; the concrete GRDB store conforms. Privacy
//  is a property of the *coder* (plain JSON vs AES-GCM), not the store, so "encrypted
//  at rest" is a one-line swap with no store changes.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project CallPersistence (Kev) — neutral, coder-pluggable.

import Foundation

public protocol CallPersistence: Sendable {
    /// Insert or replace a call by id.
    func save(_ session: CallSession) throws
    /// Load one call, or nil if absent.
    func load(id: UUID) throws -> CallSession?
    /// All calls, newest first.
    func loadAll() throws -> [CallSession]
    /// Delete a call; returns whether a row was removed.
    @discardableResult
    func delete(id: UUID) throws -> Bool

    /// Number of stored calls. Stores should override with a cheap `COUNT(*)` —
    /// the default decodes (and so decrypts) every row just to count, which is the
    /// thing we're trying to avoid on the hot count path.
    func count() throws -> Int
}

public extension CallPersistence {
    func count() throws -> Int {
        try loadAll().count
    }
}

public enum CallPersistenceError: Error, Sendable, Equatable {
    case encryptionFailed
    case decodingFailed
}
