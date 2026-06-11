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
    /// as reasoning; a lone `</think>` with no opening tag (Qwen3.5's chat
    /// template pre-opens `<think>` in the prompt, so the model emits only the
    /// close) treats the prefix as reasoning. Both parts are whitespace-trimmed.
    static func split(_ text: String) -> (reasoning: String?, answer: String) {
        var reasoningParts: [String] = []
        var answer = ""
        var remaining = Substring(text)

        // Lone closing tag first: the close appears before any open (or with no
        // open at all) → everything up to it is reasoning the template opened
        // for us. Later tags are then handled by the normal pair loop.
        if let close = remaining.range(of: "</think>") {
            let open = remaining.range(of: "<think>")
            if open == nil || close.lowerBound < open!.lowerBound {
                reasoningParts.append(String(remaining[..<close.lowerBound]))
                remaining = remaining[close.upperBound...]
            }
        }

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
