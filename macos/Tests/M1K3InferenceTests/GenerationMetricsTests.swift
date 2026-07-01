//
//  GenerationMetricsTests.swift
//  M1K3InferenceTests
//
//  The pure metrics value + the install/report reporter sink.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Inference
import Testing

struct GenerationMetricsTests {
    @Test("context fraction is promptTokens / window, clamped to 0…1")
    func contextFraction() {
        let metrics = GenerationMetrics(promptTokens: 1240, generationTokens: 380, tokensPerSecond: 34)
        #expect(metrics.contextFraction(window: 8192) == 1240.0 / 8192.0)
        // Over-full clamps to 1; a zero/negative window is nil (unknown).
        #expect(GenerationMetrics(promptTokens: 9000, generationTokens: 0, tokensPerSecond: 0)
            .contextFraction(window: 8192) == 1)
        #expect(metrics.contextFraction(window: 0) == nil)
    }

    @Test("report forwards to the installed handler")
    func reportForwards() {
        let box = MetricsBox()
        GenerationMetricsReporter.install { box.record($0) }
        defer { GenerationMetricsReporter.install(nil) }

        let metrics = GenerationMetrics(promptTokens: 100, generationTokens: 50, tokensPerSecond: 20)
        GenerationMetricsReporter.report(metrics)
        #expect(box.last == metrics)
    }

    @Test("report is a safe no-op when nothing is installed")
    func reportWithoutHandler() {
        GenerationMetricsReporter.install(nil)
        // Must not crash / must not reach a stale handler.
        GenerationMetricsReporter.report(
            GenerationMetrics(promptTokens: 1, generationTokens: 1, tokensPerSecond: 1)
        )
    }
}

private final class MetricsBox: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: GenerationMetrics?
    var last: GenerationMetrics? {
        lock.lock(); defer { lock.unlock() }
        return stored
    }

    func record(_ metrics: GenerationMetrics) {
        lock.lock(); stored = metrics; lock.unlock()
    }
}
