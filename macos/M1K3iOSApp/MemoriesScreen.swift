//
//  MemoriesScreen.swift
//  M1K3iOS / M1K3visionOS
//
//  The temporal memory graph, on mobile: a live fact count and a hybrid recall
//  search (FTS + cosine, RRF-fused with a similarity cutoff — the same MemoryStore
//  query the MCP recall_memory tool runs). Read-only for v1; capture happens
//  through chat auto-distillation, not a form.
//
//  Signed: Kev + claude-fable-5, 2026-07-06, Confidence 0.75. Prior: Unknown.
//

import M1K3Knowledge
import M1K3Memory
import SwiftUI

struct MemoriesScreen: View {
    @Environment(AppCore.self) private var core
    @State private var query = ""
    @State private var hits: [MemoryHit] = []
    @State private var liveCount = 0
    @State private var searching = false

    var body: some View {
        NavigationStack {
            Group {
                if core.memoryStore == nil {
                    ContentUnavailableView(
                        "Memory unavailable",
                        systemImage: "brain",
                        description: Text("M1K3's memory store couldn't open on this device.")
                    )
                } else if query.isEmpty {
                    ContentUnavailableView {
                        Label("\(liveCount) memories", systemImage: "brain")
                    } description: {
                        // Honest for this phase: recall works; automatic capture from
                        // chat isn't wired into the mobile shell yet (the Mac's
                        // MemoryDistillationCoordinator is the follow-on). Don't
                        // promise learning that doesn't happen here.
                        Text(liveCount == 0
                            ? "Nothing here yet. On this early build, memories aren’t captured from chat on mobile — that’s coming. Everything stays on your device."
                            : "Search what M1K3 remembers — all on your device.")
                    }
                } else if hits.isEmpty, !searching {
                    ContentUnavailableView.search(text: query)
                } else {
                    List(hits) { hit in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(hit.memory.text).font(.body)
                            Text(hit.memory.source)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Memories")
            .searchable(text: $query, prompt: "Recall a memory")
            .onSubmit(of: .search) { Task { await search() } }
            .onChange(of: query) { _, new in if new.isEmpty { hits = [] } }
            .onAppear { liveCount = (try? core.memoryStore?.liveCount()) ?? 0 }
        }
    }

    private func search() async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let memoryStore = core.memoryStore else { return }
        searching = true
        defer { searching = false }
        do {
            let vector = try await core.embedder.embed(text)
            hits = try memoryStore.recall(query: text, queryVector: vector, limit: 20)
        } catch {
            hits = []
        }
    }
}
