//
//  StreamingFollowUpSplitterTests.swift
//  M1K3ChatTests
//
//  The live counterpart to FollowUpSplit: hides the "FOLLOWUPS: [...]" trailer
//  from the streaming bubble AS IT ARRIVES, rather than flashing raw JSON for
//  a frame before the post-stream split retroactively strips it. `.raw` is
//  the accumulated input for the final-authority pass (FollowUpSplit.split);
//  `.answer` is only the live view.
//
//  Signed: Kev + claude-sonnet-5, 2026-07-14, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Chat
import M1K3Inference // FollowUpSplit lives here now, alongside ThinkStripper
import Testing

struct StreamingFollowUpSplitterTests {
    private func run(_ chunks: [String]) -> StreamingFollowUpSplitter {
        var splitter = StreamingFollowUpSplitter()
        for chunk in chunks {
            splitter.feed(chunk)
        }
        splitter.finish()
        return splitter
    }

    @Test("plain text with no trailer streams straight to the answer")
    func plainAnswer() {
        let splitter = run(["The weather ", "is sunny."])
        #expect(splitter.answer == "The weather is sunny.")
    }

    @Test("a trailer arriving in one chunk never reaches the live answer")
    func trailerInOneChunk() {
        let splitter = run(["It's sunny.\nFOLLOWUPS: [\"Tomorrow?\"]"])
        #expect(splitter.answer == "It's sunny.")
        #expect(!splitter.answer.contains("FOLLOWUPS"))
    }

    @Test("the sentinel split across chunk boundaries is still caught, never leaked")
    func sentinelSplitAcrossChunks() {
        let splitter = run(["The answer.\nFOLLOW", "UPS: [\"Q?\"]"])
        #expect(splitter.answer == "The answer.")
        #expect(!splitter.answer.contains("FOLLOW"))
    }

    @Test("the sentinel split one character at a time is still caught")
    func sentinelSplitCharByChar() {
        let chunks = "Answer.\nFOLLOWUPS: [\"Q?\"]".map(String.init)
        let splitter = run(chunks)
        #expect(splitter.answer == "Answer.")
    }

    @Test("live answer never transiently shows any part of the sentinel")
    func neverLeaksLiveEitherIntermediate() {
        var splitter = StreamingFollowUpSplitter()
        splitter.feed("Answer.\n")
        splitter.feed("FOLL")
        // Even mid-sentinel, nothing that could be part of "FOLLOWUPS:" is
        // visible yet — it's held back, not shown-then-retracted.
        #expect(!splitter.answer.contains("FOLL"))
        splitter.feed("OWUPS: [\"Q?\"]")
        splitter.finish()
        #expect(splitter.answer == "Answer.")
    }

    @Test("a near-miss that never completes the sentinel eventually flushes to the answer")
    func nearMissFlushesOnFinish() {
        let splitter = run(["The word FOLLOW is not a trailer."])
        #expect(splitter.answer == "The word FOLLOW is not a trailer.")
    }

    @Test("raw accumulates everything for the post-stream final-authority pass")
    func rawIsFullAccumulation() {
        let splitter = run(["Answer.\nFOLLOWUPS: [\"Q1?\", \"Q2?\"]"])
        let final = FollowUpSplit.split(splitter.raw)
        #expect(final.answer == "Answer.")
        #expect(final.followUps == ["Q1?", "Q2?"])
    }

    @Test("cumulative snapshots (Apple Foundation Models style) are normalised to deltas")
    func cumulativeSnapshots() {
        // Each feed is the FULL text so far, not just the new piece — same
        // contract StreamFold.delta already handles for ReasoningSplit.
        var splitter = StreamingFollowUpSplitter()
        splitter.feed("The ")
        splitter.feed("The weather ")
        splitter.feed("The weather is sunny.")
        splitter.finish()
        #expect(splitter.answer == "The weather is sunny.")
    }
}
