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
//

import Foundation

public enum GroundingGate {
    /// Minimum cosine similarity for a chunk to be injected. BGE-family
    /// embeddings live in a NARROW cosine cone — unrelated pairs commonly
    /// score 0.55–0.7, topical ≈0.72+ — so a "generous" floor passes
    /// everything (proven live 2026-06-10: 0.45 injected arxiv chunks for
    /// "Yo mike, what's up?"). Tune from the per-hit responder logs; per-query
    /// normalisation is the upgrade path if a fixed floor keeps misfiring.
    public static let chunkThreshold: Float = 0.62

    /// Filter retrieved hits down to the ones worth injecting: a hit must clear
    /// the cosine bar ON ITS OWN. An FTS-only hit (no vector score) appeared
    /// only in the keyword ranking, never the vector top-K — lexical
    /// coincidence, not relevance — so it is dropped, NOT carried in on a
    /// topical sibling. (Letting it ride along flooded a "what's the weather in
    /// Cork" prompt with 7KB of keyword-matched noise that drowned the
    /// web_search routing — the ⌘R weather bug.) When nothing clears the bar,
    /// nothing is injected and the model retrieves on its own via search_knowledge.
    public static func filter(_ hits: [ChunkHit]) -> [ChunkHit] {
        hits.filter { ($0.similarity ?? 0) >= chunkThreshold }
    }

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
    /// else (cleared `chunkThreshold`) feeds KNOWLEDGE exactly as `filter`
    /// would. FTS-only hits (nil similarity) are dropped from BOTH — the
    /// no-keyword-flood rule doesn't care what kind the noise is.
    public static func partition(_ hits: [ChunkHit]) -> (knowledge: [ChunkHit], memories: [ChunkHit]) {
        var knowledge: [ChunkHit] = []
        var memories: [ChunkHit] = []
        for hit in hits {
            guard let similarity = hit.similarity else { continue }
            if hit.kind == .memory {
                if similarity >= memoryThreshold { memories.append(hit) }
            } else if similarity >= chunkThreshold {
                knowledge.append(hit)
            }
        }
        return (knowledge, memories)
    }
}
