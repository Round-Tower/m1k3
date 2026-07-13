//
//  AgeBandPolicy.swift
//  M1K3Chat
//
//  Privacy-preserving age-adaptive behaviour (PLAN.md item 19) — the pure,
//  unit-pinned core. The band comes from Apple's Declared Age Range API
//  (gates 13/16/18 → four ranges + declined); this file never sees an age,
//  a birthdate, or the API itself — only coarse bounds.
//
//  Doctrine (mirrors GlyphTreatment/StartupVisibility: pure policy, app wires):
//    - Policy→prompt for tone (the clause rides the DYNAMIC persona seam —
//      never baked into weights or the static persona).
//    - Enforcement→code for safety: minor bands hard-gate the web tools;
//      the prompt alone is not a safety boundary (CanaryGuard doctrine).
//    - Age-ASSURANCE, not verification: .undeclared (declined / unavailable /
//      ambiguous) is byte-identical to .adult. Declining must not degrade.
//    - ZERO PII: persisted at most as this enum's rawValue; unknown persisted
//      values degrade to .undeclared — never guess a minor band, never crash.
//
//  The DeclaredAgeRange request flow (entitlement + sheet) is the app-target
//  follow-up; it maps AgeRangeService.Response → AgeBand at the boundary,
//  routing BOTH .declinedSharing and Error.notAvailable here as .undeclared
//  (Apple's docs are internally inconsistent about which fires on decline).
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.9, Prior: Unknown
//

import Foundation

/// A coarse life-stage band — the ONLY age-related fact M1K3 ever holds.
public enum AgeBand: String, CaseIterable, Codable, Sendable {
    case under13
    case teen13to15
    case teen16to17
    case adult
    /// Declined, unavailable, or ambiguous — full capability, by principle.
    case undeclared

    /// Map the Declared Age Range response bounds (requested gates 13/16/18)
    /// to a band. `lowerBound == nil` means below the lowest gate;
    /// `upperBound == nil` means at/above the highest.
    public init(lowerBound: Int?, upperBound: Int?) {
        switch (lowerBound, upperBound) {
        case (nil, let .some(upper)) where upper < 13: self = .under13
        case (.some(13 ... 15), _): self = .teen13to15
        case (.some(16 ... 17), _): self = .teen16to17
        case let (.some(lower), _) where lower >= 18: self = .adult
        default:
            // (nil, nil) or malformed bounds carry no information — never
            // guess a minor band from nothing, never degrade an adult.
            self = .undeclared
        }
    }

    /// Restore from the persisted rawValue; unknown/absent → .undeclared.
    public init(persisted: String?) {
        self = persisted.flatMap(AgeBand.init(rawValue:)) ?? .undeclared
    }
}

/// What a band means for this turn: a hard tool gate + an optional clause
/// for the dynamic persona prompt.
public struct AgeAppropriateness: Equatable, Sendable {
    public let webToolsAllowed: Bool
    /// Injected into the DYNAMIC persona section. Must never name numbers,
    /// bands, or the API (pinned) — tone guidance only.
    public let promptClause: String?

    public static func policy(for band: AgeBand) -> AgeAppropriateness {
        switch band {
        case .under13, .teen13to15:
            AgeAppropriateness(
                webToolsAllowed: false,
                promptClause: """
                You're talking with a young person. Keep a warm, encouraging, \
                age-appropriate register; steer gently away from mature themes \
                and toward curiosity, learning, and kindness.
                """
            )
        case .teen16to17:
            AgeAppropriateness(
                webToolsAllowed: true,
                promptClause: """
                You're talking with a younger user. Keep the register \
                age-appropriate and steer away from mature themes.
                """
            )
        case .adult, .undeclared:
            AgeAppropriateness(webToolsAllowed: true, promptClause: nil)
        }
    }
}
