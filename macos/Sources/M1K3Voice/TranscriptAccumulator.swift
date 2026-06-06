//
//  TranscriptAccumulator.swift
//  M1K3Voice
//
//  Folds a stream of TranscriptSegments into the current dictation text. Both
//  recognisers (Apple Speech, WhisperKit) emit *cumulative* best-so-far text per
//  partial — so the working text is simply the latest non-empty segment, and a
//  final segment flags the utterance complete. Pure value type: the fold is the
//  one bit of dictation logic with real edge cases (empty partials, final), so
//  it's tested; the recognisers themselves are verify-by-launch.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation

/// Accumulates live partials into the text to show (and ultimately send).
public struct TranscriptAccumulator: Sendable, Equatable {
    /// The best-so-far dictation text.
    public private(set) var text: String = ""
    /// True once a final segment has been ingested.
    public private(set) var isFinal: Bool = false

    public init() {}

    /// Fold one segment in. Cumulative recognisers replace the working text;
    /// empty partials are ignored so a momentary blank doesn't wipe progress.
    public mutating func ingest(_ segment: TranscriptSegment) {
        if !segment.text.isEmpty { text = segment.text }
        if segment.isFinal { isFinal = true }
    }
}
