//
//  ConstellationGeometryTests.swift
//  M1K3MemoryVizTests
//
//  The pure simd geometry behind edge drawing. The RealityView itself is
//  verify-by-run; this is the part that has a right answer.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.88. Prior: this file.

@testable import M1K3MemoryViz
import simd
import Testing

struct ConstellationGeometryTests {
    @Test("orientation turns the +Y axis toward the target direction")
    func orientationAlignsUp() {
        let up = SIMD3<Float>(0, 1, 0)
        // Want +Y to point along +X.
        let q = ConstellationGeometry.orientation(from: .zero, to: SIMD3<Float>(2, 0, 0))
        let rotated = q.act(up)
        #expect(simd_distance(rotated, SIMD3<Float>(1, 0, 0)) < 0.001)
    }

    @Test("an already-aligned edge yields (near) identity")
    func orientationAligned() {
        let q = ConstellationGeometry.orientation(from: .zero, to: SIMD3<Float>(0, 3, 0))
        let rotated = q.act(SIMD3<Float>(0, 1, 0))
        #expect(simd_distance(rotated, SIMD3<Float>(0, 1, 0)) < 0.001)
    }

    @Test("degenerate (zero-length) edge does not produce NaN")
    func orientationDegenerate() {
        let q = ConstellationGeometry.orientation(from: .zero, to: .zero)
        let rotated = q.act(SIMD3<Float>(0, 1, 0))
        #expect(!rotated.x.isNaN && !rotated.y.isNaN && !rotated.z.isNaN)
    }

    @Test("an antiparallel edge flips +Y to -Y without NaN")
    func orientationAntiparallel() {
        let q = ConstellationGeometry.orientation(from: .zero, to: SIMD3<Float>(0, -2, 0))
        let rotated = q.act(SIMD3<Float>(0, 1, 0))
        #expect(!rotated.x.isNaN)
        #expect(simd_distance(rotated, SIMD3<Float>(0, -1, 0)) < 0.01)
    }

    @Test("midpoint and distance are the obvious things")
    func midpointAndDistance() {
        let a = SIMD3<Float>(0, 0, 0)
        let b = SIMD3<Float>(0, 4, 0)
        #expect(ConstellationGeometry.midpoint(a, b) == SIMD3<Float>(0, 2, 0))
        #expect(abs(ConstellationGeometry.distance(a, b) - 4) < 0.001)
    }
}
