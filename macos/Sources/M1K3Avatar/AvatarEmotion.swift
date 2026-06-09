//
//  AvatarEmotion.swift
//  M1K3Avatar
//
//  Ported from Android AvatarEmotion.kt, generalised for all M1K3 surfaces.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.9,
//  Prior: Kev + claude-opus-4-8 (AvatarEmotion.kt, app/composeApp)
//
//  Known tradeoff (review claude-sonnet-4-6, 2026-06-09): `accentColor: Color`
//  imports SwiftUI into what is otherwise a pure, swift-test-able data package.
//  Accepted for now — `Color` is available under `swift test` on macOS 26 and the
//  call site (AvatarView/SwiftUI) wants a `Color` directly. If this package ever
//  needs to stay UI-free, replace with a semantic `ColorToken` resolved to `Color`
//  at the view layer. Deferred, not blocking.

import SwiftUI

public enum AvatarEmotion: String, CaseIterable, Sendable {
    case neutral
    case happy
    case sad
    case angry
    case surprised
    case love
    case thinking
    case excited
    case sleepy

    public static func from(_ string: String) -> AvatarEmotion {
        AvatarEmotion(rawValue: string.lowercased()) ?? .neutral
    }

    /// Accent colour for UI elements that reflect M1K3's current emotional state.
    public var accentColor: Color {
        switch self {
        case .neutral: .secondary
        case .happy: .green
        case .sad: .blue
        case .angry: .red
        case .surprised: .yellow
        case .love: .pink
        case .thinking: .purple
        case .excited: Color(red: 1.0, green: 0.6, blue: 0.0)
        case .sleepy: Color(red: 0.38, green: 0.49, blue: 0.55)
        }
    }
}
