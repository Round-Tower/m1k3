//
//  VoiceUpgradeOfferPolicy.swift
//  M1K3Voice
//
//  The M1K3 Voice earned moment. The built-in voice works out of the box, but
//  the neural "M1K3 Voice" (Kokoro) is a real differentiator that used to
//  live only behind a Settings row and a whispered caption. This decides the
//  honest time to offer it: after the user has HEARD the everyday voice in a
//  few real spoken exchanges — experience first, pitch second. Same respect
//  rules as the brain ladder: re-offers are earned by further use, never by
//  a timer, and two dismissals is a permanent answer.
//
//  Pure; the coordinator persists the two counters and supplies them.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.85 (thresholds are
//  product taste, pinned by tests). Prior: none (new file).

import Foundation

public enum VoiceUpgradeOfferPolicy {
    /// Spoken exchanges on the built-in voice that earn the first offer —
    /// enough to have genuinely heard it, few enough to catch the session.
    public static let exchangesToOffer = 3
    /// FURTHER exchanges after a dismissal that earn the single re-offer.
    public static let exchangesToReOffer = 5
    /// Dismissals after which the user has answered — terminal.
    public static let terminalDismissals = 2

    public static func shouldOffer(
        spokenExchanges: Int,
        m1k3VoiceActiveOrStaged: Bool,
        dismissals: Int,
        exchangesSinceLastDismissal: Int
    ) -> Bool {
        guard !m1k3VoiceActiveOrStaged else { return false }
        guard dismissals < terminalDismissals else { return false }
        if dismissals == 0 { return spokenExchanges >= exchangesToOffer }
        return exchangesSinceLastDismissal >= exchangesToReOffer
    }
}
