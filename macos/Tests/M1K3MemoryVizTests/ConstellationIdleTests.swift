//
//  ConstellationIdleTests.swift
//  M1K3MemoryVizTests
//
//  The pure idle-motion pose behind the constellation's "alive at rest" feel.
//  Exists because the idle anim once shipped FROZEN — the view fed an absolute
//  reference-date clock (~7.7e8s) cast to Float (ULP ~64s), so every per-frame
//  delta quantised to zero. These tests pin the contract the view relies on:
//  the pose actually ADVANCES frame-to-frame, even hours into a session, so it
//  can't silently refreeze without a red test.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.9. Prior: this file.

import Foundation
@testable import M1K3MemoryViz
import simd
import Testing

struct ConstellationIdleTests {
    /// One animation frame at 60fps — the delta the frozen build couldn't resolve.
    private let frame: TimeInterval = 1.0 / 60.0

    @Test("at rest start (elapsed 0) the pose is the neutral identity-ish pose")
    func zeroIsNeutral() {
        let pose = ConstellationIdle.pose(elapsed: 0)
        #expect(pose.rotationAngle == 0)
        #expect(abs(pose.breathScale - 1) < 1e-6) // sin(0) == 0 → unit scale
        #expect(abs(pose.floatY) < 1e-6)
    }

    @Test("the pose advances over a single 60fps frame — it is NOT frozen")
    func advancesEachFrame() {
        let a = ConstellationIdle.pose(elapsed: 0)
        let b = ConstellationIdle.pose(elapsed: frame)
        #expect(a != b)
        #expect(a.rotationAngle != b.rotationAngle)
        #expect(a.breathScale != b.breathScale)
        #expect(a.floatY != b.floatY)
    }

    /// THE regression guard: with the math kept in Double until the final cast, a
    /// per-frame delta still resolves even two hours in. The original bug (absolute
    /// clock + Float cast) made `rotationAngle` a frozen constant at this magnitude.
    @Test("the pose still advances frame-to-frame deep into a long session")
    func advancesAtLargeElapsed() {
        let twoHours: TimeInterval = 2 * 60 * 60
        let a = ConstellationIdle.pose(elapsed: twoHours)
        let b = ConstellationIdle.pose(elapsed: twoHours + frame)
        #expect(a.rotationAngle != b.rotationAngle)
    }

    /// The breath/float are periodic, so adjacent frames can legitimately barely
    /// move at a sine extremum — that's physics, not the freeze. The real guard is
    /// that, sampled across a slow period, they still take a RANGE of values (not
    /// collapsed to a single constant the way the Float-ULP bug did). Two hours in.
    @Test("breath and float still oscillate (not frozen to a constant) deep into a session")
    func breathAndFloatLiveAtLargeElapsed() {
        let base: TimeInterval = 2 * 60 * 60
        var breaths = Set<Float>()
        var floats = Set<Float>()
        for sample in 0 ..< 80 { // ~16s window — over a full breath + float period
            let pose = ConstellationIdle.pose(elapsed: base + Double(sample) * 0.2)
            breaths.insert(pose.breathScale)
            floats.insert(pose.floatY)
        }
        #expect(breaths.count > 1) // breath is alive at large elapsed, not a constant
        #expect(floats.count > 1) // and so is the float
    }

    @Test("rotation increases monotonically with time (a continuous turn)")
    func rotationIsMonotonic() {
        #expect(ConstellationIdle.pose(elapsed: 1).rotationAngle
            < ConstellationIdle.pose(elapsed: 2).rotationAngle)
    }

    @Test("breath and float stay within their gentle bounds")
    func motionStaysSubtle() {
        for step in 0 ..< 400 {
            let pose = ConstellationIdle.pose(elapsed: Double(step) * 0.25)
            #expect(pose.breathScale >= 1 - 0.025 - 1e-6)
            #expect(pose.breathScale <= 1 + 0.025 + 1e-6)
            #expect(abs(pose.floatY) <= 0.06 + 1e-6)
        }
    }

    @Test("pose is deterministic for a given time")
    func deterministic() {
        #expect(ConstellationIdle.pose(elapsed: 3.7) == ConstellationIdle.pose(elapsed: 3.7))
    }
}
