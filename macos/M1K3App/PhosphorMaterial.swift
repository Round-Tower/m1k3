//
//  PhosphorMaterial.swift
//  M1K3App
//
//  App glue for the phosphor skin: load the RealityKit CustomMaterial surface
//  shader (Phosphor.metal, compiled by Xcode into the app's default.metallib) and
//  paint it over a loaded companion's whole mesh tree. The look itself is decided
//  by the pure PhosphorTreatment (M1K3Avatar); this file is the RealityKit/Metal
//  plumbing — verify-at-⌘R.
//
//  Falls back gracefully: if the shader can't load (older OS, missing metallib),
//  apply() returns false and the caller keeps the companion's baked materials, so
//  the creature still renders — just without the glow.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.8 (shader compiles
//  against the RealityKit metal headers + load path is the documented one; the
//  on-screen result is verify-at-⌘R). Prior: Unknown.
//

import M1K3Avatar
import Metal
import os
import RealityKit

@MainActor
enum PhosphorMaterial {
    private static let log = Logger(subsystem: "app.m1k3", category: "phosphor")

    /// The loaded base material, cached UNTINTED (custom.value == .zero) — apply()
    /// copies it (value type) and tints the copy, so the cache stays neutral and is
    /// reused across companions. `loadFailed` latches so a missing shader is logged
    /// once, not on every companion build.
    private static var cachedBase: CustomMaterial?
    private static var loadFailed = false

    /// Load the phosphor CustomMaterial from the app bundle's default.metallib.
    /// nil (logged once) when the shader isn't available — caller falls back.
    private static func base() -> CustomMaterial? {
        if let cachedBase { return cachedBase }
        if loadFailed { return nil }
        guard let device = MTLCreateSystemDefaultDevice() else {
            loadFailed = true
            log.error("phosphor: no Metal device")
            return nil
        }
        let library: MTLLibrary
        do {
            library = try device.makeDefaultLibrary(bundle: .main)
        } catch {
            loadFailed = true
            log.error("phosphor: no default.metallib in app bundle (\(error.localizedDescription, privacy: .public))")
            return nil
        }
        do {
            let surface = CustomMaterial.SurfaceShader(named: "phosphorSurface", in: library)
            let material = try CustomMaterial(surfaceShader: surface, lightingModel: .lit)
            cachedBase = material
            return material
        } catch {
            loadFailed = true
            log.error("phosphor: CustomMaterial init failed (\(error.localizedDescription, privacy: .public))")
            return nil
        }
    }

    /// Paint the phosphor skin over every ModelEntity in `root`'s subtree, tinted
    /// by `treatment`. Returns false (caller keeps baked materials) if the shader
    /// is unavailable.
    @discardableResult
    static func apply(_ treatment: PhosphorTreatment, to root: Entity) -> Bool {
        guard var material = base() else { return false }
        material.custom.value = treatment.customValue
        paint(material, onto: root)
        return true
    }

    private static func paint(_ material: CustomMaterial, onto entity: Entity) {
        if var model = entity.components[ModelComponent.self] {
            // Preserve the submesh material-slot count so multi-material meshes
            // render fully; an empty slot list still gets one phosphor material.
            model.materials = model.materials.isEmpty ? [material] : model.materials.map { _ in material }
            entity.components.set(model)
        }
        for child in entity.children {
            paint(material, onto: child)
        }
    }
}
