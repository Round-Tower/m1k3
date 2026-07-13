//
//  AgeBandPolicyTests.swift
//  M1K3ChatTests
//
//  Pins the age-adaptive policy core (PLAN.md item 19). Non-negotiables:
//    - ZERO PII: the band is a coarse local enum, mapped from the Declared
//      Age Range API's bounds (gates 13/16/18) — never an age, never stored
//      beyond the enum's rawValue.
//    - Declining must NOT degrade: .undeclared is pinned byte-identical to
//      .adult (full capability, no clause). Age-assurance, not verification.
//    - Policy→prompt for tone, enforcement→code for safety: minor bands get
//      a prompt clause AND a hard web-tools gate (prompt alone is not a
//      safety boundary — the CanaryGuard doctrine).
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.9, Prior: Unknown
//

import Foundation
@testable import M1K3Chat
import Testing

struct AgeBandMappingTests {
    // The API returns bounds relative to our gates (13, 16, 18):
    // nil lowerBound = below the lowest gate; nil upperBound = at/above the highest.

    @Test func belowLowestGateIsUnder13() {
        #expect(AgeBand(lowerBound: nil, upperBound: 12) == .under13)
    }

    @Test func thirteenToFifteen() {
        #expect(AgeBand(lowerBound: 13, upperBound: 15) == .teen13to15)
    }

    @Test func sixteenToSeventeen() {
        #expect(AgeBand(lowerBound: 16, upperBound: 17) == .teen16to17)
    }

    @Test func openUpperBoundIsAdult() {
        #expect(AgeBand(lowerBound: 18, upperBound: nil) == .adult)
    }

    @Test func bothBoundsNilIsUndeclared() {
        // An ambiguous response carries no information — treat as undeclared
        // (full capability), never guess a minor band from nothing.
        #expect(AgeBand(lowerBound: nil, upperBound: nil) == .undeclared)
    }

    @Test func persistedRoundTripsAndUnknownDegradesToUndeclared() {
        for band in AgeBand.allCases {
            #expect(AgeBand(persisted: band.rawValue) == band)
        }
        #expect(AgeBand(persisted: nil) == .undeclared)
        #expect(AgeBand(persisted: "corrupted-junk") == .undeclared)
    }
}

struct AgeAppropriatenessPolicyTests {
    @Test func adultIsFullCapabilityWithNoClause() {
        let policy = AgeAppropriateness.policy(for: .adult)
        #expect(policy.webToolsAllowed)
        #expect(policy.promptClause == nil)
    }

    @Test func undeclaredIsIdenticalToAdult() {
        // THE brand pin: declining to share an age range must not degrade
        // the experience in any way.
        #expect(AgeAppropriateness.policy(for: .undeclared) == AgeAppropriateness.policy(for: .adult))
    }

    @Test func under16BandsGateWebToolsHard() {
        for band in [AgeBand.under13, .teen13to15] {
            let policy = AgeAppropriateness.policy(for: band)
            #expect(!policy.webToolsAllowed, "\(band) must gate web tools in code, not just prompt")
            #expect(policy.promptClause?.isEmpty == false)
        }
    }

    @Test func olderTeensKeepWebWithAClause() {
        let policy = AgeAppropriateness.policy(for: .teen16to17)
        #expect(policy.webToolsAllowed)
        #expect(policy.promptClause?.isEmpty == false)
    }

    @Test func minorClausesNeverMentionAgeNumbersOrTheAPI() {
        // The clause rides the DYNAMIC persona prompt — it must never leak
        // band mechanics ("13-15", "declared age range") into generations.
        for band in [AgeBand.under13, .teen13to15, .teen16to17] {
            let clause = AgeAppropriateness.policy(for: band).promptClause ?? ""
            let containsDigit = clause.contains { $0.isNumber }
            #expect(!containsDigit, "\(band) clause leaks a number")
            #expect(!clause.localizedCaseInsensitiveContains("age range"))
        }
    }
}
