//
//  DiagnosticRedactor.swift
//  M1K3Diagnostics
//
//  Privacy scrub for diagnostic text before it leaves the Mac in an issue
//  report. M1K3's promise is "nothing leaves this device" — a bug report is the
//  one deliberate exception, so the scrub is defence-in-depth: home paths, other
//  account paths, emails, and the user's own profile name are removed BEFORE the
//  text is shown for filing. Honeypot redaction is layered on separately by the
//  app via CanaryGuard (injected at the call site, kept out of this pure target).
//
//  Pure + dependency-free so the privacy rules are unit-pinned — a redaction miss
//  leaks PII into a (potentially public) issue, so this is the most test-worthy
//  code in the feature.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-28, Confidence 0.85 (rules TDD-pinned;
//  over-redaction is the deliberate safe direction). Prior: Unknown.
//

import Foundation

/// Scrubs personally-identifying detail from diagnostic text. Over-redacts on
/// purpose: a false positive costs a little readability, a false negative leaks.
public struct DiagnosticRedactor: Sendable {
    private let homeDirectory: String
    private let userName: String?

    /// - Parameters:
    ///   - homeDirectory: the current user's home path, collapsed to `~`.
    ///   - userName: the user's profile name (from onboarding), removed when present.
    public init(homeDirectory: String, userName: String? = nil) {
        self.homeDirectory = homeDirectory
        self.userName = userName
    }

    public func redact(_ text: String) -> String {
        var out = text

        // 1. This user's home dir → ~ first, so the specific path collapses to ~
        //    rather than the generic /Users/[user] form (more readable, still safe).
        if !homeDirectory.isEmpty {
            out = out.replacingOccurrences(of: homeDirectory, with: "~")
        }

        // 2. Any remaining /Users/<name> (other accounts, absolute sandbox paths) →
        //    /Users/[user]. Stops at the next slash/space so the rest of a path stays.
        out = out.replacingOccurrences(
            of: #"/Users/[^/\s]+"#,
            with: "/Users/[user]",
            options: .regularExpression
        )

        // 3. Email addresses.
        out = out.replacingOccurrences(
            of: #"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"#,
            with: "[email]",
            options: .regularExpression
        )

        // 4. The user's own profile name (whole-word, case-insensitive). Escaped so
        //    regex metacharacters in a name are literal; bounded by alphanumeric
        //    lookarounds rather than \b so a name ending in punctuation (e.g.
        //    "A.(x)") still anchors — \b would silently fail to match and leak it.
        //    Lookarounds keep whole-word safety: "Kevin" won't maul "Kevinson".
        if let name = userName?.trimmingCharacters(in: .whitespaces), !name.isEmpty {
            let escaped = NSRegularExpression.escapedPattern(for: name)
            let pattern = #"(?<![A-Za-z0-9])"# + escaped + #"(?![A-Za-z0-9])"#
            out = out.replacingOccurrences(
                of: pattern,
                with: "[name]",
                options: [.regularExpression, .caseInsensitive]
            )
        }

        return out
    }
}
