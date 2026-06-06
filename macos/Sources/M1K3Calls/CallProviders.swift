//
//  CallProviders.swift
//  M1K3Calls
//
//  The seams that make call intelligence model-agnostic — THE reusable IP (per the
//  2026-06-06 challenger pass: extract the seam, never the model). Concrete engines
//  conform and plug in behind these, fallback-chained, none linked into this target:
//    - BatchTranscriptionProvider ← WhisperKit-batch (default) · Gemma 4 E4B (shadow)
//    - DiarizationProvider        ← FluidAudio (CoreML) · stereo-channel fallback
//  Summarization rides M1K3Inference's existing `InferenceProvider` (Gemma/AFM as a
//  TEXT model — the safe win), orchestrated by SummarizationPipeline.
//
//  File-based (not the prior call-pipeline's AVAudioPCMBuffer streaming): M1K3 calls are recorded
//  then processed, so a URL in / segments out is the honest, dependency-light shape.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project {TranscriptionProvider, DiarizationProvider} (Kev) —
//  generalised to file-based batch, PowerEfficiency/buffer methods dropped.

import Foundation

/// Transcribe a recorded audio file into timestamped segments.
public protocol BatchTranscriptionProvider: Sendable {
    var name: String { get }
    var isAvailable: Bool { get }
    func transcribe(fileURL: URL) async throws -> [CallTranscriptSegment]
}

/// Identify who-spoke-when in a recorded audio file.
public protocol DiarizationProvider: Sendable {
    var name: String { get }
    var isAvailable: Bool { get }
    func diarize(fileURL: URL) async throws -> [SpeakerSegment]
}

public enum CallIntelligenceError: Error, Sendable, Equatable {
    case noTranscriberAvailable
    case transcriptionFailed(String)
    case diarizationFailed(String)
}
