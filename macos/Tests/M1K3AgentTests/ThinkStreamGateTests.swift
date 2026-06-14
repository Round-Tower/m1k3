//
//  ThinkStreamGateTests.swift
//  M1K3AgentTests
//
//  The native loop streams a model turn's tokens BEFORE knowing whether the
//  turn ends in text or tool calls. The gate makes that safe: the think phase
//  (tags included) is emitted live — it routes to the reasoning disclosure —
//  while post-think text is held back until the outcome is known (.text →
//  flush as the answer; .toolCalls → discard, the transcript keeps it).
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
@testable import M1K3Agent
import Testing

struct ThinkStreamGateTests {
    private func feedAll(_ tokens: [String]) -> (live: String, gate: ThinkStreamGate) {
        var gate = ThinkStreamGate()
        var live = ""
        for token in tokens {
            live += gate.feed(token)
        }
        return (live, gate)
    }

    private func feedAllWithAnswer(_ tokens: [String]) -> (live: String, answer: String, gate: ThinkStreamGate) {
        var gate = ThinkStreamGate()
        var live = ""
        var answer = ""
        for token in tokens {
            live += gate.feed(token, onAnswerToken: { answer += $0 })
        }
        return (live, answer, gate)
    }

    @Test("the think phase streams live, tags included; the answer is held back")
    func thinkStreamsLive() {
        var (live, gate) = feedAll(["<think>", "checking", " tools", "</think>", "It's sunny."])
        #expect(live == "<think>checking tools</think>")
        #expect(gate.flushRemainder() == "It's sunny.")
    }

    @Test("a non-thinking turn emits nothing live; everything is in the remainder")
    func noThinkAllHeld() {
        var (live, gate) = feedAll(["The answer ", "is 42."])
        #expect(live == "")
        #expect(gate.flushRemainder() == "The answer is 42.")
    }

    @Test("a close tag split across tokens is still caught")
    func splitCloseTag() {
        var (live, gate) = feedAll(["<think>plan</th", "ink>answer"])
        #expect(live == "<think>plan</think>")
        #expect(gate.flushRemainder() == "answer")
    }

    @Test("whitespace before the opening tag still engages live mode")
    func leadingWhitespace() {
        var (live, gate) = feedAll(["\n<think>plan</think>done"])
        #expect(live == "<think>plan</think>")
        #expect(gate.flushRemainder() == "done")
    }

    @Test("an unclosed think flushes its tail as the remainder")
    func unclosedThink() {
        var (live, gate) = feedAll(["<think>endless thought"])
        #expect(live.hasPrefix("<think>"))
        #expect(live + gate.flushRemainder() == "<think>endless thought")
    }

    @Test("a bare </think> with no opener buffers as plain text, never streams live")
    func bareCloseWithoutOpen() {
        // Only reachable when the synthetic opener is NOT prepended (a
        // non-pre-opening template emitting a stray close): it must be held
        // for the outcome like any other non-think text.
        var (live, gate) = feedAll(["</think>just an answer"])
        #expect(live.isEmpty)
        #expect(gate.flushRemainder() == "</think>just an answer")
    }

    @Test("answer tokens stream live after </think> via the callback")
    func answerStreamsLive() {
        var (live, answer, gate) = feedAllWithAnswer(["<think>", "plan", "</think>", "The", " answer."])
        #expect(live == "<think>plan</think>")
        #expect(answer == "The answer.")
        #expect(gate.flushRemainder() == "The answer.")
    }

    @Test("a non-thinking turn streams to answer via callback")
    func noThinkAnswer() {
        var (live, answer, gate) = feedAllWithAnswer(["Plain ", "answer text."])
        #expect(live.isEmpty)
        #expect(answer == "Plain answer text.")
        #expect(gate.flushRemainder() == "Plain answer text.")
    }

    @Test("answer callback is not invoked for thinking-only turns")
    func thinkOnlyNoAnswer() {
        var gate = ThinkStreamGate()
        var answerFired = false
        let live = gate.feed("<think>just thinking</think>", onAnswerToken: { _ in answerFired = true })
        #expect(live == "<think>just thinking</think>")
        #expect(!answerFired)
        #expect(gate.flushRemainder().isEmpty)
    }

    @Test("answer tokens after a split close tag are streamed")
    func answerAfterSplitClose() {
        var (live, answer, gate) = feedAllWithAnswer(["<think>p", "lan</th", "ink>It's done."])
        #expect(live == "<think>plan</think>")
        #expect(answer == "It's done.")
        #expect(gate.flushRemainder() == "It's done.")
    }

    @Test("full answer in one chunk (like StatelessToolTurnSession)")
    func fullAnswerOneChunk() {
        var (live, answer, gate) = feedAllWithAnswer(["<think>checking the weather</think>It's sunny."])
        #expect(live == "<think>checking the weather</think>")
        #expect(answer == "It's sunny.")
        #expect(gate.flushRemainder() == "It's sunny.")
    }
}
