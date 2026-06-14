//
//  CanaryGuardTests.swift
//  M1K3ChatTests
//
//  The leak tripwire: a pure scan over outgoing text for known honeypot
//  strings. If a canary ever reaches the output, the guard redacts it and
//  reports a match count (never the value). Tests use a DUMMY canary — a real
//  honeypot must never live in the repo (that would defeat the tripwire).
//

@testable import M1K3Chat
import Testing

private let dummy = "XYZZY-TEST-CANARY"
private let dummy2 = "PLUGH-TEST-CANARY"

struct CanaryGuardTests {
    @Test("the disabled guard is a no-op: nothing tripped, text untouched")
    func disabledIsNoOp() {
        let result = CanaryGuard.disabled.scan("a perfectly innocent answer")
        #expect(!result.tripped)
        #expect(result.text == "a perfectly innocent answer")
        #expect(result.count == 0)
    }

    @Test("an empty canary list is inert even via the initializer")
    func emptyCanariesInert() {
        let guarded = CanaryGuard(canaries: [])
        let result = guarded.scan("text with \(dummy) in it")
        #expect(!result.tripped)
        #expect(result.text.contains(dummy))
        #expect(result.count == 0)
    }

    @Test("clean text passes through unchanged and untripped")
    func cleanTextUntouched() {
        let guarded = CanaryGuard(canaries: [dummy])
        let result = guarded.scan("the answer is forty-two")
        #expect(!result.tripped)
        #expect(result.text == "the answer is forty-two")
        #expect(result.count == 0)
    }

    @Test("a single canary trips the guard, is redacted, and counts once")
    func singleMatchRedacted() {
        let guarded = CanaryGuard(canaries: [dummy])
        let result = guarded.scan("here it is: \(dummy) — leaked")
        #expect(result.tripped)
        #expect(!result.text.contains(dummy))
        #expect(result.text.contains("[REDACTED]"))
        #expect(result.count == 1)
    }

    @Test("repeated occurrences of one canary are all redacted, counted once")
    func repeatedOccurrencesAllRedacted() {
        let guarded = CanaryGuard(canaries: [dummy])
        let result = guarded.scan("\(dummy) and again \(dummy)")
        #expect(result.tripped)
        #expect(!result.text.contains(dummy))
        // One distinct canary matched, even though it appeared twice.
        #expect(result.count == 1)
    }

    @Test("multiple distinct canaries each count toward the match total")
    func multipleDistinctCanaries() {
        let guarded = CanaryGuard(canaries: [dummy, dummy2])
        let result = guarded.scan("first \(dummy) then \(dummy2)")
        #expect(result.tripped)
        #expect(!result.text.contains(dummy))
        #expect(!result.text.contains(dummy2))
        #expect(result.count == 2)
    }

    @Test("empty strings in the canary list are ignored, real ones still fire")
    func emptyStringsFilteredOut() {
        let guarded = CanaryGuard(canaries: ["", dummy, "   "])
        let result = guarded.scan("leak: \(dummy)")
        #expect(result.tripped)
        #expect(result.count == 1)
    }

    @Test("matching is exact and case-sensitive — a near-miss does not trip")
    func caseSensitiveExact() {
        let guarded = CanaryGuard(canaries: [dummy])
        let result = guarded.scan("lowercased xyzzy-test-canary should not match")
        #expect(!result.tripped)
        #expect(result.count == 0)
    }

    @Test("a canary that is a substring of the redaction marker can't re-match what we inserted")
    func markerSubstringDoesNotSelfTrip() {
        // "REDACTED" is a fragment of the "[REDACTED]" marker. It is NOT in the
        // original text — only the real canary is. The scan must decide trips
        // against the original, so redacting the real canary doesn't make
        // "REDACTED" appear to leak (which would double-redact + over-count).
        let guarded = CanaryGuard(canaries: ["REDACTED", dummy])
        let result = guarded.scan("leak: \(dummy)")
        #expect(result.tripped)
        #expect(result.count == 1) // only the real canary, not the marker fragment
        #expect(result.text == "leak: [REDACTED]")
    }
}
