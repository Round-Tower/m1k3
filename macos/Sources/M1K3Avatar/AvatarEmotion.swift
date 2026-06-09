//
//  AvatarEmotion.swift
//  M1K3Avatar
//
//  Ported from Android AvatarEmotion.kt, generalised for all M1K3 surfaces.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.9,
//  Prior: Kev + claude-opus-4-8 (AvatarEmotion.kt, app/composeApp)
//  Review: claude-opus-4-8, 2026-06-09 (PR #10 follow-up) — kept this package
//  UI-free: the SwiftUI `accentColor` mapping moved to M1K3App
//  (AvatarEmotion+SwiftUI.swift), so M1K3Avatar imports no SwiftUI.

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
}
