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
        .task { rebuild() }
    }

    /// Snapshot the live graph and lay it out. Best-effort: a closed/empty store
    /// just yields an empty constellation, never a crash.
    private func rebuild() {
        guard let store = env?.memoryStore else {
            model = ConstellationLayout.build(memories: [], edges: [])
            return
        }
        let memories = (try? store.allMemories(limit: 2000)) ?? []
        let edges = (try? store.allEdges()) ?? []
        model = ConstellationLayout.build(memories: memories, edges: edges)
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
