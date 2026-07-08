//
//  RootView.swift
//  M1K3iOS / M1K3visionOS
//
//  The adaptive shell: a first-run onboarding gate, then a home built on the
//  iOS 26 adaptive sidebar (`TabView` + `.tabViewStyle(.sidebarAdaptable)`). This
//  is the "full sidebar menu": a real sidebar on iPad / visionOS (and an
//  expandable sidebar on iPhone, with the bottom tab bar kept for one-handed
//  reach). Sections group the menu — Chat up top, a Workspace group (Memories +
//  Documents), Settings at the foot. The spatial flagship (volumetric avatar +
//  walkable constellation) is the Phase-D follow.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-06, Confidence 0.8. Prior: Kev +
//  claude-fable-5 (the TabView form).
//

import SwiftUI

struct RootView: View {
    @Environment(AppCore.self) private var core
    @State private var onboarded: Bool
    @State private var selection: Section = .chat

    /// The sidebar's menu entries — a stable identity for the selection binding.
    enum Section: Hashable {
        case chat, memories, documents, settings
    }

    init(startOnboarded: Bool) {
        _onboarded = State(initialValue: startOnboarded)
    }

    var body: some View {
        if onboarded {
            home
        } else {
            OnboardingScreen {
                // Sole writer of the first-run gate: selectBrain can no-op
                // early-return (picking Mini at idle) before it could persist this,
                // so onboarding must record its own completion or it repeats every
                // launch (startOnboarded reads hasChosenBrain at init).
                UserDefaults.standard.set(true, forKey: AppCore.hasChosenBrainKey)
                withAnimation { onboarded = true }
            }
            .transition(.opacity)
        }
    }

    private var home: some View {
        TabView(selection: $selection) {
            Tab("Chat", systemImage: "bubble.left.and.text.bubble.right", value: Section.chat) {
                NavigationStack { ChatScreen() }
            }

            TabSection("Workspace") {
                Tab("Memories", systemImage: "brain", value: Section.memories) {
                    MemoriesScreen()
                }
                Tab("Documents", systemImage: "doc.text", value: Section.documents) {
                    DocumentsScreen()
                }
            }

            Tab("Settings", systemImage: "gearshape", value: Section.settings) {
                SettingsScreen()
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .preferredColorScheme(.dark)
    }
}
