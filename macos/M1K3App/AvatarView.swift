//
//  AvatarView.swift
//  M1K3App
//
//  The companion avatar: a procedural pixel-cube FACE — a fixed LED-matrix of
//  RealityKit cubes (FaceGrid) whose brightness is recomputed every frame by the
//  pure FaceExpression model. Lighting the right cubes "draws" two pupils that
//  blink/look and a mouth that curves with emotion and opens when M1K3 speaks. No
//  asset file, no USDZ — fully in code, on-brand with the Silkscreen pixel look.
//
//  VERIFY-BY-LAUNCH: RealityKit entity/material behaviour is confirmed
//  interactively (⌘R); the matrix geometry + expression maths are unit-tested in
//  the M1K3Avatar package (FaceGrid / FaceExpression).
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.6, Prior: Unknown
//  Review: Kev + claude-sonnet-4-6, 2026-06-08 — pivoted from the M1K3 wordmark to
//  an emoting face per Kev's feedback; same fixed-grid / mutate-emission renderer,
//  AvatarScene stays non-@Observable (the mutate-during-update crash fix).

import AppKit
import M1K3Avatar
import RealityKit
import SwiftUI

/// Stable reference holding the built cube entities (parallel to
/// FaceGrid.allCells()) so the per-frame update can drive them without re-querying
/// the RealityView content.
///
/// NOT @Observable: the per-frame `update` closure mutates `materials` while
/// SwiftUI is already updating the graph; an @Observable willSet there reenters
/// the AttributeGraph and traps ("thread_is_updating"). This is pure entity
/// storage the view never observes — TimelineView's clock drives redraws.
@MainActor
final class AvatarScene {
    var root: Entity?
    var cubes: [ModelEntity] = []
    /// One material per cube, allocated once and mutated in place each frame —
    /// avoids constructing a fresh UnlitMaterial per cube per frame (GPU churn).
    var materials: [UnlitMaterial] = []
    /// The matrix cells, parallel to `cubes`/`materials` (build + animate order).
    var cells: [(col: Int, row: Int)] = []
    var built = false
    /// Animation clock origin, captured at build time. Animation time is measured
    /// elapsed-since-build so `sin/cos` arguments stay small — mirrors the
    /// AudioCaptureBackdrop fix, where an absolute reference-date (~8e8) lost enough
    /// per-frame precision to flatten the motion. (BUGS.md / AudioCaptureBackdrop.)
    var startDate = Date()
}

struct AvatarView: View {
    let controller: AvatarController

    @State private var scene = AvatarScene()

    // Layout constants (RealityKit world units).
    private static let spacing: Float = 0.1
    private static let cubeSize: Float = 0.075

    var body: some View {
        TimelineView(schedule) { context in
            RealityView { content in
                // Build once: one cube per matrix cell, each with its own material
                // (mutated in place each frame, never reallocated).
                let root = Entity()
                var cubes: [ModelEntity] = []
                var materials: [UnlitMaterial] = []
                let cells = FaceGrid.allCells()
                for cell in cells {
                    let mesh = MeshResource.generateBox(size: Self.cubeSize)
                    let material = UnlitMaterial(color: .black)
                    let cube = ModelEntity(mesh: mesh, materials: [material])
                    cube.position = Self.basePosition(for: cell)
                    root.addChild(cube)
                    cubes.append(cube)
                    materials.append(material)
                }
                content.add(root)

                // A head-on camera so the matrix face reads flat-but-dimensional.
                let camera = PerspectiveCamera()
                camera.look(at: .zero, from: [0, 0, 2.6], relativeTo: nil)
                content.add(camera)

                scene.root = root
                scene.cubes = cubes
                scene.materials = materials
                scene.cells = cells
                scene.startDate = context.date
                scene.built = true
            } update: { _ in
                guard scene.built else { return }
                let time = context.date.timeIntervalSince(scene.startDate)
                animate(at: time)
            }
        }
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Per-frame animation

    private func animate(at time: Double) {
        let state = controller.state
        let accent = NSColor(state.emotion.accentColor).usingColorSpace(.deviceRGB) ?? .white

        for index in scene.cubes.indices {
            let cell = scene.cells[index]
            let cube = scene.cubes[index]

            // Depth pop for lit features.
            var position = Self.basePosition(for: cell)
            position.z = FaceExpression.displacement(col: cell.col, row: cell.row, state: state, time: time)
            cube.position = position

            // Brightness → emission. Mutate the cached material's colour in place
            // rather than allocating a new UnlitMaterial every frame.
            let intensity = FaceExpression.intensity(col: cell.col, row: cell.row, state: state, time: time)
            scene.materials[index].color = .init(tint: Self.scaled(accent, by: intensity))
            cube.model?.materials = [scene.materials[index]]
        }

        animateBody(at: time, root: scene.root)
    }

    /// Whole-face motion: a gentle multi-axis 3D drift (turn + nod + tilt at
    /// incommensurate rates, so it never repeats and reads as "alive") plus a tiny
    /// high-frequency positional jitter for an analog feel. Both move the entire
    /// face as one rigid body, so eyes + mouth stay perfectly in register.
    private func animateBody(at time: Double, root: Entity?) {
        guard let root else { return }
        let active = controller.state.activity.isActive
        let gain: Float = active ? 1.3 : 1.0 // a touch livelier mid-interaction

        // 3D drift — yaw (turn), pitch (nod), roll (tilt).
        let yaw = Float(sin(time * 0.45)) * 0.07 * gain
        let pitch = Float(sin(time * 0.33 + 1.0)) * 0.045 * gain
        let roll = Float(sin(time * 0.27)) * 0.025 * gain
        root.orientation = simd_quatf(angle: yaw, axis: [0, 1, 0])
            * simd_quatf(angle: pitch, axis: [1, 0, 0])
            * simd_quatf(angle: roll, axis: [0, 0, 1])

        // Micro-jitter — two summed sines per axis (≈±0.008 units) so it shivers
        // organically rather than buzzing at one rate. Kept ≤6 Hz so it stays
        // smooth at 30 fps instead of aliasing into a wobble.
        let jitterX = Float(sin(time * 6.0) + sin(time * 4.3)) * 0.004
        let jitterY = Float(sin(time * 5.0 + 2.0) + sin(time * 3.7)) * 0.004
        root.position = SIMD3(jitterX, jitterY, 0)
    }

    // MARK: - Geometry

    /// The resting position for a cell, centred on the origin. Row increases
    /// downward in the matrix, so Y is negated for RealityKit's Y-up space.
    private static func basePosition(for cell: (col: Int, row: Int)) -> SIMD3<Float> {
        let xPos = (Float(cell.col) - Float(FaceGrid.cols - 1) / 2) * spacing
        let yPos = -(Float(cell.row) - Float(FaceGrid.rows - 1) / 2) * spacing
        return SIMD3(xPos, yPos, 0)
    }

    /// Blend a colour toward black by `1 - brightness` so low emission reads dim.
    private static func scaled(_ color: NSColor, by brightness: Float) -> NSColor {
        let clamped = CGFloat(min(max(brightness, 0), 1))
        return NSColor(
            red: color.redComponent * clamped,
            green: color.greenComponent * clamped,
            blue: color.blueComponent * clamped,
            alpha: 1
        )
    }

    // MARK: - Timeline

    /// 30 fps throughout — the idle micro-jitter + LED flicker need a steady 30 fps
    /// to read as a smooth analog shiver rather than aliasing into a slow wobble.
    private var schedule: AnimationTimelineSchedule {
        AnimationTimelineSchedule(minimumInterval: 1.0 / 30.0)
    }
}
