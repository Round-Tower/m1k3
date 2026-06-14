//
//  MLXToolCalling.swift
//  M1K3MLX
//
//  Phase 12c — makes MLXGemmaProvider a `ToolCallingProvider`, so M1K3's main
//  on-device brain calls tools in its model's NATIVE dialect instead of the
//  prompt-ReAct floor. mlx-swift-lm 2.30.6 already parses the output for us
//  (ToolCallProcessor + per-dialect parsers emit `.toolCall(ToolCall)` inline
//  off the `Generation` stream); this file is the WIRING + type mapping between
//  the dialect-free seam (M1K3Inference) and the library's types — NOT a parser.
//
//  Model-agnostic by design (Kev's "support all brain types"): the dialect is
//  resolved per model family (Gemma → .gemma, Qwen/Llama → .json), the OUTPUT
//  parsing is the library's job for every family, and the only family-specific
//  code here is echoing the assistant's prior call back in its own syntax.
//
//  The pure mappers below are unit-tested (no Metal). `continueToolTurn` runs
//  the real model and is VERIFY-BY-LAUNCH — MLX needs the app-bundle metallib,
//  so it can't execute under `swift test` (same limit as all MLX generation).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.75 (pure mappers
//  tested; the generation glue is verify-by-launch). Prior: Unknown.
//  Review: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 — PR #16/#17
//  review follow-ups: nil-dialect now guard-throws (was a silent .gemma
//  fallback) in continueToolTurn + makeToolTurnSession; serial-use contract
//  comments extended to finish() and the non-seeded fallback path.

import Foundation
import M1K3Inference
import MLX
import MLXLMCommon
import os
import Synchronization

private let mlxToolLog = Logger(subsystem: "app.m1k3", category: "mlx-load")

/// Pure, testable bridges between the M1K3 tool-calling seam and mlx-swift-lm.
enum MLXToolMapping {
    /// Project a dialect-free `ToolDefinition` into the JSON-schema `ToolSpec`
    /// the model's chat template renders (same shape as the library's `Tool`
    /// initializer builds).
    static func toolSpec(from definition: ToolDefinition) -> ToolSpec {
        var properties: [String: any Sendable] = [:]
        var required: [String] = []
        for parameter in definition.parameters {
            properties[parameter.name] = [
                "type": parameter.type,
                "description": parameter.description,
            ] as [String: any Sendable]
            if parameter.isRequired { required.append(parameter.name) }
        }
        return [
            "type": "function",
            "function": [
                "name": definition.name,
                "description": definition.description,
                "parameters": [
                    "type": "object",
                    "properties": properties,
                    "required": required,
                ] as [String: any Sendable],
            ] as [String: any Sendable],
        ]
    }

    /// Map one transcript turn to a `Chat.Message`. Tool results become the
    /// `.tool` role; an assistant turn that made calls echoes them back in the
    /// model's own dialect so the model sees its prior calls in trained form.
    static func chatMessage(from message: ToolMessage, format: ToolCallFormat = .gemma) -> Chat.Message {
        switch message {
        case let .system(text):
            return .system(text)
        case let .user(text):
            return .user(text)
        case let .toolResult(_, output):
            // The library's Chat.Message.tool takes only the output text; the
            // model correlates result-to-call by turn order (one result per
            // call, in sequence). Revisit if multi-call round-trips ever need
            // the name for correlation.
            return .tool(output)
        case let .assistant(text, calls):
            guard !calls.isEmpty else { return .assistant(text ?? "") }
            let renderedCalls = calls.map { callText($0, format: format) }.joined(separator: "\n")
            let content = [text, renderedCalls]
                .compactMap { $0 }
                .filter { !$0.isEmpty }
                .joined(separator: "\n")
            return .assistant(content)
        }
    }

    /// Render a parsed call back into the model's native call syntax.
    static func callText(_ call: ParsedToolCall, format: ToolCallFormat) -> String {
        switch format {
        case .gemma: gemmaCallText(call)
        case .xmlFunction: xmlFunctionCallText(call)
        default: jsonCallText(call)
        }
    }

    /// Gemma dialect: `<start_function_call>call:name{k:value,k:<escape>str<escape>}<end_function_call>`
    /// — string args are escape-wrapped, scalars raw (mirrors GemmaFunctionParser).
    static func gemmaCallText(_ call: ParsedToolCall) -> String {
        let arguments = call.arguments
            .sorted { $0.key < $1.key }
            .map { key, value -> String in
                if case let .string(string) = value {
                    return "\(key):<escape>\(string)<escape>"
                }
                return "\(key):\(value.stringValue)"
            }
            .joined(separator: ",")
        return "<start_function_call>call:\(call.name){\(arguments)}<end_function_call>"
    }

    /// XML function dialect (Qwen3.5/Nemotron):
    /// `<function=name><parameter=key>value</parameter></function>`
    /// (mirrors the library's XMLFunctionParser pattern).
    static func xmlFunctionCallText(_ call: ParsedToolCall) -> String {
        let parameters = call.arguments
            .sorted { $0.key < $1.key }
            .map { key, value -> String in
                let rendered: String = if case let .string(string) = value {
                    string
                } else {
                    value.stringValue
                }
                return "<parameter=\(key)>\(rendered)</parameter>"
            }
            .joined()
        return "<function=\(call.name)>\(parameters)</function>"
    }

    /// JSON dialect (Qwen/Llama/most): `<tool_call>{"name":…,"arguments":{…}}</tool_call>`.
    static func jsonCallText(_ call: ParsedToolCall) -> String {
        let payload = WireCall(name: call.name, arguments: call.arguments)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        guard let data = try? encoder.encode(payload),
              let json = String(data: data, encoding: .utf8)
        else {
            return "<tool_call>{\"name\":\"\(call.name)\"}</tool_call>"
        }
        return "<tool_call>\(json)</tool_call>"
    }

    /// Map the library's parsed `ToolCall` to our dialect-free `ParsedToolCall`.
    static func parsedToolCall(from call: MLXLMCommon.ToolCall) -> ParsedToolCall {
        ParsedToolCall(
            name: call.function.name,
            arguments: call.function.arguments.mapValues(jsonValue(from:))
        )
    }

    /// Convert the library's `JSONValue` to ours (identical case sets).
    static func jsonValue(from value: MLXLMCommon.JSONValue) -> M1K3Inference.JSONValue {
        switch value {
        case .null: .null
        case let .bool(bool): .bool(bool)
        case let .int(int): .int(int)
        case let .double(double): .double(double)
        case let .string(string): .string(string)
        case let .array(array): .array(array.map(jsonValue(from:)))
        case let .object(object): .object(object.mapValues(jsonValue(from:)))
        }
    }

    /// Codable shape for serialising a call to the JSON dialect.
    private struct WireCall: Codable {
        let name: String
        let arguments: [String: M1K3Inference.JSONValue]
    }
}

// MARK: - ToolCallingProvider conformance

extension MLXGemmaProvider: ToolCallingProvider {
    /// Resolve a model's native tool-call dialect from its identifier. Explicit
    /// configuration wins; otherwise the model family decides. `nil` means we
    /// don't recognise the family → the agent falls back to the ReAct floor
    /// rather than running a native loop that will never parse a call.
    static func resolveToolCallFormat(for configuration: ModelConfiguration) -> ToolCallFormat? {
        if let explicit = configuration.toolCallFormat { return explicit }
        let name = configuration.name.lowercased()
        // Gemma 4 emits a NEW dialect (<|tool_call>…) that mlx-swift-lm 3.31.3
        // has no parser for (.gemma4 landed upstream post-tag). nil → the agent
        // uses the proven ReAct floor; flip to .gemma4 on the next upstream tag.
        if name.contains("gemma-4") || name.contains("gemma4") { return nil }
        if name.contains("gemma") { return .gemma }
        // Qwen3.5 is trained on the XML function dialect, NOT <tool_call> JSON
        // (matches upstream infer(): qwen3_5 → .xmlFunction).
        if name.contains("qwen3.5") || name.contains("qwen3_5") || name.contains("qwen3-5") {
            return .xmlFunction
        }
        if name.contains("qwen") || name.contains("llama")
            || name.contains("mistral") || name.contains("phi") { return .json }
        if name.contains("glm") { return .glm4 }
        if name.contains("lfm2") { return .lfm2 }
        return nil
    }

    /// True only when the model family has a known dialect — defuses the silent
    /// `.json` fallback trap (a model we can't parse would loop to the cap with
    /// none of the ReAct safety nets).
    public var supportsToolCalls: Bool {
        resolvedToolCallFormat != nil
    }

    /// Run one model turn over the transcript + tools, returning structure. The
    /// library parses the model's native dialect into `.toolCall` events inline;
    /// we collect them (and any free text) into a `ToolTurn`. Stateless-renders-
    /// array: the whole transcript is re-rendered each call so the agent keeps
    /// owning it (for the trace + observation rescue), per the 12a challenger pass.
    public func continueToolTurn(messages: [ToolMessage], tools: [ToolDefinition]) async throws -> ToolTurn {
        let container = try await ensureLoaded()
        // Unreachable via LocalAgent (supportsToolCalls == false gates this
        // path for an unrecognised family), but this is public API — throwing
        // beats silently rendering the wrong dialect and never parsing a call.
        guard let format = resolvedToolCallFormat else {
            throw InferenceError.generationFailed(
                "no tool-call dialect resolved for this model family"
            )
        }
        let parameters = generateParameters
        let prefixNeeded = thinkPrefixNeeded
        let thinkingContext = thinkingAdditionalContext
        // An agent turn runs several generations back-to-back; reclaim after
        // each so their peaks don't compound in the process-global MLX cache.
        defer { MLXMemoryBudget.reclaim(label: "toolTurn") }

        return try await container.perform { context in
            let chat = messages.map { MLXToolMapping.chatMessage(from: $0, format: format) }
            let specs = tools.map(MLXToolMapping.toolSpec(from:))
            let userInput = UserInput(
                chat: chat,
                tools: specs.isEmpty ? nil : specs,
                additionalContext: thinkingContext
            )
            let input = try await context.processor.prepare(input: userInput)

            let stream = try MLXLMCommon.generate(input: input, parameters: parameters, context: context)
            var text = ""
            var calls: [ParsedToolCall] = []
            for await event in stream {
                switch event {
                case let .chunk(piece):
                    text += piece
                case let .toolCall(libraryCall):
                    calls.append(MLXToolMapping.parsedToolCall(from: libraryCall))
                case let .info(info):
                    logGenerationInfo(info, label: "toolTurn")
                @unknown default:
                    break
                }
            }
            if calls.isEmpty {
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                return ToolTurn.text(Self.normaliseThinkPrefix(trimmed, preOpened: prefixNeeded))
            }
            return ToolTurn.toolCalls(calls)
        }
    }

    /// The REAL session: one KV cache for the whole agent turn. Iteration ≥2
    /// renders and prefills only the new tool-result messages — the prompt,
    /// grounding, tool specs, and every prior generation are already in the
    /// cache (upstream ChatSession's delta-render pattern, with our loop in
    /// control).
    public func makeToolTurnSession(
        tools: [ToolDefinition],
        options: ToolTurnOptions
    ) async throws -> any ToolTurnSession {
        // Same gate as continueToolTurn: an unrecognised family must not get
        // a session that renders the wrong dialect.
        guard let format = resolvedToolCallFormat else {
            throw InferenceError.generationFailed(
                "no tool-call dialect resolved for this model family"
            )
        }
        let container = try await ensureLoaded()
        let specs = tools.map(MLXToolMapping.toolSpec(from:))
        let normalizedSpecs = specs.isEmpty ? nil : specs
        // Persona (and tools — they render in the SAME system block) prefilled
        // once per (model × tools × persona); this turn starts from a copy.
        // (enable_thinking only touches the generation suffix, template-probe
        // verified — the cached prefix is identical either way.)
        let seed = await personaPrefixSnapshot(
            container: container,
            specs: normalizedSpecs,
            toolNames: tools.map(\.name)
        )
        // Per-turn thinking, decided ONLY from this turn's flag + the family's
        // toggle capability — never the provider's construction-time thinking
        // state (which would silently override a turn that asked to think).
        let thinking = Self.toolTurnThinkingDecision(
            turnThinking: options.thinkingEnabled,
            supportsToggle: supportsThinkingToggle
        )
        return MLXToolTurnSession(
            container: container,
            parameters: generateParameters,
            format: format,
            specs: normalizedSpecs,
            thinkingContext: thinking.context,
            prefixNeeded: thinking.prefixNeeded,
            seed: seed
        )
    }

    /// Per-turn thinking decision (pure): given the turn's thinking flag and
    /// whether the family has an `enable_thinking` switch, produce the template
    /// context + whether to pre-open a synthetic `<think>`. A fast turn renders
    /// the EMPTY think pair (`enable_thinking:false`) and skips the opener; a
    /// thinking turn leaves the template default and pre-opens. Independent of
    /// construction-time `thinkingEnabled` so the in-turn decision wins.
    static func toolTurnThinkingDecision(
        turnThinking: Bool,
        supportsToggle: Bool
    ) -> (context: [String: any Sendable]?, prefixNeeded: Bool) {
        let suppressThinking = supportsToggle && !turnThinking
        return (
            suppressThinking ? ["enable_thinking": false] : nil,
            turnThinking && supportsToggle
        )
    }
}

/// Per-turn MLX session: a live `[KVCache]` shared across the turn's
/// generations. `@unchecked Sendable`: the agent loop sends strictly serially,
/// state hands off through a Mutex, and the cache arrays are evaluated by the
/// generation loop before the next send (the same cross-isolation contract
/// upstream ChatSession relies on).
final class MLXToolTurnSession: ToolTurnSession, @unchecked Sendable {
    private let container: ModelContainer
    private let parameters: GenerateParameters
    private let format: ToolCallFormat
    private let specs: [ToolSpec]?
    private let thinkingContext: [String: any Sendable]?
    private let prefixNeeded: Bool

    /// Serial-use contract (why @unchecked is sound): `LocalAgent` is an
    /// actor and `runNativeLoop` awaits each `send` — and calls `finish()`
    /// only after the loop returns — so these are only ever touched one call
    /// at a time, inside `container.perform`'s isolation. The actor enforces
    /// what the compiler can't check here. A Mutex can't hold a non-Sendable
    /// KVCache without tripping region isolation on the way out.
    private var kvCache: [KVCache]?
    /// An EXACT mirror of the token sequence the live cache holds, so a
    /// token-level common prefix against the next render is positionally-valid
    /// KV. Seeded from the persona prefix; kept in sync on every send (trim the
    /// generated tail, set to the rendered fullIDs). The whole reuse scheme
    /// rests on this staying truthful — see CrossTurnCacheReuse.
    private var cachedIDs: [Int]
    /// Sends so far this session — the first send is the one whose reuse SHOULD
    /// equal the seeded persona prefix, so a shortfall there is a diagnosable
    /// seed/render mismatch (logged, decoded).
    private var sendCount = 0
    /// The full conversation — re-rendered every turn so strict templates
    /// (Qwen3.5's "No user query found") always see the user query; only the
    /// suffix beyond `cachedIDs` is actually prefilled.
    private var transcript = ToolTurnTranscript()

    init(
        container: ModelContainer,
        parameters: GenerateParameters,
        format: ToolCallFormat,
        specs: [ToolSpec]?,
        thinkingContext: [String: any Sendable]?,
        prefixNeeded: Bool,
        seed: PersonaPrefixSnapshot? = nil
    ) {
        self.container = container
        self.parameters = parameters
        self.format = format
        self.specs = specs
        self.thinkingContext = thinkingContext
        self.prefixNeeded = prefixNeeded
        kvCache = seed?.cache
        cachedIDs = seed?.tokenIDs ?? []
    }

    func send(
        _ messages: [ToolMessage],
        onToken: @escaping @Sendable (String) -> Void
    ) async throws -> ToolTurn {
        let format = format
        let parameters = parameters
        let prefixNeeded = prefixNeeded
        let thinkingContext = thinkingContext
        // Qwen3.5's template pre-opens <think> — surface the opener so live
        // splitting engages from the generation's first real token.
        if prefixNeeded { onToken("<think>") }

        return try await container.perform { context in
            self.transcript.recordSent(messages)

            // Render the WHOLE conversation (system + goal + every assistant
            // call + every tool result). A full array always carries the user
            // query, so strict templates never reject it — but we prefill only
            // the suffix past what the live cache already holds.
            let fullChat = self.transcript.full.map { MLXToolMapping.chatMessage(from: $0, format: format) }
            let prepared = try await context.processor.prepare(
                input: UserInput(chat: fullChat, tools: self.specs, additionalContext: thinkingContext)
            )
            let fullIDs = prepared.text.tokens.asArray(Int.self)

            let seedCount = self.cachedIDs.count
            let reuse = CrossTurnCacheReuse.reusableLength(
                cached: self.cachedIDs, full: fullIDs, hasCache: self.kvCache != nil
            )
            // Diagnose a first-send seed miss: the persona prefix SHOULD be a
            // full prefix of the first render. If reuse falls short, decode the
            // tokens either side of the divergence so the cause is visible (a
            // tool-JSON ordering drift, a persona-text mismatch, …).
            if self.sendCount == 0, seedCount > 0, reuse < seedCount {
                let window = { (ids: [Int]) -> String in
                    let lo = max(0, reuse - 3), hi = min(ids.count, reuse + 8)
                    return context.tokenizer.decode(tokenIds: Array(ids[lo ..< hi]))
                }
                self.logSeedReuseMiss(
                    at: reuse, of: seedCount, seed: window(self.cachedIDs), render: window(fullIDs)
                )
            }
            self.sendCount += 1
            let cache: [KVCache]
            let input: LMInput
            if reuse > 0, let existing = self.kvCache {
                // Keep the reusable prefix; trim past it (the prior turn's
                // generated tail + any divergence) and prefill only the rest.
                for layer in existing {
                    let extra = layer.offset - reuse
                    if extra > 0 { _ = layer.trim(extra) }
                }
                cache = existing
                input = LMInput(tokens: MLXArray(Array(fullIDs[reuse...])))
            } else {
                cache = context.model.newCache(parameters: parameters)
                input = prepared
            }
            self.logPrefillReuse(reused: reuse, total: fullIDs.count)
            self.kvCache = cache

            let stream = try MLXLMCommon.generate(
                input: input, cache: cache, parameters: parameters, context: context
            )
            var text = ""
            var calls: [ParsedToolCall] = []
            for await event in stream {
                switch event {
                case let .chunk(piece):
                    text += piece
                    onToken(piece)
                case let .toolCall(libraryCall):
                    calls.append(MLXToolMapping.parsedToolCall(from: libraryCall))
                case let .info(info):
                    logGenerationInfo(info, label: "toolTurnSession")
                @unknown default:
                    break
                }
            }
            // Keep cachedIDs an EXACT mirror of the cache: trim this turn's
            // generated tokens (unstable — the next render re-derives the
            // assistant turn structurally), leaving precisely fullIDs in place.
            for layer in cache {
                let extra = layer.offset - fullIDs.count
                if extra > 0 { _ = layer.trim(extra) }
            }
            self.cachedIDs = fullIDs

            let turn: ToolTurn
            if calls.isEmpty {
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                turn = .text(MLXGemmaProvider.normaliseThinkPrefix(trimmed, preOpened: prefixNeeded))
            } else {
                turn = .toolCalls(calls)
            }
            self.transcript.recordGenerated(turn)
            return turn
        }
    }

    /// Param-only (the Logger interpolation is an autoclosure; swiftformat
    /// strips the `self.` the compiler would need on a member).
    private func logPrefillReuse(reused: Int, total: Int) {
        mlxToolLog.notice(
            "toolTurnSession reuse: \(reused, privacy: .public)/\(total, privacy: .public) tok from cache, prefilling \(total - reused, privacy: .public)"
        )
    }

    /// First-send persona-prefix shortfall — the seed should be a full prefix
    /// of the first render; decode both sides of the divergence to show why.
    private func logSeedReuseMiss(at index: Int, of seed: Int, seed seedSlice: String, render: String) {
        mlxToolLog.error(
            "toolTurnSession seed-reuse miss: \(index, privacy: .public)/\(seed, privacy: .public) — diverges at seed=[\(seedSlice, privacy: .public)] render=[\(render, privacy: .public)]"
        )
    }

    /// Turn over: drop the cache and hand the pooled Metal buffers back —
    /// the per-TURN reclaim (per-generation would thrash the pool between
    /// iterations that are about to reuse it). The bare `kvCache = nil` write
    /// is covered by the serial-use contract above: finish() is reachable
    /// only after runNativeLoop returns, never overlapping a `send`.
    func finish() async {
        kvCache = nil
        MLXMemoryBudget.reclaim(label: "toolTurnSession")
    }
}
