//
//  MemoriesView.swift
//  M1K3App
//
//  The memory surface, now explorable: every fact M1K3 holds about you, who
//  wrote it ("you told me" vs "I noticed"), a free-text interrogation box
//  ("what do you know about X?"), one-tap forget, and a tap-through into each
//  fact's detail — its correction history and its connections in the graph.
//
//  Reads the memory GRAPH (AppEnvironment+Memory) rather than the corpus twin
//  the old flat list used, so rows carry the ids + edges + provenance that make
//  traversal possible. Delete is still the real cascade (graph + corpus twin).
//
//  Signed: Kev + claude-opus-4-8, 2026-07-20, Confidence 0.82 (verify-owed =
//  on-device click-through). Prior: flat list over documents(kind: .memory)
//  (Kev + claude-fable-5, 2026-06-12).

import M1K3Memory
import SwiftUI

/// The consent-facing label for a fact's provenance. UI copy lives here
/// (localized), while the pure `MemoryProvenance` classifier lives in the core.
extension MemoryProvenance {
    var label: String {
        switch self {
        case .youToldMe: String(localized: "you told me")
        case .iNoticed: String(localized: "I noticed")
        case .remembered: String(localized: "remembered")
        }
    }
}

struct MemoriesView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.openWindow) private var openWindow

    @State private var memories: [Memory] = []
    @State private var query: String = ""
    /// nil = not interrogating (show the full list); non-nil = search results.
    @State private var results: [Memory]?
    @State private var searching = false

    /// What the list renders: interrogation results when searching, else all.
    private var shown: [Memory] {
        results ?? memories
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                interrogateField
                content
            }
            .navigationDestination(for: Memory.self) { memory in
                MemoryDetailView(memory: memory)
            }
        }
        .onAppear { reload() }
    }

    private var header: some View {
        HStack {
            Label("Memories", systemImage: "brain")
                .font(.pixelTitle)
            Spacer()
            Button {
                openWindow(id: M1K3App.constellationWindowID)
            } label: {
                Label("Constellation", systemImage: "sparkles")
            }
            .buttonStyle(.borderless)
            .help("See your memories as a 3D constellation")
            Text("\(memories.count) memor\(memories.count == 1 ? "y" : "ies")")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .padding(16)
    }

    private var interrogateField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            TextField("Ask what I know…", text: $query)
                .textFieldStyle(.plain)
                .onSubmit(runInterrogation)
            if searching {
                ProgressView().controlSize(.small)
            } else if results != nil {
                Button {
                    query = ""
                    results = nil
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    @ViewBuilder
    private var content: some View {
        if shown.isEmpty {
            ContentUnavailableView {
                Label(emptyTitle, systemImage: "brain")
            } description: {
                Text(emptyMessage)
            }
        } else {
            List {
                ForEach(shown) { memory in
                    NavigationLink(value: memory) {
                        MemoryRow(memory: memory)
                    }
                    .swipeActions {
                        Button(role: .destructive) { forget(memory) } label: {
                            Label("Forget", systemImage: "trash")
                        }
                    }
                }
            }
            .listStyle(.inset)
            .scrollContentBackground(.hidden)
        }
    }

    private var emptyTitle: String {
        results == nil
            ? String(localized: "Nothing remembered about you yet")
            : String(localized: "No memories match that")
    }

    private var emptyMessage: String {
        results == nil
            ? String(localized: "Tell M1K3 to remember something, or let it learn as you chat.")
            : String(localized: "Try different words, or clear the search to see everything.")
    }

    private func reload() {
        memories = env.memories()
    }

    private func runInterrogation() {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else {
            results = nil
            return
        }
        searching = true
        Task {
            let hits = await env.interrogateMemories(q)
            await MainActor.run {
                results = hits
                searching = false
            }
        }
    }

    private func forget(_ memory: Memory) {
        env.forgetMemory(memory)
        reload()
        // Keep an active search consistent without a round-trip re-query.
        results?.removeAll { $0.id == memory.id }
    }
}

/// One row in the memory list: the fact, its provenance, and when it landed.
/// Navigation + forget are owned by the parent so the row stays a pure label.
private struct MemoryRow: View {
    let memory: Memory

    private var displayText: String {
        // Titled MCP facts carry discriminating context the bare text may lack.
        if let title = memory.title, !title.isEmpty, title != memory.text {
            return title
        }
        return memory.text
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "brain")
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(displayText)
                    .lineLimit(2)
                Text("\(MemoryProvenance(source: memory.source).label) · \(memory.createdAt.formatted(date: .abbreviated, time: .omitted))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
