//
//  M1K3App.swift
//  M1K3App
//
//  Entry point. The composition root (AppEnvironment) opens the on-disk store, so
//  construction can fail — we build it once in a task and show a clear failure
//  surface rather than crashing if the container isn't writable.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import SwiftUI

@main
struct M1K3App: App {
    @State private var env: AppEnvironment?
    @State private var startupError: String?
    /// First-run gate: show "choose your brain" until a brain has been picked.
    @AppStorage(AppEnvironment.hasChosenBrainKey) private var hasChosenBrain = false

    var body: some Scene {
        WindowGroup {
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

        // Native macOS Settings scene — opened with ⌘, (or the toolbar gear via
        // SettingsLink), in its own window with the system title bar, instead of
        // the iOS-style modal sheet it used to be. Shares the one AppEnvironment;
        // the env-nil branch only shows in the first beat before startup completes.
        // Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.8, Prior: Unknown
        Settings {
            Group {
                if let env {
                    SettingsView().environment(env)
                } else {
                    Text("M1K3 is still waking up…")
                        .foregroundStyle(.secondary)
                        .frame(width: 480, height: 220)
                }
            }
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
