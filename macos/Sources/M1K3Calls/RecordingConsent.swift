//
//  RecordingConsent.swift
//  M1K3Calls
//
//  The consent gate for call recording. Recording is never silent or implicit:
//  the user must affirm (per-call or remembered), and the affirmation is captured
//  with a timestamp for an audit trail. M1K3 can't verify the other party
//  consented — that's the user's responsibility under their jurisdiction — but it
//  guarantees recording is explicit, gated, and logged. Pure logic over a store
//  seam, so it's fully testable.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation

/// How long an affirmation lasts.
public enum ConsentScope: String, Sendable, Codable, Equatable {
    /// Only this recording — the gate asks again next time.
    case once
    /// Remembered until revoked — future recordings start without re-asking.
    case remembered
}

/// A logged consent affirmation (the audit trail entry).
public struct ConsentDecision: Sendable, Equatable, Codable {
    public let affirmedAt: Date
    public let scope: ConsentScope

    public init(affirmedAt: Date, scope: ConsentScope) {
        self.affirmedAt = affirmedAt
        self.scope = scope
    }
}

/// Persists whether the user has a remembered consent. Seam so the gate is testable.
public protocol ConsentStore: Sendable {
    func remembered() -> Bool
    func remember()
    func forget()
}

/// UserDefaults-backed remembered-consent store (the app default).
/// `@unchecked Sendable`: `UserDefaults` is documented thread-safe.
public struct UserDefaultsConsentStore: ConsentStore, @unchecked Sendable {
    private let defaults: UserDefaults
    private let key: String

    public init(defaults: UserDefaults = .standard, key: String = "dev.murphysig.M1K3.recordingConsent") {
        self.defaults = defaults
        self.key = key
    }

    public func remembered() -> Bool {
        defaults.bool(forKey: key)
    }

    public func remember() {
        defaults.set(true, forKey: key)
    }

    public func forget() {
        defaults.removeObject(forKey: key)
    }
}

/// Decides whether recording may start, and records affirmations.
public struct RecordingConsentGate: Sendable {
    private let store: any ConsentStore

    public init(store: any ConsentStore) {
        self.store = store
    }

    /// True when a remembered consent means recording can start without re-asking.
    public var isPreAuthorised: Bool {
        store.remembered()
    }

    /// Log an affirmation, persisting it when the scope is `.remembered`.
    @discardableResult
    public func affirm(scope: ConsentScope, at date: Date) -> ConsentDecision {
        if scope == .remembered { store.remember() }
        return ConsentDecision(affirmedAt: date, scope: scope)
    }

    /// Drop a remembered consent — the gate will ask again next time.
    public func revoke() {
        store.forget()
    }
}
