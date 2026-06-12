//
//  GroundingGateTests.swift
//  M1K3KnowledgeTests
//
//  Relevance gate for retrieve-first grounding: top-K injection regardless of
//  similarity polluted prompts ("what model are you?" pulled arxiv chunks).
//  The gate drops weak hits and — when NOTHING is topical — injects nothing,
//  leaving retrieval to the model via search_knowledge.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8 (thresholds are
//  empirical starting points, tuned via the responder score logs). Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

struct GroundingGateTests {
    private func hit(_ content: String, similarity: Float?) -> ChunkHit {
        ChunkHit(
            chunkID: UUID(),
            itemID: UUID(),
            itemTitle: "Doc",
            kind: .document,
            heading: nil,
            content: content,
            similarity: similarity,
            rrfScore: 0.016
        )
    }

    @Test("topical hits pass; weak hits are dropped")
    func dropsWeakHits() {
        // BGE's narrow cosine cone: unrelated content still scores ~0.55-0.7.
        let hits = [
            hit("relevant", similarity: 0.78),
            hit("borderline", similarity: 0.64),
            hit("noise", similarity: 0.58),
        ]
        let kept = GroundingGate.filter(hits)
        #expect(kept.map(\.content) == ["relevant", "borderline"])
    }

    @Test("when no hit is topical, nothing is injected at all")
    func allWeakInjectsNothing() {
        let hits = [hit("noise a", similarity: 0.57), hit("noise b", similarity: 0.49)]
        #expect(GroundingGate.filter(hits).isEmpty)
    }

    @Test("an FTS-only hit (no vector score) is never injected — even beside a topical hit")
    func ftsOnlyAlwaysDropped() {
        // A nil-similarity hit appeared ONLY in the keyword ranking, never the
        // vector top-K (post-backfill): "Cork/weather/today" matching stored
        // docs is lexical coincidence, not topical relevance. One borderline
        // vector hit must NOT flood the prompt with all the keyword noise (the
        // ⌘R weather bug: 7KB of grounding for "what's the weather in Cork").
        let beside = [hit("vector match", similarity: 0.74), hit("fts only", similarity: nil)]
        #expect(GroundingGate.filter(beside).map(\.content) == ["vector match"])

        let alone = [hit("fts only", similarity: nil)]
        #expect(GroundingGate.filter(alone).isEmpty)
    }

    @Test("an empty retrieval stays empty")
    func emptyStaysEmpty() {
        #expect(GroundingGate.filter([]).isEmpty)
    }

    @Test("the threshold boundary is inclusive: exactly 0.62 passes, just below is gated")
    func thresholdBoundary() {
        // Pins the CONSTANT, not just the ordering — a silent threshold edit
        // (or >= flipping to >) should fail here, not at ⌘R.
        let exactlyAt = hit("at threshold", similarity: GroundingGate.chunkThreshold)
        let justBelow = hit("below threshold", similarity: GroundingGate.chunkThreshold - 0.001)
        #expect(GroundingGate.filter([exactlyAt]).count == 1)
        #expect(GroundingGate.filter([justBelow]).isEmpty)
        // A passing sibling never carries a below-threshold hit through.
        #expect(GroundingGate.filter([exactlyAt, justBelow]).map(\.content) == ["at threshold"])
        #expect(GroundingGate.chunkThreshold == 0.62)
    }

    // MARK: - Memory partition

    private func memoryHit(_ content: String, similarity: Float?) -> ChunkHit {
        ChunkHit(
            chunkID: UUID(),
            itemID: UUID(),
            itemTitle: "Memory",
            kind: .memory,
            heading: nil,
            content: content,
            similarity: similarity,
            rrfScore: 0.016
        )
    }

    @Test("partition routes memory hits to memories, the rest to knowledge")
    func partitionRoutesByKind() {
        let hits = [
            hit("doc fact", similarity: 0.78),
            memoryHit("sister is Aoife", similarity: 0.75),
        ]
        let (knowledge, memories) = GroundingGate.partition(hits)
        #expect(knowledge.map(\.content) == ["doc fact"])
        #expect(memories.map(\.content) == ["sister is Aoife"])
    }

    @Test("memory hits clear a LOWER bar — short facts sit lower in BGE's cone")
    func memoryBandKept() {
        // A similarity in the band between memoryThreshold and chunkThreshold:
        // kept as a memory, dropped as a document.
        let band = (GroundingGate.memoryThreshold + GroundingGate.chunkThreshold) / 2
        let (knowledge, memories) = GroundingGate.partition([
            hit("doc in band", similarity: band),
            memoryHit("memory in band", similarity: band),
        ])
        #expect(knowledge.isEmpty)
        #expect(memories.map(\.content) == ["memory in band"])
    }

    @Test("a memory below memoryThreshold is dropped")
    func memoryBelowBarDropped() {
        let below = GroundingGate.memoryThreshold - 0.001
        let (_, memories) = GroundingGate.partition([memoryHit("too weak", similarity: below)])
        #expect(memories.isEmpty)
        // Boundary is inclusive, same contract as the chunk gate.
        let (_, kept) = GroundingGate.partition(
            [memoryHit("at bar", similarity: GroundingGate.memoryThreshold)]
        )
        #expect(kept.count == 1)
    }

    @Test("FTS-only memories are dropped — the no-keyword-flood rule holds for memory too")
    func ftsOnlyMemoryDropped() {
        let (_, memories) = GroundingGate.partition([memoryHit("fts only", similarity: nil)])
        #expect(memories.isEmpty)
    }

    @Test("with no memory hits, partition.knowledge ≡ filter — the legacy pin")
    func partitionMatchesFilterWithoutMemories() {
        let hits = [
            hit("relevant", similarity: 0.78),
            hit("noise", similarity: 0.58),
            hit("fts only", similarity: nil),
        ]
        let (knowledge, memories) = GroundingGate.partition(hits)
        #expect(memories.isEmpty)
        #expect(knowledge.map(\.content) == GroundingGate.filter(hits).map(\.content))
    }

    @Test("memoryThreshold is provisional 0.60 and below chunkThreshold")
    func memoryThresholdPinned() {
        // Replaced from MEMEVAL distribution data — update this pin with it.
        #expect(GroundingGate.memoryThreshold == 0.60)
        #expect(GroundingGate.memoryThreshold < GroundingGate.chunkThreshold)
    }
}
