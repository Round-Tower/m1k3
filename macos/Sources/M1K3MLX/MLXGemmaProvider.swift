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
//
//  Review: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8 — added
//  `ModelPreloading` conformance + `prepare(progress:)` and threaded
//  `loadContainer`'s `progressHandler` through `ensureLoaded`, so the ~1GB first
//  download reports a 0...1 fraction to the UI instead of stalling silently. The
//  progress path is still verify-by-launch (MLX only runs from the .app).
//
//  Review: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.85 - replaced the
//  check-then-act NSLock cache with a SingleFlightLoader actor. The old
//  ensureLoaded read the cache under the lock, RELEASED it, then ran the slow
//  load unlocked, so a Settings preload racing the first generate could both pass
//  the nil-check and download the container twice. The loader coalesces them into
//  one load (proven Metal-free in SingleFlightLoaderTests).

import Foundation
import M1K3Inference
import MLXLLM
import MLXLMCommon

/// `@unchecked Sendable`: model loading is coalesced through a `SingleFlightLoader`
/// actor and the loaded `ModelContainer` is itself an isolation actor; everything
/// else is immutable.
public final class MLXGemmaProvider: InferenceProvider, ModelPreloading, @unchecked Sendable {
    public let name: String

    private let generateParameters: GenerateParameters
    private let loader: SingleFlightLoader<ModelContainer>

    public init(
        configuration: ModelConfiguration = LLMRegistry.gemma3_1B_qat_4bit,
        maxTokens: Int = 512,
        name: String = "mlx-gemma"
    ) {
        var params = GenerateParameters()
        params.maxTokens = maxTokens
        generateParameters = params
        self.name = name
        // Single-flight the container load so a Settings preload racing the first
        // generate share ONE ~1GB download instead of each kicking off their own.
        loader = SingleFlightLoader { progress in
            try await LLMModelFactory.shared.loadContainer(configuration: configuration) { prog in
                progress(prog.fractionCompleted)
            }
        }
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

    /// Warm the model ahead of the first turn, reporting download progress, so
    /// the runtime picker can show a real bar instead of a silent ~1GB stall.
    /// No-ops fast once the container is cached.
    public func prepare(progress: @escaping @Sendable (Double) -> Void) async throws {
        _ = try await ensureLoaded(progress: progress)
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

    private func ensureLoaded(
        progress: @escaping @Sendable (Double) -> Void = { _ in }
    ) async throws -> ModelContainer {
        try await loader.value(progress: progress)
    }
}
