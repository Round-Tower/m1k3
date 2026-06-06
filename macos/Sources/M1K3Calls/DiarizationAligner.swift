//
//  DiarizationAligner.swift
//  M1K3Calls
//
//  The model-agnostic heart of call intelligence: merge a transcript (text +
//  timestamps, from ANY transcriber) with speaker turns (from ANY diarizer) into
//  a speaker-attributed transcript. Pure logic, no audio, no model — so it's fully
//  unit-tested and survives any engine swap underneath. This is the seam paying off.
//
//  Algorithm: estimate each line's duration (to the next line's start, else a
//  default), find the speaker turns overlapping that window, assign the speaker
//  with the greatest overlap, and score confidence as the geometric mean of the
//  overlap ratio and the diarizer's own confidence.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project DiarizationAligner (Kev) — retargeted to M1K3's
//  CallTranscriptSegment / SpeakerSegment.

import Foundation

public struct DiarizationAligner: Sendable {
    /// Duration assumed for the final segment (no next segment to bound it).
    public let defaultSegmentDuration: TimeInterval
    /// When true, lines that already carry a speaker are left untouched.
    public let preserveExistingSpeaker: Bool

    public init(defaultSegmentDuration: TimeInterval = 2.0, preserveExistingSpeaker: Bool = true) {
        self.defaultSegmentDuration = defaultSegmentDuration
        self.preserveExistingSpeaker = preserveExistingSpeaker
    }

    /// Attribute each transcript line to a speaker by time overlap.
    public func align(
        transcription: [CallTranscriptSegment],
        diarization: [SpeakerSegment]
    ) -> [CallTranscriptSegment] {
        guard !transcription.isEmpty else { return [] }
        let sorted = diarization.sorted { $0.startTime < $1.startTime }
        return transcription.enumerated().map { index, segment in
            alignSegment(segment, index: index, all: transcription, diarization: sorted)
        }
    }

    private func alignSegment(
        _ segment: CallTranscriptSegment,
        index: Int,
        all: [CallTranscriptSegment],
        diarization: [SpeakerSegment]
    ) -> CallTranscriptSegment {
        if preserveExistingSpeaker, segment.speaker != nil { return segment }

        let duration = estimateDuration(at: index, in: all)
        let range = segment.startTime ... (segment.startTime + duration)
        let overlaps = diarization.filter { $0.overlaps(with: range) }
        guard !overlaps.isEmpty else { return segment.withSpeaker(nil, confidence: nil) }

        let (best, ratio) = bestSpeaker(among: overlaps, in: range)
        let confidence = (ratio * best.confidence).squareRoot()
        return segment.withSpeaker(best.speakerId, confidence: confidence)
    }

    /// Window from this line's start to the next line's start (min 100ms), or the
    /// default for the last line.
    private func estimateDuration(at index: Int, in segments: [CallTranscriptSegment]) -> TimeInterval {
        guard index + 1 < segments.count else { return defaultSegmentDuration }
        return max(segments[index + 1].startTime - segments[index].startTime, 0.1)
    }

    /// The overlapping speaker covering the greatest fraction of the line's window.
    private func bestSpeaker(
        among overlaps: [SpeakerSegment],
        in range: ClosedRange<TimeInterval>
    ) -> (speaker: SpeakerSegment, ratio: Float) {
        let window = max(range.upperBound - range.lowerBound, 0.0001)
        var best = overlaps[0]
        var bestRatio: Float = 0
        for speaker in overlaps {
            let overlap = min(speaker.endTime, range.upperBound) - max(speaker.startTime, range.lowerBound)
            let ratio = Float(max(overlap, 0) / window)
            if ratio > bestRatio { bestRatio = ratio; best = speaker }
        }
        return (best, bestRatio)
    }
}
