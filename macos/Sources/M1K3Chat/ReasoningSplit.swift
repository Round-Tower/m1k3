//
//  ReasoningSplit.swift
//  M1K3Chat
//
//  Reasoning models (Qwen3 and friends) emit their chain-of-thought inside
//  <think>…</think> before the answer. We separate that reasoning from the
//  answer so the bubble shows a clean reply while the thinking is surfaced
//  one tap away — transparency without clutter.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation

enum ReasoningSplit {
    /// Separate `<think>…</think>` chain-of-thought from the answer. Returns the
    /// joined reasoning (nil if none) and the answer with the think blocks
    /// removed. An unclosed `<think>` (a stream cut mid-thought) treats the rest
    /// as reasoning; both parts are whitespace-trimmed.
    static func split(_ text: String) -> (reasoning: String?, answer: String) {
        var reasoningParts: [String] = []
        var answer = ""
        var remaining = Substring(text)

        while let open = remaining.range(of: "<think>") {
            answer += remaining[..<open.lowerBound]
            let afterOpen = remaining[open.upperBound...]
            if let close = afterOpen.range(of: "</think>") {
                reasoningParts.append(String(afterOpen[..<close.lowerBound]))
                remaining = afterOpen[close.upperBound...]
            } else {
                // Unclosed: everything after the opening tag is reasoning.
                reasoningParts.append(String(afterOpen))
                remaining = ""
                break
            }
        }
        answer += remaining

        let reasoning = reasoningParts
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: "\n\n")
        return (
            reasoning.isEmpty ? nil : reasoning,
            answer.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }
}
