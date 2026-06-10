//
//  LocalAgent+Native.swift
//  M1K3Agent
//
//  The NATIVE tool-calling loop (Phase 12a) — the path taken when the active
//  provider conforms to `ToolCallingProvider` and its model can emit parseable
//  calls. The model speaks structured `ToolTurn`s; everything dialect-
//  independent — dispatch, repeat-guard, unknown-tool steering, the iteration
//  cap, the reasoning trace, activity events — is SHARED with the ReAct floor
//  (in LocalAgent.swift), so no model is ever locked out and the two paths can't
//  drift apart.
//
//  The agent owns the conversation as a typed `[ToolMessage]` transcript (NOT a
//  concatenated prompt string): native models render tool RESULTS into role-
//  tagged turns they were trained on, so threading results as prose would be
//  off-distribution. This array maps straight onto mlx-swift-lm's
//  `UserInput(chat:)` in the MLX adapter (Phase 12c) — no seam churn.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.85, Prior: Unknown
//  Context: pressure-tested by the challenger — typed transcript + typed args +
//  runtime capability flag adopted over a stateless prompt-string seam.

import Foundation
import M1K3Inference

extension LocalAgent {
    /// Structured loop for a tool-calling provider. Hands the provider the
    /// transcript + tool list each turn; executes any returned calls; threads
    /// the model's call and each result back as role-tagged messages.
    func runNative(
        provider: any ToolCallingProvider,
        goal: String,
        grounding: String?,
        onEvent: (@Sendable (AgentLoopEvent) -> Void)?,
        onConclusionToken: (@Sendable (String) -> Void)?
    ) async throws -> AgentResult {
        var usedTools = Set<String>()
        var executedActions = Set<String>()
        let toolDefinitions = tools.values.map(\.toolDefinition)
        var transcript: [ToolMessage] = [.user(Self.buildNativeGoal(goal: goal, grounding: grounding))]

        logRunStart(goal: goal, grounding: grounding)

        for iteration in 0 ..< maxIterations {
            try Task.checkCancellation()
            onEvent?(.thinking(iteration: iteration))
            let turn = try await provider.continueToolTurn(messages: transcript, tools: toolDefinitions)

            switch turn {
            case let .text(answer):
                if let onConclusionToken, !answer.isEmpty { onConclusionToken(answer) }
                reasoningTrace.append(ReasoningStep(iteration: iteration, thought: answer))
                return concluded(answer, usedTools, iteration + 1)

            case let .toolCalls(calls) where calls.isEmpty:
                // A turn with neither text nor calls: record it and move on
                // rather than re-sending an identical transcript forever.
                transcript.append(.assistant(text: nil, toolCalls: []))

            case let .toolCalls(calls):
                transcript.append(.assistant(text: nil, toolCalls: calls))
                for parsedCall in calls {
                    let observation = await observeNative(
                        parsedCall: parsedCall,
                        iteration: iteration,
                        executedActions: &executedActions,
                        usedTools: &usedTools,
                        onEvent: onEvent
                    )
                    reasoningTrace.append(ReasoningStep(
                        iteration: iteration,
                        thought: "",
                        action: Self.nativeDescription(parsedCall),
                        observation: observation
                    ))
                    transcript.append(.toolResult(name: parsedCall.name, output: observation))
                }
            }
        }

        // Iteration cap reached — withdraw the tools and ask for a plain answer.
        logCapReached()
        let finalAnswer = try await synthesizeNativeConclusion(provider: provider, transcript: transcript)
        return concluded(finalAnswer, usedTools, maxIterations)
    }

    /// Execute one structured tool call through the shared dispatch core,
    /// flattening the typed arguments to the tool's `[String: String]` contract.
    private func observeNative(
        parsedCall: ParsedToolCall,
        iteration: Int,
        executedActions: inout Set<String>,
        usedTools: inout Set<String>,
        onEvent: (@Sendable (AgentLoopEvent) -> Void)?
    ) async -> String {
        let start = ContinuousClock.now
        let display = Self.nativeDescription(parsedCall)
        let observation = await dispatchCall(
            ToolCallSite(
                toolName: parsedCall.name,
                displayDescription: display,
                eventArgument: parsedCall.stringArguments.values.first ?? ""
            ),
            executedActions: &executedActions,
            usedTools: &usedTools,
            onEvent: onEvent
        ) { tool in
            try await tool.execute(input: parsedCall.stringArguments).output
        }
        logObservation(observation, callDescription: display, iteration: iteration, start: start)
        return observation
    }

    /// Final answer when the loop hits its cap: re-ask with the tools withdrawn
    /// so the model must answer in prose; if it stubbornly calls anyway, fall
    /// back to the gathered observations (so evidence is never discarded).
    private func synthesizeNativeConclusion(
        provider: any ToolCallingProvider,
        transcript: [ToolMessage]
    ) async throws -> String {
        var messages = transcript
        messages.append(.user(
            "You have reached the maximum number of steps. Based on the information "
                + "gathered, answer the user directly in plain language. Do not call any more tools."
        ))
        let turn = try await provider.continueToolTurn(messages: messages, tools: [])
        switch turn {
        case let .text(answer):
            return answer
        case .toolCalls:
            return Self.gatheredObservations(from: transcript)
        }
    }

    /// A stable, human-readable rendering of a structured call for the trace and
    /// the repeat-guard key: `name(k=v, k=v)` with keys sorted for determinism.
    static func nativeDescription(_ call: ParsedToolCall) -> String {
        let arguments = call.stringArguments
            .sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: ", ")
        return "\(call.name)(\(arguments))"
    }

    /// The opening user turn: goal + optional grounding, with NO ReAct
    /// scaffolding — tools are supplied structurally, not described in prose.
    static func buildNativeGoal(goal: String, grounding: String?) -> String {
        let groundingBlock = grounding.map { "\n\nContext:\n\($0)" } ?? ""
        return """
        You are M1K3, a local assistant. Use the available tools when they help \
        answer the user's request. When you have enough information, reply with \
        your final answer in plain language.

        Goal: \(goal)\(groundingBlock)
        """
    }

    /// Join the tool observations gathered so far — the evidence-rescue fallback
    /// for a cap synthesis where the model refused to stop calling tools.
    static func gatheredObservations(from transcript: [ToolMessage]) -> String {
        let facts = transcript.compactMap { message -> String? in
            if case let .toolResult(_, output) = message { return output }
            return nil
        }
        return facts.isEmpty
            ? "I gathered some information but couldn't form a final answer."
            : facts.joined(separator: "\n")
    }
}
