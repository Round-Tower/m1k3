//
//  BrainTier.swift
//  M1K3Inference
//
//  The three brains the user chooses between at onboarding — Mini / Lil / Big
//  M1K3 — echoing the KMP app's tier concept (app/.../domain/ai/M1K3Tier.kt).
//  Mini is Apple Foundation Models (instant, on-device, no download); Lil and Big
//  are local MLX models that download once. Pure value type in the seam module
//  (no MLX import), so its metadata + the device recommendation + persistence are
//  `swift test`-able; AppEnvironment maps `.backing` to a concrete provider.
//
//  Model mapping (2026-06-08): backed by models that load on the pinned
//  mlx-swift-lm 2.30.6 — Lil = Qwen3-1.7B, Big = Gemma-3n-E4B. The literal
//  Qwen3.5 / Gemma-4 upgrade is a one-line id change here, gated on a future
//  mlx-swift-lm 3.x bump (a major-version + re-index effort — deferred after a
//  dependency probe showed 2.x conflicts with WhisperKit's swift-transformers).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.8, Prior: Unknown

import Foundation

/// How a brain actually runs.
public enum BrainBacking: Sendable, Equatable {
    /// Apple's built-in on-device model — instant, no download.
    case appleFoundationModels
    /// A local MLX model identified by its HuggingFace id (downloads on first use).
    case mlx(modelID: String)
}

/// One of the three M1K3 brains. `rawValue` ("mini"/"lil"/"big") is the stable
/// persistence key.
public enum BrainTier: String, CaseIterable, Identifiable, Sendable {
    case mini
    case lil
    case big

    public var id: String {
        rawValue
    }

    public var displayName: String {
        switch self {
        case .mini: "Mini M1K3"
        case .lil: "Lil M1K3"
        case .big: "Big M1K3"
        }
    }

    /// The one-line personality hook (lifted from the KMP tiers).
    public var tagline: String {
        switch self {
        case .mini: "Fast and focused"
        case .lil: "Sharp and capable"
        case .big: "Full intelligence"
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
            "Maximum capability on your hardware — deeper reasoning and longer "
                + "context, fully local. The full M1K3."
        }
    }

    /// SF Symbol for the brain card. Speed → power, echoing the KMP emojis
    /// (🤏 / ⚡ / 🧠).
    public var glyph: String {
        switch self {
        case .mini: "hare.fill"
        case .lil: "bolt.fill"
        case .big: "brain.head.profile.fill"
        }
    }

    public var backing: BrainBacking {
        switch self {
        case .mini: .appleFoundationModels
        // Models that load on the current mlx-swift-lm pin; one-line swap to
        // Qwen3.5-2B / Gemma-4-E4B once the 3.x bump lands.
        case .lil: .mlx(modelID: "mlx-community/Qwen3-1.7B-4bit")
        case .big: .mlx(modelID: "mlx-community/gemma-3n-E4B-it-lm-4bit")
        }
    }

    /// The HuggingFace model id for an MLX-backed tier, or `nil` for Mini (Apple).
    public var mlxModelID: String? {
        if case let .mlx(modelID) = backing { return modelID }
        return nil
    }

    /// Approx one-time download in MB, or `nil` for the no-download Apple tier.
    /// Rough estimates surfaced as "~NN MB"; the real size shows on the progress
    /// bar at download time.
    public var approxDownloadMB: Int? {
        switch self {
        case .mini: nil
        case .lil: 1100
        case .big: 1500
        }
    }

    public var requiresDownload: Bool {
        approxDownloadMB != nil
    }

    /// The brain best matched to this Mac's memory, echoing KMP's device tiers
    /// (flagship → Big, mid → Lil, low → Mini). Tunable thresholds.
    public static func recommended(forPhysicalMemoryGB gigabytes: Double) -> BrainTier {
        switch gigabytes {
        case 32...: .big
        case 16...: .lil
        default: .mini
        }
    }

    /// Convenience: the recommendation for the machine we're running on.
    public static var recommendedForThisMac: BrainTier {
        let gigabytes = Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824
        return recommended(forPhysicalMemoryGB: gigabytes)
    }
}
