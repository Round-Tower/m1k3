//
//  AppEnvironment+Avatar.swift
//  M1K3App
//
//  Avatar-presentation settings. One control governs how M1K3's avatar
//  (companion / constellation / pixel face) appears in the chat — replacing the
//  two separate "show panel" + "show background" toggles with a single mode.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.9, Prior: Unknown

import Foundation

/// Where the avatar shows in the chat. One picker, three states — unifies the
/// old `showAvatar` (panel) and `chat.avatarBackground` (backdrop) toggles.
enum AvatarDisplay: String, CaseIterable, Identifiable {
    /// The 200pt strip above the transcript (the long-standing default).
    case panel
    /// Full-window backdrop behind the glass bubbles; recedes while reading/typing.
    case background
    /// No avatar — just the transcript.
    case off

    var id: String {
        rawValue
    }

    var label: String {
        switch self {
        case .panel: "Panel"
        case .background: "Background"
        case .off: "Off"
        }
    }

    /// Menu/toolbar glyph for each mode (the toolbar shows the current one).
    var systemImage: String {
        switch self {
        case .panel: "bird.fill"
        case .background: "rectangle.fill.on.rectangle.fill"
        case .off: "bird"
        }
    }
}

extension AppEnvironment {
    /// How the avatar appears in the chat (`AvatarDisplay`). Default `.panel`
    /// (preserves the prior `showAvatar = true` behaviour). Read by ContentView
    /// and the Settings companion section.
    nonisolated static let avatarDisplayKey = "chat.avatarDisplay"
}
