//
//  ConstellationView.swift
//  M1K3MemoryViz
//
//  The 3D memory constellation: motes for memories, threads for edges, accreting
//  as the store grows. A thin RealityKit adapter over the pure ConstellationModel
//  — all the layout/colour/geometry decisions are made (and tested) upstream;
//  this file just places entities and animates the growth. VERIFY-BY-RUN: there
//  are no unit tests for RealityView (the tested surface is ConstellationLayout +
//  ConstellationGeometry + ConstellationPalette); the visual feel is Kev's eye.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.7 (compiles against
//  the RealityKit SPM target like M1K3Avatar; on-device look + the growth timing
//  are the named verify-owed). Prior: M1K3Avatar RealityKit views.

import M1K3Memory
import RealityKit
import simd
import SwiftUI

public struct ConstellationView: View {
    private let model: ConstellationModel
    /// Seconds between each mote popping in — the accretion cadence.
    private let growthStep: TimeInterval
    /// World scale applied to the unit-ish layout positions.
    private let spread: Float
    /// Star size is decoupled from `spread` so tightening the cluster doesn't
    /// shrink the motes — the field gets more compact, the stars stay visible.
    private let moteScale: Float = 2.0

    /// `root` spins (drag); `field` holds the motes/threads and is what we frame;
    /// the camera dollies to fit the field so all stars stay in view.
    @State private var root = Entity()
    @State private var field = Entity()
    @State private var camera = PerspectiveCamera()
    /// Accumulated spin, composed with each drag's delta so a new drag continues
    /// from where the last one ended instead of snapping back to the un-spun pose.
    @State private var storedOrientation = simd_quatf(ix: 0, iy: 0, iz: 0, r: 1)
    /// Honour the system Reduce Motion preference — freeze the idle breath/float/
    /// rotation (the field still draws + grows, it just doesn't drift on its own).
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(model: ConstellationModel, growthStep: TimeInterval = 0.08, spread: Float = 1.6) {
        self.model = model
        self.growthStep = growthStep
        self.spread = spread
    }

    public var body: some View {
        // TimelineView drives a per-frame tick for the idle animation (breath /
        // float / slow rotation) — the field feels alive at rest, not a frozen chart.
        TimelineView(.animation) { timeline in
            RealityView { content in
                root.addChild(field)
                content.add(root)
                content.add(camera)
                sync(into: field, firstBuild: true)
                frameToFit()
            } update: { _ in
                // Re-runs each frame (timeline) AND whenever a fresh model arrives.
                // sync adds only what's new, so the field GROWS live without tearing
                // down the scene or resetting the drag-spin; then re-frame + breathe.
                sync(into: field, firstBuild: false)
                frameToFit()
                if !reduceMotion {
                    idle(at: timeline.date.timeIntervalSinceReferenceDate)
                }
            }
            // No background: render transparent over the app's glass, like the 3D
            // companions — rounded-clipped to the same card silhouette.
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .gesture(spinGesture)
        }
    }

    /// Subtle life at rest: a slow rotation about a gently tilted axis, a shallow
    /// breath (scale pulse), and a soft vertical float. Applied to `field` so it
    /// composes with the user's drag-spin (on `root`) rather than fighting it.
    private func idle(at t: TimeInterval) {
        let time = Float(t)
        field.orientation = simd_quatf(
            angle: time * 0.06, axis: simd_normalize(SIMD3<Float>(0.15, 1, 0.05))
        )
        field.scale = SIMD3<Float>(repeating: 1 + 0.025 * sin(time * 0.6))
        field.position.y = 0.06 * sin(time * 0.4)
    }

    // MARK: - Scene construction

    /// Dolly the camera so the WHOLE field fits — count-independent framing, so a
    /// dozen seeds and a few hundred memories both fill the card (fixes "too zoomed
    /// in"). Measured in the field's own space so the drag-spin (on `root`) never
    /// perturbs the fit.
    private func frameToFit() {
        // Field-local bounds: invariant to the idle rotation/breath on `field`, so
        // the camera distance stays steady while the field animates.
        let bounds = field.visualBounds(relativeTo: field)
        let maxDim = max(bounds.extents.x, bounds.extents.y, bounds.extents.z)
        // Camera at ~1.3× the field's extent → everything in frame with margin.
        let distance = max(maxDim * 1.3, 1.5)
        camera.look(at: .zero, from: SIMD3<Float>(0, 0, distance), relativeTo: nil)
    }

    /// Idempotent reconcile: place any node/edge not already in the scene. Entities
    /// are named by id so re-running is cheap and additive — the heart of the
    /// "grows over time" effect. On the first build motes stagger in along the
    /// accretion timeline; motes added later just pop in.
    private func sync(into container: Entity, firstBuild: Bool) {
        let present = Set(container.children.compactMap { UUID(uuidString: $0.name) })
        let growthIndex = Dictionary(
            uniqueKeysWithValues: model.growthOrder.enumerated().map { ($1, $0) }
        )
        for node in model.nodes where !present.contains(node.id) {
            let mote = makeMote(node)
            mote.name = node.id.uuidString
            container.addChild(mote)
            let delay = firstBuild ? Double(growthIndex[node.id] ?? 0) * growthStep : 0
            scheduleGrowth(of: mote, fullScale: 1, delay: delay)
        }

        // Threads — named by endpoints so each is placed once. Pre-compute the
        // present edge names into a Set (mirrors the node dedup above) so reconcile
        // is O(edges), not O(edges × children); inserting as we go also dedupes any
        // repeat within this pass.
        var presentEdges = Set(container.children.compactMap(\.name).filter { $0.hasPrefix("edge:") })
        for edge in model.edges {
            let name = "edge:\(edge.from.uuidString):\(edge.to.uuidString):\(edge.relation)"
            guard presentEdges.insert(name).inserted else { continue }
            guard let a = model.node(edge.from), let b = model.node(edge.to) else { continue }
            let thread = makeThread(from: a.position * spread, to: b.position * spread)
            thread.name = name
            container.addChild(thread)
        }
    }

    private func makeMote(_ node: ConstellationNode) -> ModelEntity {
        let mesh = MeshResource.generateSphere(radius: node.radius * moteScale)
        #if canImport(AppKit)
            let material = UnlitMaterial(
                color: ConstellationPalette.materialColor(forHue: node.hue, saturation: node.saturation)
            )
        #else
            let material = UnlitMaterial(color: .white)
        #endif
        let mote = ModelEntity(mesh: mesh, materials: [material])
        mote.position = node.position * spread
        mote.scale = SIMD3<Float>(repeating: 0.001) // collapsed until its turn
        return mote
    }

    private func makeThread(from: SIMD3<Float>, to: SIMD3<Float>) -> ModelEntity {
        let length = ConstellationGeometry.distance(from, to)
        // A thin box used as a line; +Y is its long axis (matches the geometry helper).
        let mesh = MeshResource.generateBox(size: SIMD3<Float>(0.01, max(length, 0.0001), 0.01))
        #if canImport(AppKit)
            var material = SimpleMaterial(color: .init(white: 0.6, alpha: 1.0), isMetallic: false)
            material.roughness = 1.0
        #else
            let material = SimpleMaterial(color: .gray, isMetallic: false)
        #endif
        let thread = ModelEntity(mesh: mesh, materials: [material])
        thread.position = ConstellationGeometry.midpoint(from, to)
        thread.orientation = ConstellationGeometry.orientation(from: from, to: to)
        return thread
    }

    /// Animate a mote from collapsed to full scale, staggered by `delay`.
    private func scheduleGrowth(of entity: Entity, fullScale: Float, delay: TimeInterval) {
        let target = Transform(
            scale: SIMD3<Float>(repeating: fullScale),
            rotation: entity.orientation,
            translation: entity.position
        )
        // Structured concurrency over DispatchQueue.main.asyncAfter: keeps the
        // non-Sendable Entity capture inside a proven @MainActor context (no Swift 6
        // strict-concurrency warning), and the growth cancels automatically when the
        // view's task tree tears down.
        Task { @MainActor [entity] in
            if delay > 0 { try? await Task.sleep(for: .seconds(delay)) }
            entity.move(to: target, relativeTo: entity.parent, duration: 0.6, timingFunction: .easeOut)
        }
    }

    // MARK: - Interaction

    private var spinGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                root.orientation = storedOrientation * Self.spinDelta(value.translation)
            }
            .onEnded { value in
                storedOrientation *= Self.spinDelta(value.translation)
            }
    }

    /// The incremental rotation for a drag translation: yaw about Y, pitch about X.
    private static func spinDelta(_ translation: CGSize) -> simd_quatf {
        let yaw = Float(translation.width) * 0.005
        let pitch = Float(translation.height) * 0.005
        return simd_quatf(angle: yaw, axis: SIMD3<Float>(0, 1, 0))
            * simd_quatf(angle: pitch, axis: SIMD3<Float>(1, 0, 0))
    }
}
