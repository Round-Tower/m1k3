//
//  VectorMathTests.swift
//  M1K3KnowledgeTests
//
//  Contract tests for the ported pure vector math. Covers cosine identity,
//  orthogonality, opposition, the zero-vector guard, dimension mismatch,
//  and BLOB serialization round-trip.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct VectorMathTests {
    @Test("identical vectors have cosine similarity 1.0")
    func identical() {
        let v: [Float] = [1, 2, 3, 4]
        #expect(abs(VectorMath.cosineSimilarity(v, v) - 1.0) < 1e-5)
    }

    @Test("orthogonal vectors have cosine similarity 0.0")
    func orthogonal() {
        #expect(abs(VectorMath.cosineSimilarity([1, 0], [0, 1])) < 1e-6)
    }

    @Test("opposite vectors have cosine similarity -1.0")
    func opposite() {
        #expect(abs(VectorMath.cosineSimilarity([1, 2], [-1, -2]) - -1.0) < 1e-5)
    }

    @Test("magnitude does not affect cosine — only direction")
    func magnitudeInvariant() {
        let a: [Float] = [1, 1, 1]
        let b: [Float] = [10, 10, 10]
        #expect(abs(VectorMath.cosineSimilarity(a, b) - 1.0) < 1e-5)
    }

    @Test("zero-length vector returns 0.0, not NaN")
    func zeroVector() {
        let sim = VectorMath.cosineSimilarity([0, 0, 0], [1, 2, 3])
        #expect(sim == 0.0)
        #expect(!sim.isNaN)
    }

    @Test("empty vectors return 0.0")
    func empty() {
        #expect(VectorMath.cosineSimilarity([], []) == 0.0)
    }

    @Test("dimension mismatch returns 0.0")
    func dimensionMismatch() {
        #expect(VectorMath.cosineSimilarity([1, 2, 3], [1, 2]) == 0.0)
    }

    @Test("serialize → deserialize round-trips a 768-dim vector exactly")
    func serializationRoundTrip() {
        let original = (0 ..< 768).map { Float($0) * 0.001 - 0.4 }
        let restored = VectorMath.deserialize(VectorMath.serialize(original))
        #expect(restored.count == 768)
        #expect(restored == original)
    }

    @Test("serialized 768-dim vector is 3072 bytes")
    func serializedByteCount() {
        let v = [Float](repeating: 0.5, count: 768)
        #expect(VectorMath.serialize(v).count == 3072)
    }

    @Test("l2Normalized scales a vector to unit length, preserving direction")
    func l2NormalizedUnitLength() {
        let out = VectorMath.l2Normalized([3, 4]) // norm 5
        #expect(abs(out[0] - 0.6) < 1e-6 && abs(out[1] - 0.8) < 1e-6)
        let norm = sqrt(out.reduce(Float(0)) { $0 + $1 * $1 })
        #expect(abs(norm - 1.0) < 1e-6)
        // direction unchanged: cosine with the original is 1.0
        #expect(abs(VectorMath.cosineSimilarity(out, [3, 4]) - 1.0) < 1e-5)
    }

    @Test("l2Normalized returns zeros (not NaN) for a zero vector")
    func l2NormalizedZeroGuard() {
        let out = VectorMath.l2Normalized([0, 0, 0])
        #expect(out == [0, 0, 0])
        #expect(out.allSatisfy { !$0.isNaN })
    }

    @Test("l2Normalized of an empty vector is empty")
    func l2NormalizedEmpty() {
        #expect(VectorMath.l2Normalized([]).isEmpty)
    }
}
