//
//  ConstellationIdle.swift
//  M1K3MemoryViz
//
//  The constellation's "alive at rest" motion — a slow rotation about a gently
//  tilted axis, a shallow breath (scale pulse), and a soft vertical float.
//
//  Pure and tested because this exact effect once shipped FROZEN: the view fed
//  `idle()` an ABSOLUTE reference-date clock (~7.7e8s for 2026) and cast it to
//  Float on the first line. Float carries ~7 significant digits, so at that
//  magnitude its ULP is ~64 seconds — the ~0.0167s/frame advance quantised to
//  zero and every trig argument became a frozen constant. Nodes still grew in
//  (different clock), so it looked half-alive. The same trap was already fixed
//  twice in this repo (AudioCaptureBackdrop, AvatarView): measure elapsed since
//  the view appeared and keep the math in Double until the final cast.
//
//  Keeping that math here lets a unit test assert the pose actually advances
//  frame-to-frame — there are no RealityView tests, which is precisely why it
//  shipped frozen the first time.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.9 (textbook trig; the
//  frame-advance + large-elapsed regression are pinned by tests; the on-screen
//  feel stays verify-by-run). Prior: this file (new).

import Foundation
import simd

/// The field's transform at a moment in time. Plain Float scalars (not a built
/// quaternion) so the values are trivially assertable in tests; the view composes
/// the `simd_quatf` from `rotationAngle` about ``ConstellationIdle/axis``.
public struct ConstellationIdlePose: Equatable {
    /// Rotation angle (radians) about ``ConstellationIdle/axis``.
    public let rotationAngle: Float
    /// Uniform scale — the shallow "breath".
    public let breathScale: Float
    /// Vertical offset (world units) — the soft float.
    public let floatY: Float
}

public enum ConstellationIdle {
    /// The tilted spin axis — mostly +Y with a slight lean, so the rotation reads
    /// as a drifting tumble rather than a flat turntable.
    public static let axis = simd_normalize(SIMD3<Float>(0.15, 1, 0.05))

    /// The field pose at `elapsed` seconds SINCE THE VIEW APPEARED — NOT an
    /// absolute clock (see the file header). The math runs in Double and casts to
    /// Float only at the end, so the ~0.0167s/frame delta survives even hours into
    /// a session instead of collapsing to a constant.
    public static func pose(elapsed: TimeInterval) -> ConstellationIdlePose {
        ConstellationIdlePose(
            rotationAngle: Float(elapsed * 0.06),
            breathScale: Float(1 + 0.025 * sin(elapsed * 0.6)),
            floatY: Float(0.06 * sin(elapsed * 0.4))
        )
    }
}
