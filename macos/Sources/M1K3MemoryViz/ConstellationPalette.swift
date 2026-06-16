//
//  ConstellationPalette.swift
//  M1K3MemoryViz
//
//  Maps a node's hue (from ConstellationLayout.hue, kind-derived) to the colours
//  the view paints — a SwiftUI Color for any chrome and a platform Material.Color
//  for the RealityKit mote materials. Saturation/brightness are fixed here so the
//  field reads as one palette rather than a rainbow.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.8. Prior: Unknown.

import SwiftUI

#if canImport(AppKit)
    import AppKit
#endif

public enum ConstellationPalette {
    /// Fixed saturation/brightness so motes share a family; only hue varies.
    static let saturation: CGFloat = 0.68
    static let brightness: CGFloat = 1.0

    public static func color(forHue hue: Float) -> Color {
        Color(hue: Double(hue), saturation: Double(saturation), brightness: Double(brightness))
    }

    #if canImport(AppKit)
        /// RealityKit's `Material.Color` is `NSColor` on macOS — what
        /// `UnlitMaterial(color:)` wants for a glowing mote.
        public static func materialColor(forHue hue: Float) -> NSColor {
            NSColor(hue: CGFloat(hue), saturation: saturation, brightness: brightness, alpha: 1.0)
        }
    #endif
}
