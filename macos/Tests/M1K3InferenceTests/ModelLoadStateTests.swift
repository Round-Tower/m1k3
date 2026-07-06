//
//  ModelLoadStateTests.swift
//  M1K3InferenceTests
//
//  Contract tests for the model-load progress state machine — the pure piece
//  behind "MLX Gemma is downloading, don't look frozen". No MLX, no Metal: this
//  is exactly the bit that CAN run under `swift test` (the real download is
//  verify-by-launch only), so it's where the logic lives and gets pinned.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

@testable import M1K3Inference
import Testing

struct ModelLoadStateTests {
    @Test("progress clamps a fraction into 0...1")
    func clampsFraction() {
        #expect(ModelLoadState.progress(-0.5) == .downloading(fraction: 0))
        #expect(ModelLoadState.progress(0.42) == .downloading(fraction: 0.42))
        #expect(ModelLoadState.progress(1.7) == .downloading(fraction: 1))
    }

    @Test("fraction is exposed only while downloading")
    func fractionAccessor() {
        #expect(ModelLoadState.progress(0.3).fraction == 0.3)
        #expect(ModelLoadState.idle.fraction == nil)
        #expect(ModelLoadState.ready.fraction == nil)
        #expect(ModelLoadState.failed(message: "x").fraction == nil)
    }

    @Test("isActive is true only mid-download")
    func isActive() {
        #expect(ModelLoadState.progress(0).isActive)
        #expect(ModelLoadState.progress(1).isActive)
        #expect(!ModelLoadState.idle.isActive)
        #expect(!ModelLoadState.ready.isActive)
        #expect(!ModelLoadState.failed(message: "x").isActive)
    }

    @Test("preparing is active, indeterminate, and labelled as prep — not a download")
    func preparing() {
        // For a model that's on disk but still loading (e.g. WhisperKit's opaque
        // CoreML compile), there's no honest fraction — show a spinner, and DON'T
        // say "Downloading" (the lie that read as a re-download).
        #expect(ModelLoadState.preparing.isActive) // the row still shows
        #expect(ModelLoadState.preparing.fraction == nil) // → indeterminate bar
        #expect(ModelLoadState.preparing.label(modelName: "WhisperKit") == "Preparing WhisperKit…")
    }

    @Test("failure carries its message")
    func failureMessage() {
        let state = ModelLoadState.failed(message: "no network")
        #expect(state == .failed(message: "no network"))
        #expect(state.fraction == nil)
    }

    @Test("label is human, dyslexia-friendly, and names the model")
    func labels() {
        #expect(ModelLoadState.idle.label(modelName: "Gemma 3") == "")
        #expect(ModelLoadState.progress(0.42).label(modelName: "Gemma 3") == "Downloading Gemma 3… 42%")
        #expect(ModelLoadState.progress(0).label(modelName: "Gemma 3") == "Downloading Gemma 3… 0%")
        #expect(ModelLoadState.ready.label(modelName: "Gemma 3") == "Gemma 3 ready")
        #expect(ModelLoadState.failed(message: "offline").label(modelName: "Gemma 3")
            == "Couldn’t load Gemma 3: offline")
    }

    @Test("label rounds the percentage to the nearest whole number")
    func labelRounds() {
        #expect(ModelLoadState.progress(0.006).label(modelName: "M") == "Downloading M… 1%")
        #expect(ModelLoadState.progress(0.994).label(modelName: "M") == "Downloading M… 99%")
        #expect(ModelLoadState.progress(0.999).label(modelName: "M") == "Downloading M… 100%")
    }

    @Test("compactLabel is just the number while downloading, empty otherwise")
    func compactLabel() {
        // The toolbar pill keeps only the percentage; the spinner beside it says "loading".
        #expect(ModelLoadState.progress(0.42).compactLabel == "42%")
        #expect(ModelLoadState.progress(1).compactLabel == "100%")
        // No frozen number where there's no honest fraction — the spinner alone carries it.
        #expect(ModelLoadState.preparing.compactLabel == "")
        #expect(ModelLoadState.idle.compactLabel == "")
        #expect(ModelLoadState.ready.compactLabel == "")
        #expect(ModelLoadState.failed(message: "x").compactLabel == "")
    }
}
