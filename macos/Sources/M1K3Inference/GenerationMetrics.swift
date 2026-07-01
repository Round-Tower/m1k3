//
//  GenerationMetrics.swift
//  M1K3Inference
//
//  Per-turn generation stats — prompt/context tokens, generated tokens, and
//  throughput — surfaced as an in-app testing readout. The numbers are already
//  computed by the MLX backend (it logs them to os_log today); this is the seam
//  that carries them to the UI.
//
//  The MLX generation path lives below the app, and the active provider is
//  reconstructed on every brain swap, so a per-provider callback would need
//  re-wiring each swap. Instead a process-wide reporter (installed once by the
//  app) decouples producer from consumer: MLX reports, the app records. tok/s is
//  MLX-only — Apple Foundation Models (Mini) doesn't expose throughput, so those
//  turns simply never report.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.85 (pure metrics +
//  reporter TDD'd; the MLX report call + UI badge are verify-by-launch). Prior: Unknown.

import Synchronization

/// One completed generation's stats. `promptTokens` is the context the turn
/// actually used (persona + grounding + history + tool observations) — the number
/// that says how close a turn got to the model's context window.
public struct GenerationMetrics: Sendable, Equatable, Codable {
    public let promptTokens: Int
    public let generationTokens: Int
    public let tokensPerSecond: Double

    public init(promptTokens: Int, generationTokens: Int, tokensPerSecond: Double) {
        self.promptTokens = promptTokens
        self.generationTokens = generationTokens
        self.tokensPerSecond = tokensPerSecond
    }

    /// Fraction of a context window the prompt consumed, clamped to 0…1. `nil`
    /// when the window is unknown or non-positive (e.g. a tier without a fixed cap).
    public func contextFraction(window: Int) -> Double? {
        guard window > 0 else { return nil }
        return min(1, max(0, Double(promptTokens) / Double(window)))
    }
}

/// Process-wide sink so the MLX generation path can report per-turn metrics to the
/// app without M1K3MLX depending on the app. The app installs a handler once at
/// launch; generation calls `report`. One wire, survives the provider swaps that
/// happen on every brain change.
public enum GenerationMetricsReporter {
    private static let handler = Mutex<(@Sendable (GenerationMetrics) -> Void)?>(nil)

    /// Install (or clear, with nil) the sink. Called once by the app at launch.
    public static func install(_ handler: (@Sendable (GenerationMetrics) -> Void)?) {
        Self.handler.withLock { $0 = handler }
    }

    /// Report a completed turn's metrics. A no-op when no handler is installed
    /// (tests, headless). The handler is copied out under the lock and invoked
    /// outside it, so a slow consumer never blocks the generation thread.
    public static func report(_ metrics: GenerationMetrics) {
        let handler = Self.handler.withLock { $0 }
        handler?(metrics)
    }
}
