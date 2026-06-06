//
//  WhisperKitBatchTranscriberTests.swift
//  M1K3WhisperKitTests
//
//  The batch transcriber is a CoreML OS adapter — the actual model download +
//  file transcription are verify-by-launch. What's deterministic (and where the
//  real bugs live) is the mapping from WhisperKit's timed spans to the call
//  domain's CallTranscriptSegment: trimming, dropping non-speech markers,
//  stripping special tokens, ordering. That pure step is extracted + pinned here.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-07, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Calls
@testable import M1K3WhisperKit
import Testing

struct WhisperKitBatchTranscriberTests {
    @Test("maps timed spans to call segments, trimmed")
    func mapsTrimmed() {
        let segs = WhisperKitBatchTranscriber.makeSegments(from: [
            (start: 0.0, text: "  Hello there  "),
            (start: 2.5, text: "General Kenobi"),
        ])
        #expect(segs.count == 2)
        #expect(segs[0].text == "Hello there")
        #expect(segs[0].startTime == 0.0)
        #expect(segs[1].text == "General Kenobi")
        #expect(segs[1].startTime == 2.5)
    }

    @Test("drops empty and whitespace-only spans")
    func dropsEmpty() {
        let segs = WhisperKitBatchTranscriber.makeSegments(from: [
            (start: 0.0, text: "real"),
            (start: 1.0, text: "   "),
            (start: 2.0, text: ""),
        ])
        #expect(segs.map(\.text) == ["real"])
    }

    @Test("strips WhisperKit special tokens")
    func stripsSpecialTokens() {
        let segs = WhisperKitBatchTranscriber.makeSegments(from: [
            (start: 0.0, text: "<|startoftranscript|><|en|><|transcribe|> Hi there<|endoftext|>"),
        ])
        #expect(segs.map(\.text) == ["Hi there"])
    }

    @Test("drops whole-bracket non-speech markers")
    func dropsNonSpeechMarkers() {
        let segs = WhisperKitBatchTranscriber.makeSegments(from: [
            (start: 0.0, text: "[BLANK_AUDIO]"),
            (start: 1.0, text: "[Music]"),
            (start: 2.0, text: "actual words [pause] mid-sentence"),
        ])
        // Whole-bracket markers go; brackets inside real speech stay.
        #expect(segs.map(\.text) == ["actual words [pause] mid-sentence"])
    }

    @Test("orders segments by start time")
    func ordersByStart() {
        let segs = WhisperKitBatchTranscriber.makeSegments(from: [
            (start: 5.0, text: "later"),
            (start: 1.0, text: "earlier"),
        ])
        #expect(segs.map(\.text) == ["earlier", "later"])
        #expect(segs.map(\.startTime) == [1.0, 5.0])
    }

    @Test("empty input yields no segments")
    func emptyInput() {
        #expect(WhisperKitBatchTranscriber.makeSegments(from: []).isEmpty)
    }

    @Test("has a stable name and is a BatchTranscriptionProvider")
    func metadata() {
        let transcriber: any BatchTranscriptionProvider = WhisperKitBatchTranscriber()
        #expect(transcriber.name == "WhisperKit (batch)")
    }

    @Test("is unavailable until a model is loaded")
    func unavailableBeforeModelLoad() {
        #expect(WhisperKitBatchTranscriber().isAvailable == false)
    }

    @Test("transcribing without a loaded model throws, not crashes")
    func transcribeWithoutModelThrows() async {
        await #expect(throws: CallIntelligenceError.self) {
            _ = try await WhisperKitBatchTranscriber().transcribe(
                fileURL: URL(fileURLWithPath: "/tmp/nonexistent.caf")
            )
        }
    }
}
