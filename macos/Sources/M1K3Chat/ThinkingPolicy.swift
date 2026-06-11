//
//  ThinkingPolicy.swift
//  M1K3Chat
//
//  Per-turn reasoning budget: Qwen3.5's think phase is gold on hard questions
//  and a 30-second tax on "yo, what's up?". Auto mode decides from cheap
//  signals — grounding present, analytic verbs, length — and the Settings
//  override (Always think / Fast answers) always wins. Fast turns render
//  no reasoning disclosure at all, honestly: nothing was thought.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8 (heuristic is a
//  starting point — tune on real turns). Prior: Unknown
//

import Foundation

/// The Settings-facing reasoning preference.
public enum ThinkingMode: String, CaseIterable, Sendable {
    case auto
    case always
    case fast
}

enum ThinkingPolicy {
    /// Words at or above which a question earns the think phase regardless
    /// of other signals.
    private static let longQuestionWordCount = 12

    /// Analytic markers — asks that benefit from chain-of-thought even when
    /// short. Matched as SUBSTRINGS (not word prefixes) on purpose — "analy"
    /// catches analyse/analyze/analysis — which also means "barcode" trips
    /// "code" and "rewrite" trips "write": acceptable false positives, they
    /// only cost a think phase. Tune from real ttft logs.
    private static let analyticMarkers = [
        "why", "how", "explain", "compare", "analy", "plan", "debug",
        "write", "code", "calculate", "summar", "review", "design",
    ]

    static func shouldThink(
        question: String,
        hasGroundedKnowledge: Bool,
        mode: ThinkingMode
    ) -> Bool {
        switch mode {
        case .always: return true
        case .fast: return false
        case .auto: break
        }
        if hasGroundedKnowledge { return true }
        let lowered = question.lowercased()
        if analyticMarkers.contains(where: { lowered.contains($0) }) { return true }
        let words = lowered.split(whereSeparator: \.isWhitespace).count
        return words >= longQuestionWordCount
    }
}
