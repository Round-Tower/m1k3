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
//  endpoint mid-sentence — hence the ~1.6 s default; tune live.
//
//  Completeness-aware: when the stable partial trails off mid-thought (a dangling
//  conjunction/preposition/filler — see UtteranceCompleteness), the endpointer
//  waits the longer `holdSilence` before ending, so a natural pause inside a
//  multi-clause utterance ("tell me about the" <pause> "weather") no longer
//  fragments it into half-thoughts the model then reasons over incorrectly. A
//  `maxWait` from first speech is the anti-hang backstop for a partial that never
//  stabilises (a stuck/hallucinating recognizer).
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 (pure, test-pinned;
//  the default thresholds are empirical starting points). Prior: Unknown.
//  Review: Kev + claude-opus-4-8, 2026-06-17 — added completeness-aware hold +
//  maxWait backstop to stop utterance fragmentation. Confidence 0.85.
//

import Foundation

public struct SilenceEndpointer: Sendable {
    private let silence: Duration
    private let holdSilence: Duration
    private let maxWait: Duration
    private var lastText = ""
    private var lastChange: ContinuousClock.Instant?
    /// When the partial first became non-empty (first RECOGNISED speech, which can
    /// lag mic-open by ~1s on WhisperKit) — the anchor for `maxWait`.
    private var firstSpeech: ContinuousClock.Instant?

    /// - Parameters:
    ///   - silence: idle gap that ends a listen when the partial reads complete.
    ///   - holdSilence: the longer gap allowed when the partial trails off
    ///     mid-thought, so a natural pause doesn't fragment the utterance.
    ///   - maxWait: hard cap from first speech so a never-stabilising partial
    ///     (stuck recognizer) still endpoints rather than hanging.
    public init(
        silence: Duration = .seconds(1.6),
        holdSilence: Duration = .seconds(3.0),
        maxWait: Duration = .seconds(20)
    ) {
        self.silence = silence
        self.holdSilence = holdSilence
        self.maxWait = maxWait
    }

    /// Feed every partial as it arrives. Identical re-emissions (recognizer
    /// window hops) do NOT reset the clock — only actual text change does. (A
    /// non-empty partial always stamps `lastChange` on the change that set it, so
    /// there's no separate "first identical emission" branch to handle.)
    public mutating func ingest(partial: String, at instant: ContinuousClock.Instant) {
        guard partial != lastText else { return }
        lastText = partial
        lastChange = instant
        if firstSpeech == nil, !partial.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            firstSpeech = instant
        }
    }

    /// True once a non-empty partial has gone idle for long enough — the normal
    /// `silence` when it reads complete, the longer `holdSilence` when it trails
    /// off mid-thought — or once `maxWait` from first speech is hit (anti-hang).
    public func shouldEndpoint(at now: ContinuousClock.Instant) -> Bool {
        guard let lastChange, !lastText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return false }
        let idle = lastChange.duration(to: now)
        // Anti-hang backstop: once we've been going past maxWait AND the recognizer
        // has actually gone quiet (idle ≥ silence), end it — but never cut a user
        // who's still actively speaking (partials still advancing).
        if let firstSpeech, firstSpeech.duration(to: now) >= maxWait, idle >= silence {
            return true
        }
        let needed = UtteranceCompleteness.looksComplete(lastText) ? silence : holdSilence
        return idle >= needed
    }

    /// Clear for the next listen.
    public mutating func reset() {
        lastText = ""
        lastChange = nil
        firstSpeech = nil
    }
}
