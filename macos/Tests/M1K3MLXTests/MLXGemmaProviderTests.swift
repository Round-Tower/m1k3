//
//  MLXGemmaProviderTests.swift
//  M1K3MLXTests
//
//  Fast tier: protocol conformance + availability, no model load. Integration
//  tier (M1K3_MLX_INTEGRATION=1): actually downloads Gemma and generates — the
//  first real proof the MLXLLM path works end-to-end. Network + minutes.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.75, Prior: Unknown

import Foundation
import M1K3Inference
@testable import M1K3MLX
import Testing

struct MLXGemmaProviderTests {
    @Test("conforms to InferenceProvider and is available on this target")
    func conformanceAndAvailability() {
        let provider: any InferenceProvider = MLXGemmaProvider()
        #expect(provider.name == "mlx-gemma")
        #expect(provider.isAvailable)
    }

    @Test(
        "INTEGRATION: generates a non-empty response from a real Gemma",
        .enabled(if: ProcessInfo.processInfo.environment["M1K3_MLX_INTEGRATION"] == "1")
    )
    func generatesRealText() async throws {
        let provider = MLXGemmaProvider(maxTokens: 64)
        let answer = try await provider.generate(prompt: "Reply with exactly one short sentence about seals.")
        #expect(!answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }

    @Test(
        "INTEGRATION: streams delta chunks that accumulate to a non-empty answer",
        .enabled(if: ProcessInfo.processInfo.environment["M1K3_MLX_INTEGRATION"] == "1")
    )
    func streamsRealText() async {
        let provider = MLXGemmaProvider(maxTokens: 64)
        var collected = ""
        for await chunk in provider.generateStreaming(prompt: "Say hello in one word.") {
            collected += chunk
        }
        #expect(!collected.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }
}
