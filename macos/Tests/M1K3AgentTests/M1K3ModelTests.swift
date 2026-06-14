//
//  M1K3ModelTests.swift
//  M1K3AgentTests
//
//  Pins the LanguageModelDescribing brick (ADR 0001): a M1K3 brain exposes its
//  descriptor for the ladder, vends an executor that round-trips through the real
//  session seam, reuses ONE session across turns (the KV-reuse win), and drives a
//  real ToolCallingProvider via the convenience init.
//

@testable import M1K3Agent
import M1K3Inference
import M1K3LanguageModel
import Synchronization
import Testing

/// Streams fixed tokens then returns a scripted outcome; counts its sends.
private final class StreamingSession: ToolTurnSession, @unchecked Sendable {
    let tokens: [String]
    let outcome: ToolTurn
    private let sends = Mutex(0)
    var sendCount: Int {
        sends.withLock { $0 }
    }

    init(tokens: [String], outcome: ToolTurn) {
        self.tokens = tokens
        self.outcome = outcome
    }

    func send(
        _: [ToolMessage],
        onToken: @escaping @Sendable (String) -> Void
    ) async throws -> ToolTurn {
        sends.withLock { $0 += 1 }
        for token in tokens {
            onToken(token)
        }
        return outcome
    }

    func finish() async {}
}

/// Minimal `ToolCallingProvider` — its default `makeToolTurnSession` yields a
/// `StatelessToolTurnSession`, so this exercises the real convenience-init path.
private final class StubToolProvider: ToolCallingProvider, @unchecked Sendable {
    let name = "stub"
    let isAvailable = true
    let supportsToolCalls = true
    private let answer: String

    init(answer: String) {
        self.answer = answer
    }

    func generate(prompt _: String) async throws -> String {
        answer
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { $0.finish() }
    }

    func continueToolTurn(messages _: [ToolMessage], tools _: [ToolDefinition]) async throws -> ToolTurn {
        .text(answer)
    }
}

struct M1K3ModelTests {
    private let descriptor = LanguageModelDescriptor(
        id: "lil-4b", reach: .onDevice, capabilities: [.toolCalling, .reasoning], isLocalFloor: true
    )

    @Test("exposes its descriptor for the ladder")
    func exposesDescriptor() {
        let model = M1K3Model(descriptor: descriptor) {
            StreamingSession(tokens: [], outcome: .text(""))
        }
        #expect(model.descriptor.id == "lil-4b")
        #expect(model.descriptor.isLocalFloor)
        #expect(model.descriptor.reach == .onDevice)
    }

    @Test("Describing → executor → channel round-trips through the session factory")
    func roundTripViaFactory() async throws {
        let model = M1K3Model(descriptor: descriptor) {
            StreamingSession(tokens: ["<think>x</think>", "Hi ", "there."], outcome: .text("Hi there."))
        }
        let channel = GenerationChannel()
        try await model.makeExecutor().respond(to: "hi", into: channel)
        #expect(channel.answer == "Hi there.")
        #expect(channel.reasoning.contains("<think>"))
    }

    @Test("one executor reuses ONE session across turns (the KV-reuse seam)")
    func sessionReusedAcrossTurns() async throws {
        let session = StreamingSession(tokens: ["ok"], outcome: .text("ok"))
        let factoryCalls = Mutex(0)
        let model = M1K3Model(descriptor: descriptor) {
            factoryCalls.withLock { $0 += 1 }
            return session
        }
        let executor = model.makeExecutor()
        try await executor.respond(to: "one", into: GenerationChannel())
        try await executor.respond(to: "two", into: GenerationChannel())

        #expect(factoryCalls.withLock { $0 } == 1, "session built once, reused so the KV cache survives")
        #expect(session.sendCount == 2)
    }

    @Test("real-provider convenience init drives the default ToolTurnSession")
    func realProviderConvenienceInit() async throws {
        let provider = StubToolProvider(answer: "Grounded answer.")
        let model = M1K3Model(descriptor: descriptor, provider: provider, tools: [])
        let channel = GenerationChannel()
        try await model.makeExecutor().respond(to: "q", into: channel)
        #expect(channel.answer == "Grounded answer.")
    }
}
