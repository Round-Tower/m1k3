//
//  SwappableToolCallingTests.swift
//  M1K3InferenceTests
//
//  The swappable MLX façade must forward the tool-calling capability to its
//  current backing (Phase 12c): a tool-capable backing makes the façade
//  tool-capable, a plain backing does not, and a runtime swap re-points it.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Inference
import Testing

private final class PlainProvider: InferenceProvider, @unchecked Sendable {
    let name = "plain"
    let isAvailable = true
    func generate(prompt _: String) async throws -> String {
        "plain"
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { $0.finish() }
    }
}

private final class ToolProvider: InferenceProvider, ToolCallingProvider, @unchecked Sendable {
    let name = "tool"
    let isAvailable = true
    let supportsToolCalls: Bool
    init(supportsToolCalls: Bool = true) {
        self.supportsToolCalls = supportsToolCalls
    }

    func generate(prompt _: String) async throws -> String {
        "tool"
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { $0.finish() }
    }

    func continueToolTurn(messages _: [ToolMessage], tools _: [ToolDefinition]) async throws -> ToolTurn {
        .text("native answer")
    }
}

struct SwappableToolCallingTests {
    @Test("forwards supportsToolCalls from a tool-capable backing")
    func forwardsCapability() {
        let swappable = SwappableInferenceProvider(ToolProvider(supportsToolCalls: true))
        #expect(swappable.supportsToolCalls)
    }

    @Test("a plain backing makes the façade report no tool support")
    func plainBackingNoSupport() {
        let swappable = SwappableInferenceProvider(PlainProvider())
        #expect(!swappable.supportsToolCalls)
    }

    @Test("a runtime swap re-points the capability and the turn")
    func swapRepoints() async throws {
        let swappable = SwappableInferenceProvider(PlainProvider())
        #expect(!swappable.supportsToolCalls)

        swappable.setProvider(ToolProvider(supportsToolCalls: true))
        #expect(swappable.supportsToolCalls)

        let turn = try await swappable.continueToolTurn(messages: [.user("hi")], tools: [])
        #expect(turn == .text("native answer"))
    }
}
