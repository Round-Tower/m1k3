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
    /// Minimum cosine similarity for a chunk to be injected. bge-small over
    /// normalised vectors: unrelated content typically lands ≈0.3–0.45,
    /// topical content ≈0.55+. Starting point, not gospel — the responder
    /// logs every hit's score so this can be tuned on real queries.
    public static let chunkThreshold: Float = 0.45

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
