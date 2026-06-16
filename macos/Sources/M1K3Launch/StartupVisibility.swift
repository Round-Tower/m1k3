//
//  StartupVisibility.swift
//  M1K3Launch
//
//  Whether M1K3 shows up as a normal windowed app or lives quietly in the menu
//  bar only. This is the pure policy behind the "Show in menu bar only" toggle —
//  the app maps it onto AppKit's activation policy + SwiftUI's launch behaviour,
//  but the decision (does it hide the Dock icon? does it suppress the launch
//  window?) is tested here, AppKit-free.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.8, Prior: Unknown

import Foundation

/// The two startup/residency stances for the app.
public enum StartupVisibility: Sendable, Equatable {
    /// Default: a regular app — Dock icon, window opens at launch.
    case dockAndWindow
    /// Menu-bar companion: no Dock icon, no window forced open at launch. The
    /// status-bar item is the surface; the menu's "Open M1K3" summons a window.
    /// This is what makes a launch-at-login start quiet instead of focus-stealing.
    case menuBarOnly

    public init(menuBarOnly: Bool) {
        self = menuBarOnly ? .menuBarOnly : .dockAndWindow
    }

    /// Drop the Dock icon (maps to `.accessory` activation policy).
    public var hidesDockIcon: Bool {
        self == .menuBarOnly
    }

    /// Don't auto-present the main window at launch (maps to a suppressed launch
    /// behaviour) — the user opens it deliberately from the menu.
    public var suppressesLaunchWindow: Bool {
        self == .menuBarOnly
    }
}

/// UserDefaults keys for startup preferences, shared by the app and (potentially)
/// the headless paths so the string lives in exactly one place.
public enum StartupPreferences {
    public static let menuBarOnlyKey = "startup.menuBarOnly"
}
