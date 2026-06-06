//
//  RRFFusionTests.swift
//  M1K3KnowledgeTests
//
//  Contract tests for Reciprocal Rank Fusion. Covers the scoring formula,
//  the dual-list-beats-single-list invariant (the whole point of RRF),
//  dedupe by key, tie-break stability, and degenerate inputs.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

@testable import M1K3Knowledge
import Testing

struct RRFFusionTests {
    @Test("score formula is 1 / (k + rank + 1)")
    func scoreFormula() {
        #expect(ReciprocalRankFusion.score(rank: 0, k: 60) == 1.0 / 61.0)
        #expect(ReciprocalRankFusion.score(rank: 2, k: 60) == 1.0 / 63.0)
    }

    @Test("an item ranked in BOTH lists outranks a #1-only item")
    func dualListBeatsSingle() {
        // "b" is #2 in both lists; "a" is #1 in one only. RRF should lift "b".
        let listA = ["a", "b", "c"]
        let listB = ["x", "b", "y"]
        let fused = ReciprocalRankFusion.fuse(rankings: [listA, listB], key: { $0 })
        #expect(fused.first == "b")
    }

    @Test("items are deduped by key")
    func dedupe() {
        let listA = ["a", "b"]
        let listB = ["b", "a"]
        let fused = ReciprocalRankFusion.fuse(rankings: [listA, listB], key: { $0 })
        #expect(fused.count == 2)
        #expect(Set(fused) == ["a", "b"])
    }

    @Test("ties break by first-seen insertion order")
    func tieBreakStable() {
        // Two single-list items at the same rank → insertion order wins.
        let listA = ["first"]
        let listB = ["second"]
        let fused = ReciprocalRankFusion.fuse(rankings: [listA, listB], key: { $0 })
        #expect(fused == ["first", "second"])
    }

    @Test("custom key fuses structured items by identity")
    func customKey() {
        struct Chunk: Equatable { let id: Int; let text: String }
        let listA = [Chunk(id: 1, text: "from-fts"), Chunk(id: 2, text: "x")]
        let listB = [Chunk(id: 3, text: "y"), Chunk(id: 1, text: "from-vector")]
        let fused = ReciprocalRankFusion.fuse(rankings: [listA, listB], key: { $0.id })
        // id 1 appears in both → ranks first; first-seen copy is kept.
        #expect(fused.first?.id == 1)
        #expect(fused.first?.text == "from-fts")
    }

    @Test("empty rankings fuse to empty")
    func emptyInput() {
        let fused = ReciprocalRankFusion.fuse(rankings: [[String]](), key: { $0 })
        #expect(fused.isEmpty)
    }

    @Test("a single ranking passes through in order")
    func singleRanking() {
        let fused = ReciprocalRankFusion.fuse(rankings: [["a", "b", "c"]], key: { $0 })
        #expect(fused == ["a", "b", "c"])
    }
}
