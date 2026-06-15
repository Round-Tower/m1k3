//
//  MenuBarContent.swift
//  M1K3App
//
//  The menu shown by the status-bar item. Keeps M1K3 one click away and the
//  process resident — the menu-bar scene alone keeps the app alive after the
//  window is closed, so "always running for us and other users" needs no
//  LSUIElement hack and no custom termination policy. Everything here degrades
//  gracefully while the AppEnvironment is still waking (env may be nil).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.75, Prior: Unknown

import AppKit
import M1K3Launch
import SwiftUI

struct MenuBarContent: View {
    /// nil until the composition root finishes opening the on-disk store.
    let env: AppEnvironment?
    let launchAtLogin: LaunchAtLogin

    @Environment(\.openWindow) private var openWindow

    var body: some View {
        // Status line: which brain is live (read-only). Quietly omitted until
        // the environment is ready so the menu never shows a half-built state.
        if let env {
            Text("Brain: \(env.selectedBrain.displayName)")
            Divider()
        }

        Button("Open M1K3") { openMainWindow() }
            .keyboardShortcut("o", modifiers: [.command, .shift])

        SettingsLink {
            Text("Settings…")
        }
        .keyboardShortcut(",", modifiers: .command)

        Divider()

        Toggle("Launch at Login", isOn: launchBinding)

        Divider()

        Button("Quit M1K3") {
            NSApplication.shared.terminate(nil)
        }
        .keyboardShortcut("q", modifiers: .command)
    }

    /// Bridges the SwiftUI toggle to the reconcile policy: writes go through
    /// `setEnabled` (idempotent + error-catching), reads reflect the live status.
    private var launchBinding: Binding<Bool> {
        Binding(
            get: { launchAtLogin.isEnabled },
            set: { launchAtLogin.setEnabled($0) }
        )
    }

    /// Bring the main window forward (creating it if the user had closed it),
    /// and pull the app to the front since a menu-bar action doesn't activate it.
    /// `activate()` (no `ignoringOtherApps:`) is the macOS 14+ form — the system
    /// decides focus-stealing; the old parameter is soft-deprecated.
    private func openMainWindow() {
        NSApplication.shared.activate()
        openWindow(id: M1K3App.mainWindowID)
    }
}
