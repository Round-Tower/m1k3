//
//  AvatarView.swift
//  M1K3App
//
//  RealityKit companion panel. Loads Sparrow.usdz from the app bundle — add the
//  file to the Xcode target resources in project.yml after the Step 0 GLB→USDZ
//  conversion (Reality Composer Pro: File → Import → Export as USDZ, save to
//  `macos/M1K3App/Resources/Sparrow.usdz`, add to project.yml resourceRules).
//
//  Until Sparrow.usdz is present, shows an SF Symbol bird placeholder that
//  already responds to the avatar's emotional state via symbolEffect.
//
//  VERIFY-BY-LAUNCH: RealityKit types + ModelEntity are not available under
//  `swift test` — all RealityKit behaviour is confirmed interactively (⌘R).
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.65,
//  Prior: Unknown

import M1K3Avatar
import RealityKit
import SwiftUI

/// Stable reference holding the loaded USDZ entity so onChange closures
/// can call playAnimation without going through RealityViewContent.
@Observable
@MainActor
final class AvatarScene {
    var entity: ModelEntity?
    var clipNames: [String] = []
}

struct AvatarView: View {
    let controller: AvatarController

    @State private var scene = AvatarScene()

    var body: some View {
        Group {
            if hasModel {
                TimelineView(schedule) { context in
                    RealityView { content in
                        // Inline: avoid naming RealityViewContent in helper signatures.
                        guard let url = Bundle.main.url(forResource: "Sparrow", withExtension: "usdz"),
                              let entity = try? await ModelEntity(contentsOf: url)
                        else { return }
                        entity.name = "sparrow"
                        content.add(entity)
                        scene.entity = entity
                        scene.clipNames = entity.availableAnimations.compactMap(\.name)
                        triggerClipAnimation(for: controller.state)
                    } update: { content in
                        // Procedural Y-bob, rate-matched to current activity.
                        guard let entity = content.entities
                            .first(where: { $0.name == "sparrow" })
                        else { return }
                        let time = Float(context.date.timeIntervalSince1970)
                        entity.position.y = bob(for: controller.state.activity, time: time)
                    }
                }
                .onChange(of: controller.state) { _, newState in
                    triggerClipAnimation(for: newState)
                }
            } else {
                placeholder
            }
        }
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - RealityKit

    private var hasModel: Bool {
        Bundle.main.url(forResource: "Sparrow", withExtension: "usdz") != nil
    }

    private func triggerClipAnimation(for state: AvatarState) {
        guard let entity = scene.entity else { return }
        let clip = AnimationResolver.resolve(state: state, clipNames: scene.clipNames)
        if let clip,
           let anim = entity.availableAnimations.first(where: { $0.name == clip })
        {
            entity.playAnimation(anim.repeat(), transitionDuration: 0.3)
        }
    }

    private func bob(for activity: AvatarActivity, time: Float) -> Float {
        switch activity {
        case .speaking: sin(time * 10) * 0.015
        case .thinking, .generating: sin(time * 2) * 0.008
        case .listening: sin(time * 3) * 0.006
        case .idle, .error: sin(time * 1) * 0.003
        }
    }

    private var schedule: AnimationTimelineSchedule {
        AnimationTimelineSchedule(
            minimumInterval: controller.state.activity.isActive ? 1.0 / 30.0 : 2.0
        )
    }

    // MARK: - Placeholder (shown until Sparrow.usdz is added to app target resources)

    private var placeholder: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
            VStack(spacing: 8) {
                Image(systemName: "bird.fill")
                    .font(.system(size: 52, weight: .semibold))
                    .foregroundStyle(controller.state.emotion.accentColor)
                    .symbolEffect(.pulse, isActive: controller.state.activity.isActive)
                Text(controller.state.activity.isActive
                    ? controller.state.activity.displayName
                    : "M1K3")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
