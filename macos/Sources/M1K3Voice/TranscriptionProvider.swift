//
//  TranscriptionProvider.swift
//  M1K3Voice
//
//  The live-dictation seam. Mirrors SpeechProvider (TTS) and InferenceProvider:
//  a thin, swappable adapter identified by `name` + `isAvailable`. Apple Speech
//  (system framework, zero-dep) and WhisperKit (heavy, isolated in its own
//  target) both conform, so the chat voice button doesn't care which engine runs.
//
//  Streaming contract: `startListening()` begins a session and returns a stream
//  of `TranscriptSegment`s — partials as the user speaks, then a final segment.
//  The stream finishes when `stopListening()` is called or recognition ends.
//  One active session per provider; the provider owns the mic/engine lifecycle.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: internal call-pipeline project, TranscriptionProvider (Kev) — generalised to a live
//  session API (the prior call-pipeline's is buffer-pump + call-domain), PerformanceMonitor and
//  PowerEfficiency dropped as MVP-irrelevant.

import Foundation

public protocol TranscriptionProvider: Sendable {
    /// Stable identifier for routing/UI.
    var name: String { get }
    /// Whether this recogniser can run right now (permissions, model, hardware).
    var isAvailable: Bool { get }
    /// Begin a live dictation session, streaming partial then final segments.
    /// Throws if the session can't start (e.g. mic/permission failure).
    func startListening() throws -> AsyncStream<TranscriptSegment>
    /// Stop the active session; flushes a final segment then finishes the stream.
    func stopListening()
}
