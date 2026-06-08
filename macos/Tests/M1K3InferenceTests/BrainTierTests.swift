//
//  BrainTierTests.swift
//  M1K3InferenceTests
//
//  Contract tests for the brain tiers — Mini / Lil / Big M1K3, the "choose your
//  brain" model selection echoed from the KMP app (app/.../domain/ai/M1K3Tier.kt).
//  Pure metadata + a RAM→tier recommendation + a persistence round-trip, all
//  Metal-free so they run under `swift test`. The actual model download/generation
//  is verify-by-launch; THIS is the part we can pin.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.9, Prior: Unknown

@testable import M1K3Inference
import Testing

struct BrainTierTests {
    @Test("there are exactly three tiers, mini/lil/big")
    func threeTiers() {
        #expect(BrainTier.allCases == [.mini, .lil, .big])
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
    }

    @Test("Mini runs on Apple Foundation Models with no download")
    func miniIsAppleNoDownload() {
        #expect(BrainTier.mini.backing == .appleFoundationModels)
        #expect(BrainTier.mini.approxDownloadMB == nil)
        #expect(!BrainTier.mini.requiresDownload)
    }

    @Test("Lil and Big are MLX models that download")
    func lilAndBigAreMLXDownloads() {
        guard case let .mlx(lilID) = BrainTier.lil.backing else {
            Issue.record("Lil should be MLX-backed")
            return
        }
        guard case let .mlx(bigID) = BrainTier.big.backing else {
            Issue.record("Big should be MLX-backed")
            return
        }
        #expect(lilID.contains("Qwen3"))
        #expect(bigID.contains("gemma-3n"))
        #expect(BrainTier.lil.mlxModelID == lilID)
        #expect(BrainTier.big.mlxModelID == bigID)
        #expect(BrainTier.mini.mlxModelID == nil)
        #expect((BrainTier.lil.approxDownloadMB ?? 0) > 0)
        #expect((BrainTier.big.approxDownloadMB ?? 0) > 0)
        #expect(BrainTier.lil.requiresDownload)
        #expect(BrainTier.big.requiresDownload)
    }

    @Test("recommendation scales with this Mac's memory (echoes KMP device tiers)")
    func recommendationByMemory() {
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 8) == .mini)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 15.9) == .mini)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 16) == .lil)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 24) == .lil)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 32) == .big)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 64) == .big)
    }

    @Test("rawValue round-trips for @AppStorage persistence")
    func persistenceRoundTrip() {
        for tier in BrainTier.allCases {
            #expect(BrainTier(rawValue: tier.rawValue) == tier)
        }
        #expect(BrainTier(rawValue: "nonsense") == nil)
    }
}
