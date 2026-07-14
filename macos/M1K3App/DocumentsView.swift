//
//  DocumentsView.swift
//  M1K3App
//
//  See and manage what M1K3 remembers: the indexed items, newest first, with a
//  delete affordance. Thin UI over AppEnvironment.documents()/deleteDocument()
//  (which wrap the tested KnowledgeStore methods).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import M1K3Knowledge
import SwiftUI

struct DocumentsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss

    @State private var docs: [KnowledgeItem] = []

    var body: some View {
        VStack(spacing: 0) {
            header
            if docs.isEmpty {
                ContentUnavailableView {
                    Label("Nothing remembered yet", systemImage: "tray")
                } description: {
                    Text("Drop or import a PDF or text file and it’ll appear here.")
                }
            } else {
                List {
                    ForEach(docs) { doc in
                        DocumentRow(
                            doc: doc,
                            chunks: env.chunkCount(for: doc.id),
                            onDelete: { delete(doc) }
                        )
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
            Label("Documents", systemImage: "books.vertical")
                .font(.pixelTitle)
            Spacer()
            Text("\(docs.count) item\(docs.count == 1 ? "" : "s")")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            Button("Done") { dismiss() }
                .buttonStyle(.glassProminent)
        }
        .padding(16)
    }

    private func reload() {
        docs = env.documents()
    }

    private func delete(_ doc: KnowledgeItem) {
        env.deleteDocument(id: doc.id)
        reload()
    }
}

private struct DocumentRow: View {
    let doc: KnowledgeItem
    let chunks: Int
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(doc.title)
                    .lineLimit(1)
                Text("\(doc.kind.rawValue) · \(chunks) chunk\(chunks == 1 ? "" : "s")")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(role: .destructive, action: onDelete) {
                Image(systemName: "trash")
            }
            .buttonStyle(.borderless)
            .help("Delete this document")
            .accessibilityLabel("Delete \(doc.title)")
        }
        .padding(.vertical, 4)
    }

    private var icon: String {
        switch doc.kind {
        case .call: "phone"
        case .note: "note.text"
        case .memory: "brain"
        default: "doc.text"
        }
    }
}
