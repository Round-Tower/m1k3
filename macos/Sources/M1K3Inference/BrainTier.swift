//
//  BrainTier.swift
//  M1K3Inference
//
//  The four brains the user chooses between at onboarding — Mini / Lil / Big /
//  Huge M1K3 — echoing the KMP app's tier concept (app/.../domain/ai/M1K3Tier.kt).
//  Mini is Apple Foundation Models (instant, on-device, no download); Lil, Big
//  and Huge are local MLX models that download once. Pure value type in the seam
//  module (no MLX import), so its metadata + the device recommendation + the
//  selection gate + persistence are `swift test`-able; AppEnvironment maps
//  `.backing` to a concrete provider.
//
//  Model mapping (2026-06-13, mlx-swift-lm 3.31.3): Lil = Qwen3.5-4B,
//  Big = Gemma-4-E4B (the gemma-3n-E4B successor). Huge = Qwen3.5-9B — the
//  intended gemma-4-12B uses a `gemma4_unified` arch that 3.31.3 cannot load
//  (probe-verified, Gate B); swap the id once upstream registers it.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.8, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-10 — Gemma 4 era: four tiers, new
//  RAM thresholds (Big is ~7GB at inference now → recommended floor 24GB),
//  Huge selectable at 32GB+, recommended at 48GB+.
//  Review: Kev + claude-opus-4-8, 2026-06-13 — Lil 2B → 4B. The 2B-4bit was
//  below the floor for grounded generation + tool use (ignored good sources,
//  confabulated on an empty gate); 4B is the same Qwen3.5 family so tool-call
//  format (xmlFunction) + pre-open-think resolve unchanged. ~3GB, still fits 16GB.

import Foundation

/// How a brain actually runs.
public enum BrainBacking: Sendable, Equatable {
    /// Apple's built-in on-device model — instant, no download.
    case appleFoundationModels
    /// A local MLX model identified by its HuggingFace id (downloads on first use).
    case mlx(modelID: String)
}

/// One of the four M1K3 brains. `rawValue` ("mini"/"lil"/"big"/"huge") is the
/// stable persistence key — pre-Huge installs persisted the first three.
public enum BrainTier: String, CaseIterable, Identifiable, Sendable, Comparable {
    case mini
    case lil
    case big
    case huge

    public var id: String {
        rawValue
    }

    /// Capability/resource ordering: mini < lil < big < huge. Explicit (NOT the
    /// `allCases` declaration order) so a future reorder can't silently change it.
    /// Drives `capped` and any "is this a heavier brain?" comparison.
    private var weight: Int {
        switch self {
        case .mini: 0
        case .lil: 1
        case .big: 2
        case .huge: 3
        }
    }

    public static func < (lhs: BrainTier, rhs: BrainTier) -> Bool {
        lhs.weight < rhs.weight
    }

    public var displayName: String {
        switch self {
        case .mini: "Mini M1K3"
        case .lil: "Lil M1K3"
        case .big: "Big M1K3"
        case .huge: "Huge M1K3"
        }
    }

    /// The one-line personality hook (lifted from the KMP tiers).
    public var tagline: String {
        switch self {
        case .mini: "Fast and focused"
        case .lil: "Sharp and capable"
        case .big: "Full intelligence"
        case .huge: "Frontier-class, fully local"
        }
    }

    /// The longer capability description shown on the brain card.
    public var detail: String {
        switch self {
        case .mini:
            "Apple's built-in on-device model. Instant, private, and always ready — "
                + "no download. The quickest way to start."
        case .lil:
            "A downloaded local engine — multi-turn conversation, memory, and "
                + "reasoning that keeps up with you. Runs entirely on your Mac."
        case .big:
            "Maximum capability for most Macs — deeper reasoning and longer "
                + "context, fully local. The full M1K3."
        case .huge:
            "The biggest brain that fits on a Mac — for machines with 32GB+ "
                + "of memory. Everything Big does, with more headroom."
        }
    }

    /// SF Symbol for the brain card. Speed → power, echoing the KMP emojis
    /// (🤏 / ⚡ / 🧠).
    public var glyph: String {
        switch self {
        case .mini: "hare.fill"
        case .lil: "bolt.fill"
        case .big: "brain.head.profile.fill"
        case .huge: "sparkles"
        }
    }

    public var backing: BrainBacking {
        switch self {
        case .mini: .appleFoundationModels
        case .lil: .mlx(modelID: "mlx-community/Qwen3.5-4B-4bit")
        case .big: .mlx(modelID: "mlx-community/gemma-4-e4b-it-4bit")
        // Gate B fallback — gemma-4-12B-it-4bit once mlx-swift-lm registers
        // its `gemma4_unified` arch (unloadable at 3.31.3).
        case .huge: .mlx(modelID: "mlx-community/Qwen3.5-9B-4bit")
        }
    }

    /// The HuggingFace model id for an MLX-backed tier, or `nil` for Mini (Apple).
    public var mlxModelID: String? {
        if case let .mlx(modelID) = backing { return modelID }
        return nil
    }

    /// Approx one-time download in MB, or `nil` for the no-download Apple tier.
    /// Rough estimates surfaced as "~NN MB"; the real size shows on the progress
    /// bar at download time. (HF usedStorage, 2026-06-10.)
    public var approxDownloadMB: Int? {
        switch self {
        case .mini: nil
        case .lil: 2900
        case .big: 5250
        case .huge: 5970
        }
    }

    public var requiresDownload: Bool {
        approxDownloadMB != nil
    }

    /// The memory floor below which this brain shouldn't even be SELECTABLE
    /// (the card disables with a "needs NN GB" badge), or nil for no gate.
    /// Distinct from `recommended` — selection is permissive, recommendation
    /// is comfortable.
    public var minimumPhysicalMemoryGB: Double? {
        switch self {
        case .mini, .lil, .big: nil
        case .huge: 32
        }
    }

    /// Whether this Mac has enough memory to offer the tier at all.
    public func isSelectable(forPhysicalMemoryGB gigabytes: Double) -> Bool {
        guard let floor = minimumPhysicalMemoryGB else { return true }
        return gigabytes >= floor
    }

    /// The brain best matched to this Mac's memory, echoing KMP's device tiers.
    /// Big is a ~7GB-at-inference model in the Gemma 4 era — recommending it
    /// on a 16GB Mac that also runs a browser would be hostile, so its floor
    /// is 24GB; Huge is recommended only with real headroom (48GB+) though
    /// selectable from 32GB. Tunable thresholds.
    public static func recommended(forPhysicalMemoryGB gigabytes: Double) -> BrainTier {
        switch gigabytes {
        case 48...: .huge
        case 24...: .big
        case 16...: .lil
        default: .mini
        }
    }

    /// Convenience: the recommendation for the machine we're running on.
    public static var recommendedForThisMac: BrainTier {
        recommended(forPhysicalMemoryGB: physicalMemoryGB)
    }

    /// Ease an AUTOMATIC brain pick down to what this much memory comfortably
    /// runs — never heavier than `recommended(forPhysicalMemoryGB:)`. LOWER-ONLY:
    /// a pick already at or below the comfortable ceiling is returned unchanged;
    /// this never RAISES a tier, because raising would start a silent multi-GB
    /// download the user never asked for (the onboarding flow owns that choice —
    /// see #81's download honesty). The point is to stop auto-routing from keeping
    /// a too-heavy brain on a small Mac (Big has no hard memory floor, so without
    /// this it would ride along on an 8GB machine and thrash swap). Manual
    /// selection is the user's sovereign choice and is deliberately NOT capped.
    public static func capped(_ tier: BrainTier, forPhysicalMemoryGB gigabytes: Double) -> BrainTier {
        min(tier, recommended(forPhysicalMemoryGB: gigabytes))
    }

    /// Convenience: `capped` for the machine we're running on.
    public static func cappedForThisMac(_ tier: BrainTier) -> BrainTier {
        capped(tier, forPhysicalMemoryGB: physicalMemoryGB)
    }

    /// Convenience: whether this tier is selectable on the machine we're on.
    public var isSelectableOnThisMac: Bool {
        isSelectable(forPhysicalMemoryGB: Self.physicalMemoryGB)
    }

    private static var physicalMemoryGB: Double {
        Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824
    }
}
