//
//  AudioCaptureBackdrop.swift
//  M1K3App
//
//  Two slow, blurred gradient orbs that drift behind the content while M1K3 is
//  capturing audio — recording a call or taking dictation. An ambient "I'm
//  listening" cue over the Liquid Glass, not a literal level meter. Driven by a
//  TimelineView so the motion is continuous and state-free; honours Reduce Motion
//  by rendering the orbs static.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.75, Prior: Unknown

import SwiftUI

struct AudioCaptureBackdrop: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if reduceMotion {
            orbs(phase: 0)
        } else {
            TimelineView(.animation) { timeline in
                orbs(phase: timeline.date.timeIntervalSinceReferenceDate)
            }
        }
    }

    private func orbs(phase: Double) -> some View {
        GeometryReader { geo in
            let width = geo.size.width
            let height = geo.size.height
            let span = min(width, height)
            ZStack {
                orb(color: .accentColor, diameter: span * 0.95)
                    .position(
                        x: width * (0.32 + 0.12 * cos(phase * 0.35)),
                        y: height * (0.40 + 0.16 * sin(phase * 0.27))
                    )
                orb(color: .purple, diameter: span * 0.85)
                    .position(
                        x: width * (0.70 + 0.13 * cos(phase * 0.23 + 1.5)),
                        y: height * (0.58 + 0.14 * sin(phase * 0.31 + 0.8))
                    )
            }
            .blur(radius: 64)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    private func orb(color: Color, diameter: Double) -> some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: [color.opacity(0.45), color.opacity(0)],
                    center: .center,
                    startRadius: 0,
                    endRadius: diameter / 2
                )
            )
            .frame(width: diameter, height: diameter)
    }
}
