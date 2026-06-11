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

    // Qwen3.5's chat template PRE-OPENS <think> in the generation prompt, so the
    // model's output contains only the CLOSING tag. Everything before a lone
    // </think> is reasoning.

    @Test("a lone closing tag treats the prefix as reasoning (Qwen3.5 template)")
    func loneClose() {
        let result = ReasoningSplit.split("I should check what model I am.</think>I am M1K3.")
        #expect(result.reasoning == "I should check what model I am.")
        #expect(result.answer == "I am M1K3.")
    }

    @Test("a lone closing tag with surrounding whitespace trims both parts")
    func loneCloseWhitespace() {
        let result = ReasoningSplit.split("\n thinking hard \n</think>\n\nThe answer.\n")
        #expect(result.reasoning == "thinking hard")
        #expect(result.answer == "The answer.")
    }

    @Test("a bare closing tag alone yields no reasoning and an empty answer")
    func loneCloseOnly() {
        let result = ReasoningSplit.split("</think>")
        #expect(result.reasoning == nil)
        #expect(result.answer == "")
    }

    @Test("a closing tag AFTER a matched pair stays in the answer")
    func closeAfterMatchedPair() {
        let result = ReasoningSplit.split("<think>plan</think>answer mentions </think> literally")
        #expect(result.reasoning == "plan")
        #expect(result.answer == "answer mentions </think> literally")
    }
}
