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

    @Test("the MLX tiers point at the Gemma 4-era models")
    func mlxTierModels() {
        #expect(BrainTier.lil.mlxModelID == "mlx-community/Qwen3.5-2B-4bit")
        #expect(BrainTier.big.mlxModelID == "mlx-community/gemma-4-e4b-it-4bit")
        // Gate B fallback: gemma-4-12B's `gemma4_unified` arch is unregistered
        // in mlx-swift-lm 3.31.3 (won't load) — Qwen3.5-9B until upstream does.
        #expect(BrainTier.huge.mlxModelID == "mlx-community/Qwen3.5-9B-4bit")
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
