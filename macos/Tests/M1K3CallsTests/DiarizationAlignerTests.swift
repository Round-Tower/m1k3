//
//  DiarizationAlignerTests.swift
//  M1K3CallsTests
//
//  Contract tests for the pure alignment algorithm — the heart of the model-
//  agnostic call seam: given a transcript (timestamped) and speaker turns (from
//  ANY diarizer), attribute each line to a speaker by time overlap. No audio, no
//  model — just the algorithm, which is exactly why it's the testable IP.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

@testable import M1K3Calls
import Testing

struct DiarizationAlignerTests {
    private func seg(_ text: String, at t: Double, speaker: String? = nil) -> CallTranscriptSegment {
        CallTranscriptSegment(text: text, startTime: t, speaker: speaker)
    }

    private func spk(_ id: String, _ start: Double, _ end: Double, conf: Float = 1.0) -> SpeakerSegment {
        SpeakerSegment(speakerId: id, startTime: start, endTime: end, confidence: conf)
    }

    @Test("empty transcript yields empty result")
    func empty() {
        #expect(DiarizationAligner().align(transcription: [], diarization: [spk("A", 0, 10)]).isEmpty)
    }

    @Test("a single covering speaker is attributed")
    func singleSpeaker() {
        let out = DiarizationAligner().align(
            transcription: [seg("hello", at: 0)],
            diarization: [spk("A", 0, 10)]
        )
        #expect(out[0].speaker == "A")
    }

    @Test("the speaker with the highest time overlap wins")
    func highestOverlapWins() {
        // seg0 spans 0...5 (next seg at 5). A covers 0–3 (0.6), B covers 3–10 (0.4).
        let out = DiarizationAligner().align(
            transcription: [seg("first", at: 0), seg("second", at: 5)],
            diarization: [spk("A", 0, 3), spk("B", 3, 10)]
        )
        #expect(out[0].speaker == "A")
    }

    @Test("no overlapping speaker leaves the line unattributed")
    func noOverlap() {
        let out = DiarizationAligner().align(
            transcription: [seg("orphan", at: 100)],
            diarization: [spk("A", 0, 10)]
        )
        #expect(out[0].speaker == nil)
    }

    @Test("an existing speaker is preserved when configured")
    func preservesExisting() {
        let out = DiarizationAligner(preserveExistingSpeaker: true).align(
            transcription: [seg("kept", at: 0, speaker: "ORIGINAL")],
            diarization: [spk("A", 0, 10)]
        )
        #expect(out[0].speaker == "ORIGINAL")
    }

    @Test("existing speaker is overwritten when preservation is off")
    func overwritesWhenNotPreserving() {
        let out = DiarizationAligner(preserveExistingSpeaker: false).align(
            transcription: [seg("relabelled", at: 0, speaker: "ORIGINAL")],
            diarization: [spk("A", 0, 10)]
        )
        #expect(out[0].speaker == "A")
    }

    @Test("the last segment uses the default duration window")
    func lastSegmentDefaultDuration() {
        // Default duration 2.0; speaker only covers 0–1 → still the best (and only) overlap.
        let out = DiarizationAligner(defaultSegmentDuration: 2.0).align(
            transcription: [seg("tail", at: 0)],
            diarization: [spk("A", 0, 1)]
        )
        #expect(out[0].speaker == "A")
    }

    @Test("confidence is the geometric mean of overlap ratio and diarizer confidence")
    func confidenceIsGeometricMean() {
        // seg spans 0...2 (default), A covers it fully → ratio 1.0; diarizer conf 0.64 → sqrt(1*0.64)=0.8.
        let out = DiarizationAligner(defaultSegmentDuration: 2.0).align(
            transcription: [seg("x", at: 0)],
            diarization: [spk("A", 0, 2, conf: 0.64)]
        )
        #expect(out[0].speaker == "A")
        #expect(abs((out[0].speakerConfidence ?? 0) - 0.8) < 0.001)
    }
}
