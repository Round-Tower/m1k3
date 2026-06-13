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

struct IntelligenceMCPToolsTests {
    @Test("the surface is ask_m1k3 and remember")
    func surface() {
        let registry = MCPToolRegistry(makeIntelligenceToolDefinitions(handlers: makeHandlers(log: CallLog())))
        #expect(registry.tools.map(\.name) == ["ask_m1k3", "remember"])
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
