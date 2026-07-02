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

struct ThrowingTool: AgentTool {
    struct Boom: Error {}
    let name = "boom"
    let description = "always fails"
    let parameters = [ToolParameter(name: "query", description: "the query")]

    func execute(input _: [String: String]) async throws -> ToolResult {
        throw Boom()
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

    @Test("a tool that THROWS still counts as used — downstream error-filtering depends on it")
    func throwingToolCountsAsUsed() async throws {
        // A dispatched-but-throwing tool must land in toolsUsed exactly like a
        // tool that RETURNS an error string: the responder suppresses the loose
        // in-loop conclusion the moment a tool dispatches (.actionStarted), and
        // routes through synthesis only when result.toolsUsed is non-empty. If
        // the two diverge on a throw, the model's raw post-error conclusion
        // surfaces — the unreliable output the suppression exists to stop.
        let provider = ScriptedProvider([
            "I should look this up. ACTION: boom(anything)",
            "CONCLUSION: Could not retrieve it.",
        ])
        let agent = LocalAgent(inferenceProvider: provider, tools: [ThrowingTool()])
        let result = try await agent.run(goal: "Will it fail?")

        #expect(result.toolsUsed == ["boom"])
        // The error still reaches the loop as an observation it can adapt to.
        #expect(result.reasoningTrace[0].observation?.contains("Error executing boom") == true)
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

    @Test("an unknown tool yields a steering observation listing the real tools")
    func unknownToolRecovers() async throws {
        let provider = ScriptedProvider([
            "ACTION: nonexistent(foo)",
            "CONCLUSION: recovered anyway.",
        ])
        let tool = EchoTool(name: "search", description: "searches", response: "x")
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])
        let result = try await agent.run(goal: "x")
        #expect(result.conclusion == "recovered anyway.")
        let observation = try #require(result.reasoningTrace[0].observation)
        #expect(observation.contains("unknown tool 'nonexistent'"))
        #expect(observation.contains("search"))
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

    @Test("a markerless thought earns a format reminder in the next prompt (the Gemma miss)")
    func remindsFormatAfterProse() async throws {
        // Seen live on Big/Gemma: 'I need to search the web' in plain prose,
        // no ACTION ever emitted. The re-prompt must teach the format.
        let provider = ScriptedProvider([
            "I need to search the web to get the current weather forecast.",
            "ACTION: search(boston weather)",
            "CONCLUSION: done.",
        ])
        let tool = EchoTool(response: "found")
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])
        let result = try await agent.run(goal: "weather?")

        #expect(result.conclusion == "done.")
        #expect(result.toolsUsed == ["search"])
        let secondPrompt = try #require(provider.prompts.dropFirst().first)
        #expect(secondPrompt.contains("ACTION: tool_name(argument)"))
        #expect(secondPrompt.contains("CONCLUSION:"))
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

    @Test("onEvent reports thinking and tool use in order")
    func eventsInOrder() async throws {
        let provider = ScriptedProvider([
            "ACTION: search(seals)",
            "CONCLUSION: done",
        ])
        let tool = EchoTool(response: "found it")
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])

        let recorder = EventRecorder()
        _ = try await agent.run(goal: "x") { event in
            recorder.record(event)
        }
        #expect(recorder.events == [
            .thinking(iteration: 0),
            .actionStarted(tool: "search", argument: "seals"),
            .thinking(iteration: 1),
        ])
    }

    @Test("a repeated identical action is not re-executed, the model is steered to conclude")
    func repeatGuard() async throws {
        let provider = ScriptedProvider([
            "ACTION: count(same)",
            "ACTION: count(same)",
            "CONCLUSION: stopped repeating.",
        ])
        let tool = CountingTool()
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])
        let result = try await agent.run(goal: "x")

        #expect(result.conclusion == "stopped repeating.")
        #expect(tool.executions == 1)
        let secondObservation = try #require(result.reasoningTrace[1].observation)
        #expect(secondObservation.contains("already ran count(same)"))
        #expect(secondObservation.contains("CONCLUSION"))
    }

    @Test("with concludesOnUnstructuredThought, later prose becomes the conclusion")
    func implicitConclusion() async throws {
        let provider = ScriptedProvider([
            "Let me think about what I know here.",
            "The answer is plainly forty-two, based on the context.",
            "SHOULD NEVER BE REACHED",
        ])
        let agent = LocalAgent(
            inferenceProvider: provider, tools: [],
            concludesOnUnstructuredThought: true
        )
        let result = try await agent.run(goal: "x")
        #expect(result.conclusion == "The answer is plainly forty-two, based on the context.")
        #expect(result.iterations == 2)
        #expect(provider.prompts.count == 2)
    }

    @Test("a conclusion with a trailing ACTION line is stripped before it reaches the user")
    func conclusionStripsScaffolding() async throws {
        let provider = ScriptedProvider([
            "CONCLUSION: The answer is 42.\nACTION: search(more things)",
        ])
        let agent = LocalAgent(inferenceProvider: provider, tools: [])
        let result = try await agent.run(goal: "x")
        #expect(result.conclusion == "The answer is 42.")
    }

    @Test("the iteration-cap synthesis never leaks ACTION lines (the ⌘R weather bug)")
    func synthesisStripsScaffolding() async throws {
        let provider = ScriptedProvider([
            "still thinking 1",
            "still thinking 2",
            "I am unable to find a forecast.\nACTION: search_knowledge(weather boston)",
        ])
        let agent = LocalAgent(inferenceProvider: provider, tools: [], maxIterations: 2)
        let result = try await agent.run(goal: "weather?")
        #expect(result.conclusion == "I am unable to find a forecast.")
        #expect(!result.conclusion.contains("ACTION"))
    }

    @Test("implicit prose conclusions are also stripped of malformed scaffolding")
    func implicitConclusionStripsScaffolding() async throws {
        let provider = ScriptedProvider([
            "Let me think about this question.",
            "The seal failed under load.\n**ACTION:** search_knowledge", // malformed: no parens
        ])
        let agent = LocalAgent(
            inferenceProvider: provider, tools: [],
            concludesOnUnstructuredThought: true
        )
        let result = try await agent.run(goal: "x")
        #expect(result.conclusion == "The seal failed under load.")
    }

    @Test("a leaked <function_call> block is stripped from the conclusion (dense Qwen3, ⌘R 2026-06-23)")
    func stripsLeakedFunctionCallBlock() {
        // Seen live on lil (Qwen3-4B): the model answered from context, then
        // tacked on a SPURIOUS <function_call> it never executed — leaking the raw
        // JSON into the visible answer because no dialect parser consumed it.
        let raw = """
        Today's date is Tuesday, 23 June 2026.

        <function_call>
        {"name": "datetime", "arguments": {}}
        </function_call>
        """
        #expect(LocalAgent.stripScaffolding(raw) == "Today's date is Tuesday, 23 June 2026.")
        #expect(!LocalAgent.stripScaffolding(raw).contains("function_call"))
    }

    @Test("leaked <tool_call> and Gemma <start_function_call> blocks are stripped too (cross-dialect)")
    func stripsOtherDialectToolCallBlocks() {
        #expect(LocalAgent.stripScaffolding("A.\n<tool_call>{\"name\":\"s\"}</tool_call>") == "A.")
        #expect(
            LocalAgent.stripScaffolding("B.\n<start_function_call>call:s{}<end_function_call>") == "B."
        )
        // Gemma 4 dialect (<|tool_call>…<tool_call|>) — leaks now that gemma-4 routes
        // native (PR #98). The pipe-delimited tags are DISTINCT from <tool_call>, so
        // the existing patterns don't catch them (review #98, claude-review-mac).
        #expect(
            LocalAgent.stripScaffolding("C.\n<|tool_call>call:s{n:<|\"|>x<|\"|>}<tool_call|>") == "C."
        )
    }

    @Test("prose that merely MENTIONS a tool-call word (no tags) is preserved — no false positives")
    func toolCallProseIsPreserved() {
        let prose = "You can use a function_call or a tool_call to invoke tools."
        #expect(LocalAgent.stripScaffolding(prose) == prose)
    }

    @Test("'CONCLUSION: ACTION: …' is an action in a trench coat, not a conclusion")
    func degenerateConclusionTreatedAsAction() async throws {
        // Seen live (Boston weather): the model wrapped a repeat ACTION in the
        // CONCLUSION marker, which stripped to an empty answer. The loop must
        // fall through to the action path (repeat-guard steers) and let the
        // model conclude properly next iteration.
        let provider = ScriptedProvider([
            "ACTION: search(weather boston)",
            "CONCLUSION: ACTION: search(weather boston)",
            "CONCLUSION: Sunny, 25 degrees all week.",
        ])
        let tool = EchoTool(response: "Sunny 25C for 10 days")
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])
        let result = try await agent.run(goal: "weather?")

        #expect(result.conclusion == "Sunny, 25 degrees all week.")
        #expect(result.toolsUsed == ["search"])
        #expect(result.iterations == 3)
        // The degenerate thought hit the repeat-guard, not a second execution.
        let guardObservation = try #require(result.reasoningTrace[1].observation)
        #expect(guardObservation.contains("already ran"))
    }

    @Test("cancellation stops the loop between iterations")
    func cancellationStopsLoop() async {
        let provider = SleepyProvider()
        let agent = LocalAgent(inferenceProvider: provider, tools: [])
        let task = Task {
            try await agent.run(goal: "x")
        }
        task.cancel()
        await #expect(throws: CancellationError.self) {
            try await task.value
        }
    }
}

/// Records AgentLoopEvents synchronously (the loop emits them in order).
private final class EventRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: [AgentLoopEvent] = []

    func record(_ event: AgentLoopEvent) {
        lock.withLock { stored.append(event) }
    }

    var events: [AgentLoopEvent] {
        lock.withLock { stored }
    }
}

/// Counts executions so the repeat-guard can prove the second call never ran.
private final class CountingTool: AgentTool, @unchecked Sendable {
    let name = "count"
    let description = "counts calls"
    let parameters = [ToolParameter(name: "query", description: "q")]

    private let lock = NSLock()
    private var callCount = 0

    var executions: Int {
        lock.withLock { callCount }
    }

    func execute(input _: [String: String]) async throws -> ToolResult {
        lock.withLock { callCount += 1 }
        return ToolResult(output: "counted")
    }
}

/// Never concludes; sleeps so a cancel always lands before the iteration cap.
/// The sleep RE-THROWS on cancellation — an honest fake: a mid-generation
/// cancel must propagate, not return "still thinking" (try? would let a
/// non-cooperative loop pass this suite's cancellation test).
private final class SleepyProvider: InferenceProvider, @unchecked Sendable {
    let name = "sleepy"
    let isAvailable = true

    func generate(prompt _: String) async throws -> String {
        try await Task.sleep(for: .milliseconds(20))
        return "still thinking"
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { $0.finish() }
    }
}

// MARK: - Action parsing (white-box)

struct LocalAgentParseTests {
    private func agent() -> LocalAgent {
        LocalAgent(inferenceProvider: ScriptedProvider([]), tools: [])
    }

    @Test("parses underscored tool names embedded in prose")
    func parsesUnderscoredTool() async {
        let action = await agent().parseAction(
            from: "I should check the notes. ACTION: search_knowledge(hydraulic seal)"
        )
        #expect(action?.toolName == "search_knowledge")
        #expect(action?.argument == "hydraulic seal")
    }

    @Test("parses ACTION: Tool(arg)")
    func parsesAction() async {
        let action = await agent().parseAction(from: "I will ACTION: search(hydraulic seal) now")
        #expect(action?.toolName == "search")
        #expect(action?.argument == "hydraulic seal")
        #expect(action?.description == "search(hydraulic seal)")
    }

    @Test("returns nil when there is no ACTION")
    func noAction() async {
        #expect(await agent().parseAction(from: "just a thought") == nil)
    }

    @Test("strips quotes and backticks small models wrap arguments in")
    func stripsArgumentWrapping() async {
        let quoted = await agent().parseAction(from: "ACTION: search(\"hydraulic seal\")")
        #expect(quoted?.argument == "hydraulic seal")
        let single = await agent().parseAction(from: "ACTION: search('seal')")
        #expect(single?.argument == "seal")
        let ticked = await agent().parseAction(from: "ACTION: search(`seal`)")
        #expect(ticked?.argument == "seal")
    }

    @Test("tolerates markdown around the marker and tool name")
    func toleratesMarkdown() async {
        let bold = await agent().parseAction(from: "**ACTION:** search(seal)")
        #expect(bold?.toolName == "search")
        #expect(bold?.argument == "seal")
        let ticked = await agent().parseAction(from: "ACTION: `web_search`(latest news)")
        #expect(ticked?.toolName == "web_search")
    }

    @Test("extractConclusion strips the marker")
    func extractsConclusion() async {
        let conclusion = await agent().extractConclusion(from: "CONCLUSION:  the result")
        #expect(conclusion == "the result")
    }
}
