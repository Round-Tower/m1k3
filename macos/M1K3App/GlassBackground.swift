//
//  GlassBackground.swift
//  M1K3App
//
//  The shared base layer for every M1K3 surface: true Liquid Glass that refracts
//  what's behind the window and adapts to light/dark automatically (the system
//  visual-effect material follows the active appearance). Replaces the old
//  hardcoded indigo gradient so the whole app is one sheet of glass in both modes,
//  with no colour of our own imposed on the user's desktop.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.7, Prior: Unknown

import AppKit
import SwiftUI

/// A window-spanning Liquid Glass material. `.behindWindow` blending samples the
/// desktop / content behind the window; the material follows the system
/// appearance, so it reads correctly in both light and dark mode without us
/// tinting it. `.underWindowBackground` is the semantic "base of the window"
/// material, remapped to the Liquid Glass look on macOS 26.
struct GlassBackground: NSViewRepresentable {
    var material: NSVisualEffectView.Material = .underWindowBackground

    func makeNSView(context _: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = .behindWindow
        view.state = .active
        view.isEmphasized = true
        return view
    }

    func updateNSView(_ view: NSVisualEffectView, context _: Context) {
        view.material = material
    }
}

extension View {
    /// Lay a sheet of adaptive Liquid Glass behind this view, filling the window
    /// (ignores safe areas). Pair with `.scrollContentBackground(.hidden)` on any
    /// Form / List inside so their opaque backing doesn't cover the glass.
    func glassBackdrop(_ material: NSVisualEffectView.Material = .underWindowBackground) -> some View {
        background(GlassBackground(material: material).ignoresSafeArea())
    }
}
