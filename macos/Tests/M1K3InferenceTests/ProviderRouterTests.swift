//
//  ProviderRouterTests.swift
//  M1K3InferenceTests
//
//  Contract tests for the routing logic: availability ordering, skip-unavailable,
//  fall-through-on-error, the no-provider case, and streaming selection. Uses a
//  configurable fake provider — no model, no OS dependency.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Inference
import Testing

// MARK: - Configurable fake

struct FakeProvider: InferenceProvider {
    let name: String
    let isAvailable: Bool
    /// Output to return, or an error to throw.
    let behaviour: Behaviour

    enum Behaviour {
        case returns(String)
        case throwsError
        case streams([String])
    }

    init(name: String, available: Bool = true, behaviour: Behaviour) {
        self.name = name
        isAvailable = available
        self.behaviour = behaviour
    }

    func generate(prompt _: String) async throws -> String {
        switch behaviour {
        case let .returns(text): return text
        case .throwsError: throw InferenceError.generationFailed(name)
        case let .streams(chunks): return chunks.joined()
        }
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { continuation in
            if case let .streams(chunks) = behaviour {
                for chunk in chunks {
                    continuation.yield(chunk)
                }
            } else if case let .returns(text) = behaviour {
                continuation.yield(text)
            }
            continuation.finish()
        }
    }
}

// MARK: - Tests

struct ProviderRouterTests {
    @Test("routes to the first available provider in order")
    func firstAvailable() async throws {
        let router = ProviderRouter(providers: [
            FakeProvider(name: "a", behaviour: .returns("from-a")),
            FakeProvider(name: "b", behaviour: .returns("from-b")),
        ])
        #expect(router.activeProviderName == "a")
        #expect(try await router.generate(prompt: "hi") == "from-a")
    }

    @Test("skips an unavailable provider")
    func skipsUnavailable() async throws {
        let router = ProviderRouter(providers: [
            FakeProvider(name: "down", available: false, behaviour: .returns("nope")),
            FakeProvider(name: "up", behaviour: .returns("yes")),
        ])
        #expect(router.activeProviderName == "up")
        #expect(try await router.generate(prompt: "hi") == "yes")
    }

    @Test("falls through to the next provider when one errors")
    func fallThroughOnError() async throws {
        let router = ProviderRouter(providers: [
            FakeProvider(name: "flaky", behaviour: .throwsError),
            FakeProvider(name: "solid", behaviour: .returns("recovered")),
        ])
        #expect(try await router.generate(prompt: "hi") == "recovered")
    }

    @Test("throws noProviderAvailable when nothing is available")
    func noneAvailable() async {
        let router = ProviderRouter(providers: [
            FakeProvider(name: "down", available: false, behaviour: .returns("x")),
        ])
        #expect(router.activeProvider == nil)
        await #expect(throws: InferenceError.noProviderAvailable) {
            try await router.generate(prompt: "hi")
        }
    }

    @Test("surfaces the last backend error when every available provider fails")
    func allFail() async {
        let router = ProviderRouter(providers: [
            FakeProvider(name: "a", behaviour: .throwsError),
            FakeProvider(name: "b", behaviour: .throwsError),
        ])
        await #expect(throws: InferenceError.generationFailed("b")) {
            try await router.generate(prompt: "hi")
        }
    }

    @Test("empty router throws noProviderAvailable")
    func emptyRouter() async {
        let router = ProviderRouter(providers: [])
        await #expect(throws: InferenceError.noProviderAvailable) {
            try await router.generate(prompt: "hi")
        }
    }

    @Test("streams from the first available provider")
    func streamsFromFirst() async {
        let router = ProviderRouter(providers: [
            FakeProvider(name: "down", available: false, behaviour: .streams(["x"])),
            FakeProvider(name: "up", behaviour: .streams(["Hel", "lo", "!"])),
        ])
        var collected = ""
        for await chunk in router.generateStreaming(prompt: "hi") {
            collected += chunk
        }
        #expect(collected == "Hello!")
    }

    @Test("streaming with no available provider yields an empty sequence")
    func streamsEmptyWhenNone() async {
        let router = ProviderRouter(providers: [
            FakeProvider(name: "down", available: false, behaviour: .streams(["x"])),
        ])
        var count = 0
        for await _ in router.generateStreaming(prompt: "hi") {
            count += 1
        }
        #expect(count == 0)
    }
}
