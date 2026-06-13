//
//  GroundingGate.swift
//  M1K3Knowledge
//
//  Relevance gate for retrieve-first grounding. Hybrid search always returns
//  top-K — even for "what model are you?" — so ungated injection pollutes
//  every prompt with whatever the store happens to hold, derailing small
//  reasoning models. The gate keeps a hit only when its vector similarity says
//  the query is actually about the store's content; when nothing is topical it
//  injects NOTHING and the model decides via the search_knowledge tool.
//
//  Gates on cosine similarity, NOT rrfScore: RRF is rank fusion — its scale
//  (~(0, 0.0167] per ranking) says how results order, never how relevant any
//  of them is in absolute terms.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8 (thresholds are
//  empirical starting points for bge-small — tune via the responder score
//  logs at ⌘R and update the constants). Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-12, Confidence 0.9 — kind-aware
//  `relevant` (one order-preserving list for the explicit search floor) +
//  shared `clears` predicate; removed the dead public `filter` (kind-unaware,
//  zero production callers — a divergence trap beside `relevant`).
//  Review: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.85 — chunkThreshold
//  0.62 → 0.68 from a live MCP gate-log measurement (off-domain noise ceiling
//  ~0.63, in-domain floor 0.736; the old 0.62 sat in the noise and leaked a
//  CoT chunk as a "source" for a sourdough query). memoryThreshold left at
//  0.54 (MEMEVAL-governed overlap). verify-at-⌘R on the live MCP loop.
//

import Foundation

public enum GroundingGate {
    /// Minimum cosine similarity for a chunk to be injected. BGE-family
    /// embeddings live in a NARROW cosine cone — unrelated pairs commonly
    /// score 0.55–0.7, topical ≈0.74+ — so a "generous" floor passes
    /// everything (proven live 2026-06-10: 0.45 injected arxiv chunks for
    /// "Yo mike, what's up?").
    ///
    /// 0.68 from a live MCP measurement (2026-06-12, real BGE on device,
    /// `ask_m1k3` gate logs): OFF-domain queries vs the ML-paper corpus —
    /// sourdough 0.629, apple-pruning 0.610, JS-frontend 0.630 — peaked at
    /// ~0.63 (a flat noise band, no standout); the IN-domain "attention"
    /// query floored at 0.736 (a tight high cluster). 0.62 sat INSIDE the
    /// noise — sourdough's 0.629 chunk squeaked through and got cited as a
    /// source for bread. 0.68 is the maximal-margin centre of the [0.63, 0.74]
    /// dead zone: garbage gated, real hits kept. The "flat pack = no real
    /// match" margin heuristic and a MEMEVAL-style CHUNKEVAL sweep are the
    /// durable upgrade paths if a fixed floor keeps misfiring.
    public static let chunkThreshold: Float = 0.68

    /// Minimum cosine similarity for a MEMORY hit. Memories are 5–40-token
    /// atomic facts; query-to-short-fact pairs sit LOWER in BGE's cone than
    /// query-to-chunk — measured via M1K3_SELFTEST_MEMEVAL (2026-06-12, real
    /// BGE on device): positives 0.547–0.856 (median 0.651), negatives
    /// 0.281–0.556 (median 0.427). The classes overlap — no clean cut exists.
    /// 0.54 keeps 22/22 true recalls and admits 1/11 negatives; 0.60 lost
    /// 5/22 user-given facts. Recall wins the asymmetry: a memory miss breaks
    /// the "I'll remember" promise, a false positive is one short bullet.
    /// Per-query normalisation is the upgrade path if stray bullets annoy.
    public static let memoryThreshold: Float = 0.54

    /// Split gated hits for the two prompt blocks: `.memory` hits (cleared
    /// `memoryThreshold`) feed the WHAT-I-KNOW-ABOUT-YOU block; everything
    /// else (cleared `chunkThreshold`) feeds KNOWLEDGE exactly as `relevant`
    /// would. FTS-only hits (nil similarity) are dropped from BOTH — the
    /// no-keyword-flood rule doesn't care what kind the noise is.
    public static func partition(_ hits: [ChunkHit]) -> (knowledge: [ChunkHit], memories: [ChunkHit]) {
        var knowledge: [ChunkHit] = []
        var memories: [ChunkHit] = []
        for hit in hits where clears(hit) {
            if hit.kind == .memory {
                memories.append(hit)
            } else {
                knowledge.append(hit)
            }
        }
        return (knowledge, memories)
    }

    /// The same kind-aware bar as `partition`, kept as ONE order-preserving
    /// list — the relevance floor for the explicit search_knowledge path,
    /// where the model needs the fused ranking, not the two prompt blocks.
    /// A hit must clear its bar ON ITS OWN: an FTS-only hit (no vector score)
    /// appeared only in the keyword ranking, never the vector top-K — lexical
    /// coincidence, not relevance — so it is dropped, NOT carried in on a
    /// topical sibling. (Letting it ride along flooded a "what's the weather
    /// in Cork" prompt with 7KB of keyword-matched noise that drowned the
    /// web_search routing — the ⌘R weather bug.)
    public static func relevant(_ hits: [ChunkHit]) -> [ChunkHit] {
        hits.filter(clears)
    }

    /// The single relevance predicate behind `relevant` and `partition`:
    /// memories clear `memoryThreshold`, everything else `chunkThreshold`;
    /// an FTS-only hit (nil similarity) never clears.
    private static func clears(_ hit: ChunkHit) -> Bool {
        guard let similarity = hit.similarity else { return false }
        return similarity >= (hit.kind == .memory ? memoryThreshold : chunkThreshold)
    }
}
