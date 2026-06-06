//
//  MLXGemmaProvider.swift
//  M1K3MLX
//
//  Gemma generation on-device via MLX (Metal), behind the same InferenceProvider
//  seam as AppleFoundationModelsProvider. This is M1K3's "main brain" tier — when
//  selected, the chat answers stream from a real Gemma running in-process, no
//  server, no cloud.
//
//  Unproven-path note: the broader the internal prior projects stack only ever exercised
//  MLXEmbedders; MLXLLM generation is new here. It's compile-verified and covered
//  by a gated integration test (downloads weights — minutes, network), but the
//  first real on-device generation is the milestone to watch, not a settled fact.
//
//  Default model: gemma-3-1b-it (QAT 4-bit, ~1GB) — the smallest current Gemma,
//  picked so the first download is tolerable. Pass a beefier ModelConfiguration
//  (e.g. LLMRegistry.gemma_2_9b_it_4bit) for quality once the path is trusted.
//
//  Streaming contract: MLX yields *delta* chunks (incremental text). ChatSession's
//  fold normalises that against AFM's cumulative snapshots, so both render right.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.65, Prior: Unknown
//  Context: First MLXLLM generation in the M1K3/the internal prior projects family — the PLAN's
//  flagged Phase-2 risk ("confirm MLXLLM runs Gemma cleanly"). Compiles; runtime
//  generation pending on-device verification.

import Foundation
import M1K3Inference
import MLXLLM
import MLXLMCommon

/// `@unchecked Sendable`: the loaded `ModelContainer` is cached behind a lock and
/// is itself an isolation actor; everything else is immutable.
public final class MLXGemmaProvider: InferenceProvider, @unchecked Sendable {
    public let name: String

    private let configuration: ModelConfiguration
    private let generateParameters: GenerateParameters
    private let lock = NSLock()
    private var container: ModelContainer?

    public init(
        configuration: ModelConfiguration = LLMRegistry.gemma3_1B_qat_4bit,
        maxTokens: Int = 512,
        name: String = "mlx-gemma"
    ) {
        self.configuration = configuration
        var params = GenerateParameters()
        params.maxTokens = maxTokens
        generateParameters = params
        self.name = name
    }

    /// Convenience: point at any HuggingFace model id (e.g. a locally-cached
    /// model) without the caller importing MLXLLM's ModelConfiguration.
    public convenience init(modelID: String, maxTokens: Int = 512, name: String = "mlx-gemma") {
        self.init(configuration: ModelConfiguration(id: modelID), maxTokens: maxTokens, name: name)
    }

    /// True on this target by construction: macOS 26 / Apple Silicon always has
    /// the Metal GPU MLX needs. The model itself downloads lazily on first use,
    /// so availability here means "this backend can serve", not "weights present".
    public var isAvailable: Bool {
        true
    }

    public func generate(prompt: String) async throws -> String {
        let container = try await ensureLoaded()
        let session = ChatSession(container, generateParameters: generateParameters)
        return try await session.respond(to: prompt)
    }

    public func generateStreaming(prompt: String) -> AsyncStream<String> {
        AsyncStream { continuation in
            let task = Task {
                do {
                    let container = try await ensureLoaded()
                    let session = ChatSession(container, generateParameters: generateParameters)
                    for try await chunk in session.streamResponse(to: prompt, images: [], videos: []) {
                        continuation.yield(chunk)
                    }
                    continuation.finish()
                } catch {
                    // Per the InferenceProvider contract, errors terminate the
                    // stream rather than throwing.
                    continuation.finish()
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private func ensureLoaded() async throws -> ModelContainer {
        if let cached = lock.withLock({ container }) { return cached }
        let loaded = try await LLMModelFactory.shared.loadContainer(configuration: configuration)
        lock.withLock { container = loaded }
        return loaded
    }
}
