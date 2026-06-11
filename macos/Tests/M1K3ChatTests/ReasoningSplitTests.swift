//
//  ReasoningSplitTests.swift
//  M1K3ChatTests
//
//  Reasoning models (Qwen3, etc.) emit <think>…</think> chain-of-thought before
//  the answer. We surface that reasoning separately (transparency) rather than
//  leak it into the answer bubble — this pins the split.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Chat
import Testing

struct ReasoningSplitTests {
    @Test("plain text has no reasoning")
    func noThink() {
        let result = ReasoningSplit.split("The weather is sunny.")
        #expect(result.reasoning == nil)
        #expect(result.answer == "The weather is sunny.")
    }

    @Test("a single think block is separated from the answer")
    func singleBlock() {
        let result = ReasoningSplit.split("<think>Let me check the tools.</think>It's sunny in Boston.")
        #expect(result.reasoning == "Let me check the tools.")
        #expect(result.answer == "It's sunny in Boston.")
    }

    @Test("leading/trailing whitespace is trimmed on both parts")
    func whitespace() {
        let result = ReasoningSplit.split("  <think>  thinking  </think>  the answer  ")
        #expect(result.reasoning == "thinking")
        #expect(result.answer == "the answer")
    }

    @Test("multiple think blocks join into one reasoning string")
    func multipleBlocks() {
        let result = ReasoningSplit.split("<think>first</think>answer<think>second</think>")
        #expect(result.reasoning == "first\n\nsecond")
        #expect(result.answer == "answer")
    }

    @Test("an unclosed think block (stream cut mid-thought) is all reasoning")
    func unclosed() {
        let result = ReasoningSplit.split("<think>still thinking, no close tag")
        #expect(result.reasoning == "still thinking, no close tag")
        #expect(result.answer == "")
    }

    @Test("a think-only output yields an empty answer")
    func thinkOnly() {
        let result = ReasoningSplit.split("<think>just reasoning</think>")
        #expect(result.reasoning == "just reasoning")
        #expect(result.answer == "")
    }
}
