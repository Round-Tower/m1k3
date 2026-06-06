//
//  LocalAgentTests.swift
//  M1K3AgentTests
//
//  Contract tests for the ReAct loop driven by a scripted provider + fake
//  tools — no model. Covers: immediate conclusion, tool use then conclude,
//  unknown-tool error recovery, the max-iteration synthesis path, action
//  parsing, and the reasoning trace.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Agent
import M1K3Inference
import Testing

// MARK: - Test doubles

/// Returns a scripted sequence of responses, one per generate() call. Records
/// prompts. Thread-safe (the agent actor calls it sequentially, but generate is
/// nonisolated by protocol).
final class ScriptedProvider: InferenceProvider, @unchecked Sendable {
    let name = "scripted"
    let isAvailable = true

    private let lock = NSLock()
    private var responses: [String]
    private var index = 0
    private(set) var prompts: [String] = []

    init(_ responses: [String]) {
        self.responses = responses
    }

    func generate(prompt: String) async throws -> String {
        lock.withLock {
            prompts.append(prompt)
            defer { index += 1 }
            return index < responses.count ? responses[index] : "CONCLUSION: (fallback)"
        }
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { $0.finish() }
    }
}

struct EchoTool: AgentTool {
    let name: String
    let description: String
    let parameters = [ToolParameter(name: "query", description: "the query")]
    let response: String

    init(name: String = "search", description: String = "searches knowledge", response: String) {
        self.name = name
        self.description = description
        self.response = response
    }

    func execute(input _: [String: String]) async throws -> ToolResult {
        ToolResult(output: response)
    }
}

// MARK: - Tests

struct LocalAgentTests {
    @Test("concludes immediately when the first thought is a CONCLUSION")
    func immediateConclusion() async throws {
        let provider = ScriptedProvider(["CONCLUSION: The answer is 42."])
        let agent = LocalAgent(inferenceProvider: provider, tools: [])
        let result = try await agent.run(goal: "What is the answer?")
        #expect(result.conclusion == "The answer is 42.")
        #expect(result.iterations == 1)
        #expect(result.toolsUsed.isEmpty)
        #expect(result.reasoningTrace.count == 1)
    }

    @Test("uses a tool, then concludes from the observation")
    func toolThenConclude() async throws {
        let provider = ScriptedProvider([
            "I should look this up. ACTION: search(hydraulic seal)",
            "CONCLUSION: The seal failed under load.",
        ])
        let tool = EchoTool(response: "Found: the hydraulic seal failed under load.")
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])
        let result = try await agent.run(goal: "Why did it fail?")

        #expect(result.toolsUsed == ["search"])
        #expect(result.conclusion == "The seal failed under load.")
        #expect(result.iterations == 2)
        // Trace: step 0 has the action + observation; step 1 is the conclusion.
        #expect(result.reasoningTrace.count == 2)
        #expect(result.reasoningTrace[0].action == "search(hydraulic seal)")
        #expect(result.reasoningTrace[0].observation?.contains("failed under load") == true)
    }

    @Test("the tool receives the positional argument under the first parameter name")
    func toolArgumentPassed() async throws {
        // Tool that echoes back what it received so we can assert the wiring.
        struct EchoInputTool: AgentTool {
            let name = "echo"
            let description = "echoes input"
            let parameters = [ToolParameter(name: "query", description: "q")]
            func execute(input: [String: String]) async throws -> ToolResult {
                ToolResult(output: "got=\(input["query"] ?? "nil")")
            }
        }
        let provider = ScriptedProvider([
            "ACTION: echo(seal failure)",
            "CONCLUSION: done",
        ])
        let agent = LocalAgent(inferenceProvider: provider, tools: [EchoInputTool()])
        let result = try await agent.run(goal: "x")
        #expect(result.reasoningTrace[0].observation == "got=seal failure")
    }

    @Test("an unknown tool yields an error observation but the loop recovers")
    func unknownToolRecovers() async throws {
        let provider = ScriptedProvider([
            "ACTION: nonexistent(foo)",
            "CONCLUSION: recovered anyway.",
        ])
        let agent = LocalAgent(inferenceProvider: provider, tools: [])
        let result = try await agent.run(goal: "x")
        #expect(result.conclusion == "recovered anyway.")
        #expect(result.reasoningTrace[0].observation?.contains("Error executing nonexistent") == true)
        #expect(result.toolsUsed.isEmpty)
    }

    @Test("a thought with no action and no conclusion just continues reasoning")
    func noActionContinues() async throws {
        let provider = ScriptedProvider([
            "Hmm, let me think about this.",
            "CONCLUSION: thought it through.",
        ])
        let agent = LocalAgent(inferenceProvider: provider, tools: [])
        let result = try await agent.run(goal: "x")
        #expect(result.iterations == 2)
        #expect(result.conclusion == "thought it through.")
    }

    @Test("hitting the iteration cap synthesises a final conclusion")
    func maxIterationsSynthesises() async throws {
        // Never concludes within the cap; the (maxIter+1)th generate is the
        // synthesis call returning the final answer.
        let provider = ScriptedProvider([
            "still thinking 1",
            "still thinking 2",
            "SYNTHESISED FINAL ANSWER",
        ])
        let agent = LocalAgent(inferenceProvider: provider, tools: [], maxIterations: 2)
        let result = try await agent.run(goal: "x")
        #expect(result.iterations == 2)
        #expect(result.conclusion == "SYNTHESISED FINAL ANSWER")
        // 2 loop thoughts + 1 synthesis = 3 generate calls.
        #expect(provider.prompts.count == 3)
    }

    @Test("the goal and tool list appear in the first prompt")
    func goalInPrompt() async throws {
        let provider = ScriptedProvider(["CONCLUSION: ok"])
        let tool = EchoTool(name: "search", description: "searches knowledge", response: "x")
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])
        _ = try await agent.run(goal: "Find the leak", context: "grounding facts here")
        let first = try #require(provider.prompts.first)
        #expect(first.contains("Find the leak"))
        #expect(first.contains("search: searches knowledge"))
        #expect(first.contains("grounding facts here"))
    }
}

// MARK: - Action parsing (white-box)

struct LocalAgentParseTests {
    private func agent() -> LocalAgent {
        LocalAgent(inferenceProvider: ScriptedProvider([]), tools: [])
    }

    @Test("parses ACTION: Tool(arg)")
    func parsesAction() async {
        let a = await agent().parseAction(from: "I will ACTION: search(hydraulic seal) now")
        #expect(a?.toolName == "search")
        #expect(a?.argument == "hydraulic seal")
        #expect(a?.description == "search(hydraulic seal)")
    }

    @Test("returns nil when there is no ACTION")
    func noAction() async {
        #expect(await agent().parseAction(from: "just a thought") == nil)
    }

    @Test("extractConclusion strips the marker")
    func extractsConclusion() async {
        let c = await agent().extractConclusion(from: "CONCLUSION:  the result")
        #expect(c == "the result")
    }
}
