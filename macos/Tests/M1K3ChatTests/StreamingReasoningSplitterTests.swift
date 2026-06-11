//
//  StreamingReasoningSplitterTests.swift
//  M1K3ChatTests
//
//  The live counterpart to ReasoningSplit: routes streamed chunks to reasoning
//  vs answer AS THEY ARRIVE, so chain-of-thought renders in the disclosure in
//  real time instead of flashing raw <think> text in the bubble until the
//  stream ends. Pins tag-split-across-chunks handling, the lone-</think>
//  retro-move (Qwen3.5), and cumulative-snapshot normalisation (AFM).
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
@testable import M1K3Chat
import Testing

struct StreamingReasoningSplitterTests {
    private func run(_ chunks: [String]) -> StreamingReasoningSplitter {
        var splitter = StreamingReasoningSplitter()
        for chunk in chunks {
            splitter.feed(chunk)
        }
        splitter.finish()
        return splitter
    }

    @Test("plain text streams straight to the answer")
    func plainAnswer() {
        let splitter = run(["The weather ", "is sunny."])
        #expect(splitter.reasoning == "")
        #expect(splitter.answer == "The weather is sunny.")
    }

    @Test("a think block routes to reasoning live, the rest to the answer")
    func thinkBlock() {
        let splitter = run(["<think>", "checking tools", "</think>", "It's sunny."])
        #expect(splitter.reasoning == "checking tools")
        #expect(splitter.answer == "It's sunny.")
    }

    @Test("tags split across chunk boundaries are still caught")
    func splitTags() {
        let splitter = run(["<th", "ink>plan", " here</th", "ink>the answer"])
        #expect(splitter.reasoning == "plan here")
        #expect(splitter.answer == "the answer")
    }

    @Test("reasoning streams incrementally before the close tag arrives")
    func incrementalReasoning() {
        var splitter = StreamingReasoningSplitter()
        splitter.feed("<think>step one, ")
        #expect(splitter.reasoning.hasPrefix("step one"))
        #expect(splitter.answer == "")
        splitter.feed("step two</think>done")
        splitter.finish()
        #expect(splitter.reasoning == "step one, step two")
        #expect(splitter.answer == "done")
    }

    @Test("a lone </think> retro-moves the accumulated text to reasoning (Qwen3.5)")
    func loneCloseRetroMove() {
        var splitter = StreamingReasoningSplitter()
        splitter.feed("I should check what model I am.")
        splitter.feed("</think>I am M1K3.")
        splitter.finish()
        #expect(splitter.reasoning == "I should check what model I am.")
        #expect(splitter.answer == "I am M1K3.")
    }

    @Test("cumulative snapshots (AFM) are normalised to deltas")
    func cumulativeSnapshots() {
        let splitter = run(["The wea", "The weather is ", "The weather is sunny."])
        #expect(splitter.reasoning == "")
        #expect(splitter.answer == "The weather is sunny.")
    }

    @Test("whitespace before <think> still opens reasoning mode")
    func leadingWhitespace() {
        let splitter = run(["\n <think>plan</think>answer"])
        #expect(splitter.reasoning == "plan")
        #expect(splitter.answer == "answer")
    }

    @Test("an unclosed think block keeps everything in reasoning")
    func unclosed() {
        let splitter = run(["<think>still thinking"])
        #expect(splitter.reasoning == "still thinking")
        #expect(splitter.answer == "")
    }

    @Test("the raw accumulation preserves the unsplit stream for the final pass")
    func rawPreserved() {
        let splitter = run(["<think>plan</think>", "answer"])
        #expect(splitter.raw == "<think>plan</think>answer")
    }

    @Test("a tag-like fragment at stream end is flushed by finish")
    func danglingFragmentFlushed() {
        let splitter = run(["the answer mentions <th"])
        #expect(splitter.answer == "the answer mentions <th")
    }

    @Test("multiple think blocks (one per agent iteration) all join the reasoning")
    func multipleThinkBlocks() {
        let splitter = run([
            "<think>check the weather</think>",
            "<think>got 18°C, summarise</think>",
            "It's 18°C in Galway.",
        ])
        #expect(splitter.reasoning == "check the weather\n\ngot 18°C, summarise")
        #expect(splitter.answer == "It's 18°C in Galway.")
    }

    @Test("a think block reopening after answer text routes back to reasoning")
    func reopenAfterAnswer() {
        let splitter = run(["<think>plan</think>partial ", "<think>more thought</think>final"])
        #expect(splitter.reasoning == "plan\n\nmore thought")
        #expect(splitter.answer == "partial final")
    }
}
