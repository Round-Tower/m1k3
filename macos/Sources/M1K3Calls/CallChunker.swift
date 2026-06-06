//
//  CallChunker.swift
//  M1K3Calls
//
//  Turns a finished CallSession into retrieval chunks for the knowledge graph.
//  Speaker-aware: consecutive turns from the same speaker group into one chunk
//  (heading = speaker), long runs split at a character budget, and a high-signal
//  Summary chunk leads so a search for the call's gist surfaces it first. Pure —
//  the indexing orchestration (embed + store) lives in CallIngester.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project CallChunker (Kev, concept) — speaker-grouped chunking.

import Foundation

/// A retrieval chunk of a call: a speaker heading + the spoken content.
public struct CallChunk: Equatable, Sendable {
    public let heading: String?
    public let content: String

    public init(heading: String?, content: String) {
        self.heading = heading
        self.content = content
    }
}

public enum CallChunker {
    /// Chunk a session: a leading Summary chunk (if summarised), then speaker-grouped
    /// transcript chunks bounded by `maxChars`.
    public static func chunk(session: CallSession, maxChars: Int = 800) -> [CallChunk] {
        var chunks: [CallChunk] = []
        if let summary = summaryText(for: session) {
            chunks.append(CallChunk(heading: "Summary", content: summary))
        }

        let segments = session.segments
        var index = 0
        while index < segments.count {
            let speaker = segments[index].speaker
            var run: [String] = []
            while index < segments.count, segments[index].speaker == speaker {
                let text = segments[index].text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty { run.append(text) }
                index += 1
            }
            chunks.append(contentsOf: pack(run, speaker: speaker, maxChars: maxChars))
        }
        return chunks
    }

    /// Pack one speaker's run of lines into `maxChars`-bounded chunks, splitting
    /// only at line boundaries.
    private static func pack(_ lines: [String], speaker: String?, maxChars: Int) -> [CallChunk] {
        guard !lines.isEmpty else { return [] }
        var chunks: [CallChunk] = []
        var current = ""
        for line in lines {
            let candidate = current.isEmpty ? line : current + " " + line
            if candidate.count > maxChars, !current.isEmpty {
                chunks.append(CallChunk(heading: speaker, content: current))
                current = line
            } else {
                current = candidate
            }
        }
        if !current.isEmpty { chunks.append(CallChunk(heading: speaker, content: current)) }
        return chunks
    }

    /// The deep summary (overview + key points + action items) if present, else the
    /// quick gist, else nil.
    private static func summaryText(for session: CallSession) -> String? {
        if let full = session.fullSummary {
            var parts: [String] = []
            if !full.overview.isEmpty { parts.append(full.overview) }
            if !full.keyPoints.isEmpty { parts.append("Key points: " + full.keyPoints.joined(separator: "; ")) }
            if !full.actionItems.isEmpty { parts.append("Action items: " + full.actionItems.joined(separator: "; ")) }
            let joined = parts.joined(separator: "\n")
            if !joined.isEmpty { return joined }
        }
        if let quick = session.quickSummary, !quick.overview.isEmpty { return quick.overview }
        return nil
    }
}
