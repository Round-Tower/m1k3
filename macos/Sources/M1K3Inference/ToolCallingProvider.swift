//
//  ToolCallingProvider.swift
//  M1K3Inference
//
//  Phase 12a — the capability seam for NATIVE tool calling. Most models speak
//  their own tool-call dialect (Gemma `call:name{…}`, Qwen/Llama `<tool_call>`
//  JSON, Apple Foundation Models' native `Tool` protocol). A `ToolCallingProvider`
//  is an `InferenceProvider` that parses its model's dialect into structured
//  `ToolTurn`s, so the agent never text-scrapes — and stays model-AGNOSTIC: the
//  per-model format mapping lives inside each adapter, never in the agent.
//
//  Runtimes with no tool support simply don't conform, so the prompt-ReAct loop
//  remains the universal floor (LocalAgent uses the native path only when the
//  active provider conforms AND reports `supportsToolCalls`).
//
//  Why a typed transcript, not a prompt string (challenger pass, 2026-06-10):
//  native models render tool definitions and tool RESULTS into role-tagged turns
//  they were trained on. Feeding results back as concatenated prose is
//  off-distribution and breaks small models. So the agent owns the conversation
//  as `[ToolMessage]` data; the provider renders it into its model's chat
//  template. This is also exactly the shape mlx-swift-lm's
//  `UserInput(chat: [Chat.Message])` wants — so the MLX adapter (12c) maps the
//  array straight through with no seam churn.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.85, Prior: Unknown
//  Context: foundational multi-model tool-calling seam; pressure-tested by the
//  challenger (typed transcript + typed args + runtime capability flag adopted;
//  full TurnStrategy extraction deferred — runNative reuses the shared dispatch
//  helpers rather than rewriting the proven ReAct path).

import Foundation

/// A JSON value as it arrives from a model's native tool-call output. Tool-call
/// arguments are genuinely typed (numbers, bools, nested objects), so the WIRE
/// preserves them; callers flatten to `String` at the execution edge if their
/// tool only takes text. Typing the wire now avoids a breaking change to a
/// public protocol when a multi-arg typed tool lands.
public enum JSONValue: Sendable, Equatable, Codable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case array([JSONValue])
    case object([String: JSONValue])
    case null

    /// A flat string rendering for tools that take plain text. Scalars render
    /// naturally; containers fall back to their JSON-ish description.
    public var stringValue: String {
        switch self {
        case let .string(value): value
        case let .int(value): String(value)
        case let .double(value): String(value)
        case let .bool(value): String(value)
        case .null: ""
        case let .array(values): values.map(\.stringValue).joined(separator: ", ")
        case let .object(pairs):
            pairs.sorted { $0.key < $1.key }
                .map { "\($0.key)=\($0.value.stringValue)" }
                .joined(separator: ", ")
        }
    }
}

/// One declared parameter of a tool, dialect-free. Each adapter projects this
/// into its model's schema format (JSON Schema for MLX/AFM).
public struct ToolParameterDefinition: Sendable, Equatable {
    public let name: String
    public let description: String
    /// JSON-schema type ("string", "integer", "boolean", …). Defaults to string
    /// since every current M1K3 tool takes one text argument.
    public let type: String
    public let isRequired: Bool

    public init(name: String, description: String, type: String = "string", isRequired: Bool = true) {
        self.name = name
        self.description = description
        self.type = type
        self.isRequired = isRequired
    }
}

/// A tool the model may call, described dialect-free. `AgentTool` projects to
/// this (see `AgentTool.toolDefinition`); the provider renders it natively.
public struct ToolDefinition: Sendable, Equatable {
    public let name: String
    public let description: String
    public let parameters: [ToolParameterDefinition]

    public init(name: String, description: String, parameters: [ToolParameterDefinition]) {
        self.name = name
        self.description = description
        self.parameters = parameters
    }
}

/// A tool call the model asked for — already parsed out of the model's native
/// dialect by the provider, so the agent receives structure, not text.
public struct ParsedToolCall: Sendable, Equatable {
    public let name: String
    public let arguments: [String: JSONValue]

    public init(name: String, arguments: [String: JSONValue]) {
        self.name = name
        self.arguments = arguments
    }

    /// Arguments flattened to text for the current single-positional `AgentTool`
    /// contract. Keys are preserved; values are string-rendered.
    public var stringArguments: [String: String] {
        arguments.mapValues(\.stringValue)
    }
}

/// One turn from a tool-capable model: either a final text answer, or one or
/// more tool calls to execute and feed back as results.
public enum ToolTurn: Sendable, Equatable {
    case text(String)
    case toolCalls([ParsedToolCall])
}

/// A role-tagged turn in the agent↔model conversation. The agent owns the
/// transcript as `[ToolMessage]`; the provider renders it into its model's chat
/// template (system/user/assistant/tool roles). This is the durable contract
/// that survives whether the adapter is stateless-renders-array or stateful.
public enum ToolMessage: Sendable, Equatable {
    /// The opening instruction + goal (and any grounding context).
    case user(String)
    /// What the model produced last turn — free text and/or the tool calls it
    /// requested. Threaded back so the model sees its own prior calls correctly.
    case assistant(text: String?, toolCalls: [ParsedToolCall])
    /// The observation from executing one tool call, role-tagged as a result.
    case toolResult(name: String, output: String)
}

/// Capability refinement: an `InferenceProvider` that speaks its model's NATIVE
/// tool-call dialect. `LocalAgent` runs the structured loop when the active
/// provider conforms; otherwise the prompt-ReAct floor.
public protocol ToolCallingProvider: InferenceProvider {
    /// Whether the BACKING MODEL can actually emit parseable tool calls right
    /// now — a RUNTIME answer, not type conformance. A swappable provider whose
    /// current model lacks tool support (or whose dialect isn't wired) reports
    /// `false` so the agent uses the ReAct floor instead of burning iterations
    /// on a model that will never call a tool.
    var supportsToolCalls: Bool { get }

    /// Continue the conversation: given the transcript so far and the available
    /// tools, return the model's next turn (final text, or tool calls to run).
    func continueToolTurn(messages: [ToolMessage], tools: [ToolDefinition]) async throws -> ToolTurn
}

public extension ToolCallingProvider {
    /// Conforming types are tool-capable by default; swappable wrappers override
    /// to reflect their current backing model.
    var supportsToolCalls: Bool {
        true
    }
}
