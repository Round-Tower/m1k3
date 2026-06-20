//
//  OpenLinkMCPToolsTests.swift
//  M1K3MCPKitTests
//
//  The open_link tool definition against a fake handler — surface, argument
//  validation, passthrough, and error surfacing. The real handler lives in the
//  app (it drives the review panel on the MainActor); this test owns the contract
//  between the MCP server and a visiting agent.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3MCPKit
import MCP
import Testing

private final class HandlerLog: @unchecked Sendable {
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

private func makeHandlers(log: HandlerLog, throwsError: Bool = false) -> OpenLinkToolHandlers {
    OpenLinkToolHandlers(
        open: { url in
            if throwsError { throw MCPVoiceError("\"\(url)\" isn't a web link M1K3 can open.") }
            log.add("open:\(url)")
            return "Opened \(url) in M1K3's review panel."
        }
    )
}

private func text(_ result: CallTool.Result) -> String? {
    if case let .text(text, _, _) = result.content.first { return text }
    return nil
}

struct OpenLinkMCPToolsTests {
    @Test("the surface is a single open_link tool")
    func surface() {
        let registry = MCPToolRegistry(makeOpenLinkToolDefinitions(handlers: makeHandlers(log: HandlerLog())))
        #expect(registry.tools.map(\.name) == ["open_link"])
    }

    @Test("a url is passed through to the handler")
    func passthrough() async {
        let log = HandlerLog()
        let registry = MCPToolRegistry(makeOpenLinkToolDefinitions(handlers: makeHandlers(log: log)))
        let result = await registry.call(name: "open_link", arguments: ["url": .string("https://example.com")])
        #expect(result.isError != true)
        #expect(text(result) == "Opened https://example.com in M1K3's review panel.")
        #expect(log.all == ["open:https://example.com"])
    }

    @Test("the url argument is trimmed before passthrough")
    func trims() async {
        let log = HandlerLog()
        let registry = MCPToolRegistry(makeOpenLinkToolDefinitions(handlers: makeHandlers(log: log)))
        _ = await registry.call(name: "open_link", arguments: ["url": .string("  https://example.com  ")])
        #expect(log.all == ["open:https://example.com"])
    }

    @Test("a missing url is a tool error, handler not called")
    func missingURL() async {
        let log = HandlerLog()
        let registry = MCPToolRegistry(makeOpenLinkToolDefinitions(handlers: makeHandlers(log: log)))
        let result = await registry.call(name: "open_link", arguments: [:])
        #expect(result.isError == true)
        #expect(log.all.isEmpty)
    }

    @Test("a blank url is a tool error, handler not called")
    func blankURL() async {
        let log = HandlerLog()
        let registry = MCPToolRegistry(makeOpenLinkToolDefinitions(handlers: makeHandlers(log: log)))
        let result = await registry.call(name: "open_link", arguments: ["url": .string("   ")])
        #expect(result.isError == true)
        #expect(log.all.isEmpty)
    }

    @Test("a handler rejection (non-web url) surfaces as a tool error")
    func handlerRejectionSurfaces() async {
        let log = HandlerLog()
        let registry = MCPToolRegistry(makeOpenLinkToolDefinitions(handlers: makeHandlers(log: log, throwsError: true)))
        let result = await registry.call(name: "open_link", arguments: ["url": .string("ftp://nope")])
        #expect(result.isError == true)
    }
}
