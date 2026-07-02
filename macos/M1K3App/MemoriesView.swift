//
//  MemoriesView.swift
//  M1K3App
//
//  The memory consent surface: every fact M1K3 holds about you, who wrote it
//  ("you told me" vs "I noticed"), and one-tap forget. Visibility + deletion
//  are the two halves of the privacy promise — nothing here is hidden, and
//  delete is the real KnowledgeStore cascade, not a soft hide.
//
//  Thin UI over AppEnvironment.documents(kind: .memory)/deleteDocument()
//  (tested KnowledgeStore methods underneath), DocumentsView's shape.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.85, Prior: Unknown

import M1K3Knowledge
import SwiftUI

struct MemoriesView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss

    @State private var memories: [KnowledgeItem] = []

    var body: some View {
        VStack(spacing: 0) {
            header
            if memories.isEmpty {
                ContentUnavailableView {
                    Label("Nothing remembered about you yet", systemImage: "brain")
                } description: {
                    Text("Tell M1K3 to remember something, or let it learn as you chat.")
                }
            } else {
                List {
                    ForEach(memories) { memory in
                        MemoryRow(memory: memory, onDelete: { delete(memory) })
                    }
                }
                .listStyle(.inset)
                .scrollContentBackground(.hidden)
            }
        }
        .frame(width: 480, height: 480)
        .glassBackdrop()
        .onAppear { reload() }
    }

    private var header: some View {
        HStack {
            Label("Memories", systemImage: "brain")
                .font(.pixelTitle)
            Spacer()
            Text("\(memories.count) memor\(memories.count == 1 ? "y" : "ies")")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            Button("Done") { dismiss() }
                .buttonStyle(.glassProminent)
        }
        .padding(16)
    }

    private func reload() {
        memories = env.documents(kind: .memory)
    }

    private func delete(_ memory: KnowledgeItem) {
        env.deleteDocument(id: memory.id)
        reload()
    }
}

private struct MemoryRow: View {
    let memory: KnowledgeItem
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "brain")
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(memory.title)
                    .lineLimit(2)
                Text("\(provenance) · \(memory.createdAt.formatted(date: .abbreviated, time: .omitted))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(role: .destructive, action: onDelete) {
                Image(systemName: "trash")
            }
            .buttonStyle(.borderless)
            .help("Forget this")
            .accessibilityLabel("Forget \(memory.title)")
        }
        .padding(.vertical, 4)
    }

    private var provenance: String {
        switch memory.source {
        case .user: "you told me"
        case .distilled: "I noticed"
        default: "remembered"
        }
    }
}
