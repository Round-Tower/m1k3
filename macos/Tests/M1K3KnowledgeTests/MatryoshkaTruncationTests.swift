//
//  MatryoshkaTruncationTests.swift
//  M1K3KnowledgeTests
//
//  Contract tests for Matryoshka (MRL) embedding truncation. EmbeddingGemma-300m
//  emits a 768-dim unit vector trained so that any leading prefix is itself a
//  valid (lower-fidelity) embedding — but only AFTER re-normalizing, since
//  chopping a unit vector breaks unit length. These tests pin: prefix
//  correctness, unit-length renormalization, the k>=count boundary, and the
//  zero-vector guard (return zeros, never NaN — mirrors VectorMath).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-13, Confidence 0.9, Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

struct MatryoshkaTruncationTests {
    @Test("truncating to k returns the first k elements (direction preserved)")
    func prefixCorrectness() {
        let v = (0 ..< 768).map { Float($0) + 1 } // non-zero, strictly increasing
        let out = MatryoshkaTruncation.truncate(v, to: 512)
        #expect(out.count == 512)

        // The truncated vector must point the same direction as the manually
        // sliced-then-normalized prefix — cosine 1.0.
        let manual = VectorMath.l2Normalized(Array(v.prefix(512)))
        #expect(abs(VectorMath.cosineSimilarity(out, manual) - 1.0) < 1e-5)
    }

    @Test("the truncated vector is unit length")
    func renormalizesToUnitLength() {
        let v = (0 ..< 768).map { _ in Float.random(in: -1 ... 1) }
        let out = MatryoshkaTruncation.truncate(v, to: 512)
        let norm = sqrt(out.reduce(Float(0)) { $0 + $1 * $1 })
        #expect(abs(norm - 1.0) < 1e-5)
    }

    @Test("k >= count returns the renormalized whole vector, not padded")
    func boundaryAtOrAboveCount() {
        let v: [Float] = [3, 4] // norm 5 → normalizes to [0.6, 0.8]
        let exact = MatryoshkaTruncation.truncate(v, to: 2)
        let over = MatryoshkaTruncation.truncate(v, to: 99)
        #expect(exact.count == 2)
        #expect(over.count == 2) // never pads beyond the source
        #expect(abs(exact[0] - 0.6) < 1e-5 && abs(exact[1] - 0.8) < 1e-5)
        #expect(over == exact)
    }

    @Test("a zero vector truncates to zeros, never NaN")
    func zeroVectorGuard() {
        let out = MatryoshkaTruncation.truncate([Float](repeating: 0, count: 768), to: 512)
        #expect(out.count == 512)
        #expect(out.allSatisfy { $0 == 0 })
        #expect(out.allSatisfy { !$0.isNaN })
    }

    @Test("k <= 0 returns an empty vector")
    func nonPositiveK() {
        #expect(MatryoshkaTruncation.truncate([1, 2, 3], to: 0).isEmpty)
        #expect(MatryoshkaTruncation.truncate([1, 2, 3], to: -5).isEmpty)
    }

    @Test("truncateValidated truncates when the source is wide enough")
    func validatedHappyPath() throws {
        let v = (0 ..< 768).map { Float($0) + 1 }
        let out = try MatryoshkaTruncation.truncateValidated(v, to: 512)
        #expect(out.count == 512)
    }

    @Test("truncateValidated throws dimensionMismatch when the source is too narrow")
    func validatedTooNarrowThrows() {
        // A mis-converted MLX embedder emitting fewer dims than we target must
        // fail LOUD on the first call, not silently store a truncated-to-nothing
        // vector that poisons every cosine.
        #expect(throws: EmbeddingError.self) {
            _ = try MatryoshkaTruncation.truncateValidated([1, 2, 3], to: 512)
        }
    }

    @Test("truncateValidated throws on a non-positive target dimension")
    func validatedNonPositiveThrows() {
        // The plain `truncate` returns [] for k<=0 by contract; the VALIDATED
        // wrapper must instead fail loud — a zero/negative target is a config
        // bug that would otherwise persist empty embeddings store-wide.
        #expect(throws: EmbeddingError.self) {
            _ = try MatryoshkaTruncation.truncateValidated([1, 2, 3], to: 0)
        }
    }
}
