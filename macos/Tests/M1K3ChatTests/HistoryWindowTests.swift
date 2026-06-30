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

    @Test("only the most recent turns survive the turn-count ceiling")
    func capsTurnCount() {
        // More turns than the ceiling, each tiny (so the total-char budget can't
        // bite first) — the turn ceiling is what trims. The `END` delimiter keeps
        // "Q1END" from being a substring of "Q10END".
        let n = HistoryWindow.maxTurns + 5
        let turns = (1 ... n).map { ChatTurn(role: .user, text: "Q\($0)END") }
        let text = HistoryWindow.render(turns) ?? ""
        #expect(!text.contains("Q1END")) // oldest dropped
        #expect(text.contains("Q\(n)END")) // newest kept
        #expect(text.components(separatedBy: "USER:").count - 1 == HistoryWindow.maxTurns)
    }

    @Test("the total-char budget keeps newest turns whole and drops the oldest")
    func totalBudgetFillsNewestFirst() {
        // Each turn ~1000 chars (under the per-turn cap, so untruncated). With a
        // ~8K budget only the newest handful fit, even though the turn ceiling
        // would allow all 16 — the budget is the real governor on prefill.
        let big = String(repeating: "x", count: 1000)
        // Bracketed markers so "[1]" can't be a substring of "[11]"/"[16]".
        let turns = (1 ... 16).map { ChatTurn(role: .user, text: "[\($0)]" + big) }
        let text = HistoryWindow.render(turns) ?? ""
        #expect(text.count < HistoryWindow.maxTotalChars + 100) // bounded (+ header slack)
        #expect(text.contains("[16]")) // newest survives whole
        #expect(!text.contains("[1]")) // oldest dropped by the budget
    }

    @Test("the most recent turn is never dropped, even against the budget")
    func mostRecentAlwaysKept() {
        // Per-turn cap < total budget, so one turn always fits — guard the invariant.
        let turns = (1 ... 20).map { _ in ChatTurn(role: .assistant, text: String(repeating: "y", count: 2000)) }
        let text = HistoryWindow.render(turns) ?? ""
        #expect(text.contains("M1K3:")) // at least the newest rendered
        #expect(text.contains("…")) // truncated to the per-turn cap
    }

    @Test("a normal-length answer survives intact (no more 400-char mangling)")
    func normalAnswerSurvivesIntact() {
        // ~600 chars: over the OLD 400 cap, under the new per-turn cap → kept whole.
        // This is the "expand on your third point" fix — M1K3's own answer is no
        // longer chopped before the follow-up sees it.
        // ≈623 chars, no trailing space (the per-turn trim would strip it).
        let answer = Array(repeating: "A clear, useful sentence.", count: 24).joined(separator: " ")
        let text = HistoryWindow.render([
            ChatTurn(role: .user, text: "explain it"),
            ChatTurn(role: .assistant, text: answer),
        ]) ?? ""
        #expect(text.contains(answer)) // whole answer present
        #expect(!text.contains("…")) // not truncated
    }

    @Test("turns longer than the per-turn cap truncate with an ellipsis")
    func truncatesLongTurns() {
        let long = String(repeating: "word ", count: 600) // 3000 chars > per-turn cap
        let text = HistoryWindow.render([ChatTurn(role: .assistant, text: long)]) ?? ""
        #expect(text.count < long.count)
        #expect(text.contains("…"))
    }

    @Test("the default budget reproduces the shipped constants byte-for-byte")
    func defaultBudgetMatchesConstants() {
        #expect(HistoryWindow.Budget.default.totalChars == HistoryWindow.maxTotalChars)
        #expect(HistoryWindow.Budget.default.perTurnChars == HistoryWindow.maxCharsPerTurn)
        #expect(HistoryWindow.Budget.default.maxTurns == HistoryWindow.maxTurns)
        let turns = [
            ChatTurn(role: .user, text: "hi"),
            ChatTurn(role: .assistant, text: "hello"),
        ]
        #expect(HistoryWindow.render(turns) == HistoryWindow.render(turns, budget: .default))
    }

    @Test("an injected budget scales the window — wider keeps more, tighter keeps less")
    func injectedBudgetScales() {
        // 30 turns of ~500 chars each. A wide budget keeps far more total than a tight one.
        let turns = (1 ... 30).map { ChatTurn(role: .user, text: "[\($0)]" + String(repeating: "z", count: 500)) }
        let wide = HistoryWindow.render(turns, budget: .init(totalChars: 12000, perTurnChars: 1500, maxTurns: 40)) ?? ""
        let tight = HistoryWindow.render(turns, budget: .init(totalChars: 2000, perTurnChars: 1500, maxTurns: 40)) ?? ""
        #expect(wide.count > tight.count)
        #expect(wide.count < 12000 + 100) // bounded by the injected total (+ header)
        #expect(tight.count < 2000 + 100)
        #expect(wide.contains("[30]")) // both keep the newest
        #expect(tight.contains("[30]"))
    }

    @Test("with perTurnChars ≤ totalChars the block never exceeds totalChars (the rotating-KV clamp invariant)")
    func renderHonoursHardBound() {
        // HistoryBudgetPolicy keeps perTurn ≤ total for exactly this guarantee:
        // even the unconditional newest turn can't push the block over the cap.
        let turns = (1 ... 20).map { _ in ChatTurn(role: .assistant, text: String(repeating: "q", count: 4000)) }
        let budget = HistoryWindow.Budget(totalChars: 3000, perTurnChars: 3000, maxTurns: 16)
        let text = HistoryWindow.render(turns, budget: budget) ?? ""
        #expect(text.count <= budget.totalChars + 80) // header slack only
    }

    @Test("whitespace-only turns are skipped")
    func skipsEmptyTurns() {
        #expect(HistoryWindow.render([ChatTurn(role: .assistant, text: "  \n ")]) == nil)
    }
}
