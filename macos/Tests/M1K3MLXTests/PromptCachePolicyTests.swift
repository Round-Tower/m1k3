//
//  PromptCachePolicyTests.swift
//  M1K3MLXTests
//
//  Pure policy for on-disk prompt-cache (KV prefix) reuse: the filename IS the
//  fingerprint, so any change to what makes a saved prefix valid — model,
//  persona text, tool set, KV quantization params, MLX kernel generation —
//  produces a different name and the stale file is simply a miss (and
//  prunable). Same doctrine as the embedder fingerprint re-index policy.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8, Prior: Unknown
//

@testable import M1K3MLX
import Testing

struct PromptCachePolicyTests {
    private func fingerprint(
        modelID: String = "mlx-community/Qwen3.5-2B-4bit",
        prefixText: String = "You are M1K3.",
        toolNames: [String] = ["web_search", "fetch_page"],
        kvBits: Int? = 8,
        kvGroupSize: Int = 64,
        kernelTag: String = "0.31"
    ) -> PromptCachePolicy.Fingerprint {
        PromptCachePolicy.Fingerprint(
            modelID: modelID,
            prefixText: prefixText,
            toolNames: toolNames,
            kvBits: kvBits,
            kvGroupSize: kvGroupSize,
            kernelTag: kernelTag
        )
    }

    @Test("file name is deterministic for an identical fingerprint")
    func deterministicName() {
        #expect(
            PromptCachePolicy.fileName(for: fingerprint())
                == PromptCachePolicy.fileName(for: fingerprint())
        )
    }

    @Test("tool order does not change the fingerprint")
    func toolOrderInsensitive() {
        let forward = fingerprint(toolNames: ["web_search", "fetch_page"])
        let reversed = fingerprint(toolNames: ["fetch_page", "web_search"])
        #expect(PromptCachePolicy.fileName(for: forward) == PromptCachePolicy.fileName(for: reversed))
    }

    @Test("every fingerprint component changes the name")
    func componentsChangeName() {
        let base = PromptCachePolicy.fileName(for: fingerprint())
        #expect(PromptCachePolicy.fileName(for: fingerprint(modelID: "other/model")) != base)
        #expect(PromptCachePolicy.fileName(for: fingerprint(prefixText: "You are someone else.")) != base)
        #expect(PromptCachePolicy.fileName(for: fingerprint(toolNames: [])) != base)
        // Quantized and plain caches are different on-disk artifacts.
        #expect(PromptCachePolicy.fileName(for: fingerprint(kvBits: nil)) != base)
        #expect(PromptCachePolicy.fileName(for: fingerprint(kvGroupSize: 32)) != base)
        #expect(PromptCachePolicy.fileName(for: fingerprint(kernelTag: "0.32")) != base)
    }

    @Test("name is a flat .safetensors file name, no path separators")
    func filesystemSafe() {
        let name = PromptCachePolicy.fileName(for: fingerprint(modelID: "mlx-community/Qwen3.5-2B-4bit"))
        #expect(name.hasSuffix(".safetensors"))
        #expect(!name.contains("/"))
        #expect(!name.contains(":"))
    }
}
