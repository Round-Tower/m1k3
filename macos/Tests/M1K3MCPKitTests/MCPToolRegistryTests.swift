//
//  MCPToolRegistryTests.swift
//  M1K3MCPKitTests
//
//  The injectable tool registry behind every transport (stdio exec, in-app
//  HTTP). Dispatch, unknown-tool, and error-to-isError behaviour pinned here;
//  the transports stay glue.
//

import Foundation
@testable import M1K3MCPKit
import MCP
import Testing

private struct FakeToolError: Error, CustomStringConvertible {
    let description = "the seal failed"
}

private func makeRegistry(
    logSink: (any MCPCallLogSink)? = nil,
    recorder: @escaping @Sendable ([String: Value]?) -> Void = { _ in }
) -> MCPToolRegistry {
    MCPToolRegistry([
        MCPToolDefinition(
            tool: Tool(name: "alpha", description: "first", inputSchema: ["type": "object"]),
            handler: { args in
                recorder(args)
                return "alpha says hi"
            }
        ),
        MCPToolDefinition(
            tool: Tool(name: "beta", description: "second", inputSchema: ["type": "object"]),
            handler: { _ in throw FakeToolError() }
        ),
    ], logSink: logSink)
}

/// Test sink that captures every recorded entry.
private final class CollectingLogSink: MCPCallLogSink, @unchecked Sendable {
    private let box = Locked<[MCPCallLogEntry]>([])
    var entries: [MCPCallLogEntry] {
        box.get()
    }

    func record(_ entry: MCPCallLogEntry) {
        box.set(box.get() + [entry])
    }
}

struct MCPToolRegistryTests {
    @Test("tools lists definitions in registration order")
    func toolOrder() {
        let registry = makeRegistry()
        #expect(registry.tools.map(\.name) == ["alpha", "beta"])
    }

    @Test("call dispatches by name and returns the handler text")
    func dispatch() async {
        let registry = makeRegistry()
        let result = await registry.call(name: "alpha", arguments: ["x": .int(1)])
        #expect(result.isError != true)
        #expect(resultText(result) == "alpha says hi")
    }

    @Test("arguments reach the handler verbatim")
    func argumentsPassThrough() async {
        let received = Locked<[String: Value]?>(nil)
        let registry = makeRegistry { received.set($0) }
        _ = await registry.call(name: "alpha", arguments: ["query": .string("seals")])
        #expect(received.get()?["query"] == .string("seals"))
    }

    @Test("unknown tool name is an isError result, not a throw")
    func unknownTool() async {
        let registry = makeRegistry()
        let result = await registry.call(name: "gamma", arguments: nil)
        #expect(result.isError == true)
        #expect(resultText(result)?.contains("Unknown tool") == true)
        #expect(resultText(result)?.contains("gamma") == true)
    }

    @Test("a throwing handler becomes an isError result carrying the message")
    func handlerThrow() async {
        let registry = makeRegistry()
        let result = await registry.call(name: "beta", arguments: nil)
        #expect(result.isError == true)
        #expect(resultText(result)?.contains("the seal failed") == true)
    }

    @Test("a successful call is recorded to the log sink with args and response")
    func recordsSuccess() async {
        let sink = CollectingLogSink()
        let registry = makeRegistry(logSink: sink)
        _ = await registry.call(name: "alpha", arguments: ["query": .string("seals")])
        #expect(sink.entries.count == 1)
        let entry = sink.entries.first
        #expect(entry?.tool == "alpha")
        #expect(entry?.arguments?["query"] == .string("seals"))
        #expect(entry?.responseText == "alpha says hi")
        #expect(entry?.isError == false)
    }

    @Test("a throwing call is still recorded, marked isError")
    func recordsError() async {
        let sink = CollectingLogSink()
        let registry = makeRegistry(logSink: sink)
        _ = await registry.call(name: "beta", arguments: nil)
        #expect(sink.entries.count == 1)
        #expect(sink.entries.first?.tool == "beta")
        #expect(sink.entries.first?.isError == true)
    }

    @Test("an unknown tool is recorded, marked isError")
    func recordsUnknown() async {
        let sink = CollectingLogSink()
        let registry = makeRegistry(logSink: sink)
        _ = await registry.call(name: "gamma", arguments: nil)
        #expect(sink.entries.count == 1)
        #expect(sink.entries.first?.tool == "gamma")
        #expect(sink.entries.first?.isError == true)
    }

    @Test("with no sink, nothing is recorded — today's behaviour is unchanged")
    func noSinkNoRecord() async {
        // A registry built the old way (no logSink) must dispatch exactly as before.
        let registry = makeRegistry()
        let result = await registry.call(name: "alpha", arguments: nil)
        #expect(resultText(result) == "alpha says hi")
    }

    @Test("argument helpers extract typed values")
    func argHelpers() {
        let args: [String: Value] = ["s": .string("x"), "i": .int(7), "d": .double(1.5)]
        #expect(stringArg(args, "s") == "x")
        #expect(intArg(args, "i") == 7)
        #expect(doubleArg(args, "d") == 1.5)
        // int also satisfies a double ask (JSON clients send 30 for 30.0)
        #expect(doubleArg(["d": .int(3)], "d") == 3.0)
        #expect(stringArg(args, "missing") == nil)
        #expect(stringArg(nil, "s") == nil)
    }
}

// MARK: - Helpers

private func resultText(_ result: CallTool.Result) -> String? {
    if case let .text(text, _, _) = result.content.first { return text }
    return nil
}

/// Tiny test-only box: swift-testing closures must be Sendable.
private final class Locked<T>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: T

    init(_ value: T) {
        self.value = value
    }

    func set(_ new: T) {
        lock.lock()
        value = new
        lock.unlock()
    }

    func get() -> T {
        lock.lock()
        defer { lock.unlock() }
        return value
    }
}
