//
//  LocalAgent.swift
//  M1K3Agent
//
//  ReAct-style local agent: an iterative Thought → Action → Observation loop
//  over an InferenceProvider + injected tools. Generalised from the internal call-pipeline project/
//  clair's the prior domain ReAct agent — domain-specific transcript input becomes a generic
//  `goal` + optional `context`, and the PerformanceMonitor coupling is dropped.
//  The loop logic (CONCLUSION marker, ACTION parsing, max-iteration synthesis)
//  is unchanged.
//
//  Drives M1K3's "basic tool calling for the local agent": the model reasons,
//  calls knowledge tools to gather facts, and concludes.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project the prior domain ReAct agent (Kev)
//
//  Review: Kev + claude-fable-5, 2026-06-09 — hardened for always-on chat with
//  small on-device models: onEvent loop callbacks (drives the chat activity
//  label), quote/markdown-tolerant action parsing, repeat-action guard,
//  optional implicit-conclusion on unstructured prose, cooperative
//  cancellation, and unknown-tool steering. Loop shape unchanged.

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
    private let inferenceProvider: any InferenceProvider
    private let tools: [String: AgentTool]
    private let maxIterations: Int
    private let concludesOnUnstructuredThought: Bool

    public private(set) var reasoningTrace: [ReasoningStep] = []

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

    /// Run the ReAct loop toward `goal`, optionally grounded in `context`
    /// (e.g. retrieved knowledge). Terminates on a `CONCLUSION:` thought or,
    /// at the iteration cap, by synthesising from what was gathered.
    public func run(
        goal: String,
        context groundingContext: String? = nil,
        onEvent: (@Sendable (AgentLoopEvent) -> Void)? = nil
    ) async throws -> AgentResult {
        reasoningTrace.removeAll()
        var usedTools = Set<String>()
        var executedActions = Set<String>()
        var currentContext = buildInitialContext(goal: goal, grounding: groundingContext)

        for iteration in 0 ..< maxIterations {
            try Task.checkCancellation()
            onEvent?(.thinking(iteration: iteration))
            let thought = try await generateThought(context: currentContext, iteration: iteration)

            if thought.contains("CONCLUSION:") {
                reasoningTrace.append(ReasoningStep(iteration: iteration, thought: thought))
                return concluded(extractConclusion(from: thought), usedTools, iteration + 1)
            }

            guard let action = parseAction(from: thought) else {
                reasoningTrace.append(ReasoningStep(iteration: iteration, thought: thought))
                // Small models often just answer in prose instead of emitting the
                // CONCLUSION marker. After they've had one structured chance,
                // treat substantive unstructured prose as the answer rather than
                // burning the remaining iterations re-prompting.
                if concludesOnUnstructuredThought, iteration >= 1, !thought.isEmpty {
                    return concluded(thought, usedTools, iteration + 1)
                }
                // No action — record the thought and keep reasoning.
                currentContext += "\n\nThought: \(thought)"
                continue
            }

            let observation = await observe(
                action: action,
                executedActions: &executedActions,
                usedTools: &usedTools,
                onEvent: onEvent
            )

            reasoningTrace.append(ReasoningStep(
                iteration: iteration,
                thought: thought,
                action: action.description,
                observation: observation
            ))

            currentContext += """


            Thought: \(thought)
            Action: \(action.description)
            Observation: \(observation)
            """
        }

        // Iteration cap reached — synthesise from the accumulated context.
        let finalConclusion = try await synthesizeConclusion(context: currentContext)
        return concluded(finalConclusion, usedTools, maxIterations)
    }

    private func concluded(
        _ conclusion: String, _ usedTools: Set<String>, _ iterations: Int
    ) -> AgentResult {
        AgentResult(
            conclusion: conclusion,
            toolsUsed: Array(usedTools),
            iterations: iterations,
            reasoningTrace: reasoningTrace
        )
    }

    // MARK: - Prompt construction

    private func buildInitialContext(goal: String, grounding: String?) -> String {
        let toolDescriptions = tools.values
            .map { "\($0.name): \($0.description)" }
            .sorted()
            .joined(separator: "\n")

        let groundingBlock = grounding.map { "\n\nContext:\n\($0)" } ?? ""

        return """
        You are M1K3, a local assistant. Your goal: \(goal)\(groundingBlock)

        Available Tools:
        \(toolDescriptions)

        Use ReAct reasoning:
        - Think step-by-step about what information you need.
        - To use a tool, write: "ACTION: ToolName(argument)"
        - When you have enough information, reply starting with "CONCLUSION:"

        Begin your analysis:
        """
    }

    private func generateThought(context: String, iteration: Int) async throws -> String {
        let prompt = context + "\n\nThought \(iteration + 1):"
        let response = try await inferenceProvider.generate(prompt: prompt)
        return response.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func synthesizeConclusion(context: String) async throws -> String {
        let prompt = context + "\n\n\nYou have reached the maximum iterations. "
            + "Based on the information gathered, provide your final conclusion:"
        return try await inferenceProvider.generate(prompt: prompt)
    }

    // MARK: - Action parsing

    struct Action: Equatable {
        let toolName: String
        let argument: String
        var description: String {
            "\(toolName)(\(argument))"
        }
    }

    /// Characters small models wrap tool names/arguments in: markdown bold,
    /// backticks, straight quotes — stripped so `**ACTION:** search("x")`
    /// dispatches the same as the canonical form.
    private static let decoration = CharacterSet(charactersIn: "*`\"'").union(.whitespaces)

    func parseAction(from thought: String) -> Action? {
        guard let actionRange = thought.range(of: "ACTION:\\s*", options: .regularExpression) else {
            return nil
        }
        let actionString = String(thought[actionRange.upperBound...])
        guard let openParen = actionString.firstIndex(of: "("),
              let closeParen = actionString.lastIndex(of: ")")
        else { return nil }

        let toolName = String(actionString[..<openParen])
            .trimmingCharacters(in: Self.decoration)
        let argument = String(actionString[actionString.index(after: openParen) ..< closeParen])
            .trimmingCharacters(in: Self.decoration)
        guard !toolName.isEmpty else { return nil }
        return Action(toolName: toolName, argument: argument)
    }

    /// Resolve one parsed action to its observation: repeat-guard first, then
    /// dispatch, with unknown tools steered back toward the real tool list.
    private func observe(
        action: Action,
        executedActions: inout Set<String>,
        usedTools: inout Set<String>,
        onEvent: (@Sendable (AgentLoopEvent) -> Void)?
    ) async -> String {
        if executedActions.contains(action.description) {
            // Repeat-guard: small models loop on the same call. Steer to a
            // conclusion instead of re-executing.
            return "You already ran \(action.description). "
                + "Use what you have and reply starting with \"CONCLUSION:\"."
        }
        guard let tool = tools[action.toolName] else {
            // Steer, don't just error: list what IS callable so the next
            // thought can correct itself.
            let available = tools.keys.sorted().joined(separator: ", ")
            return "Error: unknown tool '\(action.toolName)'. "
                + "Available tools: \(available.isEmpty ? "(none)" : available)."
        }
        onEvent?(.actionStarted(tool: action.toolName, argument: action.argument))
        executedActions.insert(action.description)
        do {
            let output = try await executeTool(tool, action: action)
            usedTools.insert(action.toolName)
            return output
        } catch {
            return "Error executing \(action.toolName): \(error)"
        }
    }

    private func executeTool(_ tool: any AgentTool, action: Action) async throws -> String {
        // First declared parameter receives the positional argument.
        let paramName = tool.parameters.first?.name ?? "input"
        let result = try await tool.execute(input: [paramName: action.argument])
        return result.output
    }

    func extractConclusion(from thought: String) -> String {
        guard let range = thought.range(of: "CONCLUSION:\\s*", options: .regularExpression) else {
            return thought
        }
        return String(thought[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
