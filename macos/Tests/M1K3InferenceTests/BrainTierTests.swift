//
//  BrainTierTests.swift
//  M1K3InferenceTests
//
//  Contract tests for the brain tiers — Mini / Lil / Big / Huge M1K3, the
//  "choose your brain" model selection echoed from the KMP app
//  (app/.../domain/ai/M1K3Tier.kt). Pure metadata + a RAM→tier recommendation
//  + a selection gate + a persistence round-trip, all Metal-free so they run
//  under `swift test`. The actual model download/generation is
//  verify-by-launch; THIS is the part we can pin.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.9, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-10 — four tiers (Gemma 4 era):
//  Lil → Qwen3.5-2B, Big → Gemma-4-E4B, new Huge gated to 32GB+.
//  Review: Kev + claude-opus-4-8, 2026-06-13 — Lil promoted Qwen3.5-2B → 4B:
//  the 2B-4bit ignored grounding and confabulated (greeted instead of answering,
//  invented facts on empty gate); 4B clears the reliability floor, same family
//  (xmlFunction tool-calls, pre-open-think) so it's a drop-in id swap.

@testable import M1K3Inference
import Testing

struct BrainTierTests {
    @Test("there are exactly four tiers, mini/lil/big/huge")
    func fourTiers() {
        #expect(BrainTier.allCases == [.mini, .lil, .big, .huge])
    }

    @Test("every tier carries non-empty display copy")
    func copyIsPresent() {
        for tier in BrainTier.allCases {
            #expect(!tier.displayName.isEmpty)
            #expect(!tier.tagline.isEmpty)
            #expect(!tier.detail.isEmpty)
            #expect(!tier.glyph.isEmpty)
        }
    }

    @Test("display names follow the M1K3 family naming")
    func displayNames() {
        #expect(BrainTier.mini.displayName == "Mini M1K3")
        #expect(BrainTier.lil.displayName == "Lil M1K3")
        #expect(BrainTier.big.displayName == "Big M1K3")
        #expect(BrainTier.huge.displayName == "Huge M1K3")
    }

    @Test("Mini runs on Apple Foundation Models with no download")
    func miniIsAppleNoDownload() {
        #expect(BrainTier.mini.backing == .appleFoundationModels)
        #expect(BrainTier.mini.approxDownloadMB == nil)
        #expect(!BrainTier.mini.requiresDownload)
    }

    @Test("the MLX tiers point at the dense Qwen3 / Gemma 4 models")
    func mlxTierModels() {
        // lil/huge use DENSE Qwen3 (not the Qwen3.5 GatedDeltaNet hybrid, which
        // CPU-spikes on mlx-swift-lm 3.31.3 — see MODEL_CHOICES.md). Dense routes
        // through the existing qwen3 path: .json tools, no pre-open-think,
        // quantized KV — verified against the real Qwen3 chat template.
        #expect(BrainTier.lil.mlxModelID == "mlx-community/Qwen3-4B-4bit")
        #expect(BrainTier.big.mlxModelID == "mlx-community/gemma-4-e4b-it-4bit")
        // Gate B: gemma-4-12B's `gemma4_unified` arch is unregistered in
        // mlx-swift-lm 3.31.3 (won't load) — dense Qwen3-8B fills huge until upstream.
        #expect(BrainTier.huge.mlxModelID == "mlx-community/Qwen3-8B-4bit")
        #expect(BrainTier.mini.mlxModelID == nil)
        for tier in [BrainTier.lil, .big, .huge] {
            #expect((tier.approxDownloadMB ?? 0) > 0)
            #expect(tier.requiresDownload)
        }
    }

    @Test("recommendation scales with this Mac's memory (echoes KMP device tiers)")
    func recommendationByMemory() {
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 8) == .mini)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 15.9) == .mini)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 16) == .lil)
        // Big is a ~7GB-RAM model now — recommending it on a 16GB Mac that
        // also runs a browser would be hostile. Floor rises to 24GB.
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 23.9) == .lil)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 24) == .big)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 32) == .big)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 48) == .huge)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 64) == .huge)
    }

    @Test("tiers order by capability — mini < lil < big < huge")
    func tierOrdering() {
        #expect(BrainTier.mini < .lil)
        #expect(BrainTier.lil < .big)
        #expect(BrainTier.big < .huge)
        #expect(BrainTier.allCases.sorted() == [.mini, .lil, .big, .huge])
        // min/max read naturally off the ordering (what `capped` relies on).
        #expect(min(BrainTier.huge, .lil) == .lil)
        #expect(max(BrainTier.mini, .big) == .big)
    }

    @Test("capped eases a too-heavy automatic pick down to the Mac's comfortable ceiling")
    func cappedDemotesTooHeavy() {
        // Huge auto-picked on a 16GB Mac → eased to that Mac's ceiling (Lil).
        #expect(BrainTier.capped(.huge, forPhysicalMemoryGB: 16) == .lil)
        // Big has NO hard memory floor, so without the cap it would ride along on
        // an 8GB Mac and thrash swap — the named bug. Capped → Mini.
        #expect(BrainTier.capped(.big, forPhysicalMemoryGB: 8) == .mini)
        // Huge on a 24GB Mac → Big (24GB *recommends* Big; Huge is comfortable
        // only at 48GB — the cap uses `recommended`, the comfortable ceiling, not
        // the permissive `isSelectable` floor).
        #expect(BrainTier.capped(.huge, forPhysicalMemoryGB: 24) == .big)
    }

    @Test("capped NEVER raises a tier — a light pick on a big Mac stays put (no silent download)")
    func cappedNeverRaises() {
        // A .lil pick on a 64GB Mac is left alone: raising to Big/Huge would start
        // a multi-GB download the user never asked for (#81's honesty rule).
        #expect(BrainTier.capped(.lil, forPhysicalMemoryGB: 64) == .lil)
        #expect(BrainTier.capped(.mini, forPhysicalMemoryGB: 8) == .mini)
        #expect(BrainTier.capped(.mini, forPhysicalMemoryGB: 128) == .mini)
    }

    @Test("capped leaves an at-or-below-ceiling pick unchanged (boundaries)")
    func cappedAtCeilingUnchanged() {
        #expect(BrainTier.capped(.huge, forPhysicalMemoryGB: 48) == .huge) // exactly Huge's ceiling
        #expect(BrainTier.capped(.big, forPhysicalMemoryGB: 24) == .big) // exactly Big's ceiling
        #expect(BrainTier.capped(.lil, forPhysicalMemoryGB: 16) == .lil) // exactly Lil's ceiling
    }

    @Test("Huge is selectable from 32GB even though only recommended at 48GB")
    func hugeSelectionGate() {
        #expect(BrainTier.huge.minimumPhysicalMemoryGB == 32)
        #expect(BrainTier.huge.isSelectable(forPhysicalMemoryGB: 32))
        #expect(!BrainTier.huge.isSelectable(forPhysicalMemoryGB: 16))
        for tier in [BrainTier.mini, .lil, .big] {
            #expect(tier.minimumPhysicalMemoryGB == nil)
            #expect(tier.isSelectable(forPhysicalMemoryGB: 8))
        }
    }

    @Test("rawValue round-trips for @AppStorage persistence, legacy values intact")
    func persistenceRoundTrip() {
        for tier in BrainTier.allCases {
            #expect(BrainTier(rawValue: tier.rawValue) == tier)
        }
        // The pre-Huge persisted strings must keep decoding.
        #expect(BrainTier(rawValue: "mini") == .mini)
        #expect(BrainTier(rawValue: "lil") == .lil)
        #expect(BrainTier(rawValue: "big") == .big)
        #expect(BrainTier(rawValue: "nonsense") == nil)
    }
}
