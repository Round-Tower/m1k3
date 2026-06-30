//
//  HistoryBudgetPolicy.swift
//  M1K3Chat
//
//  Brain-aware sizing for the conversation replay (the long-context work). The
//  per-message text replay (HistoryWindow) is correct-by-construction — nothing
//  to invalidate — but how WIDE it can safely go is a per-tier fact, not a knob:
//    • dense Qwen3 (lil/huge) hold a ~32K window in an unbounded KVCacheSimple —
//      a wide replay costs memory, never truncates.
//    • gemma-4-e4b (big) holds a HARD RotatingKVCache(maxSize: 8192) — a prompt
//      over the window silently rotates the persona/grounding HEAD out during
//      prefill, answering off-persona and ungrounded with NO error. So `big`
//      MUST be clamped below 8192 (with margin for the char≈token estimate).
//
//  This pure policy turns a tier + the fixed-prompt reserve into a
//  `HistoryWindow.Budget`. It keeps `perTurnChars ≤ totalChars` so the rendered
//  block is provably ≤ totalChars (the rotating-KV clamp invariant), and caps the
//  replay at a latency ceiling so re-prefill cost stays bounded (replay prefill
//  does not amortize — the honest tradeoff for skipping KV persistence).
//
//  The char≈token ratio is a heuristic (the safety net is the conservative ratio
//  + the rotating margin + the on-device SelfTest verify that gemma's head never
//  rotates). Tune `charsPerToken`/`defaultLatencyCeilingTokens` from the ttft-log
//  token counts — the named [SPIKE].
//
//  Signed: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.8 (the gemma-8192
//  clamp invariant is TDD-pinned; the real-token guarantee for dense/URL text is
//  the verify-owed the margin hedges). Prior: the long-context design memo.
//

import Foundation
import M1K3Inference

public enum HistoryBudgetPolicy {
    /// Chars-per-token estimate. Conservative for English PROSE (real ≈4 chars/
    /// token, so a char budget maps to FEWER tokens than the model counts — the
    /// safe direction). The RESIDUAL RISK is the other way: dense code/URL/CJK
    /// text tokenizes DENSER (≈3 / ≈1 chars/token), so the same chars cost MORE
    /// tokens than estimated — on a rotating tier that eats into the window. The
    /// `rotatingSafetyMarginTokens` buffer hedges the common (code) case; CJK and
    /// the hard guarantee need real token counting (the deferred provider seam /
    /// [SPIKE]). The on-device SelfTest fixture should include a code-paste turn.
    static let charsPerToken = 3.5
    /// Don't re-prefill the whole conversation every turn even when the model
    /// could hold it: cap the replay so TTFT stays bounded (replay prefill does
    /// not amortize). The wide-context tiers hit this before their real window.
    public static let defaultLatencyCeilingTokens = 8000
    /// Head-room subtracted on a rotating-KV tier to absorb the char≈token
    /// estimate error, so gemma's 8192 window isn't crossed in practice. 1024
    /// (≈3600 chars of buffer) covers a moderate code paste in the replay at the
    /// worst-case ≈3 chars/token; a CJK-heavy replay still needs real token
    /// counting (named residual — the gemma cliff is silent, so we over-reserve).
    static let rotatingSafetyMarginTokens = 1024
    /// Per-turn fidelity cap (one long answer can't swallow the whole budget).
    static let perTurnCharCap = 1500
    /// Turn-count ceiling.
    static let maxTurns = 16

    /// Apple Foundation Models (mini) manages its OWN ~4K window and fails LOUDLY
    /// on overflow, and its real effective budget is unmeasured (the [SPIKE]). So
    /// mini gets a fixed CONSERVATIVE replay — wider per-turn than the legacy
    /// 6×400 (less answer mangling) but well under the window — NOT the wide MLX
    /// default and NOT the tier policy (whose MLX-sized reserves would zero it
    /// out). Revisit once AFM's effective window is measured on-device.
    public static let conservativeMiniBudget = HistoryWindow.Budget(
        totalChars: 3000, perTurnChars: 750, maxTurns: 8
    )

    /// The replay budget for a tier, reserving room for the fixed prompt (persona
    /// + tools + grounding + goal, in `reservedTokens`) and the generation
    /// headroom (`generationTokens`). On a rotating-KV tier the result is
    /// hard-clamped with margin so the TOTAL prompt provably stays under the
    /// window. The latency ceiling caps the replay on the wide tiers.
    public static func budget(
        for tier: BrainTier,
        reservedTokens: Int,
        generationTokens: Int,
        latencyCeilingTokens: Int = defaultLatencyCeilingTokens
    ) -> HistoryWindow.Budget {
        let margin = tier.usesRotatingKVCache ? rotatingSafetyMarginTokens : 0
        let contextTokens = max(
            0, tier.approximateContextTokens - reservedTokens - generationTokens - margin
        )
        let historyTokens = min(contextTokens, max(0, latencyCeilingTokens))
        let totalChars = Int(Double(historyTokens) * charsPerToken)
        let perTurnChars = min(perTurnCharCap, totalChars)
        return HistoryWindow.Budget(
            totalChars: totalChars, perTurnChars: perTurnChars, maxTurns: maxTurns
        )
    }
}
