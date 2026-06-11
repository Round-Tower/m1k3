//
//  HeadlessAskTests.swift
//  M1K3ChatTests
//
//  The MCP ask_m1k3 core: one headless turn through a RAGResponding —
//  stream drained (delta AND cumulative), reasoning stripped, hallucinated
//  citations removed, sources appended. Same post-processing contract as
//  ChatSession.send, pinned independently because no ChatSession exists here.
//

import Foundation
@testable import M1K3Chat
import M1K3Knowledge
import Testing

private func hit(title: String, heading: String?) -> ChunkHit {
    ChunkHit(
        chunkID: UUID(), itemID: UUID(), itemTitle: title, kind: .document,
        heading: heading, content: "chunk content for \(title)"
    )
}

private struct StreamResponder: RAGResponding {
    var sources: [ChunkHit] = []
    var chunks: [String]
    var collected: [ChunkHit] = []

    func answerStreaming(_: String) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        let stream = AsyncStream<String> { continuation in
            for chunk in chunks {
                continuation.yield(chunk)
            }
            continuation.finish()
        }
        return (sources, stream)
    }

    func collectedSources() -> [ChunkHit] {
        collected
    }
}

private struct BoomResponder: RAGResponding {
    struct Boom: Error {}
    func answerStreaming(_: String) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        throw Boom()
    }
}

struct HeadlessAskTests {
    @Test("a delta stream drains into the final answer")
    func deltaStream() async throws {
        let responder = StreamResponder(chunks: ["The seal ", "failed."])
        let answer = try await HeadlessAsk.answer("what failed?", using: responder)
        #expect(answer.hasPrefix("The seal failed."))
    }

    @Test("cumulative snapshots fold without duplication")
    func cumulativeStream() async throws {
        let responder = StreamResponder(chunks: ["The seal", "The seal failed."])
        let answer = try await HeadlessAsk.answer("what failed?", using: responder)
        #expect(answer.hasPrefix("The seal failed."))
        #expect(!answer.contains("The sealThe seal"))
    }

    @Test("reasoning think-blocks are stripped from the answer")
    func reasoningStripped() async throws {
        let responder = StreamResponder(chunks: ["<think>The user asks about seals.</think>It failed under load."])
        let answer = try await HeadlessAsk.answer("what failed?", using: responder)
        #expect(answer.hasPrefix("It failed under load."))
        #expect(!answer.contains("<think>"))
        #expect(!answer.contains("user asks"))
    }

    @Test("hallucinated citations are stripped; grounded ones survive")
    func citationValidation() async throws {
        // ALL-CAPS source ids: CitationValidator's regex only recognises
        // those (CitationValidator.swift:124) — mixed-case titles pass
        // through unvalidated in the app too. Known follow-up, not ours.
        let grounded = hit(title: "PLANT-NOTES", heading: "Seals")
        let responder = StreamResponder(
            sources: [grounded],
            chunks: ["It failed [PLANT-NOTES §Seals] not [GHOST-DOC §Nowhere]."]
        )
        let answer = try await HeadlessAsk.answer("what failed?", using: responder)
        #expect(answer.contains("[PLANT-NOTES §Seals]"))
        #expect(!answer.contains("GHOST-DOC"))
    }

    @Test("sources are listed under the answer, including tool-collected ones")
    func sourcesAppended() async throws {
        let injected = hit(title: "Plant Notes", heading: "Seals")
        let collected = hit(title: "Safety Manual", heading: nil)
        let responder = StreamResponder(sources: [injected], chunks: ["Answer."], collected: [collected])
        let answer = try await HeadlessAsk.answer("q", using: responder)
        #expect(answer.contains("Sources:"))
        #expect(answer.contains("Plant Notes §Seals"))
        #expect(answer.contains("Safety Manual"))
    }

    @Test("no sources means no Sources block")
    func noSourcesBlock() async throws {
        let responder = StreamResponder(chunks: ["Just an answer."])
        let answer = try await HeadlessAsk.answer("q", using: responder)
        #expect(!answer.contains("Sources:"))
    }

    @Test("an empty answer throws rather than returning silence")
    func emptyAnswerThrows() async {
        let responder = StreamResponder(chunks: ["<think>only thinking, no answer</think>"])
        await #expect(throws: HeadlessAskError.self) {
            _ = try await HeadlessAsk.answer("q", using: responder)
        }
    }

    @Test("responder failures propagate")
    func responderThrowPropagates() async {
        await #expect(throws: Error.self) {
            _ = try await HeadlessAsk.answer("q", using: BoomResponder())
        }
    }
}
