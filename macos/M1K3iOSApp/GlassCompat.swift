//
//  GlassCompat.swift
//  M1K3iOS / M1K3visionOS
//
//  A tiny cross-platform surface for M1K3's glass chips. Liquid Glass's
//  `glassEffect(_:in:)` modifier ships on iOS/macOS but is UNAVAILABLE on
//  visionOS (which has its own spatial material system — `glassBackgroundEffect`
//  is for whole volumes/windows, not inline chips). So the shell routes every
//  bubble/pill/card through one helper: Liquid Glass on iOS, a `.regularMaterial`
//  fill on visionOS — same rounded-rect shape, one call site to evolve when the
//  spatial flagship (Phase D) gives these a proper volumetric treatment.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-06, Confidence 0.8. Prior: Unknown.
//

import SwiftUI

extension View {
    /// M1K3's glass chip, portable across iOS and visionOS.
    @ViewBuilder
    func m1k3Glass(cornerRadius: CGFloat = 16, tint: Color? = nil) -> some View {
        #if os(visionOS)
            background(.regularMaterial, in: .rect(cornerRadius: cornerRadius))
        #else
            if let tint {
                glassEffect(.regular.tint(tint), in: .rect(cornerRadius: cornerRadius))
            } else {
                glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
            }
        #endif
    }
}
