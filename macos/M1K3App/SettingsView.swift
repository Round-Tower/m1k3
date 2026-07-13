//
//  SettingsView.swift
//  M1K3App
//
//  The Settings window shell — five tabs, one file each under Settings/. Split
//  out of a single ~700-line Form (2026-06-06 → 2026-07-13, it had grown to
//  the ViewBuilder type-checker's budget) into: M1K3 (brain + companion +
//  voice output), You (about-you + memories + reading), Privacy (web search /
//  Spotlight / MCP — the product's promise told in one place), General
//  (startup + notifications + sound + reasoning), Advanced (embeddings /
//  voice input / calls / generation stats / agent log / status / diagnostics
//  / licenses). Each pane owns its own @AppStorage/@State; this file is
//  purely composition. `AppEnvironment` and `LaunchAtLogin` reach every pane
//  via the `.environment(...)` M1K3App.swift already attaches to the
//  Settings scene — no re-injection needed here.
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.85 (a straight move
//  — every footer/copy carried verbatim except the three Kev-approved cuts
//  documented at each pane; verify-by-launch as ever for SwiftUI moves).
//  Prior: Kev + claude-opus-4-8 (SettingsView.swift lineage, 2026-06-06).
//

import SwiftUI

struct SettingsView: View {
    var body: some View {
        TabView {
            M1K3SettingsPane()
                .tabItem { Label("M1K3", systemImage: "brain") }
            YouSettingsPane()
                .tabItem { Label("You", systemImage: "person.crop.circle") }
            PrivacySettingsPane()
                .tabItem { Label("Privacy", systemImage: "hand.raised") }
            GeneralSettingsPane()
                .tabItem { Label("General", systemImage: "gearshape") }
            AdvancedSettingsPane()
                .tabItem { Label("Advanced", systemImage: "wrench.and.screwdriver") }
        }
        .frame(width: 480)
        .glassBackdrop()
    }
}
