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

    /// Held so the drag gesture can spin the whole field.
    @State private var root = Entity()

    public init(model: ConstellationModel, growthStep: TimeInterval = 0.08, spread: Float = 2.5) {
        self.model = model
        self.growthStep = growthStep
        self.spread = spread
    }

    public var body: some View {
        RealityView { content in
            content.add(makeCamera())
            content.add(root)
            sync(into: root, firstBuild: true)
        } update: { _ in
            // Re-runs whenever a fresh model arrives (the window polls the store
            // and hands us a new snapshot as memories accrete). We add only what's
            // new, so the field GROWS live without tearing down the scene or
            // resetting the user's drag-spin.
            sync(into: root, firstBuild: false)
        }
        .background(.black)
        .gesture(spinGesture)
    }

    // MARK: - Scene construction

    private func makeCamera() -> Entity {
        let camera = PerspectiveCamera()
        camera.look(at: .zero, from: SIMD3<Float>(0, 0, spread * 2.6), relativeTo: nil)
        return camera
    }

    /// Idempotent reconcile: place any node/edge not already in the scene. Entities
    /// are named by id so re-running is cheap and additive — the heart of the
    /// "grows over time" effect. On the first build motes stagger in along the
    /// accretion timeline; motes added later just pop in.
    private func sync(into root: Entity, firstBuild: Bool) {
        let present = Set(root.children.compactMap { UUID(uuidString: $0.name) })
        let growthIndex = Dictionary(
            uniqueKeysWithValues: model.growthOrder.enumerated().map { ($1, $0) }
        )
        for node in model.nodes where !present.contains(node.id) {
            let mote = makeMote(node)
            mote.name = node.id.uuidString
            root.addChild(mote)
            let delay = firstBuild ? Double(growthIndex[node.id] ?? 0) * growthStep : 0
            scheduleGrowth(of: mote, fullScale: 1, delay: delay)
        }

        // Threads — named by endpoints so each is placed once.
        for edge in model.edges {
            let name = "edge:\(edge.from.uuidString):\(edge.to.uuidString):\(edge.relation)"
            guard !root.children.contains(where: { $0.name == name }) else { continue }
            guard let a = model.node(edge.from), let b = model.node(edge.to) else { continue }
            let thread = makeThread(from: a.position * spread, to: b.position * spread)
            thread.name = name
            root.addChild(thread)
        }
    }

    private func makeMote(_ node: ConstellationNode) -> ModelEntity {
        let mesh = MeshResource.generateSphere(radius: node.radius * spread)
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
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            entity.move(to: target, relativeTo: entity.parent, duration: 0.6, timingFunction: .easeOut)
        }
    }

    // MARK: - Interaction

    private var spinGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                let yaw = Float(value.translation.width) * 0.005
                let pitch = Float(value.translation.height) * 0.005
                root.orientation = simd_quatf(angle: yaw, axis: SIMD3<Float>(0, 1, 0))
                    * simd_quatf(angle: pitch, axis: SIMD3<Float>(1, 0, 0))
            }
    }
}
