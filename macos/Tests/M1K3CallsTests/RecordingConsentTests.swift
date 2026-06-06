//
//  RecordingConsentTests.swift
//  M1K3CallsTests
//
//  Recording calls carries consent obligations (one- vs all-party laws vary). M1K3
//  can't verify the other party, but it MUST make recording explicit + consented +
//  logged, and never start recording without an affirmation. This pins that gate.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Calls
import Testing

private final class FakeConsentStore: ConsentStore, @unchecked Sendable {
    private let lock = NSLock()
    private var remembered_ = false
    func remembered() -> Bool {
        lock.withLock { remembered_ }
    }

    func remember() {
        lock.withLock { remembered_ = true }
    }

    func forget() {
        lock.withLock { remembered_ = false }
    }
}

struct RecordingConsentTests {
    @Test("a fresh gate is not pre-authorised — recording must ask first")
    func freshGateAsks() {
        #expect(RecordingConsentGate(store: FakeConsentStore()).isPreAuthorised == false)
    }

    @Test("affirming for this call only does NOT pre-authorise future calls")
    func onceDoesNotPersist() {
        let gate = RecordingConsentGate(store: FakeConsentStore())
        let decision = gate.affirm(scope: .once, at: Date(timeIntervalSince1970: 0))
        #expect(decision.scope == .once)
        #expect(gate.isPreAuthorised == false)
    }

    @Test("affirming with remember pre-authorises subsequent recordings")
    func rememberPersists() {
        let store = FakeConsentStore()
        let gate = RecordingConsentGate(store: store)
        let decision = gate.affirm(scope: .remembered, at: Date(timeIntervalSince1970: 0))
        #expect(decision.scope == .remembered)
        #expect(gate.isPreAuthorised == true)
        // A fresh gate over the same store still sees the remembered consent.
        #expect(RecordingConsentGate(store: store).isPreAuthorised == true)
    }

    @Test("revoking consent forces the gate to ask again")
    func revokeReAsks() {
        let store = FakeConsentStore()
        let gate = RecordingConsentGate(store: store)
        _ = gate.affirm(scope: .remembered, at: Date(timeIntervalSince1970: 0))
        gate.revoke()
        #expect(gate.isPreAuthorised == false)
    }

    @Test("the decision carries the affirmation time for the audit trail")
    func decisionTimestamped() {
        let when = Date(timeIntervalSince1970: 1000)
        let decision = RecordingConsentGate(store: FakeConsentStore()).affirm(scope: .once, at: when)
        #expect(decision.affirmedAt == when)
    }
}
