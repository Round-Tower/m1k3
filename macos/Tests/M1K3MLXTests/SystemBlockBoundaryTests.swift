//
//  SystemBlockBoundaryTests.swift
//  M1K3MLXTests
//
//  The pure core of the Qwen persona-prefix fix. Some chat templates (Qwen3.5)
//  reject a SYSTEM-ONLY render with "No user query found in messages", so the
//  prefix can't be derived directly. Instead we render the system block behind
//  a throwaway user turn and locate the system-block token boundary by
//  subtraction:
//
//      systemLen = commonPrefix(render[sys,A], render[sys,B])      // = [sysblock][userhdr]
//                - commonPrefix(render[A],    render[B])           // = [userhdr]
//
//  Two probes A/B that diverge at their first content token make each common
//  prefix stop exactly at the boundary. Because the user-turn header opens with
//  an ATOMIC special token (<|im_start|>, <start_of_turn>, …) the header
//  tokenises identically whether it follows the system block or sequence-start,
//  so the subtraction is exact. Any anomaly returns nil → caller falls back to
//  an inline system turn (correct, just unoptimised); a wrong boundary is never
//  cached.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-11, Confidence 0.85 (arithmetic
//  pinned here; the prefix+delta==full invariant is asserted by the
//  verify-at-launch self-test). Prior: Kev + claude-fable-5 (PersonaPrefixCache).
//

import Foundation
@testable import M1K3MLX
import Testing

struct SystemBlockBoundaryTests {
    // MARK: commonPrefixLength

    @Test("common prefix length: shared lead then divergence")
    func commonPrefixPartial() {
        #expect(SystemBlockBoundary.commonPrefixLength([1, 2, 3, 9], [1, 2, 3, 8]) == 3)
    }

    @Test("common prefix length: identical, empty, and immediate divergence")
    func commonPrefixEdges() {
        #expect(SystemBlockBoundary.commonPrefixLength([1, 2, 3], [1, 2, 3]) == 3)
        #expect(SystemBlockBoundary.commonPrefixLength([], [1, 2]) == 0)
        #expect(SystemBlockBoundary.commonPrefixLength([1, 2], []) == 0)
        #expect(SystemBlockBoundary.commonPrefixLength([7, 1], [9, 1]) == 0)
    }

    @Test("common prefix length: one array a strict prefix of the other")
    func commonPrefixStrictPrefix() {
        #expect(SystemBlockBoundary.commonPrefixLength([1, 2], [1, 2, 3, 4]) == 2)
    }

    // MARK: systemBlockLength — happy path

    @Test("system-block length is the subtraction of the two common prefixes")
    func boundaryHappyPath() {
        // [sysblock]=[100,101] · [userhdr]=[200,201] · content diverges (A0 vs B0)
        let renderA = [100, 101, 200, 201, 300]
        let renderB = [100, 101, 200, 201, 400]
        let userA = [200, 201, 300]
        let userB = [200, 201, 400]
        #expect(
            SystemBlockBoundary.systemBlockLength(
                renderA: renderA, renderB: renderB, userOnlyA: userA, userOnlyB: userB
            ) == 2
        )
    }

    @Test("a longer, realistic system block (tools + persona) resolves cleanly")
    func boundaryLargerBlock() {
        let sys = Array(1000 ..< 1085) // 85-token tools+persona block
        let hdr = [200, 201, 202] // <|im_start|> user \n
        let renderA = sys + hdr + [500, 501]
        let renderB = sys + hdr + [600]
        let userA = hdr + [500, 501]
        let userB = hdr + [600]
        #expect(
            SystemBlockBoundary.systemBlockLength(
                renderA: renderA, renderB: renderB, userOnlyA: userA, userOnlyB: userB
            ) == 85
        )
    }

    // MARK: systemBlockLength — defensive bails (return nil → inline system turn)

    @Test("probes that never diverge cannot locate a boundary → nil")
    func boundaryProbesIdentical() {
        let render = [100, 101, 200, 201, 300]
        let user = [200, 201, 300]
        #expect(
            SystemBlockBoundary.systemBlockLength(
                renderA: render, renderB: render, userOnlyA: user, userOnlyB: user
            ) == nil
        )
    }

    @Test("no common user header (user renders diverge at token 0) → nil")
    func boundaryNoUserHeader() {
        let renderA = [100, 101, 200, 300]
        let renderB = [100, 101, 200, 400]
        // userOnly renders share nothing — can't isolate the header to subtract
        let userA = [201, 300]
        let userB = [777, 400]
        #expect(
            SystemBlockBoundary.systemBlockLength(
                renderA: renderA, renderB: renderB, userOnlyA: userA, userOnlyB: userB
            ) == nil
        )
    }

    @Test("a degenerate empty system block (prefix == header) → nil")
    func boundaryEmptySystem() {
        // renderA/B common prefix == user header length → systemLen 0
        let renderA = [200, 201, 300]
        let renderB = [200, 201, 400]
        let userA = [200, 201, 300]
        let userB = [200, 201, 400]
        #expect(
            SystemBlockBoundary.systemBlockLength(
                renderA: renderA, renderB: renderB, userOnlyA: userA, userOnlyB: userB
            ) == nil
        )
    }

    @Test("header longer than the full-render common prefix is incoherent → nil")
    func boundaryHeaderLongerThanPrefix() {
        // Both renders diverge (gate 1) AND both user renders diverge (gate 2),
        // so the only thing that bails is the header (3) exceeding the render
        // common prefix (2) — gate 4 fires directly, not the divergence gates.
        let renderA = [100, 200, 300]
        let renderB = [100, 200, 400]
        let userA = [100, 200, 201, 300]
        let userB = [100, 200, 201, 400]
        #expect(
            SystemBlockBoundary.systemBlockLength(
                renderA: renderA, renderB: renderB, userOnlyA: userA, userOnlyB: userB
            ) == nil
        )
    }
}
