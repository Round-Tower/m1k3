//
//  AvatarSurface.swift
//  M1K3App
//
//  The avatar, resolved from the user's companion choice — SHARED by every roomy
//  surface (the main-window panel and the voice-mode hero) so the chosen face
//  carries throughout, not just in voice mode. One place decides what the avatar
//  is; both callers render it.
//
//  The tiny menu-bar glyph + popover header are deliberately NOT routed through
//  here: a live 3D constellation can't render at 16–20px, so those stay M1K3's
//  pixel-M brand mark (the app's identity, distinct from the companion skin).
//  (The popover's full-bleed BACKDROP is different — since 2026-07-14 it routes
//  through here via AvatarChatBackground at hero size, same as the main chat;
//  the not-at-glyph-size rule above is about tiny renders, not the popover.)
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.75 (DRY selection +
//  compiles; the look in each surface is verify-by-run). Prior: VoiceModeView.avatar.
//  Review: claude-fable-5, 2026-07-18 — `paused` pass-through added so
//  AvatarChatBackground can finally honor ChatBackdropTreatment.animatesMotion
//  (recede/still/Reduce Motion freeze the pixel face; constellation + companion
//  surfaces don't take a pause yet — logged follow-up, they receive it as a
//  no-op param when they do).

import M1K3Avatar
import SwiftUI

struct AvatarSurface: View {
    let env: AppEnvironment
    /// Freeze idle motion (pixel-face surface only today — see header Review).
    var paused = false
    @AppStorage(AppEnvironment.voiceCompanionKey) private var companion = ""

    var body: some View {
        if companion == AppEnvironment.voiceCompanionConstellation {
            MemoryConstellationCanvas(env: env)
        } else if let spec = CompanionSpec.named(companion), CompanionAssets.isInstalled(spec) {
            // .id(spec.id): a creature→creature switch must REBUILD the RealityView
            // (fresh identity → fresh CompanionScene + make closure). Without it,
            // SwiftUI updates the view in place and the previous creature's built
            // scene survives — the picker appears to do nothing. Pixel↔creature↔
            // constellation switches change the view TYPE, so only same-type
            // swaps hit this; it fires only on an actual different companion
            // (the onboarding per-step-rebuild trap is the opposite failure).
            CompanionAvatarView(controller: env.avatar, companion: spec)
                .id(spec.id)
        } else {
            AvatarView(controller: env.avatar, paused: paused)
        }
    }
}
