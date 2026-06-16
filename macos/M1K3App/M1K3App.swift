//
//  M1K3App.swift
//  M1K3App
//
//  Entry point. The composition root (AppEnvironment) opens the on-disk store, so
//  construction can fail — we build it once in a task and show a clear failure
//  surface rather than crashing if the container isn't writable.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import AppKit
import M1K3Launch
import SwiftUI

@main
struct M1K3App: App {
    /// Stable id so the menu-bar item can re-open the main window after it's closed.
    static let mainWindowID = "main"

    /// Sets the activation policy (Dock icon vs accessory) before the first
    /// window appears, so a menu-bar-only launch never flashes a Dock icon.
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    @State private var env: AppEnvironment?
    @State private var startupError: String?
    /// Launch-at-login is app-level (not on AppEnvironment) so it works even if
    /// the store fails to open — the toggle and menu must function regardless.
    @State private var launchAtLogin = LaunchAtLogin(item: SMAppServiceLoginItem())
    /// First-run gate: show "choose your brain" until a brain has been picked.
    @AppStorage(AppEnvironment.hasChosenBrainKey) private var hasChosenBrain = false
    /// Menu-bar mark (favicon "M" by default) and whether to live in the menu bar
    /// only (no Dock icon, no window forced open at launch).
    @AppStorage(MenuBarGlyphStyle.storageKey) private var glyphStyle = MenuBarGlyphStyle.pixelM
    @AppStorage(StartupPreferences.menuBarOnlyKey) private var menuBarOnly = false

    init() {
        BundledFonts.register()
    }

    var body: some Scene {
        WindowGroup(id: Self.mainWindowID) {
            Group {
                if SelfTest.isRequested {
                    ProgressView("Running self-test…")
                        .frame(minWidth: 360, minHeight: 200)
                        .task {
                            await SelfTest.run()
                            exit(0)
                        }
                } else if let env {
                    if hasChosenBrain {
                        ContentView()
                            .environment(env)
                    } else {
                        OnboardingView { hasChosenBrain = true }
                            .environment(env)
                    }
                } else if let startupError {
                    StartupFailureView(message: startupError)
                } else {
                    ProgressView("Waking M1K3…")
                        .controlSize(.large)
                        .frame(minWidth: 560, minHeight: 480)
                }
            }
            // No forced colour scheme: M1K3 follows the system appearance. The
            // surface is pure adaptive Liquid Glass (GlassBackground) that refracts
            // the desktop and reads correctly in both light and dark — so the old
            // painted-dark / white-sheet mismatch can't happen in either mode.
            // Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.75, Prior: Unknown
            .task {
                guard !SelfTest.isRequested, env == nil, startupError == nil else { return }
                do {
                    env = try AppEnvironment()
                } catch {
                    startupError = error.localizedDescription
                }
            }
        }
        .windowResizability(.contentSize)
        // Hidden title bar: drop the "M1K3" window title so the glass runs
        // uninterrupted to the top. Traffic lights + toolbar actions remain; the
        // app's identity is the conversation, not a chrome label.
        .windowStyle(.hiddenTitleBar)
        // Menu-bar-only: don't auto-present the window at launch (incl. login) —
        // M1K3 starts quietly in the bar and the menu's "Open M1K3" summons it.
        .defaultLaunchBehavior(menuBarOnly ? .suppressed : .automatic)

        // Native macOS Settings scene — opened with ⌘, (or the toolbar gear via
        // SettingsLink), in its own window with the system title bar, instead of
        // the iOS-style modal sheet it used to be. Shares the one AppEnvironment;
        // the env-nil branch only shows in the first beat before startup completes.
        // Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.8, Prior: Unknown
        Settings {
            Group {
                if let env {
                    SettingsView()
                        .environment(env)
                        .environment(launchAtLogin)
                } else {
                    Text("M1K3 is still waking up…")
                        .foregroundStyle(.secondary)
                        .frame(width: 480, height: 220)
                }
            }
        }

        // The always-resident status-bar item. Its presence alone keeps M1K3
        // running after the window is closed — the menu-bar companion. A rich glass
        // popover (ask without opening the window) behind a state-reflective pixel
        // glyph (calm idle, pulsing while thinking, red while recording, glow while
        // speaking).
        // Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.7, Prior: Unknown
        MenuBarExtra {
            MenuBarPopover(env: env)
        } label: {
            MenuBarLabel(env: env, glyphStyle: glyphStyle)
        }
        .menuBarExtraStyle(.window)
    }
}

/// Applies the menu-bar-only activation policy before the first window shows, so
/// no Dock icon flashes on a quiet launch. Read straight from UserDefaults (the
/// @AppStorage key) — the delegate runs before SwiftUI state is available.
final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationWillFinishLaunching(_: Notification) {
        let menuBarOnly = UserDefaults.standard.bool(forKey: StartupPreferences.menuBarOnlyKey)
        if StartupVisibility(menuBarOnly: menuBarOnly).hidesDockIcon {
            NSApp.setActivationPolicy(.accessory)
        }
    }
}

private struct StartupFailureView: View {
    let message: String

    var body: some View {
        ContentUnavailableView {
            Label("M1K3 couldn’t start", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        }
        .frame(minWidth: 560, minHeight: 480)
    }
}
