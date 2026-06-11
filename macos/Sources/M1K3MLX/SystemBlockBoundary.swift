//
//  SystemBlockBoundary.swift
//  M1K3MLX
//
//  Pure token-arithmetic for locating the system-block boundary when a chat
//  template refuses a system-only render (Qwen3.5: "No user query found in
//  messages"). See SystemBlockBoundaryTests for the why and the worked example.
//
//  The contract: given two full renders [sys, probeA] / [sys, probeB] and two
//  user-only renders [probeA] / [probeB] — where probeA/probeB diverge at their
//  first content token — return the exact token count of the system block, or
//  nil if any sanity gate trips (the caller then sends the system turn inline).
//  A nil is always safe; a wrong (non-nil) boundary is the failure we refuse to
//  ship, so every gate biases toward nil.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-11, Confidence 0.85 (gates pinned by
//  tests; exactness for special-token dialects argued in the header doc; the
//  prefix+delta==full invariant is asserted by the verify-at-launch self-test).
//  Prior: Kev + claude-fable-5 (PersonaPrefixCache).
//

import Foundation

enum SystemBlockBoundary {
    /// Length of the longest shared leading run of two token arrays.
    static func commonPrefixLength(_ a: [Int], _ b: [Int]) -> Int {
        let bound = min(a.count, b.count)
        var i = 0
        while i < bound, a[i] == b[i] {
            i += 1
        }
        return i
    }

    /// The system-block token count, derived by subtracting the user-header
    /// length from the full-render common prefix. Returns nil on any anomaly.
    static func systemBlockLength(
        renderA: [Int],
        renderB: [Int],
        userOnlyA: [Int],
        userOnlyB: [Int]
    ) -> Int? {
        let fullPrefix = commonPrefixLength(renderA, renderB)
        let headerLength = commonPrefixLength(userOnlyA, userOnlyB)

        // Probes must actually diverge in BOTH renders — otherwise a common
        // prefix that runs to the end of an array isn't a boundary, it's just
        // "these are (a prefix of) the same render", and the arithmetic is junk.
        guard fullPrefix < min(renderA.count, renderB.count),
              headerLength < min(userOnlyA.count, userOnlyB.count)
        else { return nil }

        // A header we can subtract, and a system block strictly between the
        // header and the full prefix. puts systemLength in (0, fullPrefix).
        guard headerLength > 0, fullPrefix > headerLength else { return nil }

        let systemLength = fullPrefix - headerLength
        // Can't slice more tokens than renderA holds (defensive; implied by the
        // gates above, but the cache copy depends on it so assert it directly).
        guard systemLength <= renderA.count else { return nil }
        return systemLength
    }
}
