//
//  AppEnvironment+Avatar.swift
//  M1K3App
//
//  Avatar-presentation settings keys. Kept in their own extension file (not the
//  already-large AppEnvironment.swift) so the avatar-background feature lands
//  self-contained.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.9, Prior: Unknown

extension AppEnvironment {
    /// Render the avatar as a full-window background behind the chat. Default OFF
    /// — bold but opt-in; reactive (recedes while reading/typing). Read by
    /// ContentView and the Settings companion section.
    nonisolated static let avatarBackgroundKey = "chat.avatarBackground"
}
