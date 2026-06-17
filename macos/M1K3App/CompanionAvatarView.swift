//
//  CompanionAvatarView.swift
//  M1K3App
//
//  The opt-in 3D companion creature — an alternative to the pixel face for voice
//  mode. Extracted from AvatarView.swift to keep that file under SwiftLint's
//  file_length ceiling. References CRTOverlay (same module, stays in AvatarView)
//  and the M1K3Avatar companion cores. RealityKit render is verify-by-launch; the
//  state→clip maths is unit-tested in M1K3Avatar (ClipMapper / CompanionSpec).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.85, Prior: Kev +
//  claude-opus-4-8 (CompanionScene/CompanionAvatarView originate in AvatarView.swift,
//  2026-06-11; moved here verbatim).

import AppKit
import M1K3Avatar
import RealityKit
import SwiftUI

// MARK: - Companion avatar (opt-in 3D creature)

/// Stable storage for the loaded companion: the mesh-bearing host entity and the
/// harvested per-clip animations. NOT @Observable — like AvatarScene, the update
/// closure drives it while SwiftUI is mid-graph-update.
@MainActor
final class CompanionScene {
    var host: Entity?
    var fillLight: DirectionalLight?
    /// Clip name → harvested animation resource (cross-bound onto `host`'s rig).
    var clips: [String: AnimationResource] = [:]
    var currentClip: String?
    /// Last emotion the fill light was tinted for — guards the per-frame colour write.
    var lastEmotion: AvatarEmotion = .neutral
    /// Last activity the skin was tinted for — guards the per-frame material
    /// rewrite (only re-paint when the state actually changes).
    var lastActivity: AvatarActivity = .idle
    /// Last shading style painted — so switching the style picker repaints live.
    var lastShadingStyle: CompanionShadingStyle = .off
    /// The creature's baked materials, snapshotted before any shader is applied —
    /// cel rebuilds FROM these (keeping the fur texture) and Off restores them.
    var bakedMaterials: [ObjectIdentifier: [any RealityKit.Material]] = [:]
    var built = false
}

/// The 3D companion: an opt-in alternative to the pixel face for voice mode. Loads
/// one mesh + N per-clip USDZs from M1K3Avatar's bundle, harvests each clip onto a
/// single rig (the one-mesh/N-clip-files architecture proven in scratch/usdz-probe),
/// and crossfades clips as the AvatarController's state changes — driven by the same
/// AvatarState the pixel face reads, through ClipMapper instead of FaceExpression.
///
/// The pixel face stays M1K3's default brand face (chat, onboarding, the app icon);
/// this is the wink the curious user opts into.
///
/// VERIFY-BY-LAUNCH: RealityKit load/animation/lighting is eyeballed at ⌘R; the
/// state→clip maths is unit-tested in M1K3Avatar (ClipMapper / CompanionSpec).
///
/// Signed: Kev + claude-opus-4-8, 2026-06-11, Confidence 0.6 (render quality is the
/// gate this view exists to answer; lighting + framing constants are by-eye), Prior: Unknown
struct CompanionAvatarView: View {
    let controller: AvatarController
    let companion: CompanionSpec

    /// Opt-in shading style (phosphor glow / cel toon) over the companion's baked
    /// textures. Applies on build and switches live when the picker changes.
    @AppStorage(AppEnvironment.companionShadingKey) private var shadingRaw = CompanionShadingStyle.off.rawValue

    private var shadingStyle: CompanionShadingStyle {
        CompanionShadingStyle(rawValue: shadingRaw) ?? .off
    }

    @State private var scene = CompanionScene()

    /// Fit the creature's largest dimension to this many world units, then frame it.
    private static let targetSize: Float = 1.7
    /// A flattering three-quarter base pose (radians about Y) rather than head-on —
    /// after the upright correction the creature's length runs in Z (depth), so this
    /// turns its broadside toward the camera.
    private static let baseYaw: Float = -0.9

    var body: some View {
        RealityView { content in
            await build(into: &content)
        } update: { _ in
            guard scene.built else { return }
            sync(to: controller.state)
        }
        .overlay(CRTOverlay())
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Build (once, async)

    /// Blender exports Z-up; RealityKit is Y-up, so a companion loads standing on
    /// its nose. The base pose pitches it upright (−90° about X) THEN turns it to
    /// the three-quarter yaw. Uniform across companions — all share the Blender
    /// export pipeline; a future Y-up source would need this per-spec.
    private static let blenderZUpCorrection: Float = -.pi / 2
    private static var basePose: simd_quatf {
        simd_quatf(angle: baseYaw, axis: [0, 1, 0])
            * simd_quatf(angle: blenderZUpCorrection, axis: [1, 0, 0])
    }

    private func build(into content: inout some RealityViewContentProtocol) async {
        guard let idleURL = CompanionAssets.clipURL(companion: companion.id, clip: companion.idleClip),
              let host = try? await Entity(contentsOf: idleURL)
        else { return }

        let clips = await harvestClips(idleHost: host)
        // A loaded-but-animationless asset (corrupt or re-exported without the clip
        // baked) would render a frozen bind pose. Treat it like a missing file:
        // require the resting clip, and otherwise don't claim `built` — so a broken
        // asset never enters voice mode as a static mesh masquerading as the companion.
        guard let idle = clips[companion.idleClip] else { return }

        // Pose BEFORE fit() so the recentre + scale measure the final, upright silhouette.
        host.orientation = Self.basePose
        fit(host)

        let root = Entity()
        root.addChild(host)
        content.add(root)
        addLighting(to: &content)
        addCamera(to: &content)
        host.playAnimation(idle.repeat(), transitionDuration: 0.3)

        // Snapshot the baked materials BEFORE any shader, so cel can adapt the fur
        // texture and Off can restore it on a live switch.
        scene.bakedMaterials = PhosphorMaterial.snapshotMaterials(of: host)

        // Opt-in shading style: paint the selected M1K3 shader over the baked
        // materials. Rides the skeletal animation for free (per-fragment shader,
        // blind to the rig). Falls back silently if the shader can't load.
        let activity = controller.state.activity
        PhosphorMaterial.apply(
            shadingStyle, treatment: activity.phosphorTreatment,
            originals: scene.bakedMaterials, to: host
        )

        scene.host = host
        scene.clips = clips
        scene.currentClip = companion.idleClip
        scene.lastEmotion = controller.state.emotion
        scene.lastActivity = activity
        scene.lastShadingStyle = shadingStyle
        scene.built = true
    }

    /// Harvest each bundled clip as an animation resource bound to the idle host's
    /// rig. The idle file's own clip comes from `host`; the rest cross-bind from
    /// their donor files (one mesh + N clip files — proven in scratch/usdz-probe).
    private func harvestClips(idleHost host: Entity) async -> [String: AnimationResource] {
        var clips: [String: AnimationResource] = [:]
        if let idleAnim = host.availableAnimations.first {
            clips[companion.idleClip] = idleAnim
        }
        for (name, url) in CompanionAssets.clipURLs(for: companion) where name != companion.idleClip {
            if let donor = try? await Entity(contentsOf: url), let anim = donor.availableAnimations.first {
                clips[name] = anim
            }
        }
        return clips
    }

    /// Scale the creature so its largest extent fills `targetSize`, then recentre it
    /// on the origin — companions are authored at wildly different native sizes (the
    /// probe read native height from accessor min/max; this does it live). Assumes
    /// `host`'s parent is identity-transform (the freshly-made `root`), so world-space
    /// bounds equal local bounds.
    private func fit(_ host: Entity) {
        host.scale = SIMD3(repeating: companion.scale)
        let extents = host.visualBounds(relativeTo: nil).extents
        let maxDim = max(extents.x, extents.y, extents.z)
        if maxDim > 0 { host.scale *= Self.targetSize / maxDim }
        // Re-measure post-scale: the centre shifts with scale, so this is a second,
        // necessary read (not a cacheable duplicate of the pre-scale bounds above).
        host.position -= host.visualBounds(relativeTo: nil).center
    }

    private func addLighting(to content: inout some RealityViewContentProtocol) {
        let key = DirectionalLight()
        key.light.intensity = 6000
        key.light.color = .white
        key.look(at: .zero, from: [-1.2, 1.4, 2.0], relativeTo: nil)
        content.add(key)

        // Emotion-accent fill from the opposite side — the companion's equivalent
        // of the pixel face's accent tint, as mood lighting (textures survive).
        let fill = DirectionalLight()
        fill.light.intensity = 1800
        fill.light.color = Self.fillColor(for: controller.state.emotion)
        fill.look(at: .zero, from: [1.6, 0.4, 1.6], relativeTo: nil)
        content.add(fill)
        scene.fillLight = fill
    }

    private func addCamera(to content: inout some RealityViewContentProtocol) {
        let camera = PerspectiveCamera()
        camera.look(at: [0, 0, 0], from: [0, 0.15, 2.4], relativeTo: nil)
        content.add(camera)
    }

    // MARK: - Per-update sync

    private func sync(to state: AvatarState) {
        // Re-tint the fill only when the emotion actually changes — sync() runs every
        // SwiftUI update (30 fps) and a colour write is a GPU command each time.
        if state.emotion != scene.lastEmotion {
            scene.fillLight?.light.color = Self.fillColor(for: state.emotion)
            scene.lastEmotion = state.emotion
        }

        // Reactive shading: shift the glow/tint with M1K3's state, AND repaint live
        // when the style picker changes (incl. restoring textures on switch to Off).
        // Re-paint only on a real change — sync() runs ~30 fps.
        let style = shadingStyle
        if let host = scene.host, state.activity != scene.lastActivity || style != scene.lastShadingStyle {
            PhosphorMaterial.apply(
                style, treatment: state.activity.phosphorTreatment,
                originals: scene.bakedMaterials, to: host
            )
            scene.lastActivity = state.activity
            scene.lastShadingStyle = style
        }

        let desired = ClipMapper.clip(for: state, dialect: companion.dialect)
        guard desired != scene.currentClip, let resource = scene.clips[desired], let host = scene.host
        else { return }
        let gait = ClipMapper.gait(for: state)
        host.playAnimation(resource.repeat(), transitionDuration: ClipMapper.crossfadeDuration(to: gait))
        scene.currentClip = desired
    }

    /// Accent colour for the fill light. Neutral gets a soft warm white rather than
    /// the dynamic `.secondary` grey, which doesn't read as light.
    private static func fillColor(for emotion: AvatarEmotion) -> NSColor {
        guard emotion != .neutral else { return NSColor(white: 0.95, alpha: 1) }
        return NSColor(emotion.accentColor).usingColorSpace(.deviceRGB) ?? .white
    }
}
