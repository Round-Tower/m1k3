//
//  CallIntelligencePipelineTests.swift
//  M1K3CallsTests
//
//  The seam composing end-to-end against fakes: transcribe → diarize → align →
//  summarise → CallSession, with diarization/summary optional + error-isolated.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Calls
import M1K3Inference
import Testing

private struct FakeBatchTranscriber: BatchTranscriptionProvider {
    let name = "fake-asr"
    let isAvailable: Bool
    var segments: [CallTranscriptSegment] = []
    func transcribe(fileURL _: URL) async throws -> [CallTranscriptSegment] {
        segments
    }
}

private struct FakeDiarizer: DiarizationProvider {
    let name = "fake-diar"
    let isAvailable: Bool
    var result: Result<[SpeakerSegment], FakeError> = .success([])
    func diarize(fileURL _: URL) async throws -> [SpeakerSegment] {
        try result.get()
    }
}

struct CallIntelligencePipelineTests {
    private let url = URL(fileURLWithPath: "/tmp/call.wav")
    private let startedAt = Date(timeIntervalSince1970: 0)

    private func twoLines() -> [CallTranscriptSegment] {
        [CallTranscriptSegment(text: "Hi there", startTime: 0),
         CallTranscriptSegment(text: "Hello back", startTime: 5)]
    }

    @Test("an unavailable transcriber throws rather than producing an empty call")
    func transcriberUnavailable() async {
        let pipeline = CallIntelligencePipeline(transcriber: FakeBatchTranscriber(isAvailable: false))
        await #expect(throws: CallIntelligenceError.self) {
            _ = try await pipeline.process(fileURL: url, title: "x", startedAt: startedAt)
        }
    }

    @Test("happy path: transcribe + diarize + align + summarise into a CallSession")
    func fullHappyPath() async throws {
        let diarizer = FakeDiarizer(isAvailable: true, result: .success([
            SpeakerSegment(speakerId: "A", startTime: 0, endTime: 3),
            SpeakerSegment(speakerId: "B", startTime: 4, endTime: 10),
        ]))
        let summarizer = SummarizationPipeline(
            quickProvider: FakeInference(name: "afm", isAvailable: true, response: .success("Two greetings.")),
            deepProvider: FakeInference(name: "gemma", isAvailable: true, response: .success("Overview: A greeting exchange."))
        )
        let pipeline = CallIntelligencePipeline(
            transcriber: FakeBatchTranscriber(isAvailable: true, segments: twoLines()),
            diarizer: diarizer,
            summarizer: summarizer
        )

        let session = try await pipeline.process(fileURL: url, title: "Test call", startedAt: startedAt)

        #expect(session.segments.count == 2)
        #expect(session.segments[0].speaker == "A") // 0…5 window, A covers 0–3
        #expect(session.segments[1].speaker == "B") // last line, default window, B covers it
        #expect(session.speakers.count == 2)
        #expect(session.quickSummary?.overview == "Two greetings.")
        #expect(session.fullSummary?.overview == "A greeting exchange.")
        #expect(session.plainTranscript == "A: Hi there\nB: Hello back")
    }

    @Test("no diarizer leaves the transcript unattributed but still produces a call")
    func noDiarizer() async throws {
        let pipeline = CallIntelligencePipeline(
            transcriber: FakeBatchTranscriber(isAvailable: true, segments: twoLines())
        )
        let session = try await pipeline.process(fileURL: url, title: "x", startedAt: startedAt)
        #expect(session.segments.allSatisfy { $0.speaker == nil })
        #expect(session.speakers.isEmpty)
        #expect(session.fullSummary == nil)
    }

    @Test("a failing diarizer is isolated — the call still completes, unattributed")
    func diarizerFailsIsolated() async throws {
        let diarizer = FakeDiarizer(isAvailable: true, result: .failure(.boom))
        let pipeline = CallIntelligencePipeline(
            transcriber: FakeBatchTranscriber(isAvailable: true, segments: twoLines()),
            diarizer: diarizer
        )
        let session = try await pipeline.process(fileURL: url, title: "x", startedAt: startedAt)
        #expect(session.segments.count == 2)
        #expect(session.segments.allSatisfy { $0.speaker == nil })
    }
}
