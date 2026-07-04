//
//  IntroductionOfferPolicyTests.swift
//  M1K3InferenceTests
//
//  The user-intro earned moment. The old You step asked a stranger for a
//  biography at the door; the one-screen hello cut it, which left NOTHING
//  ever inviting the intro again — the same built-it-but-never-point-at-it
//  gap the capability ladder fixed for downloads. Rules:
//    · earned: only after a few real exchanges (M1K3 has proven himself)
//    · only when M1K3 genuinely doesn't know the user (a name alone is not
//      knowing someone; real notes are)
//    · ONE dismissal is terminal — a repeated "tell me about yourself" is
//      creepy, not caring. Settings → About You remains the manual path.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.9. Prior: none (new file).

@testable import M1K3Inference
import Testing

struct IntroductionOfferPolicyTests {
    // MARK: - shouldOffer

    @Test("three completed turns with an unknown user earn the invitation")
    func earnedAtThreeTurns() {
        #expect(!IntroductionOfferPolicy.shouldOffer(profileIsSubstantial: false, completedTurns: 2, dismissed: false))
        #expect(IntroductionOfferPolicy.shouldOffer(profileIsSubstantial: false, completedTurns: 3, dismissed: false))
    }

    @Test("a substantial profile → M1K3 already knows them, never ask")
    func knownUserNeverAsked() {
        #expect(!IntroductionOfferPolicy.shouldOffer(profileIsSubstantial: true, completedTurns: 10, dismissed: false))
    }

    @Test("one dismissal is terminal — asking twice is creepy, not caring")
    func singleDismissalIsTerminal() {
        #expect(!IntroductionOfferPolicy.shouldOffer(profileIsSubstantial: false, completedTurns: 99, dismissed: true))
    }

    // MARK: - profileIsSubstantial (a name alone is not knowing someone)

    @Test("empty or whitespace profiles are not substantial")
    func emptyIsNotSubstantial() {
        #expect(!IntroductionOfferPolicy.profileIsSubstantial(nil))
        #expect(!IntroductionOfferPolicy.profileIsSubstantial(""))
        #expect(!IntroductionOfferPolicy.profileIsSubstantial("   \n"))
    }

    @Test("HelloView's name-only seed is not substantial")
    func nameOnlyIsNotSubstantial() {
        #expect(!IntroductionOfferPolicy.profileIsSubstantial("Name: Kev."))
        #expect(!IntroductionOfferPolicy.profileIsSubstantial("Name: Kev O'Brien."))
        #expect(!IntroductionOfferPolicy.profileIsSubstantial("  Name: Kev.  "))
    }

    @Test("name plus notes, or notes alone, IS substantial")
    func notesAreSubstantial() {
        #expect(IntroductionOfferPolicy.profileIsSubstantial("Name: Kev. Dyslexic engineer, builds edtech."))
        #expect(IntroductionOfferPolicy.profileIsSubstantial("I'm a cartographer in Cork."))
    }

    @Test("ambiguous dotted names read as substantial — fail toward silence, never toward a wrong ask")
    func ambiguityFailsTowardSilence() {
        #expect(IntroductionOfferPolicy.profileIsSubstantial("Name: Dr. Kev."))
    }
}
