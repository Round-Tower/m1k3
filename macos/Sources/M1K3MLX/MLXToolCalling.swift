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

import Foundation
import M1K3Inference
import MLXLMCommon
import Synchronization

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
        let format = resolvedToolCallFormat ?? .gemma
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
    public func makeToolTurnSession(tools: [ToolDefinition]) async throws -> any ToolTurnSession {
        let container = try await ensureLoaded()
        let specs = tools.map(MLXToolMapping.toolSpec(from:))
        return MLXToolTurnSession(
            container: container,
            parameters: generateParameters,
            format: resolvedToolCallFormat ?? .gemma,
            specs: specs.isEmpty ? nil : specs,
            thinkingContext: thinkingAdditionalContext,
            prefixNeeded: thinkPrefixNeeded
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

    /// Serial-use contract (why @unchecked is sound): the agent loop awaits
    /// each `send` before the next, so these are only ever touched one send at
    /// a time, inside `container.perform`'s isolation. A Mutex can't hold a
    /// non-Sendable KVCache without tripping region isolation on the way out.
    private var kvCache: [KVCache]?
    private var sentTools = false

    init(
        container: ModelContainer,
        parameters: GenerateParameters,
        format: ToolCallFormat,
        specs: [ToolSpec]?,
        thinkingContext: [String: any Sendable]?,
        prefixNeeded: Bool
    ) {
        self.container = container
        self.parameters = parameters
        self.format = format
        self.specs = specs
        self.thinkingContext = thinkingContext
        self.prefixNeeded = prefixNeeded
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
            let chat = messages.map { MLXToolMapping.chatMessage(from: $0, format: format) }
            // Tool specs render into the chat template once, on the first
            // send — they live in the cache afterwards.
            let userInput = UserInput(
                chat: chat,
                tools: self.sentTools ? nil : self.specs,
                additionalContext: thinkingContext
            )
            let input = try await context.processor.prepare(input: userInput)
            let cache = self.kvCache ?? context.model.newCache(parameters: parameters)
            self.kvCache = cache
            self.sentTools = true

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
            if calls.isEmpty {
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                return ToolTurn.text(
                    MLXGemmaProvider.normaliseThinkPrefix(trimmed, preOpened: prefixNeeded)
                )
            }
            return ToolTurn.toolCalls(calls)
        }
    }

    /// Turn over: drop the cache and hand the pooled Metal buffers back —
    /// the per-TURN reclaim (per-generation would thrash the pool between
    /// iterations that are about to reuse it).
    func finish() async {
        kvCache = nil
        MLXMemoryBudget.reclaim(label: "toolTurnSession")
    }
}
