//
//  LocalAgent.swift
//  M1K3Agent
//
//  An iterative agent over an InferenceProvider + injected tools. Two loop
//  strategies share one actor: the prompt-ReAct floor (LocalAgent+ReAct.swift),
//  which works on ANY model, and the native tool-calling loop
//  (LocalAgent+Native.swift), used when the provider speaks its model's own
//  dialect. `run()` is the dispatcher; this file holds the actor shell plus the
//  dialect-INDEPENDENT machinery both strategies reuse — the dispatch core
//  (repeat-guard, unknown-tool steering, activity events, bookkeeping), the
//  reasoning trace, and conclusion cleanup.
//
//  Generalised from the internal call-pipeline project's the prior domain ReAct agent.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project the prior domain ReAct agent (Kev)
//
//  Review: Kev + claude-fable-5, 2026-06-09 — hardened for always-on chat with
//  small on-device models: onEvent loop callbacks, quote/markdown-tolerant
//  action parsing, repeat-action guard, optional implicit-conclusion, cooperative
//  cancellation, unknown-tool steering.
//
//  Review: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.85 — Phase 12a: added
//  the native tool-calling path behind the `ToolCallingProvider` capability seam.
//  The ReAct loop moved to LocalAgent+ReAct.swift unchanged; run() now selects
//  between the two strategies, and the shared dispatch core was generalised so
//  both feed the same repeat-guard / event / bookkeeping path.

import Foundation
import M1K3Inference

/// One step in the reasoning trace, for explainability + the agent-reasoning UI.
public struct ReasoningStep: Sendable, Equatable {
    public let iteration: Int
    public let thought: String
    public let action: String?
    public let observation: String?

    public init(iteration: Int, thought: String, action: String? = nil, observation: String? = nil) {
        self.iteration = iteration
        self.thought = thought
        self.action = action
        self.observation = observation
    }
}

public struct AgentResult: Sendable, Equatable {
    public let conclusion: String
    public let toolsUsed: [String]
    public let iterations: Int
    public let reasoningTrace: [ReasoningStep]

    public init(conclusion: String, toolsUsed: [String], iterations: Int, reasoningTrace: [ReasoningStep]) {
        self.conclusion = conclusion
        self.toolsUsed = toolsUsed
        self.iterations = iterations
        self.reasoningTrace = reasoningTrace
    }
}

public enum AgentError: Error, Sendable, Equatable {
    case toolNotFound(String)
}

/// Loop progress, surfaced so the chat UI can show what the agent is doing
/// while no tokens are streaming ("Thinking…", "Searching the web…").
public enum AgentLoopEvent: Sendable, Equatable {
    case thinking(iteration: Int)
    case actionStarted(tool: String, argument: String)
}

public actor LocalAgent {
    // Internal (not private) so the cross-file ReAct/native/logging extensions
    // can read them.
    let inferenceProvider: any InferenceProvider
    let tools: [String: AgentTool]
    let maxIterations: Int
    let concludesOnUnstructuredThought: Bool

    public internal(set) var reasoningTrace: [ReasoningStep] = []

    public init(
        inferenceProvider: any InferenceProvider,
        tools: [any AgentTool],
        maxIterations: Int = 5,
        concludesOnUnstructuredThought: Bool = false
    ) {
        self.inferenceProvider = inferenceProvider
        self.tools = Dictionary(tools.map { ($0.name, $0) }, uniquingKeysWith: { first, _ in first })
        self.maxIterations = maxIterations
        self.concludesOnUnstructuredThought = concludesOnUnstructuredThought
    }

    /// Run the agent toward `goal`, optionally grounded in `context` (e.g.
    /// retrieved knowledge). Dispatches to the native tool-calling loop when the
    /// active provider speaks its model's own dialect AND its current model can
    /// emit parseable calls; otherwise the prompt-ReAct floor — the universal
    /// baseline that works on any model.
    public func run(
        goal: String,
        context groundingContext: String? = nil,
        onEvent: (@Sendable (AgentLoopEvent) -> Void)? = nil,
        onConclusionToken: (@Sendable (String) -> Void)? = nil,
        onReasoningToken: (@Sendable (String) -> Void)? = nil
    ) async throws -> AgentResult {
        reasoningTrace.removeAll()

        let toolProvider = inferenceProvider as? ToolCallingProvider
        let supportsToolCalls = toolProvider?.supportsToolCalls ?? false
        logPathSelection(
            provider: inferenceProvider.name,
            conforms: toolProvider != nil,
            supportsToolCalls: supportsToolCalls,
            usingNative: supportsToolCalls
        )
        if let toolProvider, supportsToolCalls {
            return try await runNative(
                provider: toolProvider,
                goal: goal,
                grounding: groundingContext,
                onEvent: onEvent,
                onConclusionToken: onConclusionToken,
                onReasoningToken: onReasoningToken
            )
        }

        return try await runReAct(
            goal: goal,
            grounding: groundingContext,
            onEvent: onEvent,
            onConclusionToken: onConclusionToken
        )
    }

    // MARK: - Shared conclusion handling

    func concluded(
        _ conclusion: String, _ usedTools: Set<String>, _ iterations: Int
    ) -> AgentResult {
        let cleaned = Self.stripScaffolding(conclusion)
        logConclusion(cleaned, raw: conclusion, usedTools: usedTools, iterations: iterations)
        return AgentResult(
            conclusion: cleaned,
            toolsUsed: Array(usedTools),
            iterations: iterations,
            reasoningTrace: reasoningTrace
        )
    }

    /// Remove ReAct scaffolding (ACTION: lines, with or without markdown
    /// decoration) from user-visible text. Small models leak these into
    /// conclusions and the iteration-cap synthesis — seen live at ⌘R.
    static func stripScaffolding(_ text: String) -> String {
        text.components(separatedBy: "\n")
            .filter { line in
                let bare = line.trimmingCharacters(in: decoration)
                return !bare.hasPrefix("ACTION:")
            }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Characters small models wrap tool names/arguments in: markdown bold,
    /// backticks, straight quotes — stripped so `**ACTION:** search("x")`
    /// dispatches the same as the canonical form (used by ReAct parsing + the
    /// shared scaffolding strip).
    static let decoration = CharacterSet(charactersIn: "*`\"'").union(.whitespaces)

    // MARK: - Shared tool dispatch

    /// Identifies one tool invocation for the dispatch core, dialect-free.
    struct ToolCallSite {
        let toolName: String
        let displayDescription: String
        let eventArgument: String
    }

    /// Shared dispatch core for BOTH the ReAct and native paths: repeat-guard,
    /// unknown-tool steering, the activity event, and used/executed bookkeeping.
    /// The `execute` closure adapts the dialect's arguments (a positional string
    /// for ReAct, a structured map for native) to the tool's `execute(input:)`.
    func dispatchCall(
        _ site: ToolCallSite,
        executedActions: inout Set<String>,
        usedTools: inout Set<String>,
        onEvent: (@Sendable (AgentLoopEvent) -> Void)?,
        execute: (any AgentTool) async throws -> String
    ) async -> String {
        if executedActions.contains(site.displayDescription) {
            // Repeat-guard: small models loop on the same call. Steer to a
            // DIFFERENT tool or a conclusion instead of re-executing.
            return "You already ran \(site.displayDescription) — do not repeat it. "
                + "Try a different tool if one fits, or use what you have and "
                + "reply starting with \"CONCLUSION:\"."
        }
        guard let tool = tools[site.toolName] else {
            // Steer, don't just error: list what IS callable so the next
            // thought can correct itself.
            let available = tools.keys.sorted().joined(separator: ", ")
            return "Error: unknown tool '\(site.toolName)'. "
                + "Available tools: \(available.isEmpty ? "(none)" : available)."
        }
        onEvent?(.actionStarted(tool: site.toolName, argument: site.eventArgument))
        executedActions.insert(site.displayDescription)
        do {
            let output = try await execute(tool)
            usedTools.insert(site.toolName)
            return output
        } catch {
            return "Error executing \(site.toolName): \(error)"
        }
    }
}
