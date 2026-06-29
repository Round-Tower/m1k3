//
//  ThinkingPolicyTests.swift
//  M1K3ChatTests
//
//  Adaptive thinking: a reasoning model burning 30s of <think> on "yo, what's
//  up?" is the wrong trade. The policy decides per turn from cheap signals;
//  the user override (Always / Fast) always wins.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
@testable import M1K3Chat
import Testing

struct ThinkingPolicyTests {
    @Test("casual greetings skip thinking in auto mode")
    func casualSkips() {
        #expect(!ThinkingPolicy.shouldThink(
            question: "yo M1K3, what's up?", hasGroundedKnowledge: false, mode: .auto
        ))
        #expect(!ThinkingPolicy.shouldThink(
            question: "thanks pal!", hasGroundedKnowledge: false, mode: .auto
        ))
    }

    @Test("grounded knowledge always earns thinking in auto mode")
    func groundedThinks() {
        #expect(ThinkingPolicy.shouldThink(
            question: "what failed?", hasGroundedKnowledge: true, mode: .auto
        ))
    }

    @Test("analytic asks think even when short")
    func analyticThinks() {
        #expect(ThinkingPolicy.shouldThink(
            question: "why is the sky blue?", hasGroundedKnowledge: false, mode: .auto
        ))
        #expect(ThinkingPolicy.shouldThink(
            question: "explain RRF fusion", hasGroundedKnowledge: false, mode: .auto
        ))
    }

    @Test("long questions think in auto mode")
    func longThinks() {
        let long = "could you have a look at the maintenance schedule and tell me which intervals overlap next month"
        #expect(ThinkingPolicy.shouldThink(question: long, hasGroundedKnowledge: false, mode: .auto))
    }

    @Test("short tool-ish questions stay fast (the tool call needs no essay)")
    func shortFactualFast() {
        #expect(!ThinkingPolicy.shouldThink(
            question: "what time is it?", hasGroundedKnowledge: false, mode: .auto
        ))
    }

    @Test("speed tiers skip the think phase on a plain grounded lookup; heavy tiers don't")
    func fastTierSkipsGroundedLookup() {
        // Short, non-analytic, grounded — a fact lookup. Heavy tier thinks…
        #expect(ThinkingPolicy.shouldThink(
            question: "what's my address?", hasGroundedKnowledge: true, mode: .auto, fastByDefault: false
        ))
        // …the speed tier stays fast.
        #expect(!ThinkingPolicy.shouldThink(
            question: "what's my address?", hasGroundedKnowledge: true, mode: .auto, fastByDefault: true
        ))
    }

    @Test("speed tiers think only on an explicit deep-reasoning ask — not weak openers or length")
    func fastTierThinksOnlyOnExplicitReasoning() {
        // An explicit "explain"/"compare" still earns CoT on the speed tier.
        #expect(ThinkingPolicy.shouldThink(
            question: "explain why the pump failed", hasGroundedKnowledge: true, mode: .auto, fastByDefault: true
        ))
        // A bare "why" opener does NOT — as a substring it fired on nearly every
        // message and made "fast" a lie (the always-thinking-from-the-UI report).
        #expect(!ThinkingPolicy.shouldThink(
            question: "why did the pump fail?", hasGroundedKnowledge: true, mode: .auto, fastByDefault: true
        ))
        // Length alone does NOT earn CoT on the speed tier.
        let long = "could you have a look at the maintenance schedule and tell me which intervals overlap next month"
        #expect(!ThinkingPolicy.shouldThink(
            question: long, hasGroundedKnowledge: true, mode: .auto, fastByDefault: true
        ))
    }

    @Test("generation requests never force the think phase (the 'refuses to code' budget bug)")
    func generationDoesNotForceThink() {
        // On the speed tier a codegen ask must stay fast — a forced think phase
        // eats the token budget the artifact itself needs.
        #expect(!ThinkingPolicy.shouldThink(
            question: "write me a small HTML page with a heading",
            hasGroundedKnowledge: false, mode: .auto, fastByDefault: true
        ))
        #expect(!ThinkingPolicy.shouldThink(
            question: "build a landing page for a coffee shop",
            hasGroundedKnowledge: false, mode: .auto, fastByDefault: true
        ))
        // And even on a heavier tier "write"/"code" are no longer "analytic" —
        // generating is a task to do, not a reasoning ask.
        #expect(!ThinkingPolicy.shouldThink(
            question: "write a python script to reverse a string",
            hasGroundedKnowledge: false, mode: .auto, fastByDefault: false
        ))
    }

    @Test("the fast-tier default never overrides the user's Always/Fast choice")
    func fastTierRespectsOverride() {
        #expect(ThinkingPolicy.shouldThink(
            question: "hi", hasGroundedKnowledge: false, mode: .always, fastByDefault: true
        ))
        #expect(!ThinkingPolicy.shouldThink(
            question: "why did it fail?", hasGroundedKnowledge: true, mode: .fast, fastByDefault: false
        ))
    }

    @Test("user overrides always win")
    func overridesWin() {
        #expect(ThinkingPolicy.shouldThink(
            question: "hi", hasGroundedKnowledge: false, mode: .always
        ))
        #expect(!ThinkingPolicy.shouldThink(
            question: "explain quantum tunnelling in detail please",
            hasGroundedKnowledge: true, mode: .fast
        ))
    }
}
