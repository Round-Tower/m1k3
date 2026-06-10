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

    /// Filter retrieved hits down to the ones worth injecting. An FTS-only
    /// hit (no vector score) survives only when at least one vector hit passed
    /// — a keyword match against an off-topic query is exactly the noise this
    /// gate exists to drop.
    public static func filter(_ hits: [ChunkHit]) -> [ChunkHit] {
        let anyTopical = hits.contains { ($0.similarity ?? 0) >= chunkThreshold }
        guard anyTopical else { return [] }
        return hits.filter { hit in
            guard let similarity = hit.similarity else { return true }
            return similarity >= chunkThreshold
        }
    }
}
