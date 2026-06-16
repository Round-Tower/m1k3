//
//  ConstellationSeed.swift
//  M1K3Memory
//
//  Merges the live memory graph with SEED memories pulled from elsewhere (the
//  document/knowledge store's `.memory` items) so the constellation is never
//  empty on first open — it shows what M1K3 already knows about you, then grows
//  as the graph store fills. Pure: dedups by text so a fact that's been
//  dual-written into both stores shows once (the graph copy wins, since it
//  carries edges and supersession).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.85 (dedup is text-
//  normalised, graph-wins; TDD'd). Prior: Unknown.

import Foundation

public enum ConstellationSeed {
    /// `graph` (the real MemoryStore graph, with edges) plus any `seeds` whose
    /// text isn't already in the graph. Graph entries come first and always win
    /// a duplicate, so seeding can only ADD motes, never shadow the live graph.
    public static func merge(graph: [Memory], seeds: [Memory]) -> [Memory] {
        let seen = Set(graph.map { normalize($0.text) })
        return graph + seeds.filter { !seen.contains(normalize($0.text)) }
    }

    /// Trim + case-fold so "Kev lives in Cork" and "  kev lives in cork " dedupe.
    static func normalize(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
}
