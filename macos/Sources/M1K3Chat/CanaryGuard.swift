//
//  CanaryGuard.swift
//  M1K3Chat
//
//  A leak tripwire for outgoing text. The owner plants a known honeypot string
//  (a fake "secret") somewhere it should never legitimately surface — e.g. a
//  quarantined doc in the knowledge store. If that string ever reaches an
//  outward answer (body OR Sources footer), the guard REDACTS it and reports a
//  match count so the caller can raise a loud, persistent alert.
//
//  The detector is value-blind by design: it reports HOW MANY canaries matched,
//  never WHICH text — so the alert path can log the trip without re-leaking the
//  canary. The real honeypot value is supplied at runtime from non-committed
//  config; it must never live in source (that would defeat the tripwire).
//
//  Pure on purpose (no os_log, no I/O) so the policy is fully unit-tested; the
//  app layer owns the alerting side-effect via the caller's match count.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9 (scan policy
//  test-pinned; the alert side-effect is wired + verified at the call site).
//  Prior: Unknown.
//

import Foundation

/// Scans text for known honeypot strings, redacting any that appear.
public struct CanaryGuard: Sendable {
    private let canaries: [String]

    /// Whitespace-only / empty entries are dropped: a blank canary would match
    /// stray spaces and redact innocent output.
    public init(canaries: [String]) {
        self.canaries = canaries.filter {
            !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }

    /// A guard that watches for nothing — every scan is a pass-through.
    public static let disabled = CanaryGuard(canaries: [])

    public struct Result: Sendable {
        /// True when at least one canary appeared in the scanned text.
        public let tripped: Bool
        /// The input with every canary occurrence replaced by `[REDACTED]`.
        public let text: String
        /// How many DISTINCT canaries matched (not occurrences). Safe to log —
        /// it never reveals the canary value.
        public let count: Int
    }

    /// Exact, case-sensitive scan. A near-miss (different case) does not trip —
    /// honeypots are precise strings, and loose matching would redact real text.
    public func scan(_ text: String) -> Result {
        guard !canaries.isEmpty else { return Result(tripped: false, text: text, count: 0) }
        // Decide trips against the ORIGINAL text, never the accumulator: a
        // honeypot that is a substring of the marker would otherwise re-match
        // the "[REDACTED]" we just inserted — a false trip and double-redaction.
        let present = canaries.filter { text.contains($0) }
        guard !present.isEmpty else { return Result(tripped: false, text: text, count: 0) }
        var redacted = text
        for canary in present {
            redacted = redacted.replacingOccurrences(of: canary, with: Self.marker)
        }
        return Result(tripped: true, text: redacted, count: present.count)
    }

    private static let marker = "[REDACTED]"
}

public extension CanaryGuard {
    /// Default UserDefaults key for the pipe-separated honeypot list. The value
    /// itself lives ONLY in non-committed local config — never in source — so the
    /// repo never carries the bait:
    ///   `defaults write app.m1k3 canaryTripwire "the passphrase"`
    static let localConfigKey = "canaryTripwire"

    /// Build a guard from local config (pipe-separated honeypots; a value must
    /// therefore not itself contain `|`). Unset → an inert guard. Shared by every
    /// outward surface (MCP `ask_m1k3`, the menu-bar Ask) so there's one source of
    /// truth for the tripwire and no duplicated parsing.
    static func fromLocalConfig(
        key: String = localConfigKey,
        defaults: UserDefaults = .standard
    ) -> CanaryGuard {
        guard let raw = defaults.string(forKey: key) else { return .disabled }
        return CanaryGuard(canaries: raw.split(separator: "|").map(String.init))
    }
}
