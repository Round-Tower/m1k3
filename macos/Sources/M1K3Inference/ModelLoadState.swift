//
//  ModelLoadState.swift
//  M1K3Inference
//
//  The progress state for warming a heavy on-device model (MLX Gemma weights are
//  ~1GB and download lazily on first use). Lives in the seam module — not M1K3MLX —
//  so it's pure (no Metal link), `swift test`-able, and the app/UI can observe it
//  without importing MLX. The actual download is verify-by-launch; THIS is the
//  part we can prove green in CI.
//
//  `ModelPreloading` lets a backend warm itself ahead of the first turn, reporting
//  a 0...1 fraction, so the runtime picker can show a real progress bar instead of
//  a silent hang. Only weight-downloading backends conform; cheap ones (Apple
//  Foundation Models) don't need it.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation

/// Where a model load is in its lifecycle. `idle` before anything is asked of it,
/// `downloading` while weights stream in (fraction is clamped to 0...1), `ready`
/// once warm, `failed` with a human message if the load couldn't complete.
public enum ModelLoadState: Sendable, Equatable {
    case idle
    case downloading(fraction: Double)
    /// On disk but still loading, with no honest fraction to show — e.g. WhisperKit
    /// compiling its CoreML models. Renders an indeterminate spinner, never a stuck
    /// bar, and is NOT labelled "downloading" (which read as a phantom re-download).
    case preparing
    case ready
    case failed(message: String)

    /// A `.downloading` state with `fraction` clamped to 0...1 — the only
    /// supported way to build one, so a stray callback value can't escape range.
    public static func progress(_ fraction: Double) -> ModelLoadState {
        .downloading(fraction: min(max(fraction, 0), 1))
    }

    /// The 0...1 fraction for a `ProgressView`, or nil when not downloading.
    public var fraction: Double? {
        if case let .downloading(value) = self { return value }
        return nil
    }

    /// True while a load is in flight (downloading or preparing) — the cue to show
    /// the progress row.
    public var isActive: Bool {
        switch self {
        case .downloading, .preparing: true
        case .idle, .ready, .failed: false
        }
    }

    /// A plain, audio-friendly status line for the UI. `modelName` is the
    /// user-facing label (e.g. "Gemma 3"). Empty while idle so it renders nothing.
    public func label(modelName: String) -> String {
        switch self {
        case .idle:
            return ""
        case let .downloading(fraction):
            return "Downloading \(modelName)… \(percent(fraction))%"
        case .preparing:
            return "Preparing \(modelName)…"
        case .ready:
            return "\(modelName) ready"
        case let .failed(message):
            return "Couldn’t load \(modelName): \(message)"
        }
    }

    /// A minimal status for cramped chrome (the toolbar pill): just the percentage
    /// while downloading — the spinner beside it already reads as "loading" — and
    /// nothing while preparing (the spinner alone carries it, there's no honest
    /// number). Keeps the toolbar quiet instead of jamming in "Downloading X… N%".
    public var compactLabel: String {
        switch self {
        case let .downloading(fraction): "\(percent(fraction))%"
        case .idle, .preparing, .ready, .failed: ""
        }
    }

    private func percent(_ fraction: Double) -> Int {
        Int((fraction * 100).rounded())
    }
}

/// A backend that can warm its model ahead of the first request, reporting
/// download/load progress as a 0...1 fraction. Conformed to by weight-downloading
/// providers (MLX) so the app can preload on selection and surface a progress bar.
public protocol ModelPreloading: Sendable {
    /// Ensure the model is loaded, invoking `progress` with a 0...1 fraction as
    /// weights download. Returns once the model is ready; throws on failure.
    func prepare(progress: @escaping @Sendable (Double) -> Void) async throws
}
