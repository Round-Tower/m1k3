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

private func hit(title: String, heading: String?, similarity: Float? = nil) -> ChunkHit {
    ChunkHit(
        chunkID: UUID(), itemID: UUID(), itemTitle: title, kind: .document,
        heading: heading, content: "chunk content for \(title)", similarity: similarity
    )
}

private func memoryHit(_ content: String, similarity: Float? = nil) -> ChunkHit {
    ChunkHit(
        chunkID: UUID(), itemID: UUID(), itemTitle: content, kind: .memory,
        heading: nil, content: content, similarity: similarity
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

/// Records canary-trip callbacks so a test can assert the alert path fired
/// without touching os_log. Reference type so the @Sendable closure can capture
/// it; the answer is awaited before `trips` is read, so the lock only satisfies
/// strict concurrency.
private final class TripRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var counts: [Int] = []
    func record(_ count: Int) {
        lock.withLock { counts.append(count) }
    }

    var trips: [Int] {
        lock.withLock { counts }
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

    @Test("the Sources block dedupes repeats and ranks by relevance")
    func sourcesDedupedAndRanked() async throws {
        // Mirrors the live Rosenblatt noise: the same item listed several times
        // plus lower-similarity off-topic chunks (test-report F4).
        let strong = hit(title: "Rosenblatt 1958", heading: nil, similarity: 0.81)
        let strongDupe = hit(title: "Rosenblatt 1958", heading: nil, similarity: 0.79)
        let strongHeading = hit(title: "Rosenblatt 1958", heading: "Phases", similarity: 0.74)
        let weak = hit(title: "Scaling Laws", heading: "2.3", similarity: 0.63)
        let responder = StreamResponder(
            sources: [weak, strong, strongDupe, strongHeading], chunks: ["Answer."]
        )
        let answer = try await HeadlessAsk.answer("q", using: responder)
        let block = try #require(answer.components(separatedBy: "Sources:\n").last)
        let lines = block.split(separator: "\n").map(String.init)
        // The two identical "Rosenblatt 1958" (no heading) collapse to one.
        #expect(lines == [
            "— Rosenblatt 1958",
            "— Rosenblatt 1958 §Phases",
            "— Scaling Laws §2.3",
        ])
        // Most-relevant leads; the weak off-topic chunk sinks to the bottom.
        #expect(lines.first == "— Rosenblatt 1958")
        #expect(lines.last == "— Scaling Laws §2.3")
    }

    @Test("memories never appear in the Sources block — ambient context, not citations")
    func memoriesExcludedFromSources() async throws {
        // Live MCP leak 2026-06-12: an apple-pruning query injected the memory
        // "The user has a Mac." (0.588, over memoryThreshold) and it rendered
        // as a SOURCE for tree pruning. Memories are "use naturally, do not
        // cite" by contract (AgentRAGResponder.memoryBlock) — they must never
        // surface in the citation footer, however they cleared the gate.
        let doc = hit(title: "Plant Notes", heading: "Seals", similarity: 0.8)
        let memory = memoryHit("The user has a Mac.", similarity: 0.59)
        let responder = StreamResponder(sources: [doc, memory], chunks: ["Answer."])
        let answer = try await HeadlessAsk.answer("q", using: responder)
        #expect(answer.contains("Plant Notes §Seals"))
        #expect(!answer.contains("The user has a Mac"))
    }

    @Test("a memory-only turn produces no Sources block at all")
    func memoryOnlySourcesSuppressed() async throws {
        let memory = memoryHit("The user has a gecko.", similarity: 0.6)
        let responder = StreamResponder(sources: [memory], chunks: ["Answer."])
        let answer = try await HeadlessAsk.answer("q", using: responder)
        #expect(!answer.contains("Sources:"))
        #expect(!answer.contains("gecko"))
    }

    @Test("no sources means no Sources block")
    func noSourcesBlock() async throws {
        let responder = StreamResponder(chunks: ["Just an answer."])
        let answer = try await HeadlessAsk.answer("q", using: responder)
        #expect(!answer.contains("Sources:"))
    }

    @Test("a think-only turn degrades to an honest message, not a hard error")
    func emptyAfterReasoningDegrades() async throws {
        // The smaller brains routinely reason without emitting a conclusion;
        // ReasoningSplit then reduces the turn to empty. The MCP visitor must
        // get a graceful answer, not "Error: emptyAnswer" (test-report F1 floor).
        let responder = StreamResponder(chunks: ["<think>only thinking, no answer</think>"])
        let answer = try await HeadlessAsk.answer("q", using: responder)
        #expect(!answer.isEmpty)
        #expect(!answer.contains("<think>"))
        #expect(!answer.contains("Sources:"))
        // The reasoned-but-no-answer wording (vs the silent-empty one).
        #expect(answer.lowercased().contains("clear answer"))
    }

    @Test("a wholly empty turn still returns a message rather than silence")
    func emptyWithoutReasoningDegrades() async throws {
        let responder = StreamResponder(chunks: ["   "])
        let answer = try await HeadlessAsk.answer("q", using: responder)
        #expect(!answer.isEmpty)
        #expect(!answer.contains("Sources:"))
    }

    @Test("responder failures propagate")
    func responderThrowPropagates() async {
        await #expect(throws: Error.self) {
            _ = try await HeadlessAsk.answer("q", using: BoomResponder())
        }
    }

    @Test("a canary leaking into the answer body is redacted and trips the guard")
    func canaryInBodyRedacted() async throws {
        let canary = "XYZZY-LEAK-CANARY"
        let recorder = TripRecorder()
        let responder = StreamResponder(chunks: ["The passphrase is \(canary), oops."])
        let answer = try await HeadlessAsk.answer(
            "q", using: responder,
            canary: CanaryGuard(canaries: [canary]),
            onCanaryTrip: { recorder.record($0) }
        )
        #expect(!answer.contains(canary))
        #expect(answer.contains("[REDACTED]"))
        #expect(recorder.trips == [1])
    }

    @Test("a canary leaking through a Source title is redacted and trips the guard")
    func canaryInSourceRedacted() async throws {
        // A quarantined doc could leak its title into the citation footer even
        // when the body is clean — the guard scans the whole outgoing string.
        let canary = "PLUGH-LEAK-CANARY"
        let recorder = TripRecorder()
        let leakyDoc = hit(title: "Notes \(canary)", heading: nil, similarity: 0.8)
        let responder = StreamResponder(sources: [leakyDoc], chunks: ["Answer."])
        let answer = try await HeadlessAsk.answer(
            "q", using: responder,
            canary: CanaryGuard(canaries: [canary]),
            onCanaryTrip: { recorder.record($0) }
        )
        #expect(answer.contains("Sources:"))
        #expect(!answer.contains(canary))
        #expect(recorder.trips == [1])
    }

    @Test("an active guard leaves clean output untouched and never fires")
    func cleanOutputNotTripped() async throws {
        let recorder = TripRecorder()
        let responder = StreamResponder(chunks: ["A perfectly clean answer."])
        let answer = try await HeadlessAsk.answer(
            "q", using: responder,
            canary: CanaryGuard(canaries: ["NEVER-PRESENT-CANARY"]),
            onCanaryTrip: { recorder.record($0) }
        )
        #expect(answer.hasPrefix("A perfectly clean answer."))
        #expect(recorder.trips.isEmpty)
    }
}
