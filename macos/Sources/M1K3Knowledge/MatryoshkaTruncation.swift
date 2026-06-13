//
//  MatryoshkaTruncation.swift
//  M1K3Knowledge
//
//  Matryoshka Representation Learning (MRL) truncation. Models like
//  EmbeddingGemma-300m are trained so that any LEADING PREFIX of the full
//  768-dim embedding is itself a valid, lower-fidelity embedding — letting us
//  trade vector width (storage + cosine cost) for a small fidelity loss without
//  re-embedding. The one catch: the model's output is unit-length, and slicing
//  a unit vector breaks that, so the prefix MUST be re-normalized before it's
//  stored or compared. This is pure [Float] math — kept out of the MLX/Metal
//  layer so it's unit-testable in the fast loop.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-13, Confidence 0.9, Prior: Unknown
//

import Foundation

public enum MatryoshkaTruncation {
    /// Truncate `vector` to its first `k` dimensions (MRL prefix) and
    /// re-normalize to unit length. `k <= 0` yields an empty vector; `k`
    /// greater than the vector's length returns the whole (renormalized)
    /// vector — never padded. A zero vector truncates to zeros, never NaN.
    public static func truncate(_ vector: [Float], to k: Int) -> [Float] {
        guard k > 0 else { return [] }
        let prefix = k >= vector.count ? vector : Array(vector.prefix(k))
        return VectorMath.l2Normalized(prefix)
    }

    /// Like `truncate`, but throws `EmbeddingError.dimensionMismatch` when the
    /// source vector is NARROWER than the target `k`. A model that emits fewer
    /// dimensions than we target (e.g. a mis-converted 4-bit MLX checkpoint)
    /// would otherwise be padded/whole-returned and silently poison every
    /// cosine — this surfaces it loudly on the first embed call instead.
    public static func truncateValidated(_ vector: [Float], to k: Int) throws -> [Float] {
        // A non-positive target is a configuration bug, not data — the plain
        // `truncate` swallows it into []; here we refuse it loudly so it can't
        // persist empty vectors across a whole store.
        guard k > 0 else {
            throw EmbeddingError.dimensionMismatch(expected: k, got: vector.count)
        }
        guard vector.count >= k else {
            throw EmbeddingError.dimensionMismatch(expected: k, got: vector.count)
        }
        return truncate(vector, to: k)
    }
}
