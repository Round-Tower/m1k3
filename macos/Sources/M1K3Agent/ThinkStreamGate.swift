//
//  ThinkStreamGate.swift
//  M1K3Agent
//
//  Streams a native turn's THINK phase live while holding everything after it
//  back until the turn's outcome is known. A reasoning model spends most of
//  its latency inside <think>…</think>; emitting that phase as it happens
//  (it routes to the chat's reasoning disclosure) turns a silent 30-second
//  stall into visible work. Post-think text can't stream yet — the turn may
//  still end in tool calls, and prose-before-a-call must not reach the answer
//  bubble — so it buffers until the loop decides: flush on .text, drop on
//  .toolCalls (the transcript keeps the model's text either way).
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation

struct ThinkStreamGate {
    private enum Mode {
        /// Undecided: the stream may still open with `<think>`.
        case scanning
        /// Inside the think phase — emitting live, watching for the close tag.
        case live
        /// Think phase over (or never started): buffering for the outcome.
        case buffering
    }

    private var mode: Mode = .scanning
    /// Undecided text: a possible tag fragment (scanning) or the close-tag
    /// holdback window (live).
    private var pending = ""
    /// Post-think (or non-think) text held for the turn outcome.
    private var buffered = ""

    private static let openTag = "<think>"
    private static let closeTag = "</think>"

    /// Feed one streamed token. Returns the text that is safe to surface live
    /// (the think phase, tags included) — empty otherwise.
    mutating func feed(_ token: String) -> String {
        pending += token
        switch mode {
        case .scanning:
            // Leading whitespace is dropped while undecided, so the gate is
            // not byte-lossless for a whitespace-prefixed non-think turn —
            // benign: every consumer trims the remainder anyway.
            pending = String(pending.drop(while: \.isWhitespace))
            if pending.hasPrefix(Self.openTag) {
                mode = .live
                return drainLive()
            }
            if !pending.isEmpty, !Self.openTag.hasPrefix(pending) {
                mode = .buffering
                buffered += pending
                pending = ""
            }
            return ""

        case .live:
            return drainLive()

        case .buffering:
            buffered += pending
            pending = ""
            return ""
        }
    }

    /// What was held back after the think phase (or the whole turn when it
    /// never thought). Emit on a `.text` outcome; discard on `.toolCalls`.
    mutating func flushRemainder() -> String {
        let remainder = buffered + pending
        buffered = ""
        pending = ""
        return remainder
    }

    /// Emit pending live text up to (and including) the close tag; hold back a
    /// tail that could still be the start of a split close tag.
    private mutating func drainLive() -> String {
        if let close = pending.range(of: Self.closeTag) {
            let emitted = String(pending[..<close.upperBound])
            buffered += String(pending[close.upperBound...])
            pending = ""
            mode = .buffering
            return emitted
        }
        var keep = 0
        let maxKeep = min(pending.count, Self.closeTag.count - 1)
        if maxKeep > 0 {
            for length in stride(from: maxKeep, through: 1, by: -1) {
                if pending.hasSuffix(Self.closeTag.prefix(length)) {
                    keep = length
                    break
                }
            }
        }
        let emitted = String(pending.dropLast(keep))
        pending = String(pending.suffix(keep))
        return emitted
    }
}
