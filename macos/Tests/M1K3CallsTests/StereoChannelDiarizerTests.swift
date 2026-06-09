//
//  StereoChannelDiarizerTests.swift
//  M1K3CallsTests
//
//  The deterministic diarizer: when each party is on a separate audio channel,
//  channel == speaker — no ML, no DER. The file read is a thin verify-by-launch
//  adapter; the segmentation (per-frame channel activity → coalesced speaker
//  turns, with silence gaps and blip-dropping) is pure and pinned here.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-07, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Calls
import Testing

struct StereoChannelDiarizerTests {
    /// frame = 1.0s, threshold = 0.5 keeps the arithmetic legible.
    private func segs(
        _ left: [Float], _ right: [Float],
        minSegment: TimeInterval = 0
    ) -> [SpeakerSegment] {
        StereoChannelDiarizer.segments(
            left: left, right: right,
            frameDuration: 1.0, threshold: 0.5, minSegmentDuration: minSegment
        )
    }

    @Test("left channel active → one Speaker 1 turn spanning the active frames")
    func leftOnly() {
        let turns = segs([1, 1, 1], [0, 0, 0])
        #expect(turns.count == 1)
        #expect(turns[0].speakerId == "Speaker 1")
        #expect(turns[0].startTime == 0)
        #expect(turns[0].endTime == 3)
        #expect(turns[0].confidence == 1.0) // other channel silent → unambiguous
    }

    @Test("alternating channels → two turns with correct boundaries")
    func leftThenRight() {
        let turns = segs([1, 1, 0, 0], [0, 0, 1, 1])
        #expect(turns.count == 2)
        #expect(turns[0].speakerId == "Speaker 1")
        #expect(turns[0].startTime == 0)
        #expect(turns[0].endTime == 2)
        #expect(turns[1].speakerId == "Speaker 2")
        #expect(turns[1].startTime == 2)
        #expect(turns[1].endTime == 4)
    }

    @Test("silence between turns splits them and is excluded")
    func silenceSplits() {
        let turns = segs([1, 0, 1], [0, 0, 0])
        #expect(turns.map(\.speakerId) == ["Speaker 1", "Speaker 1"])
        #expect(turns[0].startTime == 0)
        #expect(turns[0].endTime == 1)
        #expect(turns[1].startTime == 2)
        #expect(turns[1].endTime == 3)
    }

    @Test("overlap goes to the louder channel, confidence reflects separation")
    func louderWins() {
        let turns = segs([1.0], [0.6])
        #expect(turns.count == 1)
        #expect(turns[0].speakerId == "Speaker 1")
        // dominant / (left + right) = 1.0 / 1.6
        #expect(abs(turns[0].confidence - Float(1.0 / 1.6)) < 0.001)
    }

    @Test("turns shorter than the minimum are dropped")
    func dropsBlips() {
        #expect(segs([1, 0, 0], [0, 0, 0], minSegment: 2.0).isEmpty)
        // …but a long-enough turn survives the same filter.
        #expect(segs([1, 1, 1], [0, 0, 0], minSegment: 2.0).count == 1)
    }

    @Test("silence everywhere → no turns")
    func allSilent() {
        #expect(segs([0, 0], [0, 0]).isEmpty)
    }

    @Test("empty input → no turns")
    func empty() {
        #expect(segs([], []).isEmpty)
    }

    @Test("is a DiarizationProvider, always available (pure, no model)")
    func metadata() {
        let provider: any DiarizationProvider = StereoChannelDiarizer()
        #expect(provider.name == "Stereo channels")
        #expect(provider.isAvailable == true)
    }
}
