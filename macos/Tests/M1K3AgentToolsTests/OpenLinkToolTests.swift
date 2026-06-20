//
//  OpenLinkToolTests.swift
//  M1K3AgentToolsTests
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3AgentTools
import Testing

struct OpenLinkToolTests {
    /// A thread-safe sink for the URL the tool hands back, so we can assert what
    /// would be opened without any UI.
    private final class Box: @unchecked Sendable {
        private let lock = NSLock()
        private var _url: URL?
        var url: URL? {
            lock.lock(); defer { lock.unlock() }
            return _url
        }

        func set(_ url: URL) {
            lock.lock(); defer { lock.unlock() }
            _url = url
        }
    }

    @Test("a valid https URL is opened and confirmed")
    func opensHTTPS() async throws {
        let box = Box()
        let tool = OpenLinkTool { box.set($0) }
        let result = try await tool.execute(input: ["url": "https://example.com/page"])
        #expect(box.url == URL(string: "https://example.com/page"))
        #expect(!result.output.hasPrefix("Error:"))
    }

    @Test("a bare domain is coerced to https and opened")
    func coercesBareDomain() async throws {
        let box = Box()
        let tool = OpenLinkTool { box.set($0) }
        _ = try await tool.execute(input: ["url": "example.com"])
        #expect(box.url == URL(string: "https://example.com"))
    }

    @Test("an empty argument is a recoverable error, nothing opened")
    func emptyIsError() async throws {
        let box = Box()
        let tool = OpenLinkTool { box.set($0) }
        let result = try await tool.execute(input: [:])
        #expect(result.output.hasPrefix("Error:"))
        #expect(box.url == nil)
    }

    @Test("a non-web target (a file path) is refused — the tool opens links, not files")
    func refusesNonWeb() async throws {
        let box = Box()
        let tool = OpenLinkTool { box.set($0) }
        let result = try await tool.execute(input: ["url": "/etc/hosts"])
        #expect(result.output.hasPrefix("Error:"))
        #expect(box.url == nil)
    }

    @Test("a local/private-network address is refused — no SSRF via the panel")
    func refusesLocalNetwork() async throws {
        let box = Box()
        let tool = OpenLinkTool { box.set($0) }
        for raw in ["http://localhost:3000", "http://127.0.0.1", "http://192.168.1.1", "http://169.254.169.254"] {
            let result = try await tool.execute(input: ["url": raw])
            #expect(result.output.hasPrefix("Error:"))
        }
        #expect(box.url == nil)
    }

    @Test("declares the open_link contract the model sees")
    func contract() {
        let tool = OpenLinkTool { _ in }
        #expect(tool.name == "open_link")
        #expect(tool.parameters.first?.name == "url")
    }
}
