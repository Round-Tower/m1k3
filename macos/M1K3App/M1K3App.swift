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
                    ContentView()
                        .environment(env)
                } else if let startupError {
                    StartupFailureView(message: startupError)
                } else {
                    ProgressView("Waking M1K3…")
                        .controlSize(.large)
                        .frame(minWidth: 560, minHeight: 480)
                }
            }
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
