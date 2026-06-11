//
//  CrossTurnCacheReuse.swift
//  M1K3MLX
//
//  Pure decision for cross-turn KV reuse in MLXToolTurnSession — see
//  CrossTurnCacheReuseTests for the worked cases and the invariant it rests on.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-11, Confidence 0.9. Prior: Unknown
//

import Foundation

enum CrossTurnCacheReuse {
    /// Leading tokens of `full` already present (positionally) in the live
    /// cache, hence skippable at prefill. Zero without a live cache. Clamped to
    /// `full.count - 1` so at least one token is always left to prefill —
    /// generation needs a non-empty input to produce the next logits.
    static func reusableLength(cached: [Int], full: [Int], hasCache: Bool) -> Int {
        guard hasCache, !full.isEmpty else { return 0 }
        let common = SystemBlockBoundary.commonPrefixLength(cached, full)
        return min(common, full.count - 1)
    }
}
