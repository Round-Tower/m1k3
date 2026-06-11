//
//  UtteranceTimelineAccumulator.swift
//  M1K3Voice
//
//  Anchors per-chunk word timelines into one global utterance timeline as
//  chunks are scheduled onto the player. The anchor is
//  max(scheduledSamples, playerSampleNow): back-to-back chunks queue at the
//  scheduled boundary, but AVAudioPlayerNode's clock keeps running through a
//  dry gap (probe-verified), so a late chunk actually starts at the player's
//  NOW — anchoring there resets any estimation drift at every chunk join.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (pure, test-pinned;
//  anchor semantics probe-verified against AVAudioPlayerNode). Prior: Unknown.
//

import Foundation

public struct UtteranceTimelineAccumulator: Sendable {
    /// The growing utterance-wide timeline (chunk times shifted to the global clock).
    public private(set) var global: SpokenWordTimeline
    /// Total samples scheduled so far — the next back-to-back anchor.
    public private(set) var scheduledSamples: Int64 = 0

    private let sampleRate: Double

    public init(text: String, sampleRate: Double) {
        global = SpokenWordTimeline(text: text, words: [], totalDuration: 0)
        self.sampleRate = sampleRate
    }

    /// Anchor `chunk` at the moment it is scheduled and fold it into `global`.
    ///
    /// - Parameters:
    ///   - chunk: the chunk's timeline, times relative to the chunk's own start.
    ///   - sampleCount: frames being scheduled for the chunk.
    ///   - playerSampleNow: the player's current sample position, if playing.
    ///     Can read slightly negative right after play() — clamped.
    /// - Returns: the updated global timeline.
    @discardableResult
    public mutating func append(
        chunk: SpokenWordTimeline,
        sampleCount: Int,
        playerSampleNow: Int64?
    ) -> SpokenWordTimeline {
        // The first chunk of an utterance ALWAYS anchors at zero: the player is
        // freshly (re)started for this utterance, and any non-zero reading here
        // is a stale clock from a previous one — trusting it would shift every
        // word past the audio and silently kill the highlight. The player-clock
        // re-anchor is a WITHIN-utterance correction (dry gaps) only.
        let playerAnchorSamples: Int64 = scheduledSamples > 0 ? max(playerSampleNow ?? 0, 0) : 0
        let anchorSamples = max(scheduledSamples, playerAnchorSamples)
        let anchorSeconds = Double(anchorSamples) / sampleRate
        global = global.appending(chunk.offset(by: anchorSeconds))
        scheduledSamples = anchorSamples + Int64(sampleCount)
        return global
    }
}
