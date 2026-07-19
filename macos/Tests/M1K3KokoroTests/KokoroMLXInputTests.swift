//
//  KokoroMLXInputTests.swift
//  M1K3KokoroTests
//
//  Pins the pad-wrap token-assembly parity: the MLX path must frame the
//  model's input tokens exactly like the prior ORT path did
//  ([0] + phonemeTokens + [0]) — same boundary, same pad id, only the
//  element width changed (Int64 → Int32, MLX's native integer width).
//
//  Signed: Kev + claude-fable-5, 2026-07-18, Confidence 0.9, Prior: none
//

import Foundation
@testable import M1K3Kokoro
import Testing

struct KokoroMLXInputTests {
    @Test("wraps phoneme tokens in [pad] … [pad], matching the prior ORT framing")
    func wrapsInPadBoundary() {
        let tokens = KokoroMLXInput.modelTokens([12, 34, 56])
        #expect(tokens == [Int32(KokoroG2P.pad), 12, 34, 56, Int32(KokoroG2P.pad)])
    }

    @Test("empty phoneme tokens still yield the two pad boundary entries")
    func emptyTokensStillPadded() {
        #expect(KokoroMLXInput.modelTokens([]) == [Int32(KokoroG2P.pad), Int32(KokoroG2P.pad)])
    }

    @Test("output length is always input length + 2 (the pad wrap)")
    func lengthIsInputPlusTwo() {
        let tokens = Array(0 ..< 510)
        #expect(KokoroMLXInput.modelTokens(tokens).count == tokens.count + 2)
    }

    @Test("the pad boundary is always token id 0 (KokoroG2P.pad)")
    func padIdMatchesG2PPad() {
        #expect(KokoroG2P.pad == 0) // pin the constant this file relies on
        let tokens = KokoroMLXInput.modelTokens([1])
        #expect(tokens.first == 0)
        #expect(tokens.last == 0)
    }
}
