//
//  CrossTurnCacheReuseTests.swift
//  M1K3MLXTests
//
//  The pure decision behind cross-turn KV reuse: given the token ids currently
//  in the live cache and the token ids of the freshly-rendered full
//  conversation, how many leading tokens can we KEEP (skip re-prefilling)?
//  Correctness rests on one invariant: `cached` must mirror the cache's token
//  sequence exactly, so a token-level common prefix is positionally valid KV.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-11, Confidence 0.9 (arithmetic pinned
//  here; the cache trim/prefill that consumes it is verify-at-launch).
//  Prior: Kev + claude-fable-5 (MLXToolTurnSession).
//

import Foundation
@testable import M1K3MLX
import Testing

struct CrossTurnCacheReuseTests {
    @Test("no live cache reuses nothing")
    func noCache() {
        #expect(CrossTurnCacheReuse.reusableLength(cached: [1, 2, 3], full: [1, 2, 3, 4], hasCache: false) == 0)
    }

    @Test("a cached prefix of the new render is fully reused")
    func fullPrefixReused() {
        // cache = [system+goal], new render extends it with an assistant turn
        #expect(CrossTurnCacheReuse.reusableLength(cached: [10, 11, 12], full: [10, 11, 12, 20, 21], hasCache: true) == 3)
    }

    @Test("identical sequences still leave one token to prefill")
    func clampLeavesOneToken() {
        // generation needs at least one input token — never reuse the whole thing
        #expect(CrossTurnCacheReuse.reusableLength(cached: [10, 11, 12], full: [10, 11, 12], hasCache: true) == 2)
    }

    @Test("divergence (e.g. an assistant turn re-rendered differently) caps reuse")
    func partialMatch() {
        // shared [system+goal]=3, then the cache's generated tokens diverge from
        // the structured re-render — reuse stops at the divergence
        #expect(CrossTurnCacheReuse.reusableLength(cached: [10, 11, 12, 99], full: [10, 11, 12, 20, 21], hasCache: true) == 3)
    }

    @Test("no common prefix reuses nothing (fresh prefill)")
    func noCommonPrefix() {
        #expect(CrossTurnCacheReuse.reusableLength(cached: [7, 8], full: [1, 2, 3], hasCache: true) == 0)
    }

    @Test("empty full render reuses nothing")
    func emptyFull() {
        #expect(CrossTurnCacheReuse.reusableLength(cached: [1, 2], full: [], hasCache: true) == 0)
    }

    @Test("a cache longer than the new render reuses only up to the render, less one")
    func cachedLongerThanFull() {
        // cached holds more than the render shares — clamp to full.count - 1
        #expect(CrossTurnCacheReuse.reusableLength(cached: [1, 2, 3, 4, 5], full: [1, 2, 3], hasCache: true) == 2)
    }
}
