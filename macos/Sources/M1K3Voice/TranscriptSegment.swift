//
//  TranscriptSegment.swift
//  M1K3Voice
//
//  A slice of live dictation — the unit a TranscriptionProvider streams as the
//  user speaks. Deliberately lean vs the prior call-pipeline's call-domain segment (no speaker /
//  sentiment / topics / entities): M1K3 P6 is "talk to fill the chat box", not
//  diarized call logging (that richer model arrives with the P7 call subsystem).
//  Pure value type, no AVFoundation — so the fold/accumulation logic is testable
//  without a microphone.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project TranscriptSegment (Kev) — generalised + slimmed.

import Foundation

/// One transcription result. Recognisers emit a run of `isFinal == false`
/// partials (the best-so-far text, cumulative) and a single `isFinal == true`
/// segment when recognition settles.
public struct TranscriptSegment: Sendable, Equatable {
    public let text: String
    public let isFinal: Bool
    /// Recogniser confidence 0...1 (clamped), or nil when unknown.
    public let confidence: Float?

    public init(text: String, isFinal: Bool, confidence: Float? = nil) {
        self.text = text
        self.isFinal = isFinal
        self.confidence = confidence.map { min(max($0, 0), 1) }
    }
}
