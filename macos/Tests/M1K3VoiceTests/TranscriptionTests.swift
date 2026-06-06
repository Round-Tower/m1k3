//
//  TranscriptionTests.swift
//  M1K3VoiceTests
//
//  Contract tests for the live-dictation seam — the pure pieces (segment model,
//  router selection, partial→final accumulation). The actual recognisers
//  (AppleSpeech, WhisperKit) are OS/mic adapters verified by launching the app,
//  exactly like AVSpeechProvider; this pins everything that doesn't need a mic.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

@testable import M1K3Voice
import Testing

struct TranscriptSegmentTests {
    @Test("confidence clamps into 0...1 (or stays nil)")
    func clampsConfidence() {
        #expect(TranscriptSegment(text: "hi", isFinal: false, confidence: 1.5).confidence == 1.0)
        #expect(TranscriptSegment(text: "hi", isFinal: false, confidence: -0.2).confidence == 0.0)
        #expect(TranscriptSegment(text: "hi", isFinal: false, confidence: 0.7).confidence == 0.7)
        #expect(TranscriptSegment(text: "hi", isFinal: true).confidence == nil)
    }
}

/// A fake recogniser: drives router selection without a microphone.
private struct FakeTranscriber: TranscriptionProvider {
    let name: String
    let isAvailable: Bool
    func startListening() -> AsyncStream<TranscriptSegment> {
        AsyncStream { $0.finish() }
    }

    func stopListening() {}
}

struct TranscriptionRouterTests {
    @Test("active provider is the first available, in declaration order")
    func picksFirstAvailable() {
        let router = TranscriptionRouter(providers: [
            FakeTranscriber(name: "whisper", isAvailable: false),
            FakeTranscriber(name: "apple", isAvailable: true),
        ])
        #expect(router.activeProvider?.name == "apple")
        #expect(router.activeProviderName == "apple")
    }

    @Test("prefers an earlier provider when both are available (WhisperKit over Apple)")
    func prefersPrimary() {
        let router = TranscriptionRouter(providers: [
            FakeTranscriber(name: "whisper", isAvailable: true),
            FakeTranscriber(name: "apple", isAvailable: true),
        ])
        #expect(router.activeProviderName == "whisper")
    }

    @Test("no available provider resolves to nil")
    func noneAvailable() {
        let router = TranscriptionRouter(providers: [
            FakeTranscriber(name: "whisper", isAvailable: false),
        ])
        #expect(router.activeProvider == nil)
        #expect(router.activeProviderName == nil)
    }
}

struct TranscriptAccumulatorTests {
    @Test("cumulative partials replace the working text")
    func cumulativePartials() {
        var acc = TranscriptAccumulator()
        acc.ingest(TranscriptSegment(text: "what", isFinal: false))
        acc.ingest(TranscriptSegment(text: "what is", isFinal: false))
        acc.ingest(TranscriptSegment(text: "what is in", isFinal: false))
        #expect(acc.text == "what is in")
        #expect(!acc.isFinal)
    }

    @Test("a final segment commits and flags done")
    func finalCommits() {
        var acc = TranscriptAccumulator()
        acc.ingest(TranscriptSegment(text: "what is in the doc", isFinal: true))
        #expect(acc.text == "what is in the doc")
        #expect(acc.isFinal)
    }

    @Test("empty segments never blank out captured text")
    func ignoresEmpty() {
        var acc = TranscriptAccumulator()
        acc.ingest(TranscriptSegment(text: "hello", isFinal: false))
        acc.ingest(TranscriptSegment(text: "", isFinal: false))
        #expect(acc.text == "hello")
    }

    @Test("a fresh accumulator has empty, non-final state")
    func startsEmpty() {
        let acc = TranscriptAccumulator()
        #expect(acc.text.isEmpty)
        #expect(!acc.isFinal)
    }
}
