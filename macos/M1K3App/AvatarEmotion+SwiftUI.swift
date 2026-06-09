//
//  AvatarEmotion+SwiftUI.swift
//  M1K3App
//
//  The SwiftUI colour mapping for AvatarEmotion lives here, in the app target,
//  so the M1K3Avatar package stays pure (no SwiftUI import) and fully
//  `swift test`-able. AvatarView is the only caller.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-09, Confidence 0.9,
//  Prior: M1K3Avatar/AvatarEmotion.swift (Kev + claude-sonnet-4-6) — the
//  accentColor mapping, relocated out of the pure data package.

import M1K3Avatar
import SwiftUI

extension AvatarEmotion {
    /// Accent colour for UI elements that reflect M1K3's current emotional state.
    var accentColor: Color {
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
