//
//  HistoryWindow.swift
//  M1K3Chat
//
//  Chat memory: until M1K3 had this, every turn was independent — the model never
//  saw prior turns, so follow-ups fell flat. This renders a budgeted replay of
//  recent turns into the grounding prompt: answer text only (never reasoning,
//  never sources).
//
//  The window is governed by a TOTAL character budget filled NEWEST-FIRST, with
//  a generous per-turn cap and a turn-count ceiling on top. The total budget is
//  the real prefill governor; the per-turn cap is deliberately large so M1K3's
//  OWN prior answer survives intact — the old 400-char cap chopped it, so a
//  follow-up like "expand on your third point" had already lost the third point.
//  The headroom is cheap: the models run ~32K context and 8-bit quantized KV, and
//  the persona prefix is cached — only this replay re-prefills each turn.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8, Prior: Unknown
//  Review: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.85 — widened from
//  6×400 to a newest-first 8000-char budget / 1500-per-turn / 16-turn ceiling.
//  The 6×400 window was far more conservative than the model or Mac needs and
//  mangled M1K3's own answers; budget-governed windowing gives real multi-turn
//  headroom while keeping prefill bounded. Tune from the TTFT logs at ⌘R.
//

import Foundation

/// One prior exchange turn, flattened for replay.
public struct ChatTurn: Sendable, Equatable {
    public enum Role: Sendable, Equatable {
        case user
        case assistant
    }

    public let role: Role
    public let text: String

    public init(role: Role, text: String) {
        self.role = role
        self.text = text
    }
}

public enum HistoryWindow {
    /// Default hard ceiling on turns kept, newest-first (≈8 exchanges).
    static let maxTurns = 16
    /// Default per-turn character cap — generous so M1K3's own prior answer
    /// survives intact (the 400-char cap mangled it). Most answers fit whole.
    static let maxCharsPerTurn = 1500
    /// Default total budget across the whole replay (~2K tokens at ≈4 chars/token).
    static let maxTotalChars = 8000

    /// The replay budget — injected so it can be made BRAIN-AWARE (see
    /// `HistoryBudgetPolicy`): a wide window on the big-context Qwen tiers, a hard
    /// clamp on gemma-4 (`big`), whose RotatingKVCache silently rotates the
    /// persona/grounding HEAD out past 8192 tokens. The defaults reproduce the
    /// conservative shipped window so an unwired caller is byte-identical.
    public struct Budget: Sendable, Equatable {
        /// Total chars across the whole replay block (the real prefill governor).
        public var totalChars: Int
        /// Per-turn fidelity cap (so one long answer can't swallow the budget).
        public var perTurnChars: Int
        /// Hard ceiling on turn count (caps a long run of tiny turns).
        public var maxTurns: Int

        public init(totalChars: Int, perTurnChars: Int, maxTurns: Int) {
            self.totalChars = totalChars
            self.perTurnChars = perTurnChars
            self.maxTurns = maxTurns
        }

        /// Today's conservative shipped window — the no-opt-in default.
        public static let `default` = Budget(
            totalChars: HistoryWindow.maxTotalChars,
            perTurnChars: HistoryWindow.maxCharsPerTurn,
            maxTurns: HistoryWindow.maxTurns
        )
    }

    /// Render the replay block for the grounding prompt, or nil when there is
    /// no usable history. Turns are walked newest→oldest, each truncated to the
    /// per-turn cap, accumulated until the next would exceed the total budget;
    /// the result is re-ordered chronologically. The most recent turn is always
    /// kept. NOTE the rendered block can exceed `totalChars` by at most one
    /// `perTurnChars` (the unconditional newest turn) — so a caller that needs a
    /// hard upper bound (a rotating-KV tier) must keep `perTurnChars ≤ totalChars`
    /// (`HistoryBudgetPolicy` does), making the block provably ≤ `totalChars`.
    static func render(_ turns: [ChatTurn], budget: Budget = .default) -> String? {
        let usable = turns
            .map { ChatTurn(role: $0.role, text: $0.text.trimmingCharacters(in: .whitespacesAndNewlines)) }
            .filter { !$0.text.isEmpty }
            .suffix(budget.maxTurns)
        guard !usable.isEmpty else { return nil }

        var kept: [String] = []
        var total = 0
        for turn in usable.reversed() {
            let label = turn.role == .user ? "USER" : "M1K3"
            let line = "\(label): \(truncate(turn.text, max: budget.perTurnChars))"
            // Keep at least the newest turn even if it alone would exceed the
            // budget (its per-turn cap still bounds it); stop once full.
            if !kept.isEmpty, total + line.count > budget.totalChars { break }
            kept.append(line)
            total += line.count
        }
        guard !kept.isEmpty else { return nil }

        return "CONVERSATION SO FAR (context for the new question):\n"
            + kept.reversed().joined(separator: "\n")
    }

    private static func truncate(_ text: String, max: Int) -> String {
        guard text.count > max else { return text }
        return text.prefix(max).trimmingCharacters(in: .whitespaces) + "…"
    }
}
