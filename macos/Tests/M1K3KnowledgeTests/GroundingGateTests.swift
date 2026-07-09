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

    @Test("topical hits pass; noise-band hits are dropped")
    func dropsWeakHits() {
        // Instructed-query cone (KEYEVAL 2026-07-09): off-domain queries top
        // out at 0.234 against the corpus and the keyword-register noise at
        // 0.203; real topical hits floor at 0.510. The chunk floor (0.37)
        // sits in the dead zone between, so a 0.30 "borderline" is NOISE
        // and a 0.23 clearly is — neither survives.
        let hits = [
            hit("relevant", similarity: 0.78),
            hit("topical", similarity: 0.52),
            hit("noise ceiling", similarity: 0.30),
            hit("noise", similarity: 0.23),
        ]
        let kept = GroundingGate.relevant(hits)
        #expect(kept.map(\.content) == ["relevant", "topical"])
    }

    @Test("when no hit is topical, nothing is injected at all")
    func allWeakInjectsNothing() {
        let hits = [hit("noise a", similarity: 0.30), hit("noise b", similarity: 0.22)]
        #expect(GroundingGate.relevant(hits).isEmpty)
    }

    @Test("an FTS-only hit (no vector score) is never injected — even beside a topical hit")
    func ftsOnlyAlwaysDropped() {
        // A nil-similarity hit appeared ONLY in the keyword ranking, never the
        // vector top-K (post-backfill): "Cork/weather/today" matching stored
        // docs is lexical coincidence, not topical relevance. One borderline
        // vector hit must NOT flood the prompt with all the keyword noise (the
        // ⌘R weather bug: 7KB of grounding for "what's the weather in Cork").
        let beside = [hit("vector match", similarity: 0.74), hit("fts only", similarity: nil)]
        #expect(GroundingGate.relevant(beside).map(\.content) == ["vector match"])

        let alone = [hit("fts only", similarity: nil)]
        #expect(GroundingGate.relevant(alone).isEmpty)
    }

    @Test("an empty retrieval stays empty")
    func emptyStaysEmpty() {
        #expect(GroundingGate.relevant([]).isEmpty)
    }

    @Test("the threshold boundary is inclusive: exactly the chunk floor passes, just below is gated")
    func thresholdBoundary() {
        // Pins the CONSTANT, not just the ordering — a silent threshold edit
        // (or >= flipping to >) should fail here, not at ⌘R.
        let exactlyAt = hit("at threshold", similarity: GroundingGate.chunkThreshold)
        let justBelow = hit("below threshold", similarity: GroundingGate.chunkThreshold - 0.001)
        #expect(GroundingGate.relevant([exactlyAt]).count == 1)
        #expect(GroundingGate.relevant([justBelow]).isEmpty)
        // A passing sibling never carries a below-threshold hit through.
        #expect(GroundingGate.relevant([exactlyAt, justBelow]).map(\.content) == ["at threshold"])
        // Re-pinned 0.51 → 0.37 (2026-07-09): the query-instruction adoption
        // re-derived the floor from the instructed ABSEP dead-zone
        // [0.234, 0.510] centre — 0.51 sat ON the weakest instructed positive.
        #expect(GroundingGate.chunkThreshold == 0.37)
    }

    @Test("the chunk floor sits in the measured dead zone between noise and signal")
    func floorInDeadZone() {
        // ABSEP 2026-07-09 (real qwen3-embed-512 on device, INSTRUCTED
        // queries via EmbeddingText.forQuery): off-domain ceiling 0.234,
        // in-domain floor 0.510. The floor must clear the noise ceiling and
        // stay below the signal floor — the whole confabulation fix.
        #expect(GroundingGate.chunkThreshold > 0.234)
        #expect(GroundingGate.chunkThreshold < 0.510)
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

    @Test("with no memory hits, partition.knowledge ≡ relevant — the legacy pin")
    func partitionMatchesRelevantWithoutMemories() {
        let hits = [
            hit("relevant", similarity: 0.78),
            hit("noise", similarity: 0.30),
            hit("fts only", similarity: nil),
        ]
        let (knowledge, memories) = GroundingGate.partition(hits)
        #expect(memories.isEmpty)
        #expect(knowledge.map(\.content) == GroundingGate.relevant(hits).map(\.content))
    }

    // MARK: - Kind-aware relevance (the explicit search_knowledge floor)

    @Test("relevant keeps retrieval order and holds each hit to its kind's bar")
    func relevantIsKindAwareAndOrdered() {
        // A similarity in the band between the two thresholds: a memory at
        // that score is relevant, a document is not — same contract as
        // partition, but as one order-preserving list for the tool path.
        let band = (GroundingGate.memoryThreshold + GroundingGate.chunkThreshold) / 2
        let hits = [
            hit("topical doc", similarity: 0.78),
            memoryHit("memory in band", similarity: band),
            hit("doc in band", similarity: band),
            hit("fts only", similarity: nil),
        ]
        #expect(GroundingGate.relevant(hits).map(\.content) == ["topical doc", "memory in band"])
    }

    @Test("relevant and partition agree — one predicate, two views")
    func relevantMatchesPartition() {
        let hits = [
            hit("doc", similarity: 0.7),
            memoryHit("mem", similarity: 0.6),
            hit("weak doc", similarity: 0.30),
            memoryHit("weak mem", similarity: 0.25),
            hit("fts only", similarity: nil),
        ]
        let (knowledge, memories) = GroundingGate.partition(hits)
        let relevant = GroundingGate.relevant(hits)
        #expect(relevant.count == knowledge.count + memories.count)
        #expect(relevant.map(\.content) == ["doc", "mem"])
    }

    @Test("relevant of nothing is nothing")
    func relevantEmptyStaysEmpty() {
        #expect(GroundingGate.relevant([]).isEmpty)
    }

    @Test("memoryThreshold is 0.35 (KEYEVAL 2026-07-09, instructed queries) and below chunkThreshold")
    func memoryThresholdPinned() {
        // Re-pinned 0.39 → 0.35 for the query-instruction adoption: the same
        // MEMEVAL pairs with instructed queries separate CLEANLY (pos min
        // 0.437 / neg max 0.260, suggested midpoint 0.349) where bare
        // overlapped — and 0.35 admits the keyword register (instructed
        // keyword positives floor 0.420) the old bar was measured missing
        // live on 2026-07-08. See the GroundingGate doc comment.
        #expect(GroundingGate.memoryThreshold == 0.35)
        #expect(GroundingGate.memoryThreshold < GroundingGate.chunkThreshold)
    }

    @Test("edgeThreshold holds the content↔content bar at 0.51 — it must NOT move with query-side re-tunes")
    func edgeThresholdPinned() {
        // Fact→fact edge cosines carry no query instruction: the 07-09
        // query-floor re-derivation deliberately left edge formation at the
        // value chunkThreshold held when the two-bar doctrine was pinned.
        #expect(GroundingGate.edgeThreshold == 0.51)
        #expect(GroundingGate.edgeThreshold > GroundingGate.chunkThreshold)
    }
}
