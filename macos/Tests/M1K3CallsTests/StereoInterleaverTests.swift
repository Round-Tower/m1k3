//
//  StereoInterleaverTests.swift
//  M1K3CallsTests
//
//  Muxing two mono channels (near-end mic, far-end system audio) into one
//  interleaved stereo stream is the pure heart of stereo call capture — and the
//  one part of it that ISN'T verify-by-launch. The SCStream + mic capture is an
//  OS adapter; this alignment/interleave is deterministic and pinned here.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-07, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Calls
import Testing

struct StereoInterleaverTests {
    @Test("interleaves equal-length channels L,R,L,R…")
    func equalLength() {
        let out = StereoInterleaver.interleave(left: [1, 2, 3], right: [4, 5, 6])
        #expect(out == [1, 4, 2, 5, 3, 6])
    }

    @Test("pads the shorter channel with silence to stay time-aligned")
    func padsShorter() {
        #expect(StereoInterleaver.interleave(left: [1, 2], right: [9]) == [1, 9, 2, 0])
        #expect(StereoInterleaver.interleave(left: [7], right: [1, 2, 3]) == [7, 1, 0, 2, 0, 3])
    }

    @Test("one empty channel → the other becomes silence-paired stereo")
    func oneEmpty() {
        #expect(StereoInterleaver.interleave(left: [1, 2], right: []) == [1, 0, 2, 0])
        #expect(StereoInterleaver.interleave(left: [], right: [1, 2]) == [0, 1, 0, 2])
    }

    @Test("both empty → empty")
    func bothEmpty() {
        #expect(StereoInterleaver.interleave(left: [], right: []).isEmpty)
    }
}
