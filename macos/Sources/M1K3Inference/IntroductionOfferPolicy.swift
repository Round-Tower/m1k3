//
//  IntroductionOfferPolicy.swift
//  M1K3Inference
//
//  The user-intro earned moment. The one-screen hello deliberately stopped
//  asking a stranger for a biography at the door — but that left nothing ever
//  inviting the intro again (the same built-it-but-never-point-at-it gap the
//  capability ladder fixed for downloads). This decides the honest moment for
//  ONE invitation to introduce yourself: after a few real exchanges, only
//  when M1K3 genuinely doesn't know the user, and never twice — a repeated
//  "tell me about yourself" is creepy, not caring. The intro itself is a
//  CONVERSATION (memory auto-capture learns from it), not a form.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.9. Prior: none (new file).

import Foundation

public enum IntroductionOfferPolicy {
    /// Real exchanges before the invitation — M1K3 earns the ask by being
    /// useful first.
    public static let turnsToOffer = 3

    public static func shouldOffer(
        profileIsSubstantial: Bool,
        completedTurns: Int,
        dismissed: Bool
    ) -> Bool {
        !profileIsSubstantial && !dismissed && completedTurns >= turnsToOffer
    }

    /// Whether the persona's About-the-user blob amounts to KNOWING the user.
    /// A bare name (HelloView's "Name: X." seed) doesn't; notes do. Ambiguous
    /// shapes (dots inside the name) read as substantial — fail toward
    /// silence, never toward asking someone we already know.
    public static func profileIsSubstantial(_ profile: String?) -> Bool {
        guard let trimmed = profile?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty
        else { return false }
        // HelloView writes exactly "Name: <name>." — strip that shape; any
        // remainder is real notes.
        guard trimmed.hasPrefix("Name:") else { return true }
        guard let dot = trimmed.firstIndex(of: ".") else { return true }
        let remainder = trimmed[trimmed.index(after: dot)...]
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return !remainder.isEmpty
    }
}
