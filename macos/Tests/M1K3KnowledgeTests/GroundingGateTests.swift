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

    @Test("an FTS-only hit (no similarity) survives only beside a topical vector hit")
    func ftsOnlyNeedsTopicalSibling() {
        let topical = [hit("vector match", similarity: 0.74), hit("fts only", similarity: nil)]
        #expect(GroundingGate.filter(topical).count == 2)

        let alone = [hit("fts only", similarity: nil)]
        #expect(GroundingGate.filter(alone).isEmpty)
    }

    @Test("an empty retrieval stays empty")
    func emptyStaysEmpty() {
        #expect(GroundingGate.filter([]).isEmpty)
    }
}
