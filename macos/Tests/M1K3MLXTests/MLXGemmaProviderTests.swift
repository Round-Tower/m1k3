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
import MLXLMCommon
import Testing

struct MLXGemmaProviderTests {
    @Test("conforms to InferenceProvider and is available on this target")
    func conformanceAndAvailability() {
        let provider: any InferenceProvider = MLXGemmaProvider()
        #expect(provider.name == "mlx-gemma")
        #expect(provider.isAvailable)
    }

    @Test("KV quantization is allow-listed per family — raw cache.update families are excluded")
    func kvQuantizationFamilyResolution() {
        // Safe: attention routes through upstream's attentionWithCacheUpdate
        // dispatcher, which handles QuantizedKVCache via updateQuantized.
        #expect(MLXGemmaProvider.supportsQuantizedKVCache(
            for: ModelConfiguration(id: "mlx-community/Qwen3.5-2B-4bit")
        ))
        #expect(MLXGemmaProvider.supportsQuantizedKVCache(
            for: ModelConfiguration(id: "mlx-community/Qwen3.5-9B-4bit")
        ))
        #expect(MLXGemmaProvider.supportsQuantizedKVCache(
            for: ModelConfiguration(id: "mlx-community/gemma-3-1b-it-qat-4bit")
        ))
        // Unsafe: Gemma3nText/Gemma4Text call cache.update(keys:values:) raw,
        // which is an upstream fatalError on a quantized cache.
        #expect(!MLXGemmaProvider.supportsQuantizedKVCache(
            for: ModelConfiguration(id: "mlx-community/gemma-4-e4b-it-4bit")
        ))
        #expect(!MLXGemmaProvider.supportsQuantizedKVCache(
            for: ModelConfiguration(id: "mlx-community/gemma-3n-E4B-it-lm-4bit")
        ))
        // Unknown families default to NOT quantizing — crash-safe over fast.
        #expect(!MLXGemmaProvider.supportsQuantizedKVCache(
            for: ModelConfiguration(id: "some/unknown-model")
        ))
        // The WIRED dense tiers (lil/huge): Qwen3 attention routes through
        // upstream's attentionWithCacheUpdate dispatcher (Qwen3.swift:84), which
        // handles QuantizedKVCache — unlike Gemma4Text's raw cache.update. So
        // quantized KV is SAFE for dense Qwen3 (verified vs mlx-swift-lm 3.31.3).
        #expect(MLXGemmaProvider.supportsQuantizedKVCache(
            for: ModelConfiguration(id: "mlx-community/Qwen3-4B-4bit")
        ))
        #expect(MLXGemmaProvider.supportsQuantizedKVCache(
            for: ModelConfiguration(id: "mlx-community/Qwen3-8B-4bit")
        ))
    }

    @Test("tool-turn thinking is decided per-turn, never from construction-time state")
    func toolTurnThinkingIsPerTurn() {
        // A toggle family that ALSO pre-opens (Qwen3.5): thinking ON ⇒ no
        // enable_thinking suppression + a pre-opened <think>; OFF ⇒
        // enable_thinking:false + no pre-open.
        let thinkOn = MLXGemmaProvider.toolTurnThinkingDecision(
            turnThinking: true, supportsToggle: true, preOpensThink: true
        )
        #expect(thinkOn.context == nil)
        #expect(thinkOn.prefixNeeded)

        let thinkOff = MLXGemmaProvider.toolTurnThinkingDecision(
            turnThinking: false, supportsToggle: true, preOpensThink: true
        )
        #expect(thinkOff.context?["enable_thinking"] as? Bool == false)
        #expect(!thinkOff.prefixNeeded)

        // THE FIX — dense Qwen3 (lil/huge): toggles enable_thinking but does NOT
        // pre-open. Fast mode MUST suppress (enable_thinking:false), and a thinking
        // turn must NOT add a synthetic opener (the model emits its own <think>).
        let denseFast = MLXGemmaProvider.toolTurnThinkingDecision(
            turnThinking: false, supportsToggle: true, preOpensThink: false
        )
        #expect(denseFast.context?["enable_thinking"] as? Bool == false)
        #expect(!denseFast.prefixNeeded)

        let denseThink = MLXGemmaProvider.toolTurnThinkingDecision(
            turnThinking: true, supportsToggle: true, preOpensThink: false
        )
        #expect(denseThink.context == nil)
        #expect(!denseThink.prefixNeeded)

        // A non-toggle family (gemma) never suppresses and never pre-opens.
        let noToggleOn = MLXGemmaProvider.toolTurnThinkingDecision(
            turnThinking: true, supportsToggle: false, preOpensThink: false
        )
        #expect(noToggleOn.context == nil)
        #expect(!noToggleOn.prefixNeeded)

        let noToggleOff = MLXGemmaProvider.toolTurnThinkingDecision(
            turnThinking: false, supportsToggle: false, preOpensThink: false
        )
        #expect(noToggleOff.context == nil)
        #expect(!noToggleOff.prefixNeeded)
    }

    @Test("repetition penalty is mild and windowed — the degenerate-loop guard")
    func repetitionPenaltyPinned() {
        // 1.1 with a 64-token window suppresses verbatim loops without
        // distorting tool-call JSON or citation tokens (each repeats little
        // within 64 tokens). A silent edit toward ≥1.2 — where structured
        // output measurably degrades — should fail here, not at ⌘R.
        let provider = MLXGemmaProvider()
        #expect(provider.generateParameters.repetitionPenalty == 1.1)
        #expect(provider.generateParameters.repetitionContextSize == 64)
    }

    @Test("quantizing families carry kvBits; excluded families keep the rotation cap")
    func generateParametersPerFamily() {
        let qwen = MLXGemmaProvider(configuration: ModelConfiguration(id: "mlx-community/Qwen3.5-2B-4bit"))
        #expect(qwen.generateParameters.kvBits == 8)
        #expect(qwen.generateParameters.kvGroupSize == 64)
        #expect(qwen.generateParameters.quantizedKVStart == 0)
        #expect(qwen.generateParameters.maxKVSize == nil)

        let gemma4 = MLXGemmaProvider(configuration: ModelConfiguration(id: "mlx-community/gemma-4-e4b-it-4bit"))
        #expect(gemma4.generateParameters.kvBits == nil)
        #expect(gemma4.generateParameters.maxKVSize == 8192)
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
