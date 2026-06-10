//
//  RRFFusion.swift
//  M1K3Knowledge
//
//  Pure Reciprocal Rank Fusion for hybrid search (FTS5 + vector). Items
//  appearing in both rankings rank higher than items in either alone, with
//  the cost of being out of top-rank in one source softened by being in the
//  other. The standard k=60 constant comes from Cormack et al. (2009); it
//  damps the early-rank advantage so a #3 hit in two rankings can outrank a
//  #1-only hit.
//
//  ── Review ───────────────────────────────────────────────────────────────
//  Ported verbatim into M1K3Knowledge from the prior knowledge-server project's the internal knowledge-server core/RRFFusion.swift
//  to back the Mac-native hybrid search. Logic unchanged.
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.95,
//  Prior: the prior knowledge-server project the internal knowledge-server core/RRFFusion.swift (Kev)

import Foundation

public enum ReciprocalRankFusion {
    /// Standard RRF constant. Cormack et al. (2009).
    public static let defaultK = 60

    /// Score for a single item at a given 0-based rank position. `1 / (k + rank + 1)`.
    /// Exposed for unit-level verification of the scoring math.
    public static func score(rank: Int, k: Int = defaultK) -> Double {
        1.0 / Double(k + rank + 1)
    }

    /// Fuse N ranked lists into one ordered list. Each input list is assumed
    /// to be in descending-relevance order (most relevant first). Items
    /// appearing in multiple lists accumulate score and rank above items in
    /// only one list. Ties broken by first-seen insertion order.
    ///
    /// `key` extracts the dedupe identity for each item — typically the chunk
    /// id when fusing FTS + vector results over the same chunks.
    public static func fuse<T, Key: Hashable>(
        rankings: [[T]],
        key: (T) -> Key,
        k: Int = defaultK
    ) -> [T] {
        fuseScored(rankings: rankings, key: key, k: k).map(\.item)
    }

    /// `fuse`, but each fused item carries its accumulated RRF score — for
    /// callers that gate or log on relevance rather than just ordering.
    public static func fuseScored<T, Key: Hashable>(
        rankings: [[T]],
        key: (T) -> Key,
        k: Int = defaultK
    ) -> [(item: T, score: Double)] {
        var scores: [Key: Double] = [:]
        var firstSeen: [Key: T] = [:]
        var insertionOrder: [Key] = []

        for ranking in rankings {
            for (rank, item) in ranking.enumerated() {
                let id = key(item)
                scores[id, default: 0] += score(rank: rank, k: k)
                if firstSeen[id] == nil {
                    firstSeen[id] = item
                    insertionOrder.append(id)
                }
            }
        }

        // Sort by RRF score descending; preserve insertion order on tie.
        let orderIndex = Dictionary(uniqueKeysWithValues: insertionOrder.enumerated().map { ($1, $0) })
        let sortedKeys = scores.keys.sorted { lhs, rhs in
            let lscore = scores[lhs] ?? 0
            let rscore = scores[rhs] ?? 0
            if lscore != rscore { return lscore > rscore }
            return (orderIndex[lhs] ?? 0) < (orderIndex[rhs] ?? 0)
        }
        return sortedKeys.compactMap { id in
            firstSeen[id].map { ($0, scores[id] ?? 0) }
        }
    }
}
