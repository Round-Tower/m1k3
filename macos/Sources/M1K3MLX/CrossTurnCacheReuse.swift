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

    /// Whether the live cache may be trimmed-and-reused this turn (and whether
    /// its post-generation tail may be trimmed back into a faithful mirror).
    ///
    /// Reuse trims the cache down to the reusable prefix and prefills only the
    /// suffix — which assumes a LINEAR cache, where a token's logical position
    /// equals its physical slot. Qwen3's `KVCacheSimple` is always linear. A
    /// sliding-window cache (gemma-4's `RotatingKVCache`, `keep: 0`) holds that
    /// only until it WRAPS: past the window, `trim(_:)` clamps against `offset`,
    /// not the internal rotation pointer `idx`, so trimming more than `idx`
    /// drives it negative and the next decode asserts in `temporalOrder`
    /// (`array[..., 0 ..< idx, ...]` with `idx < 0`). Upstream signals the
    /// wrapped state as `isTrimmable == false` (`offset >= maxSize`), so trim —
    /// for reuse OR for the post-turn mirror — is safe iff EVERY layer is
    /// currently trimmable. An empty list means no live cache: not reusable.
    static func cacheReusable(layersTrimmable: [Bool]) -> Bool {
        !layersTrimmable.isEmpty && layersTrimmable.allSatisfy { $0 }
    }

    /// Whether the suffix-reuse fast path may run for this turn. An
    /// image-carrying turn must NOT: the reuse branch rebuilds the prefill
    /// input as `LMInput(tokens: fullIDs[reuse...])` — raw token ids — while a
    /// prepared image turn carries its pixels OUTSIDE the token array
    /// (`LMInput.image`, absolute position ids). Slicing would silently drop
    /// the image and leave dangling placeholder tokens. Image turns prefill
    /// the FULL prepared input on a fresh cache instead — correctness over
    /// the persona-prefix win. (Follow-up optimization: suffix reuse with
    /// pixels intact needs positionId rebasing upstream.)
    static func suffixReuseAllowed(turnCarriesImages: Bool) -> Bool {
        !turnCarriesImages
    }
}
