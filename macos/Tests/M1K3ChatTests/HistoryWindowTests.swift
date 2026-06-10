//
//  HistoryWindowTests.swift
//  M1K3ChatTests
//
//  The minimal chat memory: a tightly capped replay of recent turns rendered
//  into the grounding prompt. Caps are the contract — history replay grows
//  prefill, which fights the TTFT work, so the window must stay small.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
@testable import M1K3Chat
import Testing

struct HistoryWindowTests {
    @Test("no history renders nothing")
    func emptyIsNil() {
        #expect(HistoryWindow.render([]) == nil)
    }

    @Test("turns render in order with role labels")
    func rendersInOrder() {
        let block = HistoryWindow.render([
            ChatTurn(role: .user, text: "What's the weather in Galway?"),
            ChatTurn(role: .assistant, text: "18°C and cloudy."),
        ])
        let text = block ?? ""
        #expect(text.contains("CONVERSATION SO FAR"))
        let userIndex = text.range(of: "USER: What's the weather in Galway?")
        let assistantIndex = text.range(of: "M1K3: 18°C and cloudy.")
        #expect(userIndex != nil)
        #expect(assistantIndex != nil)
        if let userIndex, let assistantIndex {
            #expect(userIndex.lowerBound < assistantIndex.lowerBound)
        }
    }

    @Test("only the most recent turns survive the cap")
    func capsTurnCount() {
        let turns = (1 ... 10).map { ChatTurn(role: .user, text: "question \($0)") }
        let text = HistoryWindow.render(turns) ?? ""
        #expect(!text.contains("question 1\n"))
        #expect(text.contains("question 10"))
        #expect(text.components(separatedBy: "USER:").count - 1 == HistoryWindow.maxTurns)
    }

    @Test("long turns truncate with an ellipsis")
    func truncatesLongTurns() {
        let long = String(repeating: "word ", count: 400)
        let text = HistoryWindow.render([ChatTurn(role: .assistant, text: long)]) ?? ""
        #expect(text.count < long.count)
        #expect(text.contains("…"))
    }

    @Test("whitespace-only turns are skipped")
    func skipsEmptyTurns() {
        #expect(HistoryWindow.render([ChatTurn(role: .assistant, text: "  \n ")]) == nil)
    }
}
