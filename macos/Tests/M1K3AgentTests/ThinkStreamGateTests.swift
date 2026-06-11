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
}
