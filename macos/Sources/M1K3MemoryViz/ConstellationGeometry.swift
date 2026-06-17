//
//  ConstellationGeometry.swift
//  M1K3MemoryViz
//
//  Pure simd helpers the RealityKit view needs to draw edges — kept out of the
//  view so the fiddly bits (orienting a thin cylinder to span two motes) are
//  unit-tested, not eyeballed. No RealityKit import here.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.88 (quaternion +
//  midpoint + length are textbook and TDD'd). Prior: Unknown.

import simd

public enum ConstellationGeometry {
    /// A rotation that turns the +Y axis (a cylinder's long axis in RealityKit)
    /// to point from `from` toward `to`. Degenerate inputs (zero-length, exactly
    /// parallel/antiparallel) fall back to identity / a 180° flip so the result
    /// is never NaN.
    public static func orientation(from: SIMD3<Float>, to: SIMD3<Float>) -> simd_quatf {
        let up = SIMD3<Float>(0, 1, 0)
        let delta = to - from
        let len = simd_length(delta)
        guard len > 1e-6 else { return simd_quatf(angle: 0, axis: up) }
        let dir = delta / len
        let dot = simd_dot(up, dir)
        if dot > 0.9999 { return simd_quatf(angle: 0, axis: up) } // already aligned
        if dot < -0.9999 { return simd_quatf(angle: .pi, axis: SIMD3<Float>(1, 0, 0)) } // flipped
        let axis = simd_normalize(simd_cross(up, dir))
        return simd_quatf(angle: acos(dot), axis: axis)
    }

    /// Midpoint of two motes — where the connecting cylinder is centred.
    public static func midpoint(_ a: SIMD3<Float>, _ b: SIMD3<Float>) -> SIMD3<Float> {
        (a + b) / 2
    }

    /// Distance between two motes — the connecting cylinder's length.
    public static func distance(_ a: SIMD3<Float>, _ b: SIMD3<Float>) -> Float {
        simd_length(b - a)
    }
}
