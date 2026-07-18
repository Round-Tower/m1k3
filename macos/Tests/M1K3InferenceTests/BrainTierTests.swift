//
//  BrainTierTests.swift
//  M1K3InferenceTests
//
//  Contract tests for the brain tiers — Mini / Lil / Big M1K3, the
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
//  Review: Kev + claude-fable-5, 2026-07-02 — Huge RETIRED (Qwen3-8B: weakest
//  tool-caller, nobody's favourite at anything; the all-gemma reshuffle).
//  Three tiers again; a persisted "huge" migrates to .big via
//  BrainTier(persisted:) — the Huge user is exactly who wants Big.
//

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

    @Test("macOS keeps Lil's exact card copy — the platform-honesty byte freeze")
    func lilDetailMacOSBytesFrozen() {
        #if os(macOS)
            #expect(BrainTier.lil.detail.hasSuffix("Runs entirely on your Mac."))
        #else
            #expect(!BrainTier.lil.detail.contains("Mac"))
        #endif
    }

    @Test("display names follow the M1K3 family naming")
    func displayNames() {
        #expect(BrainTier.mini.displayName == "Mini")
        #expect(BrainTier.lil.displayName == "Lil")
        #expect(BrainTier.big.displayName == "Big")
    }

    @Test("Mini runs on Apple Foundation Models with no download")
    func miniIsAppleNoDownload() {
        #expect(BrainTier.mini.backing == .appleFoundationModels)
        #expect(BrainTier.mini.approxDownloadMB == nil)
        #expect(!BrainTier.mini.requiresDownload)
    }

    @Test("the MLX tiers point at the dense Qwen3 / Gemma 4 models")
    func mlxTierModels() {
        // lil uses DENSE Qwen3 (not the Qwen3.5 GatedDeltaNet hybrid, which
        // CPU-spikes on mlx-swift-lm 3.31.3 — see MODEL_CHOICES.md). Dense routes
        // through the existing qwen3 path: .json tools, no pre-open-think,
        // quantized KV — verified against the real Qwen3 chat template.
        // lil is the NON-THINKING Instruct-2507 refresh since 2026-07-16: same
        // dense-qwen3 family/size, but no <think> phase — tools 4.4s vs 21.0s
        // median, reasoning answers 1.8s vs 11.9s, security parity with the
        // model it replaces (Run E, macos/scratch/eval-2026-07-15-model-runs/).
        #expect(BrainTier.lil.mlxModelID == "mlx-community/Qwen3-4B-Instruct-2507-4bit")
        // big is gemma-4-12B since 2026-07-15: both June blockers cleared on the
        // pinned mlx-swift-lm 3.31.4 (vision_embedder sanitize IS in the tag;
        // the RotatingKVCache.temporalOrder tool-use crash did not reproduce),
        // and the live-path CHATEVAL swept 13/13 vs e4b's 9/13 — see
        // macos/scratch/eval-2026-07-15-model-runs/RESULTS.md + MODEL_CHOICES.md.
        #expect(BrainTier.big.mlxModelID == "mlx-community/gemma-4-12B-it-4bit")
        #expect(BrainTier.mini.mlxModelID == nil)
        for tier in [BrainTier.lil, .big] {
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
        // Big is the ceiling now — Huge retired 2026-07-02; big Macs stay on Big
        // until gemma-4-12B unblocks upstream (RotatingKVCache.temporalOrder).
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 48) == .big)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 64) == .big)
    }

    @Test("mobile recommendation is conservative — never Big, Lil only on iPad-Pro/Vision-Pro RAM")
    func recommendationOnMobile() {
        // Physical RAM OVERSTATES the per-app jetsam budget on iOS/visionOS, and
        // Big (gemma-4-12B, ~7.4GB at inference) exceeds any current mobile budget —
        // so the mobile ladder tops out at Lil and only on ≥16GB devices.
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 6, platform: .mobile) == .mini)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 8, platform: .mobile) == .mini)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 15.9, platform: .mobile) == .mini)
        // iPad Pro / Vision Pro (16GB+) comfortably run the 4-bit 4B Lil.
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 16, platform: .mobile) == .lil)
        // Big is NEVER recommended on mobile, even at high RAM.
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 24, platform: .mobile) == .lil)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 64, platform: .mobile) == .lil)
        // The Mac ladder is unchanged (regression guard): default platform is .mac.
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 24) == .big)
        #expect(BrainTier.recommended(forPhysicalMemoryGB: 24, platform: .mac) == .big)
    }

    @Test("tiers order by capability — mini < lil < big")
    func tierOrdering() {
        #expect(BrainTier.mini < .lil)
        #expect(BrainTier.lil < .big)
        #expect(BrainTier.allCases.sorted() == [.mini, .lil, .big])
        // min/max read naturally off the ordering (what `capped` relies on).
        #expect(min(BrainTier.big, .lil) == .lil)
        #expect(max(BrainTier.mini, .big) == .big)
    }

    @Test("capped eases a too-heavy automatic pick down to the Mac's comfortable ceiling")
    func cappedDemotesTooHeavy() {
        // Big auto-picked on a 16GB Mac → eased to that Mac's ceiling (Lil).
        #expect(BrainTier.capped(.big, forPhysicalMemoryGB: 16) == .lil)
        // Belt-and-braces with Big's 16GB selection floor (2026-07-15): even if
        // a persisted .big pick predates the floor, the cap eases an 8GB Mac to
        // Mini instead of letting it swap-thrash.
        #expect(BrainTier.capped(.big, forPhysicalMemoryGB: 8) == .mini)
    }

    @Test("capped NEVER raises a tier — a light pick on a big Mac stays put (no silent download)")
    func cappedNeverRaises() {
        // A .lil pick on a 64GB Mac is left alone: raising to Big would start
        // a multi-GB download the user never asked for (#81's honesty rule).
        #expect(BrainTier.capped(.lil, forPhysicalMemoryGB: 64) == .lil)
        #expect(BrainTier.capped(.mini, forPhysicalMemoryGB: 8) == .mini)
        #expect(BrainTier.capped(.mini, forPhysicalMemoryGB: 128) == .mini)
    }

    @Test("capped leaves an at-or-below-ceiling pick unchanged (boundaries)")
    func cappedAtCeilingUnchanged() {
        #expect(BrainTier.capped(.big, forPhysicalMemoryGB: 48) == .big) // above Big's ceiling
        #expect(BrainTier.capped(.big, forPhysicalMemoryGB: 24) == .big) // exactly Big's ceiling
        #expect(BrainTier.capped(.lil, forPhysicalMemoryGB: 16) == .lil) // exactly Lil's ceiling
    }

    @Test("Big-12B carries the promised 16GB selection floor; Mini/Lil stay floorless")
    func bigTwelveBSelectionFloor() {
        // The seam this test's predecessor kept warm ("gemma-4-12B will want it
        // back when Big upgrades") is now armed: 12B peaks ~7.4GB at inference
        // (2026-06-24 memloop, geometry unchanged), which an 8GB Mac physically
        // cannot hold — the card disables rather than letting an explicit pick
        // swap-thrash. 16GB is tight-but-runnable: selection stays permissive,
        // the RECOMMENDATION floor stays 24GB (recommendationByMemory above).
        #expect(BrainTier.big.minimumPhysicalMemoryGB == 16)
        #expect(!BrainTier.big.isSelectable(forPhysicalMemoryGB: 8))
        #expect(BrainTier.big.isSelectable(forPhysicalMemoryGB: 16))
        for tier in [BrainTier.mini, .lil] {
            #expect(tier.minimumPhysicalMemoryGB == nil)
            #expect(tier.isSelectable(forPhysicalMemoryGB: 8))
        }
    }

    @Test("a persisted pick below its tier's floor eases down; a selectable pick is never touched")
    func selectableOrEased() {
        // The 12B floor (2026-07-15) created a new boundary case: a persisted
        // .big on a sub-16GB Mac would render as a LOCKED row while still
        // running — stranded. Ease exactly that case through capped(); an
        // explicit pick that merely exceeds the RECOMMENDATION ceiling (Big on
        // a 16GB Mac) is legitimate and must never be demoted (#81's honesty
        // rule — capped() is for automatic picks only).
        #expect(BrainTier.selectableOrEased(.big, forPhysicalMemoryGB: 8) == .mini)
        #expect(BrainTier.selectableOrEased(.big, forPhysicalMemoryGB: 16) == .big)
        #expect(BrainTier.selectableOrEased(.big, forPhysicalMemoryGB: 64) == .big)
        #expect(BrainTier.selectableOrEased(.lil, forPhysicalMemoryGB: 8) == .lil)
        #expect(BrainTier.selectableOrEased(.mini, forPhysicalMemoryGB: 8) == .mini)
    }

    @Test("rawValue round-trips for @AppStorage persistence; retired 'huge' is not a live rawValue")
    func persistenceRoundTrip() {
        for tier in BrainTier.allCases {
            #expect(BrainTier(rawValue: tier.rawValue) == tier)
        }
        #expect(BrainTier(rawValue: "mini") == .mini)
        #expect(BrainTier(rawValue: "lil") == .lil)
        #expect(BrainTier(rawValue: "big") == .big)
        #expect(BrainTier(rawValue: "huge") == nil)
        #expect(BrainTier(rawValue: "nonsense") == nil)
    }

    @Test("persisted decoding migrates the retired 'huge' to Big — never a silent Mini downgrade")
    func persistedMigratesHugeToBig() {
        // A Huge user is exactly who wants Big (the biggest remaining brain).
        // Falling to nil at the read site would default them to Mini — hostile.
        #expect(BrainTier(persisted: "huge") == .big)
        // Live values decode unchanged.
        for tier in BrainTier.allCases {
            #expect(BrainTier(persisted: tier.rawValue) == tier)
        }
        // Junk still fails — the caller owns the default.
        #expect(BrainTier(persisted: "nonsense") == nil)
        #expect(BrainTier(persisted: "") == nil)
    }

    @Test("context-window metadata: big is the hard 8192 rotating window; dense-Qwen lil is wide")
    func contextWindowMetadata() {
        // big = gemma-4-12B → RotatingKVCache(maxSize: 8192): the load-bearing fact.
        #expect(BrainTier.big.approximateContextTokens == 8192)
        #expect(BrainTier.big.usesRotatingKVCache)
        // dense Qwen3 lil is unbounded (memory-bound, not truncation-bound) and wide.
        #expect(BrainTier.lil.approximateContextTokens == 32768)
        #expect(!BrainTier.lil.usesRotatingKVCache)
        // mini (AFM) is conservatively small and not rotating.
        #expect(BrainTier.mini.approximateContextTokens == 4096)
        #expect(!BrainTier.mini.usesRotatingKVCache)
        // Only big rotates — the clamp is a correctness bound there, a latency knob elsewhere.
        #expect(BrainTier.allCases.filter(\.usesRotatingKVCache) == [.big])
    }
}
