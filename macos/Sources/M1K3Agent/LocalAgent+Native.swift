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
import Synchronization

extension LocalAgent {
    /// Structured loop for a tool-calling provider. One `ToolTurnSession` per
    /// turn: the session retains the conversation (for MLX, a live KV cache —
    /// iteration ≥2 prefills only the new tool results, not the whole
    /// transcript), so the agent sends only the DELTA messages each iteration.
    /// The think phase of every generation streams live through
    /// `onReasoningToken` (it renders in the reasoning disclosure); post-think
    /// text is gated until the turn's outcome is known.
    func runNative(
        provider: any ToolCallingProvider,
        goal: String,
        grounding: String?,
        onEvent: (@Sendable (AgentLoopEvent) -> Void)?,
        onConclusionToken: (@Sendable (String) -> Void)?,
        onReasoningToken: (@Sendable (String) -> Void)?
    ) async throws -> AgentResult {
        let toolDefinitions = tools.values.map(\.toolDefinition)
        let session = try await provider.makeToolTurnSession(tools: toolDefinitions)
        do {
            let result = try await runNativeLoop(
                session: session,
                goal: goal,
                grounding: grounding,
                onEvent: onEvent,
                onConclusionToken: onConclusionToken,
                onReasoningToken: onReasoningToken
            )
            await session.finish()
            return result
        } catch {
            await session.finish()
            throw error
        }
    }

    private func runNativeLoop(
        session: any ToolTurnSession,
        goal: String,
        grounding: String?,
        onEvent: (@Sendable (AgentLoopEvent) -> Void)?,
        onConclusionToken: (@Sendable (String) -> Void)?,
        onReasoningToken: (@Sendable (String) -> Void)?
    ) async throws -> AgentResult {
        var usedTools = Set<String>()
        var executedActions = Set<String>()
        // The trace/rescue record — the SESSION owns the model-side state, so
        // this array is never re-sent; only the per-iteration delta is.
        var transcript: [ToolMessage] = []
        // Persona first (the chat template's system turn), then the goal —
        // identity is standing, the goal is this turn's.
        var pendingMessages: [ToolMessage] = [
            .system(M1K3Persona.systemPrompt),
            .user(Self.buildNativeGoal(goal: goal, grounding: grounding)),
        ]

        logRunStart(goal: goal, grounding: grounding)

        for iteration in 0 ..< maxIterations {
            try Task.checkCancellation()
            onEvent?(.thinking(iteration: iteration))
            transcript.append(contentsOf: pendingMessages)
            let (turn, remainder) = try await sendThroughGate(
                session: session,
                messages: pendingMessages,
                onReasoningToken: onReasoningToken
            )
            pendingMessages = []

            switch turn {
            case let .text(answer):
                if let onConclusionToken, !remainder.isEmpty { onConclusionToken(remainder) }
                reasoningTrace.append(ReasoningStep(iteration: iteration, thought: answer))
                // A pure-think turn (nothing after the reasoning) concludes
                // empty, so the caller's fallback synthesis still produces a
                // real answer instead of re-showing the chain-of-thought.
                return concluded(remainder.isEmpty ? "" : answer, usedTools, iteration + 1)

            case let .toolCalls(calls) where calls.isEmpty:
                // Neither text nor calls: steer instead of regenerating over an
                // unchanged conversation (an empty delta has nothing to render).
                pendingMessages = [.user("Reply with your final answer, or call one of the tools.")]
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
                    pendingMessages.append(.toolResult(name: parsedCall.name, output: observation))
                }
            }
        }

        // Iteration cap reached — ask for a plain answer over what was gathered.
        logCapReached()
        let finalAnswer = try await synthesizeNativeConclusion(
            session: session,
            transcript: transcript,
            onConclusionToken: onConclusionToken,
            onReasoningToken: onReasoningToken
        )
        return concluded(finalAnswer, usedTools, maxIterations)
    }

    /// One session send with the think-gate applied: think-phase tokens stream
    /// live to `onReasoningToken`; the post-think remainder is returned for the
    /// caller to flush (`.text`) or drop (`.toolCalls`).
    private func sendThroughGate(
        session: any ToolTurnSession,
        messages: [ToolMessage],
        onReasoningToken: (@Sendable (String) -> Void)?
    ) async throws -> (turn: ToolTurn, remainder: String) {
        let gate = Mutex(ThinkStreamGate())
        let turn = try await session.send(messages) { token in
            let live = gate.withLock { $0.feed(token) }
            if !live.isEmpty { onReasoningToken?(live) }
        }
        let remainder = gate.withLock { $0.flushRemainder() }
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (turn, remainder)
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

    /// Final answer when the loop hits its cap: instruct the model to answer in
    /// prose over the same session (the tools stay rendered in its context —
    /// the instruction, not withdrawal, does the steering now); if it calls
    /// anyway or only thinks, fall back to the gathered observations so
    /// evidence is never discarded.
    private func synthesizeNativeConclusion(
        session: any ToolTurnSession,
        transcript: [ToolMessage],
        onConclusionToken: (@Sendable (String) -> Void)?,
        onReasoningToken: (@Sendable (String) -> Void)?
    ) async throws -> String {
        let instruction = ToolMessage.user(
            "You have reached the maximum number of steps. Based on the information "
                + "gathered, answer the user directly in plain language. Do not call any more tools."
        )
        let (turn, remainder) = try await sendThroughGate(
            session: session,
            messages: [instruction],
            onReasoningToken: onReasoningToken
        )
        switch turn {
        case let .text(answer) where !remainder.isEmpty:
            onConclusionToken?(remainder)
            return answer
        case .text, .toolCalls:
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
    /// scaffolding — tools are supplied structurally, identity lives in the
    /// system turn (M1K3Persona), so this carries only the turn's task.
    static func buildNativeGoal(goal: String, grounding: String?) -> String {
        let groundingBlock = grounding.map { "\n\nContext:\n\($0)" } ?? ""
        return """
        Use the available tools when they help answer the user's request. When \
        you have enough information, reply with your final answer in plain language.

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
