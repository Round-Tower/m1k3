//
//  StreamingReasoningSplitter.swift
//  M1K3Chat
//
//  The live counterpart to ReasoningSplit: routes streamed chunks to reasoning
//  vs answer AS THEY ARRIVE, so a reasoning model's chain-of-thought renders in
//  the disclosure in real time — the user sees the model working within a
//  second instead of staring at a silent bubble for the whole think phase.
//
//  Handles both provider stream contracts (cumulative snapshots à la Apple
//  Foundation Models, plain deltas from MLX) with the same normalise-to-delta
//  rule as ChatSession.fold, tags split across chunk boundaries (a small
//  holdback window, same trick as ConclusionStreamSplitter), and Qwen3.5's
//  lone `</think>` (the template pre-opens the tag) via a retro-move of the
//  accumulated text into reasoning. The post-stream ReasoningSplit over `raw`
//  remains the final authority; this struct only drives the live rendering.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation

struct StreamingReasoningSplitter {
    /// Chain-of-thought routed out so far (live view of the disclosure).
    private(set) var reasoning = ""
    /// Answer text routed out so far (live view of the bubble).
    private(set) var answer = ""
    /// The unsplit accumulated stream — feed this to the post-stream
    /// ReasoningSplit/CitationValidator pass as the single source of truth.
    private(set) var raw = ""

    private enum Mode {
        /// Start state: could still open with `<think>`.
        case scanning
        /// Inside a think block, watching for the close tag.
        case reasoning
        /// Plain text so far, but a lone `</think>` may still arrive (Qwen3.5).
        case answerWatching
        /// Think block closed — everything else is answer.
        case answer
    }

    private var mode: Mode = .scanning
    private var buffer = ""

    private static let openTag = "<think>"
    private static let closeTag = "</think>"

    /// Feed one raw stream chunk (delta or cumulative snapshot).
    mutating func feed(_ chunk: String) {
        // Normalise cumulative snapshots to deltas (ChatSession.fold's rule):
        // a chunk that extends the accumulated text is a snapshot, replace.
        let delta: String = if !raw.isEmpty, chunk.hasPrefix(raw) {
            String(chunk.dropFirst(raw.count))
        } else {
            chunk
        }
        raw += delta
        buffer += delta
        process()
    }

    /// Release any held-back tail once the stream is over.
    mutating func finish() {
        process()
        switch mode {
        case .reasoning:
            appendReasoning(buffer)
        case .scanning, .answerWatching, .answer:
            appendAnswer(buffer)
        }
        buffer = ""
    }

    private mutating func process() {
        var advanced = true
        while advanced {
            advanced = false
            switch mode {
            case .scanning:
                buffer = String(buffer.drop(while: \.isWhitespace))
                if buffer.hasPrefix(Self.openTag) {
                    buffer.removeFirst(Self.openTag.count)
                    mode = .reasoning
                    advanced = true
                } else if !buffer.isEmpty, !Self.openTag.hasPrefix(buffer) {
                    mode = .answerWatching
                    advanced = true
                }

            case .reasoning:
                if let close = buffer.range(of: Self.closeTag) {
                    appendReasoning(String(buffer[..<close.lowerBound]))
                    buffer = String(buffer[close.upperBound...])
                    mode = .answer
                    advanced = true
                } else {
                    appendReasoning(takeAllButHoldback())
                }

            case .answerWatching:
                if let close = buffer.range(of: Self.closeTag) {
                    // Lone close with no open: the template opened the block
                    // for us — everything emitted so far was reasoning.
                    let before = String(buffer[..<close.lowerBound])
                    reasoning = trimTrailingWhitespace(answer + before)
                    answer = ""
                    buffer = String(buffer[close.upperBound...])
                    mode = .answer
                    advanced = true
                } else {
                    appendAnswer(takeAllButHoldback())
                }

            case .answer:
                appendAnswer(buffer)
                buffer = ""
            }
        }
    }

    /// Drain the buffer except the longest tail that could still be the start
    /// of a split `</think>` — keeps a dangling half-tag out of the output.
    private mutating func takeAllButHoldback() -> String {
        var keep = 0
        let maxKeep = min(buffer.count, Self.closeTag.count - 1)
        if maxKeep > 0 {
            for length in stride(from: maxKeep, through: 1, by: -1) {
                if buffer.hasSuffix(Self.closeTag.prefix(length)) {
                    keep = length
                    break
                }
            }
        }
        let emitted = String(buffer.dropLast(keep))
        buffer = String(buffer.suffix(keep))
        return emitted
    }

    private mutating func appendReasoning(_ text: String) {
        var piece = text
        if reasoning.isEmpty { piece = String(piece.drop(while: \.isWhitespace)) }
        reasoning += piece
    }

    private mutating func appendAnswer(_ text: String) {
        var piece = text
        if answer.isEmpty { piece = String(piece.drop(while: \.isWhitespace)) }
        answer += piece
    }

    private func trimTrailingWhitespace(_ text: String) -> String {
        var trimmed = text
        while let last = trimmed.last, last.isWhitespace {
            trimmed.removeLast()
        }
        return trimmed
    }
}
