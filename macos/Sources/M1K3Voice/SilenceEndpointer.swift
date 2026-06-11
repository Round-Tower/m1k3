//
//  SilenceEndpointer.swift
//  M1K3Voice
//
//  Closes the recognizer-finality gap for the voice loop: live recognizers can
//  sit on a finished utterance for seconds before declaring isFinal, which
//  reads as M1K3 ignoring you. A non-empty partial that stops CHANGING for the
//  silence threshold is the user being done — the driver polls this and ends
//  the listen itself.
//
//  The threshold must exceed the recognizer's partial-emission cadence
//  (WhisperKit hops ~1 s windows, re-emitting identical text) or it would
//  endpoint mid-sentence — hence the 1.8 s default; tune live.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 (pure, test-pinned;
//  the default threshold is an empirical starting point). Prior: Unknown.
//

import Foundation

public struct SilenceEndpointer: Sendable {
    private let silence: Duration
    private var lastText = ""
    private var lastChange: ContinuousClock.Instant?

    public init(silence: Duration = .seconds(1.8)) {
        self.silence = silence
    }

    /// Feed every partial as it arrives. Identical re-emissions (recognizer
    /// window hops) do NOT reset the clock — only actual text change does.
    public mutating func ingest(partial: String, at instant: ContinuousClock.Instant) {
        if partial != lastText {
            lastText = partial
            lastChange = instant
        } else if lastChange == nil {
            lastChange = instant
        }
    }

    /// True once a non-empty partial has sat unchanged for the threshold.
    public func shouldEndpoint(at now: ContinuousClock.Instant) -> Bool {
        guard let lastChange, !lastText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return false }
        return lastChange.duration(to: now) >= silence
    }

    /// Clear for the next listen.
    public mutating func reset() {
        lastText = ""
        lastChange = nil
    }
}
