//
//  ConstellationWindow.swift
//  M1K3App
//
//  The window + menu command that open the 3D memory constellation. Pulls the
//  live graph from the store (allMemories + allEdges), builds the pure
//  ConstellationModel, and hands it to the RealityKit ConstellationView. The
//  layout/colour/geometry are all tested upstream in M1K3Memory / M1K3MemoryViz;
//  this is app glue (verify-by-run, like the other windows).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.75 (compiles against
//  the viz target; the live look + the "grows over time" feel are Kev's ⌘R).
//  Prior: Unknown.

import M1K3Memory
import M1K3MemoryViz
import SwiftUI

extension M1K3App {
    /// Stable id so the menu command summons (not respawns) the constellation.
    static let constellationWindowID = "constellation"
}

struct ConstellationWindowContent: View {
    let env: AppEnvironment?
    @State private var model: ConstellationModel?
    /// Last seen live-memory count — the cheap change signal that gates a relayout.
    @State private var lastCount = -1

    /// Cap the field so a big store stays legible and the O(n²) layout stays cheap;
    /// the view shows the newest motes. Poll cadence for "grows over time".
    private let maxNodes = 300
    private let refresh: Duration = .seconds(2)

    var body: some View {
        Group {
            if let model {
                if model.isEmpty {
                    ContentUnavailableView(
                        "No memories yet",
                        systemImage: "sparkles",
                        description: Text("As M1K3 remembers things, they appear here as a constellation that grows over time.")
                    )
                } else {
                    ConstellationView(model: model)
                }
            } else {
                ProgressView("Mapping memory…")
            }
        }
        .frame(minWidth: 640, minHeight: 480)
        .navigationTitle("Memory Constellation")
        .task { await watch() }
    }

    /// Poll the store while the window is open, relaying out only when the live
    /// memory count actually changes — so a new `remember` makes a mote appear
    /// within a couple of seconds, but an idle window does no work.
    private func watch() async {
        rebuildIfChanged()
        while !Task.isCancelled {
            try? await Task.sleep(for: refresh)
            rebuildIfChanged()
        }
    }

    private func rebuildIfChanged() {
        guard let store = env?.memoryStore else {
            if model == nil { model = ConstellationLayout.build(memories: [], edges: []) }
            return
        }
        let count = (try? store.liveCount()) ?? 0
        guard count != lastCount || model == nil else { return }
        lastCount = count
        let memories = (try? store.allMemories(limit: 2000)) ?? []
        let edges = (try? store.allEdges()) ?? []
        model = ConstellationLayout.build(memories: memories, edges: edges, maxNodes: maxNodes)
    }
}

/// Adds "Memory Constellation" to the Window menu.
struct ConstellationCommands: Commands {
    var body: some Commands {
        CommandGroup(after: .windowList) {
            ConstellationMenuItem()
        }
    }
}

private struct ConstellationMenuItem: View {
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Button("Memory Constellation") {
            openWindow(id: M1K3App.constellationWindowID)
        }
    }
}
