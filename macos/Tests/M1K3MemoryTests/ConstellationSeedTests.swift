//
//  ConstellationSeedTests.swift
//  M1K3MemoryTests
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.85. Prior: this file.

import Foundation
@testable import M1K3Memory
import Testing

private func mem(_ text: String) -> Memory {
    Memory(kind: .note, text: text, source: "test")
}

struct ConstellationSeedTests {
    @Test("seeds add fresh memories the graph doesn't have")
    func addsFreshSeeds() {
        let graph = [mem("Kev lives in Cork")]
        let seeds = [mem("Kev has a dog named Biscuit")]
        let merged = ConstellationSeed.merge(graph: graph, seeds: seeds)
        #expect(merged.count == 2)
        #expect(merged.contains { $0.text == "Kev has a dog named Biscuit" })
    }

    @Test("a seed already in the graph (by normalised text) is dropped")
    func dedupesByText() {
        let graph = [mem("Kev lives in Cork")]
        let seeds = [mem("  kev lives in cork "), mem("new fact")]
        let merged = ConstellationSeed.merge(graph: graph, seeds: seeds)
        #expect(merged.count == 2) // graph + "new fact"; the dup seed dropped
        #expect(merged.filter { ConstellationSeed.normalize($0.text) == "kev lives in cork" }.count == 1)
    }

    @Test("graph entries come first and win duplicates")
    func graphWins() {
        let graphFact = mem("shared fact")
        let merged = ConstellationSeed.merge(graph: [graphFact], seeds: [mem("shared fact")])
        #expect(merged.count == 1)
        #expect(merged.first?.id == graphFact.id) // the graph copy, not the seed
    }

    @Test("an empty graph yields all the seeds (the first-open case)")
    func emptyGraphAllSeeds() {
        let seeds = [mem("a"), mem("b"), mem("c")]
        #expect(ConstellationSeed.merge(graph: [], seeds: seeds).count == 3)
    }
}
