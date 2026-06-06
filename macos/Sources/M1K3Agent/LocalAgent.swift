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

public actor LocalAgent {
    private let inferenceProvider: any InferenceProvider
    private let tools: [String: AgentTool]
    private let maxIterations: Int

    public private(set) var reasoningTrace: [ReasoningStep] = []

    public init(
        inferenceProvider: any InferenceProvider,
        tools: [any AgentTool],
        maxIterations: Int = 5
    ) {
        self.inferenceProvider = inferenceProvider
        self.tools = Dictionary(tools.map { ($0.name, $0) }, uniquingKeysWith: { first, _ in first })
        self.maxIterations = maxIterations
    }

    /// Run the ReAct loop toward `goal`, optionally grounded in `context`
    /// (e.g. retrieved knowledge). Terminates on a `CONCLUSION:` thought or,
    /// at the iteration cap, by synthesising from what was gathered.
    public func run(goal: String, context groundingContext: String? = nil) async throws -> AgentResult {
        reasoningTrace.removeAll()
        var usedTools = Set<String>()
        var currentContext = buildInitialContext(goal: goal, grounding: groundingContext)

        for iteration in 0 ..< maxIterations {
            let thought = try await generateThought(context: currentContext, iteration: iteration)

            if thought.contains("CONCLUSION:") {
                reasoningTrace.append(ReasoningStep(iteration: iteration, thought: thought))
                return AgentResult(
                    conclusion: extractConclusion(from: thought),
                    toolsUsed: Array(usedTools),
                    iterations: iteration + 1,
                    reasoningTrace: reasoningTrace
                )
            }

            guard let action = parseAction(from: thought) else {
                // No action — record the thought and keep reasoning.
                reasoningTrace.append(ReasoningStep(iteration: iteration, thought: thought))
                currentContext += "\n\nThought: \(thought)"
                continue
            }

            let observation: String
            do {
                observation = try await executeTool(action: action)
                usedTools.insert(action.toolName)
            } catch {
                observation = "Error executing \(action.toolName): \(error)"
            }

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
        return AgentResult(
            conclusion: finalConclusion,
            toolsUsed: Array(usedTools),
            iterations: maxIterations,
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

    func parseAction(from thought: String) -> Action? {
        guard let actionRange = thought.range(of: "ACTION:\\s*", options: .regularExpression) else {
            return nil
        }
        let actionString = String(thought[actionRange.upperBound...])
        guard let openParen = actionString.firstIndex(of: "("),
              let closeParen = actionString.lastIndex(of: ")")
        else { return nil }

        let toolName = String(actionString[..<openParen]).trimmingCharacters(in: .whitespaces)
        let argument = String(actionString[actionString.index(after: openParen) ..< closeParen])
            .trimmingCharacters(in: .whitespaces)
        guard !toolName.isEmpty else { return nil }
        return Action(toolName: toolName, argument: argument)
    }

    private func executeTool(action: Action) async throws -> String {
        guard let tool = tools[action.toolName] else {
            throw AgentError.toolNotFound(action.toolName)
        }
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
