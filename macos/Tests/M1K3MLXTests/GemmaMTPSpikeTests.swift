//
//  GemmaMTPSpikeTests.swift
//  M1K3MLXTests
//
//  Pins the spike's one pure instrument: `firstDivergence` is the core of the
//  greedy exact-match gate (speculative greedy MUST reproduce baseline greedy;
//  the divergence index is the wrapped-window pollution signal). A silent
//  off-by-one here would corrupt the verdict at the upstream-fix re-run —
//  the PR #61 review's ask, folded.
//
//  Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.9 (pure function,
//  exhaustive small cases incl. grapheme clusters). Prior: Unknown
//

import Foundation
@testable import M1K3MLX
import Testing

struct GemmaMTPSpikeTests {
    @Test("identical strings never diverge")
    func identical() {
        #expect(GemmaMTPSpike.firstDivergence("hello world", "hello world") == nil)
        #expect(GemmaMTPSpike.firstDivergence("", "") == nil)
    }

    @Test("a mid-string difference reports the exact character index")
    func midStringDifference() {
        #expect(GemmaMTPSpike.firstDivergence("abcdef", "abcXef") == 3)
        #expect(GemmaMTPSpike.firstDivergence("Xbcdef", "abcdef") == 0)
    }

    @Test("a strict prefix diverges at the shorter length")
    func prefixDivergesAtLength() {
        #expect(GemmaMTPSpike.firstDivergence("abc", "abcdef") == 3)
        #expect(GemmaMTPSpike.firstDivergence("abcdef", "abc") == 3)
        #expect(GemmaMTPSpike.firstDivergence("", "a") == 0)
    }

    @Test("indices count grapheme clusters, not UTF-8 bytes")
    func graphemeClusters() {
        // "é" and the flag are single Characters; a byte-indexed comparison
        // would report a larger offset.
        #expect(GemmaMTPSpike.firstDivergence("é🇮🇪x", "é🇮🇪y") == 2)
        #expect(GemmaMTPSpike.firstDivergence("🇮🇪", "🇮🇪") == nil)
    }
}
