//
//  IntelligenceMCPToolsTests.swift
//  M1K3MCPKitTests
//
//  ask_m1k3 + remember against fake handlers — argument validation and
//  passthrough. The live halves (HeadlessAsk, DocumentIngester) carry their
//  own tests in their packages.
//

import Foundation
@testable import M1K3MCPKit
import MCP
import Testing

private final class CallLog: @unchecked Sendable {
    private let lock = NSLock()
    private var entries: [String] = []

    func add(_ entry: String) {
        lock.lock()
        entries.append(entry)
        lock.unlock()
    }

    var all: [String] {
        lock.lock()
        defer { lock.unlock() }
        return entries
    }
}

private func makeHandlers(log: CallLog, askThrows: Bool = false) -> IntelligenceToolHandlers {
    IntelligenceToolHandlers(
        ask: { question in
            if askThrows { throw MCPVoiceError("M1K3 is in a conversation right now") }
            log.add("ask:\(question)")
            return "Grounded answer [Doc §Heading]"
        },
        remember: { title, text in
            log.add("remember:\(title):\(text.count)")
            return "Indexed “\(title)” — 2 chunks."
        }
    )
}

private func text(_ result: CallTool.Result) -> String? {
    if case let .text(text, _, _) = result.content.first { return text }
    return nil
}

/// A one-shot gate so a fake `ask` can block until the test releases it — used to
/// exercise the submit → poll → done lifecycle deterministically.
private actor Gate {
    private var continuation: CheckedContinuation<Void, Never>?
    private var opened = false

    func wait() async {
        if opened { return }
        await withCheckedContinuation { continuation = $0 }
    }

    func open() {
        opened = true
        continuation?.resume()
        continuation = nil
    }
}

struct IntelligenceMCPToolsTests {
    @Test("the surface is ask_m1k3, get_answer, and remember")
    func surface() {
        let registry = MCPToolRegistry(makeIntelligenceToolDefinitions(handlers: makeHandlers(log: CallLog())))
        #expect(registry.tools.map(\.name) == ["ask_m1k3", "get_answer", "remember"])
    }

    @Test("ask_m1k3 passes the question through and returns the answer")
    func askPassthrough() async {
        let log = CallLog()
        let registry = MCPToolRegistry(makeIntelligenceToolDefinitions(handlers: makeHandlers(log: log)))
        let result = await registry.call(name: "ask_m1k3", arguments: ["question": .string("what failed?")])
        #expect(result.isError != true)
        #expect(text(result) == "Grounded answer [Doc §Heading]")
        #expect(log.all == ["ask:what failed?"])
    }

    @Test("ask_m1k3 with a missing or blank question is an isError without invoking the brain")
    func askMissingQuestion() async {
        let log = CallLog()
        let registry = MCPToolRegistry(makeIntelligenceToolDefinitions(handlers: makeHandlers(log: log)))
        let missing = await registry.call(name: "ask_m1k3", arguments: nil)
        let blank = await registry.call(name: "ask_m1k3", arguments: ["question": .string("  ")])
        #expect(missing.isError == true)
        #expect(blank.isError == true)
        #expect(log.all.isEmpty)
    }

    @Test("a busy brain surfaces its message as isError")
    func askBusy() async {
        let registry = MCPToolRegistry(
            makeIntelligenceToolDefinitions(handlers: makeHandlers(log: CallLog(), askThrows: true))
        )
        let result = await registry.call(name: "ask_m1k3", arguments: ["question": .string("hi")])
        #expect(result.isError == true)
        #expect(text(result)?.contains("conversation") == true)
    }

    @Test("a turn that outlives the grace window hands back a job id, fast, instead of blocking")
    func askExceedingGraceReturnsJobId() async {
        // A generation slower than the grace window: ask_m1k3 must return a job
        // id promptly (non-error) rather than block until the client's deadline.
        let handlers = IntelligenceToolHandlers(
            ask: { _ in
                try await Task.sleep(for: .seconds(60))
                return "arrives after the grace window"
            },
            remember: { _, _ in "noop" }
        )
        let registry = MCPToolRegistry(
            makeIntelligenceToolDefinitions(handlers: handlers, graceSeconds: 0.2)
        )
        let start = ContinuousClock.now
        let result = await registry.call(
            name: "ask_m1k3",
            arguments: ["question": .string("why is the sky blue — explain step by step, in great detail?")]
        )
        // The job-id response is the semantic proof of non-blocking: a call
        // that blocked to completion would return the ANSWER inline, never
        // the get_answer pointer. The clock bound only guards "blocked for
        // the whole 60s generation" — kept stall-proof at half the operation,
        // because tight bounds (2s) flaked twice on stalled CI runners
        // (3.9s and 9.1s measured for a ~0.2s return).
        #expect(start.duration(to: .now) < .seconds(30))
        #expect(result.isError != true)
        #expect(text(result)?.contains("get_answer") == true)
    }

    @Test("a fast brain returns its answer inline within the grace window")
    func askFastReturnsInline() async {
        let log = CallLog()
        let registry = MCPToolRegistry(
            makeIntelligenceToolDefinitions(handlers: makeHandlers(log: log), graceSeconds: 5)
        )
        let result = await registry.call(name: "ask_m1k3", arguments: ["question": .string("what failed?")])
        #expect(result.isError != true)
        #expect(text(result) == "Grounded answer [Doc §Heading]")
        #expect(log.all == ["ask:what failed?"])
    }

    @Test("a long turn returns a job id, then get_answer fetches the result once ready")
    func submitPollLifecycle() async throws {
        let gate = Gate()
        let store = AskJobStore(makeID: { "job-x" })
        let handlers = IntelligenceToolHandlers(
            ask: { _ in
                await gate.wait()
                return "delayed answer [Doc §Heading]"
            },
            remember: { _, _ in "noop" }
        )
        let registry = MCPToolRegistry(
            makeIntelligenceToolDefinitions(handlers: handlers, jobStore: store, graceSeconds: 0.2)
        )

        // Grace expires while the generation is gated → a job id comes back.
        let submit = await registry.call(name: "ask_m1k3", arguments: ["question": .string("the slow one")])
        #expect(submit.isError != true)
        #expect(text(submit)?.contains("job-x") == true)

        // Still gated → get_answer reports it's working, not an error.
        let pending = await registry.call(name: "get_answer", arguments: ["job_id": .string("job-x")])
        #expect(pending.isError != true)
        #expect(text(pending)?.contains("still working") == true)

        // Release the generation, let the detached task write back, then fetch.
        await gate.open()
        try await Task.sleep(for: .milliseconds(150))
        let done = await registry.call(name: "get_answer", arguments: ["job_id": .string("job-x")])
        #expect(done.isError != true)
        #expect(text(done) == "delayed answer [Doc §Heading]")
    }

    @Test("get_answer for an unknown job is a clean isError")
    func getAnswerUnknownJob() async {
        let registry = MCPToolRegistry(makeIntelligenceToolDefinitions(handlers: makeHandlers(log: CallLog())))
        let result = await registry.call(name: "get_answer", arguments: ["job_id": .string("ghost")])
        #expect(result.isError == true)
        #expect(text(result)?.contains("No such job") == true)
    }

    @Test("get_answer requires a job_id")
    func getAnswerValidation() async {
        let registry = MCPToolRegistry(makeIntelligenceToolDefinitions(handlers: makeHandlers(log: CallLog())))
        let missing = await registry.call(name: "get_answer", arguments: nil)
        let blank = await registry.call(name: "get_answer", arguments: ["job_id": .string("  ")])
        #expect(missing.isError == true)
        #expect(blank.isError == true)
    }

    @Test("remember passes title and text through")
    func rememberPassthrough() async {
        let log = CallLog()
        let registry = MCPToolRegistry(makeIntelligenceToolDefinitions(handlers: makeHandlers(log: log)))
        let result = await registry.call(
            name: "remember",
            arguments: ["title": .string("Session notes"), "text": .string("The seal failed under load.")]
        )
        #expect(result.isError != true)
        #expect(text(result)?.contains("Session notes") == true)
        #expect(log.all == ["remember:Session notes:27"])
    }

    @Test("remember requires both title and text")
    func rememberValidation() async {
        let log = CallLog()
        let registry = MCPToolRegistry(makeIntelligenceToolDefinitions(handlers: makeHandlers(log: log)))
        let noTitle = await registry.call(name: "remember", arguments: ["text": .string("body")])
        let noText = await registry.call(name: "remember", arguments: ["title": .string("t")])
        #expect(noTitle.isError == true)
        #expect(noText.isError == true)
        #expect(log.all.isEmpty)
    }
}
