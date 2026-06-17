//
//  MemoryAffinityTests.swift
//  M1K3MemoryTests
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.82. Prior: this file.

import Foundation
@testable import M1K3Memory
import Testing

private func mem(_ text: String) -> Memory {
    Memory(kind: .note, text: text, source: "test")
}

struct MemoryAffinityTests {
    @Test("memories sharing a salient word are linked")
    func sharedWordLinks() {
        let a = mem("Kev's sister is Aoife")
        let b = mem("Aoife works in Galway")
        let edges = MemoryAffinity.edges(among: [a, b])
        #expect(edges.count == 1)
        #expect(edges.first?.relation == "shared-topic")
    }

    @Test("memories sharing only stopwords / short words are NOT linked")
    func stopwordsDoNotLink() {
        let a = mem("the cat is out")
        let b = mem("you and the dog")
        // Only "the" (stopword) overlaps → no salient shared token.
        #expect(MemoryAffinity.edges(among: [a, b]).isEmpty)
    }

    @Test("each node keeps at most maxPerNode links (no hairball)")
    func capsPerNode() {
        // A hub mentioning "biscuit" plus six others each sharing only "biscuit".
        let hub = mem("biscuit biscuit biscuit")
        let others = (1 ... 6).map { mem("biscuit topic\($0)") }
        let edges = MemoryAffinity.edges(among: [hub] + others, maxPerNode: 4)
        let hubDegree = edges.filter { $0.fromID == hub.id || $0.toID == hub.id }.count
        #expect(hubDegree <= 4)
    }

    @Test("deterministic — same input, same edges")
    func deterministic() {
        let ms = [mem("alpha beta gamma"), mem("beta gamma delta"), mem("gamma delta epsilon")]
        #expect(MemoryAffinity.edges(among: ms) == MemoryAffinity.edges(among: ms))
    }

    @Test("a single memory yields no edges")
    func singleNoEdges() {
        #expect(MemoryAffinity.edges(among: [mem("alone")]).isEmpty)
    }
}
