//
//  CallIntelligencePipeline.swift
//  M1K3Calls
//
//  The composition that proves the seam: transcribe → diarize → align → summarise →
//  a finished CallSession, every stage behind a protocol so ANY engine plugs in
//  (WhisperKit-batch + FluidAudio today; a Gemma-4 shadow tomorrow) with no change
//  here. Diarization + summary are OPTIONAL and error-isolated — a missing or
//  failing diarizer yields an unattributed transcript, not a failed call. This
//  orchestration is the reusable IP; it's unit-tested end-to-end against fakes.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation

public struct CallIntelligencePipeline: Sendable {
    private let transcriber: any BatchTranscriptionProvider
    private let diarizer: (any DiarizationProvider)?
    private let aligner: DiarizationAligner
    private let summarizer: SummarizationPipeline?

    public init(
        transcriber: any BatchTranscriptionProvider,
        diarizer: (any DiarizationProvider)? = nil,
        aligner: DiarizationAligner = DiarizationAligner(),
        summarizer: SummarizationPipeline? = nil
    ) {
        self.transcriber = transcriber
        self.diarizer = diarizer
        self.aligner = aligner
        self.summarizer = summarizer
    }

    /// Process a recorded call file into a finished, speaker-attributed, summarised
    /// `CallSession`. Throws only if transcription itself can't run.
    public func process(fileURL: URL, title: String, startedAt: Date) async throws -> CallSession {
        guard transcriber.isAvailable else { throw CallIntelligenceError.noTranscriberAvailable }
        let transcript = try await transcriber.transcribe(fileURL: fileURL)

        // Diarization is optional + isolated — a failure leaves the transcript unattributed.
        let speakers: [SpeakerSegment]
        if let diarizer, diarizer.isAvailable {
            speakers = (try? await diarizer.diarize(fileURL: fileURL)) ?? []
        } else {
            speakers = []
        }
        let aligned = speakers.isEmpty
            ? transcript
            : aligner.align(transcription: transcript, diarization: speakers)

        // Summary tiers are optional + each independently isolated (in the pipeline).
        var quick: QuickSummary?
        var full: CallSummary?
        if let summarizer {
            let text = CallSession(startedAt: startedAt, title: title, segments: aligned).plainTranscript
            let out = await summarizer.summarize(transcript: text)
            quick = out.quick
            full = out.full
        }

        return CallSession(
            startedAt: startedAt,
            title: title,
            segments: aligned,
            speakers: speakers,
            quickSummary: quick,
            fullSummary: full
        )
    }
}
