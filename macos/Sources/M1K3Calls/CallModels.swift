//
//  CallModels.swift
//  M1K3Calls
//
//  Domain models for call intelligence — deliberately neutral (not domain-
//  specific like the prior call-pipeline's). A call is recorded, transcribed, diarized, aligned,
//  and summarized; the finished `CallSession` becomes a node in M1K3's knowledge
//  graph so calls are searchable via RAG alongside documents.
//
//  `CallTranscriptSegment` is the call domain's RICHER transcript unit — it carries
//  a timestamp and optional speaker attribution, unlike M1K3Voice's slim live
//  `TranscriptSegment` (text/isFinal only). Keeping them separate is the same
//  "generalise per domain" call as the rest of M1K3.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: internal call-pipeline project, {TranscriptSegment, SpeakerModels, Summary} (Kev) —
//  generalised + slimmed to neutral fields.

import Foundation

/// One line of transcript with a timestamp and optional speaker attribution.
public struct CallTranscriptSegment: Identifiable, Sendable, Equatable, Codable {
    public let id: UUID
    public let text: String
    /// Seconds from the start of the call.
    public let startTime: TimeInterval
    public let speaker: String?
    /// Confidence in the speaker attribution (0…1), set by the aligner.
    public let speakerConfidence: Float?

    public init(
        id: UUID = UUID(),
        text: String,
        startTime: TimeInterval,
        speaker: String? = nil,
        speakerConfidence: Float? = nil
    ) {
        self.id = id
        self.text = text
        self.startTime = startTime
        self.speaker = speaker
        self.speakerConfidence = speakerConfidence.map { min(max($0, 0), 1) }
    }

    /// A copy with updated speaker attribution, all else preserved.
    public func withSpeaker(_ newSpeaker: String?, confidence: Float?) -> CallTranscriptSegment {
        CallTranscriptSegment(
            id: id, text: text, startTime: startTime,
            speaker: newSpeaker, speakerConfidence: confidence
        )
    }
}

/// A span of audio attributed to one speaker, from any diarizer.
public struct SpeakerSegment: Identifiable, Sendable, Equatable, Codable {
    public let id: UUID
    public let speakerId: String
    public let speakerLabel: String?
    public let startTime: TimeInterval
    public let endTime: TimeInterval
    /// Diarizer confidence in this attribution (0…1).
    public let confidence: Float

    public init(
        id: UUID = UUID(),
        speakerId: String,
        speakerLabel: String? = nil,
        startTime: TimeInterval,
        endTime: TimeInterval,
        confidence: Float = 1.0
    ) {
        self.id = id
        self.speakerId = speakerId
        self.speakerLabel = speakerLabel
        self.startTime = startTime
        self.endTime = endTime
        self.confidence = min(max(confidence, 0), 1)
    }

    /// Whether this speaker span intersects the given time range.
    public func overlaps(with range: ClosedRange<TimeInterval>) -> Bool {
        startTime <= range.upperBound && endTime >= range.lowerBound
    }
}

/// Tier-1 fast summary (a cheap model, AFM): the gist, in a sentence or two.
public struct QuickSummary: Sendable, Equatable, Codable {
    public let overview: String
    public init(overview: String) {
        self.overview = overview
    }
}

/// Tier-2 deep analysis (the strong model, Gemma as a TEXT model — the safe win):
/// overview + key points + action items. Neutral fields, no domain-specific carry-over.
public struct CallSummary: Sendable, Equatable, Codable {
    public let overview: String
    public let keyPoints: [String]
    public let actionItems: [String]

    public init(overview: String, keyPoints: [String] = [], actionItems: [String] = []) {
        self.overview = overview
        self.keyPoints = keyPoints
        self.actionItems = actionItems
    }
}

/// A recorded call after the pipeline runs: speaker-attributed transcript plus
/// the two summary tiers. This is what gets persisted and indexed into the graph.
public struct CallSession: Identifiable, Sendable, Equatable, Codable {
    public let id: UUID
    public let startedAt: Date
    public let title: String
    public let segments: [CallTranscriptSegment]
    public let speakers: [SpeakerSegment]
    public let quickSummary: QuickSummary?
    public let fullSummary: CallSummary?

    public init(
        id: UUID = UUID(),
        startedAt: Date,
        title: String,
        segments: [CallTranscriptSegment] = [],
        speakers: [SpeakerSegment] = [],
        quickSummary: QuickSummary? = nil,
        fullSummary: CallSummary? = nil
    ) {
        self.id = id
        self.startedAt = startedAt
        self.title = title
        self.segments = segments
        self.speakers = speakers
        self.quickSummary = quickSummary
        self.fullSummary = fullSummary
    }

    /// The full transcript as plain text (for summarization + graph indexing),
    /// speaker-prefixed when attribution exists.
    public var plainTranscript: String {
        segments.map { seg in
            if let speaker = seg.speaker { return "\(speaker): \(seg.text)" }
            return seg.text
        }.joined(separator: "\n")
    }
}
