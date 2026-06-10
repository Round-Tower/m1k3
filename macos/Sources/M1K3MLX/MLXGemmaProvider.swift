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
import MLX
import MLXLLM
import MLXLMCommon
import os

/// Model-load diagnostics. File-scope so the load closure (which runs off the
/// provider's isolation) can log retries without capturing `self`.
private let mlxLoadLog = Logger(subsystem: "dev.murphysig.M1K3", category: "mlx-load")

/// Per-generation latency diagnostics: prompt size, prefill time (the real
/// time-to-first-token driver), and decode throughput. Stream with:
///   log stream --predicate 'subsystem == "dev.murphysig.M1K3" AND category == "ttft"'
let mlxTTFTLog = Logger(subsystem: "dev.murphysig.M1K3", category: "ttft")

/// Log one generation's completion metrics. Free function taking only params
/// (the swiftformat↔Logger autoclosure landmine: never interpolate members).
func logGenerationInfo(_ info: GenerateCompletionInfo, label: String) {
    let promptTokens = info.promptTokenCount
    let prefillMS = Int(info.promptTime * 1000)
    let decodeTokens = info.generationTokenCount
    let tokensPerSecond = Int(info.tokensPerSecond)
    mlxTTFTLog.notice(
        "\(label, privacy: .public): prompt=\(promptTokens)tok prefill=\(prefillMS)ms decode=\(decodeTokens)tok @\(tokensPerSecond)tok/s"
    )
}

/// `@unchecked Sendable`: model loading is coalesced through a `SingleFlightLoader`
/// actor and the loaded `ModelContainer` is itself an isolation actor; everything
/// else is immutable.
public final class MLXGemmaProvider: InferenceProvider, ModelPreloading, @unchecked Sendable {
    public let name: String

    let generateParameters: GenerateParameters
    private let loader: SingleFlightLoader<ModelContainer>
    /// The native tool-call dialect for this model, resolved at init from the
    /// model family (Gemma → .gemma, Qwen/Llama → .json, …). nil means "no known
    /// dialect" → `supportsToolCalls` is false and the agent uses the ReAct floor.
    let resolvedToolCallFormat: ToolCallFormat?
    /// Whether output needs a synthetic `<think>` opener: the model's chat
    /// template PRE-OPENS `<think>` in the generation prompt (Qwen3.5), so the
    /// model emits only the CLOSING tag — prepending the opener keeps the
    /// downstream reasoning split seeing a well-formed pair. Effective flag:
    /// family AND `thinkingEnabled` (a disabled-thinking prompt emits no tags).
    let thinkPrefixNeeded: Bool
    /// Reasoning on/off for models whose template supports `enable_thinking`
    /// (Qwen3.5). Default on — M1K3 surfaces reasoning, it doesn't hide it.
    /// `false` renders the empty think pair into the prompt (the model skips
    /// straight to the answer — a large TTFT lever for a future fast-mode
    /// toggle) and disables the synthetic prefix.
    let thinkingEnabled: Bool
    /// Whether the family's template understands `enable_thinking` at all.
    /// (Internal: the tool-calling extension reads it for per-turn fast mode.)
    let supportsThinkingToggle: Bool
    /// The model id this provider was built for — keys the persona prefix.
    let modelIdentifier: String
    /// Per-(tools × persona) prefilled system-block KV prefix; turns start
    /// from copies instead of re-prefilling the persona every time.
    let personaPrefix = PersonaPrefixCache()

    public init(
        configuration: ModelConfiguration = LLMRegistry.gemma3_1B_qat_4bit,
        // A generous ceiling, NOT a target — the model stops at EOS naturally, so
        // this is free for short replies. Reasoning models (Qwen3) spend hundreds
        // of tokens in <think> before the tool call / answer; the old 512 cap cut
        // them off mid-thought (no call, empty answer). 4096 leaves room to think,
        // act, AND answer across an agent iteration.
        maxTokens: Int = 4096,
        name: String = "mlx-gemma",
        thinkingEnabled: Bool = true
    ) {
        var params = GenerateParameters()
        params.maxTokens = maxTokens
        if Self.supportsQuantizedKVCache(for: configuration) {
            // 8-bit quantized KV: halves per-token KV memory and, since decode is
            // memory-bandwidth-bound, speeds long transcripts. Replaces the
            // maxKVSize rotation backstop for these families — maxKVSize must stay
            // nil because upstream quantizes only KVCacheSimple (RotatingKVCache.
            // toQuantized() is a fatalError TODO), so setting both silently
            // disables kvBits. The rotation cap was sized to never fire anyway
            // (and would rotate the prompt out — a silent quality cliff — if it
            // did); growth stays bounded in practice by the per-turn session
            // lifecycle + maxTokens.
            params.kvBits = 8
            params.kvGroupSize = 64
            params.quantizedKVStart = 0
        } else {
            // Hard-bound KV-cache growth (the cache rotates past this) so a long
            // agent transcript can't balloon memory without limit. 8192 leaves the
            // full 4096-token think-act-answer budget PLUS grounding/tools without
            // ever rotating the prompt out mid-generation.
            params.maxKVSize = 8192
        }
        generateParameters = params
        self.name = name

        // Resolve the model's native tool-call dialect and bake it into the
        // configuration. `ToolCallFormat.infer` matches model_type == "gemma"
        // EXACTLY, so Gemma-3/3n (and Qwen) would silently fall back to .json
        // and never parse — we set the format explicitly per model family.
        let resolved = Self.resolveToolCallFormat(for: configuration)
        resolvedToolCallFormat = resolved
        self.thinkingEnabled = thinkingEnabled
        modelIdentifier = configuration.name
        let familyPreOpens = Self.templatePreOpensThink(for: configuration)
        supportsThinkingToggle = familyPreOpens
        thinkPrefixNeeded = thinkingEnabled && familyPreOpens
        let loadConfiguration: ModelConfiguration = {
            var config = configuration
            if let resolved { config.toolCallFormat = resolved }
            return config
        }()

        // Single-flight the container load so a Settings preload racing the first
        // generate share ONE ~1GB download instead of each kicking off their own.
        // Retry on transient network failures: a HuggingFace CDN timeout would
        // otherwise kill the turn, but the download is resumable so each retry
        // continues the cached partial rather than restarting.
        loader = SingleFlightLoader { progress in
            try await withRetry(
                onRetry: { attempt, error in
                    let reason = error.localizedDescription
                    mlxLoadLog.notice(
                        "model load attempt \(attempt) failed (\(reason, privacy: .public)); retrying"
                    )
                },
                operation: {
                    try await LLMModelFactory.shared.loadContainer(
                        from: HubApiDownloader.llmDefault,
                        using: TransformersTokenizerLoader(),
                        configuration: loadConfiguration
                    ) { prog in
                        progress(prog.fractionCompleted)
                    }
                }
            )
        }
    }

    /// Convenience: point at any HuggingFace model id (e.g. a locally-cached
    /// model) without the caller importing MLXLLM's ModelConfiguration.
    /// Uses the upstream registry entry when one exists — that carries model
    /// metadata a bare id misses (Gemma 4 needs extraEOSTokens ["<turn|>"]);
    /// unknown ids fall back to a plain configuration.
    public convenience init(
        modelID: String,
        maxTokens: Int = 4096,
        name: String = "mlx-gemma",
        thinkingEnabled: Bool = true
    ) {
        self.init(
            configuration: LLMRegistry.shared.configuration(id: modelID),
            maxTokens: maxTokens,
            name: name,
            thinkingEnabled: thinkingEnabled
        )
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
        // Return cached Metal buffers after every generation — without this the
        // process-global MLX cache holds each generation's peak forever.
        defer { MLXMemoryBudget.reclaim(label: "generate") }
        let session = await makeUpstreamSession(container)
        var raw = ""
        for try await event in session.streamDetails(to: prompt, images: [], videos: []) {
            if let piece = event.chunk { raw += piece }
            if let info = event.info { logGenerationInfo(info, label: "generate") }
        }
        return Self.normaliseThinkPrefix(raw, preOpened: thinkPrefixNeeded)
    }

    public func generateStreaming(prompt: String) -> AsyncStream<String> {
        AsyncStream { continuation in
            let task = Task {
                // Runs on every exit — completion, error, and cancellation via
                // onTermination (cancel makes the stream loop throw into catch).
                defer { MLXMemoryBudget.reclaim(label: "generateStreaming") }
                do {
                    let container = try await ensureLoaded()
                    let session = await makeUpstreamSession(container)
                    // Qwen3.5's template pre-opens <think>, so the stream's
                    // first real token is already chain-of-thought — surface
                    // the opener so live reasoning splitting engages from
                    // token one instead of after the closing tag.
                    if thinkPrefixNeeded { continuation.yield("<think>") }
                    for try await event in session.streamDetails(to: prompt, images: [], videos: []) {
                        if let chunk = event.chunk { continuation.yield(chunk) }
                        if let info = event.info { logGenerationInfo(info, label: "stream") }
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

    func ensureLoaded(
        progress: @escaping @Sendable (Double) -> Void = { _ in }
    ) async throws -> ModelContainer {
        // Bound the process-global Metal cache BEFORE any MLX work can run.
        MLXMemoryBudget.applyOnce()
        return try await loader.value(progress: progress)
    }

    /// One-shot upstream session for the plain-chat paths: seeded from the
    /// persona prefix cache when available (instructions stay nil — the cache
    /// IS the system turn), falling back to plain instructions. Thinking
    /// toggle rendered when the family supports it.
    private func makeUpstreamSession(_ container: ModelContainer) async -> ChatSession {
        let session: ChatSession
        if let seed = await personaPrefixSnapshot(container: container, specs: nil, toolNames: []) {
            session = ChatSession(
                container,
                cache: seed.cache,
                generateParameters: generateParameters
            )
        } else {
            session = ChatSession(
                container,
                instructions: M1K3Persona.systemPrompt,
                generateParameters: generateParameters
            )
        }
        if let context = thinkingAdditionalContext {
            session.additionalContext = context
        }
        return session
    }

    /// Get-or-build the persona prefix for this model + tool set. Best-effort:
    /// any failure (no template tokenizer, render error) returns nil and the
    /// caller falls back to sending the system turn as a normal message.
    func personaPrefixSnapshot(
        container: ModelContainer,
        specs: [ToolSpec]?,
        toolNames: [String]
    ) async -> PersonaPrefixSnapshot? {
        let key = PersonaCacheKey(
            modelID: modelIdentifier,
            toolNames: toolNames,
            // Exemplars ride the CACHED render — they cost once per launch,
            // not per turn. The fallback (inline instructions) stays compact.
            personaText: M1K3Persona.systemPrompt(includeExemplars: true)
        )
        if let hit = personaPrefix.snapshot(for: key) { return hit }

        let parameters = generateParameters
        do {
            // `@unchecked Sendable` box: the cache arrays are evaluated by the
            // prefill generation before crossing isolation (the same contract
            // MLXToolTurnSession relies on for its per-turn cache).
            struct PrefixBox: @unchecked Sendable {
                let cache: [KVCache]
                let tokenCount: Int
            }
            let built: PrefixBox = try await container.perform { context in
                // Render the system block WITHOUT the assistant generation
                // opener — needs the upstream tokenizer's full overload.
                guard let upstream = (context.tokenizer as? TransformersTokenizerAdapter)?.upstream else {
                    throw InferenceError.generationFailed("no template tokenizer for persona prefix")
                }
                let ids = try upstream.applyChatTemplate(
                    messages: [[
                        "role": "system",
                        "content": M1K3Persona.systemPrompt(includeExemplars: true),
                    ]],
                    chatTemplate: nil,
                    addGenerationPrompt: false,
                    truncation: false,
                    maxLength: nil,
                    tools: specs
                )
                // Prefill: run a 1-token generation over the prefix, then trim
                // the cache back to exactly the prompt (the sampled token must
                // not pollute the reusable prefix).
                var prefill = parameters
                prefill.maxTokens = 1
                let cache = context.model.newCache(parameters: parameters)
                let stream = try MLXLMCommon.generate(
                    input: LMInput(tokens: MLXArray(ids)),
                    cache: cache,
                    parameters: prefill,
                    context: context
                )
                for await _ in stream {}
                for layer in cache {
                    let extra = layer.offset - ids.count
                    if extra > 0 { _ = layer.trim(extra) }
                }
                return PrefixBox(cache: cache, tokenCount: ids.count)
            }
            personaPrefix.store(built.cache, tokenCount: built.tokenCount, for: key)
            let tokens = built.tokenCount
            mlxTTFTLog.notice("persona prefix cached: \(tokens)tok (saved from every turn's prefill)")
            return personaPrefix.snapshot(for: key)
        } catch {
            let reason = String(describing: error)
            mlxLoadLog.notice("persona prefix unavailable — sending system turn inline (\(reason, privacy: .public))")
            return nil
        }
    }

    /// Template context for the `enable_thinking` switch. nil when thinking is
    /// on (the template's default) or the family has no such switch.
    var thinkingAdditionalContext: [String: any Sendable]? {
        guard supportsThinkingToggle, !thinkingEnabled else { return nil }
        return ["enable_thinking": false]
    }

    /// Self-test diagnostics: how the chat template renders for `prompt` on
    /// this model — token count, the rendered text, and EOS metadata. Used by
    /// the headless self-test to debug template/tokenizer regressions; not a
    /// production code path.
    public func templateDebugDescription(prompt: String) async -> String {
        do {
            let container = try await ensureLoaded()
            return try await container.perform { context in
                let noTools: [[String: any Sendable]]? = nil
                let noContext: [String: any Sendable]? = nil
                let ids = try context.tokenizer.applyChatTemplate(
                    messages: [["role": "user", "content": prompt]],
                    tools: noTools,
                    additionalContext: noContext
                )
                let rendered = context.tokenizer.decode(tokenIds: ids, skipSpecialTokens: false)
                let eos = context.tokenizer.eosToken ?? "nil"
                return "ids=\(ids.count) eos=\(eos) rendered=[\(rendered.suffix(160))]"
            }
        } catch {
            return "template debug failed: \(error)"
        }
    }
}

// MARK: - Reasoning-template normalisation

extension MLXGemmaProvider {
    /// Whether the model family's chat template pre-opens `<think>` in the
    /// generation prompt, so the model emits only the CLOSING tag. Verified in
    /// Qwen3.5's chat_template.jinja (both 2B and 9B). Qwen3 re-emits its own
    /// opening tag in the output, so it is deliberately NOT listed.
    static func templatePreOpensThink(for configuration: ModelConfiguration) -> Bool {
        let modelName = configuration.name.lowercased()
        return modelName.contains("qwen3.5") || modelName.contains("qwen3_5")
            || modelName.contains("qwen3-5")
    }

    /// Allow-list of families whose attention routes through upstream's
    /// `attentionWithCacheUpdate` dispatcher (handles `QuantizedKVCache` via
    /// `updateQuantized`). Gemma3nText and Gemma4Text call
    /// `cache.update(keys:values:)` directly — an upstream fatalError on a
    /// quantized cache — so they (and unknown families) stay unquantized.
    /// Verified against the model sources at mlx-swift-lm 3.31.3; re-audit on
    /// any version bump.
    static func supportsQuantizedKVCache(for configuration: ModelConfiguration) -> Bool {
        let modelName = configuration.name.lowercased()
        // "qwen3" covers Qwen3 and every Qwen3.5 spelling; "gemma-3-" cannot
        // match gemma-3n ids ("gemma-3n…" has no trailing dash after the 3).
        return modelName.contains("qwen3") || modelName.contains("gemma-3-")
    }

    /// Prepend the synthetic `<think>` opener exactly once so downstream
    /// reasoning splitting always sees a well-formed pair. No-op when the
    /// template didn't pre-open, the text already opens one, or there is no
    /// text at all.
    static func normaliseThinkPrefix(_ text: String, preOpened: Bool) -> String {
        guard preOpened, !text.isEmpty, !text.hasPrefix("<think>") else { return text }
        return "<think>" + text
    }
}
