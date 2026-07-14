//
//  StreamingFollowUpSplitter.swift
//  M1K3Chat
//
//  The live counterpart to FollowUpSplit: hides the "FOLLOWUPS: [...]" trailer
//  from the streaming bubble as it arrives, using the same holdback-window
//  technique as StreamingReasoningSplitter (a suffix that could still be the
//  start of the watched marker is never emitted, so a marker split across
//  chunk boundaries can't leak a fragment).
//
//  Simpler than the reasoning splitter: one marker, no close tag, terminal —
//  once the sentinel is seen, everything from there to end-of-stream is
//  trailer, forever. `.raw` accumulates the full input for the final-authority
//  pass (FollowUpSplit.split(splitter.raw)), exactly as ReasoningSplit.split
//  is the authority over StreamingReasoningSplitter.raw — this struct only
//  drives live rendering.
//
//  Signed: Kev + claude-sonnet-5, 2026-07-14, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Inference

struct StreamingFollowUpSplitter {
    /// Answer text routed out so far (live view of the bubble) — never
    /// contains any part of the follow-ups trailer.
    private(set) var answer = ""
    /// The unsplit accumulated stream — feed this to FollowUpSplit.split as
    /// the single source of truth once the stream finishes.
    private(set) var raw = ""

    private enum Mode {
        /// Could still open with the sentinel.
        case scanning
        /// Sentinel seen: everything remaining is trailer, never emitted.
        case trailer
    }

    private var mode: Mode = .scanning
    private var buffer = ""

    private static let sentinel = FollowUpSplit.sentinel

    /// Feed one raw stream chunk (delta or cumulative snapshot).
    mutating func feed(_ chunk: String) {
        let delta = StreamFold.delta(current: raw, chunk: chunk)
        raw += delta
        buffer += delta
        process()
    }

    /// Release any held-back tail once the stream is over. A held-back
    /// suffix that never completed the sentinel was a near-miss, not a
    /// trailer — it belongs in the answer.
    mutating func finish() {
        if mode == .scanning {
            appendAnswer(buffer)
        }
        buffer = ""
    }

    private mutating func process() {
        guard mode == .scanning else { return }
        if let range = buffer.range(of: Self.sentinel) {
            appendAnswer(String(buffer[..<range.lowerBound]))
            // The sentinel means the answer is now permanently closed — trim
            // the trailing whitespace that separated it from the trailer,
            // since nothing will ever be appended to `answer` again. Safe
            // only here: mid-stream, a trailing space may still be followed
            // by more real text next chunk.
            while let last = answer.last, last.isWhitespace {
                answer.removeLast()
            }
            buffer = ""
            mode = .trailer
        } else {
            appendAnswer(takeAllButHoldback())
        }
    }

    private mutating func appendAnswer(_ text: String) {
        var piece = text
        if answer.isEmpty { piece = String(piece.drop(while: \.isWhitespace)) }
        guard !piece.isEmpty else { return }
        answer += piece
    }

    /// Drain the buffer except the longest tail that could still be the
    /// start of the sentinel — keeps a dangling half-marker out of the
    /// visible answer. Mirrors StreamingReasoningSplitter's holdback trick,
    /// specialised to a single marker instead of a tag list.
    private mutating func takeAllButHoldback() -> String {
        let maxKeep = min(buffer.count, Self.sentinel.count - 1)
        var keep = 0
        if maxKeep > 0 {
            for length in stride(from: maxKeep, through: 1, by: -1) {
                if buffer.hasSuffix(Self.sentinel.prefix(length)) {
                    keep = length
                    break
                }
            }
        }
        let emitted = String(buffer.dropLast(keep))
        buffer = String(buffer.suffix(keep))
        return emitted
    }
}
