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

import M1K3Knowledge
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
            // No graph store yet — still seed from the knowledge base so the
            // window isn't blank.
            if model == nil { model = buildSeeded(graphMemories: [], edges: []) }
            return
        }
        let count = (try? store.liveCount()) ?? 0
        guard count != lastCount || model == nil else { return }
        lastCount = count
        let memories = (try? store.allMemories(limit: 2000)) ?? []
        let edges = (try? store.allEdges()) ?? []
        model = buildSeeded(graphMemories: memories, edges: edges)
    }

    /// Lay out the live graph UNIONed with existing `.memory` items from the
    /// knowledge base — so the constellation shows what M1K3 already knows on
    /// first open, then grows as the graph store fills (dedup keeps dual-written
    /// facts from showing twice).
    private func buildSeeded(graphMemories: [Memory], edges: [MemoryEdge]) -> ConstellationModel {
        let merged = ConstellationSeed.merge(graph: graphMemories, seeds: knowledgeSeeds())
        return ConstellationLayout.build(memories: merged, edges: edges, maxNodes: maxNodes)
    }

    /// Existing memories from the document/knowledge store, mapped to motes.
    /// They carry no edges (the graph layer is the new store's job) — a scattered
    /// field that threads itself together as relations accrue.
    private func knowledgeSeeds() -> [Memory] {
        guard let knowledge = env?.store else { return [] }
        let items = (try? knowledge.allItems(kind: .memory, limit: 500)) ?? []
        return items.map { item in
            Memory(id: item.id, kind: .note, text: item.title, source: "knowledge", createdAt: item.createdAt)
        }
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
