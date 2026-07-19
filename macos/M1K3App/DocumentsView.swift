//
//  DocumentsView.swift
//  M1K3App
//
//  See and manage what M1K3 remembers: the indexed items, newest first, with a
//  delete affordance and a quarantine toggle. Thin UI over AppEnvironment.documents()
//  / deleteDocument() / tagDocument() (which wrap the tested KnowledgeStore methods).
//
//  Quarantine doctrine: items tagged .quarantined are stored and embedded normally
//  but invisible to every retrieval surface (search, grounding, listing, MCP).
//  This view is the ONLY place users can quarantine or restore items. The
//  show-internal toggle reveals the quarantined section; quarantined items are
//  shown muted with a lock icon so their status is unambiguous.
//
//  Restore always targets .document — the original kind is not persisted at the
//  item level. In practice quarantined items are always .document (QA notes,
//  canary honeypots); .call items restored here still expose their content
//  correctly, just with a "document" kind label.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-07-14, Confidence 0.85 (store layer
//  unit-pinned; UI is verify-by-launch). Prior: Kev + claude-opus-4-8, 2026-06-06.

import M1K3Knowledge
import SwiftUI

struct DocumentsView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var docs: [KnowledgeItem] = []
    @State private var quarantinedDocs: [KnowledgeItem] = []
    @State private var showInternal = false

    var body: some View {
        VStack(spacing: 0) {
            header
            let hasContent = !docs.isEmpty || (showInternal && !quarantinedDocs.isEmpty)
            if !hasContent {
                ContentUnavailableView {
                    Label("Nothing remembered yet", systemImage: "tray")
                } description: {
                    Text("Drop or import a PDF or text file and it'll appear here.")
                }
            } else {
                List {
                    ForEach(docs) { doc in
                        DocumentRow(
                            doc: doc,
                            chunks: env.chunkCount(for: doc.id),
                            onDelete: { delete(doc) },
                            onTag: { quarantine(doc) }
                        )
                    }
                    if showInternal && !quarantinedDocs.isEmpty {
                        Section {
                            ForEach(quarantinedDocs) { doc in
                                DocumentRow(
                                    doc: doc,
                                    chunks: env.chunkCount(for: doc.id),
                                    onDelete: { delete(doc) },
                                    onTag: { restore(doc) }
                                )
                            }
                        } header: {
                            Label("Internal — hidden from AI", systemImage: "lock.fill")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .listStyle(.inset)
                .scrollContentBackground(.hidden)
            }
        }
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
            Button {
                showInternal.toggle()
                reload()
            } label: {
                Image(systemName: showInternal ? "lock.open" : "lock")
            }
            .buttonStyle(.borderless)
            .help(showInternal ? "Hide internal items" : "Show quarantined items")
            .foregroundStyle(showInternal ? Color.primary : Color.secondary)
        }
        .padding(16)
    }

    private func reload() {
        docs = env.documents()
        quarantinedDocs = showInternal ? env.documents(kind: .quarantined) : []
    }

    private func delete(_ doc: KnowledgeItem) {
        env.deleteDocument(id: doc.id)
        reload()
    }

    private func quarantine(_ doc: KnowledgeItem) {
        env.tagDocument(id: doc.id, kind: .quarantined)
        reload()
    }

    private func restore(_ doc: KnowledgeItem) {
        env.tagDocument(id: doc.id, kind: .document)
        reload()
    }
}

private struct DocumentRow: View {
    let doc: KnowledgeItem
    let chunks: Int
    let onDelete: () -> Void
    let onTag: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.tint)
                .opacity(doc.kind == .quarantined ? 0.4 : 1.0)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(doc.title)
                    .lineLimit(1)
                    .foregroundStyle(doc.kind == .quarantined ? Color.secondary : Color.primary)
                Text("\(kindLabel) · \(chunks) chunk\(chunks == 1 ? "" : "s")")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(action: onTag) {
                Image(systemName: doc.kind == .quarantined ? "lock.open" : "lock")
            }
            .buttonStyle(.borderless)
            .help(doc.kind == .quarantined ? "Restore to documents" : "Quarantine (hidden from AI)")
            .accessibilityLabel(
                doc.kind == .quarantined
                    ? "Restore \(doc.title)"
                    : "Quarantine \(doc.title)"
            )
            Button(role: .destructive, action: onDelete) {
                Image(systemName: "trash")
            }
            .buttonStyle(.borderless)
            .help("Delete this document")
            .accessibilityLabel("Delete \(doc.title)")
        }
        .padding(.vertical, 4)
    }

    private var kindLabel: String {
        switch doc.kind {
        case .quarantined: "internal"
        default: doc.kind.rawValue
        }
    }

    private var icon: String {
        switch doc.kind {
        case .call: "phone"
        case .note: "note.text"
        case .memory: "brain"
        case .quarantined: "lock.doc"
        default: "doc.text"
        }
    }
}
