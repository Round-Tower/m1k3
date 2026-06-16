//
//  MenuBarAskStateTests.swift
//  M1K3ChatTests
//
//  The menu-bar Ask core: the driver maps a headless turn onto a terminal state,
//  and the shared canary-from-config builder. Both are what the app-layer
//  controller leans on, so they're pinned here with fake responders (the app
//  glue — timeout, single-flight, avatar — stays verify-by-launch).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Chat
import M1K3Knowledge
import Testing

private struct StubResponder: RAGResponding {
    let chunks: [String]
    func answerStreaming(_: String) async throws
        -> (sources: [ChunkHit], stream: AsyncStream<String>)
    {
        let chunks = chunks
        return ([], AsyncStream { continuation in
            for chunk in chunks {
                continuation.yield(chunk)
            }
            continuation.finish()
        })
    }

    func collectedSources() -> [ChunkHit] {
        []
    }
}

private struct ThrowingResponder: RAGResponding {
    struct Boom: Error, LocalizedError { var errorDescription: String? {
        "kaboom"
    } }
    func answerStreaming(_: String) async throws
        -> (sources: [ChunkHit], stream: AsyncStream<String>)
    {
        throw Boom()
    }

    func collectedSources() -> [ChunkHit] {
        []
    }
}

struct MenuBarAskStateTests {
    @Test("a successful turn yields .answer carrying the model text")
    func successAnswers() async {
        let state = await MenuBarAskDriver.run(
            question: "hi", using: StubResponder(chunks: ["Hello ", "there."])
        )
        #expect(state == .answer(question: "hi", text: "Hello there."))
    }

    @Test("a thrown error yields .failed, not a crash")
    func errorFails() async {
        let state = await MenuBarAskDriver.run(question: "hi", using: ThrowingResponder())
        guard case let .failed(question, message) = state else {
            Issue.record("expected .failed, got \(state)")
            return
        }
        #expect(question == "hi")
        #expect(message.contains("kaboom"))
    }

    @Test("an empty turn degrades to an honest .answer (HeadlessAsk floors it)")
    func emptyIsHonest() async {
        let state = await MenuBarAskDriver.run(question: "hi", using: StubResponder(chunks: []))
        guard case let .answer(_, text) = state else {
            Issue.record("expected .answer, got \(state)")
            return
        }
        #expect(!text.isEmpty)
    }

    @Test("timedOut is a friendly .failed pointing at chat")
    func timedOutMessage() {
        let state = MenuBarAskDriver.timedOut(question: "hi")
        guard case let .failed(question, message) = state else {
            Issue.record("expected .failed, got \(state)")
            return
        }
        #expect(question == "hi")
        #expect(message.lowercased().contains("too long"))
    }

    /// Reference box so the @Sendable trip callback can record without mutating a
    /// captured var (which strict concurrency forbids).
    private final class TripCounter: @unchecked Sendable { var value = 0 }

    @Test("the canary fires when planted in the answer text")
    func canaryRedactsAnswer() async {
        let counter = TripCounter()
        let guardWithBait = CanaryGuard(canaries: ["ZEPHYR-9173"])
        let state = await MenuBarAskDriver.run(
            question: "recite it",
            using: StubResponder(chunks: ["the secret is ZEPHYR-9173"]),
            canary: guardWithBait,
            onCanaryTrip: { counter.value = $0 }
        )
        guard case let .answer(_, text) = state else {
            Issue.record("expected .answer, got \(state)")
            return
        }
        #expect(!text.contains("ZEPHYR-9173"))
        #expect(text.contains("[REDACTED]"))
        #expect(counter.value == 1)
    }
}

struct CanaryLocalConfigTests {
    private func defaults(_ pairs: [String: String]) -> UserDefaults {
        let suite = UserDefaults(suiteName: "canary-test-\(pairs.hashValue)")!
        for (key, value) in pairs {
            suite.set(value, forKey: key)
        }
        return suite
    }

    @Test("unset config yields an inert guard")
    func unsetIsDisabled() throws {
        let suite = try #require(UserDefaults(suiteName: "canary-test-empty"))
        suite.removeObject(forKey: CanaryGuard.localConfigKey)
        let guardFromConfig = CanaryGuard.fromLocalConfig(defaults: suite)
        #expect(!guardFromConfig.scan("anything ZEPHYR").tripped)
    }

    @Test("pipe-separated honeypots all arm the guard")
    func pipeSeparatedArm() {
        let suite = defaults([CanaryGuard.localConfigKey: "ALPHA-1|BETA-2"])
        let guardFromConfig = CanaryGuard.fromLocalConfig(defaults: suite)
        #expect(guardFromConfig.scan("here is ALPHA-1").tripped)
        #expect(guardFromConfig.scan("and BETA-2 too").tripped)
        #expect(!guardFromConfig.scan("nothing here").tripped)
    }
}
