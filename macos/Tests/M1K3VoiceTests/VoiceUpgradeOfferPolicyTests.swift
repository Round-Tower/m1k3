//
//  VoiceUpgradeOfferPolicyTests.swift
//  M1K3VoiceTests
//
//  The M1K3 Voice earned moment: after a few real spoken exchanges on the
//  built-in voice, offer the proper one — the user has EXPERIENCED what the
//  upgrade improves, which is the only honest time to sell it. Same respect
//  rules as the brain ladder: re-offers are earned by further use, never a
//  timer, and two dismissals is an answer.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.9. Prior: none (new file).

@testable import M1K3Voice
import Testing

struct VoiceUpgradeOfferPolicyTests {
    private func offer(
        spokenExchanges: Int = 3,
        m1k3VoiceActiveOrStaged: Bool = false,
        dismissals: Int = 0,
        exchangesSinceLastDismissal: Int = 0
    ) -> Bool {
        VoiceUpgradeOfferPolicy.shouldOffer(
            spokenExchanges: spokenExchanges,
            m1k3VoiceActiveOrStaged: m1k3VoiceActiveOrStaged,
            dismissals: dismissals,
            exchangesSinceLastDismissal: exchangesSinceLastDismissal
        )
    }

    @Test("three spoken exchanges on the built-in voice earn the first offer")
    func firstOfferAtThree() {
        #expect(!offer(spokenExchanges: 2))
        #expect(offer(spokenExchanges: 3))
    }

    @Test("never while M1K3 Voice is already active or downloading")
    func neverWhenAlreadyUpgraded() {
        #expect(!offer(m1k3VoiceActiveOrStaged: true))
        #expect(!offer(spokenExchanges: 50, m1k3VoiceActiveOrStaged: true))
    }

    @Test("after a dismissal, five FURTHER exchanges earn one re-offer")
    func reOfferIsEarnedByUse() {
        #expect(!offer(spokenExchanges: 20, dismissals: 1, exchangesSinceLastDismissal: 4))
        #expect(offer(spokenExchanges: 20, dismissals: 1, exchangesSinceLastDismissal: 5))
    }

    @Test("two dismissals is an answer — terminal, whatever the usage says")
    func twoDismissalsIsTerminal() {
        #expect(!offer(spokenExchanges: 100, dismissals: 2, exchangesSinceLastDismissal: 100))
    }
}
