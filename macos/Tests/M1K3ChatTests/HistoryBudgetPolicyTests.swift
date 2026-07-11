//
//  HistoryBudgetPolicyTests.swift
//  M1K3ChatTests
//
//  Brain-aware conversation-replay sizing. The load-bearing guarantee: on a
//  rotating-KV tier (gemma-4 "big", maxKVSize=8192) the computed budget can NEVER
//  produce a prompt that crosses the window — else the persona/grounding head
//  silently rotates out. The wide dense-Qwen tiers get a genuinely larger window,
//  capped only by the latency ceiling (replay prefill doesn't amortize).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.85, Prior: Unknown
//

@testable import M1K3Chat
import M1K3Inference
import Testing

struct HistoryBudgetPolicyTests {
    /// Token-equivalent of a char budget at the policy's conservative ratio.
    private func historyTokens(_ budget: HistoryWindow.Budget) -> Int {
        Int(Double(budget.totalChars) / HistoryBudgetPolicy.charsPerToken)
    }

    @Test("rotating tier (big): the total prompt provably stays under the 8192 window")
    func rotatingTierNeverCrossesWindow() {
        let reserved = 1500
        let gen = 1024
        let budget = HistoryBudgetPolicy.budget(for: .big, reservedTokens: reserved, generationTokens: gen)
        // reserved + generation + history + the rotating safety margin ≤ the window.
        let total = reserved + gen + historyTokens(budget) + HistoryBudgetPolicy.rotatingSafetyMarginTokens
        #expect(total <= BrainTier.big.approximateContextTokens)
        // And the per-turn cap can't push the rendered block over total (clamp invariant).
        #expect(budget.perTurnChars <= budget.totalChars)
    }

    @Test("wide tier (lil) gets a strictly larger window than big for the same reserves")
    func wideTierIsWiderThanRotating() {
        let reserved = 1500
        let gen = 1024
        let big = HistoryBudgetPolicy.budget(for: .big, reservedTokens: reserved, generationTokens: gen)
        let lil = HistoryBudgetPolicy.budget(for: .lil, reservedTokens: reserved, generationTokens: gen)
        #expect(lil.totalChars > big.totalChars)
    }

    @Test("the latency ceiling caps even a wide-context tier (prefill cost stays bounded)")
    func latencyCeilingCapsWideTier() {
        let lil = HistoryBudgetPolicy.budget(
            for: .lil, reservedTokens: 1500, generationTokens: 1024,
            latencyCeilingTokens: 8000
        )
        // lil's context budget is ~30K, so the 8000-token ceiling binds, not the window.
        #expect(historyTokens(lil) == 8000)
        // A tighter ceiling shrinks it further — the knob actually bites.
        let tighter = HistoryBudgetPolicy.budget(
            for: .lil, reservedTokens: 1500, generationTokens: 1024,
            latencyCeilingTokens: 4000
        )
        #expect(tighter.totalChars < lil.totalChars)
    }

    @Test("a tier whose reserves swamp its window yields an empty (not negative) budget")
    func degenerateReservesClampToZero() {
        let budget = HistoryBudgetPolicy.budget(
            for: .mini, reservedTokens: 5000, generationTokens: 4096 // > mini's 4096 window
        )
        #expect(budget.totalChars == 0)
        #expect(budget.perTurnChars == 0)
    }

    @Test("mini's small window yields a small budget, well under the wide tiers")
    func miniIsSmallButPositive() {
        let mini = HistoryBudgetPolicy.budget(for: .mini, reservedTokens: 1000, generationTokens: 1024)
        let lil = HistoryBudgetPolicy.budget(for: .lil, reservedTokens: 1000, generationTokens: 1024)
        #expect(mini.totalChars > 0)
        #expect(mini.totalChars < lil.totalChars)
    }

    @Test("the conservative mini budget is non-zero, under the wide default, and fits AFM's window")
    func conservativeMiniBudgetIsSafe() {
        let mini = HistoryBudgetPolicy.conservativeMiniBudget
        // Non-zero (mini keeps multi-turn) but well under the wide MLX default
        // (which would overflow AFM's ~4K window — the CRITICAL the reviewer caught).
        #expect(mini.totalChars > 0)
        #expect(mini.totalChars < HistoryWindow.Budget.default.totalChars)
        // Token-fits AFM: history + a generous persona/grounding/answer reserve ≤ window.
        let historyTokens = Int(Double(mini.totalChars) / HistoryBudgetPolicy.charsPerToken)
        #expect(historyTokens + 3000 <= BrainTier.mini.approximateContextTokens)
        // Per-turn fidelity beats the legacy 6×400 mangle, still bounded by total.
        #expect(mini.perTurnChars > 400)
        #expect(mini.perTurnChars <= mini.totalChars)
    }

    @Test("rotating tier (big): live generation is capped so prefill + decode fit the window TOGETHER")
    func rotatingTierGenerationCapped() {
        #expect(HistoryBudgetPolicy.generationTokenCap(for: .big) == HistoryBudgetPolicy.rotatingGenerationTokenCap)
        // The window guarantee end-to-end, at the REAL live numbers: a budget
        // sized with the cap as its generation reserve + a decode that can never
        // exceed that same cap stays under 8192 — including the safety margin.
        // (The earlier test's gen=1024 modelled a value the live path never used.)
        let budget = HistoryBudgetPolicy.budget(
            for: .big, reservedTokens: 3000,
            generationTokens: HistoryBudgetPolicy.rotatingGenerationTokenCap
        )
        let total = 3000 + HistoryBudgetPolicy.rotatingGenerationTokenCap
            + historyTokens(budget) + HistoryBudgetPolicy.rotatingSafetyMarginTokens
        #expect(total <= BrainTier.big.approximateContextTokens)
    }

    @Test("linear-KV tiers keep the default generation budget — no cap needed")
    func linearTiersKeepDefaultGeneration() {
        for tier in [BrainTier.mini, .lil] {
            #expect(HistoryBudgetPolicy.generationTokenCap(for: tier) == 4096, "\(tier.rawValue)")
        }
    }

    @Test("per-turn cap and turn ceiling are HistoryWindow's own constants — one home, no drift")
    func perTurnConstantsShareOneHome() {
        let budget = HistoryBudgetPolicy.budget(for: .lil, reservedTokens: 1000, generationTokens: 1024)
        #expect(budget.perTurnChars == HistoryWindow.maxCharsPerTurn)
        #expect(budget.maxTurns == HistoryWindow.maxTurns)
    }

    @Test("the rotating margin actually costs big some headroom vs an equivalent non-rotating window")
    func rotatingMarginIsApplied() {
        // big and a hypothetical 8192 non-rotating tier would differ by exactly the
        // margin; we assert big's history is reduced by the margin vs the raw context.
        let reserved = 1000
        let gen = 1024
        let big = HistoryBudgetPolicy.budget(for: .big, reservedTokens: reserved, generationTokens: gen)
        let rawContext = BrainTier.big.approximateContextTokens - reserved - gen
        #expect(historyTokens(big) <= rawContext - HistoryBudgetPolicy.rotatingSafetyMarginTokens + 1)
    }

    @Test("optional-tier overload owns the mini/unknown guard (112 review nit)")
    func optionalTierOverloadGuardsMiniAndUnknown() {
        // nil (unknown persisted brain string) and .mini both get the fixed
        // conservative replay — never the MLX-sized computation.
        let unknown = HistoryBudgetPolicy.budget(
            for: BrainTier(persisted: "not-a-brain"), reservedTokens: 3000, generationTokens: 2048
        )
        #expect(unknown == HistoryBudgetPolicy.conservativeMiniBudget)
        let mini = HistoryBudgetPolicy.budget(
            for: BrainTier?.some(.mini), reservedTokens: 3000, generationTokens: 2048
        )
        #expect(mini == HistoryBudgetPolicy.conservativeMiniBudget)
        // A real MLX tier delegates to the non-optional computation unchanged.
        let big = HistoryBudgetPolicy.budget(
            for: BrainTier?.some(.big), reservedTokens: 3000, generationTokens: 2048
        )
        #expect(big == HistoryBudgetPolicy.budget(for: .big, reservedTokens: 3000, generationTokens: 2048))
    }
}
