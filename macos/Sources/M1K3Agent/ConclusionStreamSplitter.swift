//
//  ConclusionStreamSplitter.swift
//  M1K3Agent
//
//  Buffers a streamed thought until the CONCLUSION: marker appears, then
//  passes everything after it through live — this is why the agent's final
//  answer streams token-by-token instead of arriving as one dump, without an
//  extra generation pass.
//
//  Handles both provider stream contracts (cumulative snapshots à la Apple
//  Foundation Models, and plain deltas) with the same normalise-to-delta rule
//  as ChatSession.fold, and a marker split across token boundaries.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.9, Prior: Unknown

import Foundation

struct ConclusionStreamSplitter {
    /// The full thought accumulated so far (marker included).
    private(set) var thought = ""
    /// True once the CONCLUSION: marker has been seen.
    private(set) var isConclusion = false
    private var emittedAny = false

    private static let marker = "CONCLUSION:"

    /// Feed one raw stream chunk. Returns the text to emit live — empty while
    /// still buffering (no marker yet) or when the chunk added nothing.
    mutating func feed(_ chunk: String) -> String {
        // Normalise cumulative snapshots to deltas (ChatSession.fold's rule):
        // a chunk that extends the accumulated text is a snapshot, replace.
        let delta: String = if !thought.isEmpty, chunk.hasPrefix(thought) {
            String(chunk.dropFirst(thought.count))
        } else {
            chunk
        }
        thought += delta

        if isConclusion {
            return emit(delta)
        }
        guard let markerRange = thought.range(of: Self.marker) else { return "" }
        isConclusion = true
        return emit(String(thought[markerRange.upperBound...]))
    }

    /// Drop leading whitespace before the first real emission so the stream
    /// doesn't open with a stray gap.
    private mutating func emit(_ text: String) -> String {
        if emittedAny { return text }
        let trimmed = String(text.drop(while: \.isWhitespace))
        guard !trimmed.isEmpty else { return "" }
        emittedAny = true
        return trimmed
    }
}
