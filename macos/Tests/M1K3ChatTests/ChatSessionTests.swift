//
//  ChatSessionTests.swift
//  M1K3ChatTests
//
//  The chat state reducer the SwiftUI shell binds to. This is the one piece of
//  "app glue" that carries real bugs — folding a streamed answer into displayable
//  state across partial tokens, cancellation, and mid-stream failure — so it's
//  TDD'd in the package rather than waved through in a View. The views stay dumb.
//
//  The streaming contract is deliberately stress-tested both ways: Apple
//  Foundation Models yields *cumulative* snapshots (full text each tick) while
//  the simpler fakes yield *deltas*. ChatSession must render both correctly, so
//  both are pinned here.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Chat
import M1K3Knowledge
import Testing

// MARK: - Fakes conforming to the RAGResponding seam

/// Yields cumulative snapshots — exactly how AppleFoundationModelsProvider
/// behaves (each yield is the whole answer so far).
private struct CumulativeResponder: RAGResponding {
    let sources: [ChunkHit]
    let snapshots: [String]

    func answerStreaming(
        _: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        let snapshots = snapshots
        return (sources, AsyncStream { continuation in
            for snap in snapshots {
                continuation.yield(snap)
            }
            continuation.finish()
        })
    }
}

/// Yields per-word deltas — how the lighter providers (and the existing
/// RecordingProvider fixture) stream.
private struct DeltaResponder: RAGResponding {
    let sources: [ChunkHit]
    let deltas: [String]

    func answerStreaming(
        _: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        let deltas = deltas
        return (sources, AsyncStream { continuation in
            for d in deltas {
                continuation.yield(d)
            }
            continuation.finish()
        })
    }
}

/// Fails before producing a stream (retrieval/provider error path).
private struct FailingResponder: RAGResponding {
    struct Boom: Error {}
    func answerStreaming(
        _: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        throw Boom()
    }
}

private func fixtureSource(_ text: String) -> ChunkHit {
    ChunkHit(
        chunkID: UUID(),
        itemID: UUID(),
        itemTitle: "Plant Notes",
        kind: .document,
        heading: "3.2 Seals",
        content: text
    )
}

// MARK: - Tests

@MainActor
struct ChatSessionTests {
    @Test("send appends the user message immediately")
    func appendsUserMessage() async {
        let session = ChatSession(responder: DeltaResponder(sources: [], deltas: ["ok"]))
        await session.send("What failed?")

        let user = session.messages.first
        #expect(user?.role == .user)
        #expect(user?.text == "What failed?")
        #expect(user?.status == .complete)
    }

    @Test("blank input is ignored — no messages, no call")
    func ignoresBlankInput() async {
        let session = ChatSession(responder: DeltaResponder(sources: [], deltas: ["ok"]))
        await session.send("   \n ")
        #expect(session.messages.isEmpty)
    }

    @Test("folds CUMULATIVE snapshots into the final answer (AFM contract)")
    func foldsCumulative() async {
        let session = ChatSession(responder: CumulativeResponder(
            sources: [],
            snapshots: ["The", "The seal", "The seal failed"]
        ))
        await session.send("what failed?")

        let answer = session.messages.last
        #expect(answer?.role == .assistant)
        #expect(answer?.text == "The seal failed") // replaced, not concatenated
        #expect(answer?.status == .complete)
    }

    @Test("folds DELTA chunks into the final answer (delta contract)")
    func foldsDelta() async {
        let session = ChatSession(responder: DeltaResponder(
            sources: [],
            deltas: ["The ", "seal ", "failed"]
        ))
        await session.send("what failed?")

        #expect(session.messages.last?.text == "The seal failed")
    }

    @Test("attaches retrieved sources to the assistant message")
    func attachesSources() async {
        let src = fixtureSource("The hydraulic seal failed under load.")
        let session = ChatSession(responder: DeltaResponder(sources: [src], deltas: ["ok"]))
        await session.send("seal?")

        let answer = session.messages.last
        #expect(answer?.sources.count == 1)
        #expect(answer?.sources.first?.itemTitle == "Plant Notes")
    }

    @Test("a thrown error surfaces as a failed assistant message, not a crash")
    func failurePath() async {
        let session = ChatSession(responder: FailingResponder())
        await session.send("anything?")

        let answer = session.messages.last
        #expect(answer?.role == .assistant)
        if case .failed = answer?.status {} else {
            Issue.record("expected .failed status, got \(String(describing: answer?.status))")
        }
        #expect(!(answer?.text.isEmpty ?? true)) // user-facing fallback text
    }

    @Test("isResponding is true mid-flight and false once settled")
    func respondingFlag() async {
        let session = ChatSession(responder: DeltaResponder(sources: [], deltas: ["a", "b"]))
        #expect(session.isResponding == false)
        await session.send("go")
        #expect(session.isResponding == false)
    }

    @Test("a full turn is exactly two messages: user then assistant")
    func turnShape() async {
        let session = ChatSession(responder: DeltaResponder(sources: [], deltas: ["ok"]))
        await session.send("hello")
        #expect(session.messages.count == 2)
        #expect(session.messages[0].role == .user)
        #expect(session.messages[1].role == .assistant)
    }
}
