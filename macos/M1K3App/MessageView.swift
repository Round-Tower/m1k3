//
//  MessageView.swift
//  M1K3App
//
//  One turn in the transcript. User turns are tinted glass on the trailing edge;
//  assistant turns lead with the answer, a speak button, and a disclosure of the
//  grounding sources (the [ChunkHit]s ChatSession attached) so provenance is one
//  tap away — honest RAG, the M1K3 way.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import M1K3Chat
import M1K3Knowledge
import SwiftUI

struct MessageView: View {
    let message: ChatMessage
    /// Called with the message text when the user taps speak.
    let onSpeak: (String) -> Void

    @State private var showSources = false

    var body: some View {
        switch message.role {
        case .user:
            HStack {
                Spacer(minLength: 60)
                bubble
                    .glassEffect(.regular.tint(.accentColor.opacity(0.35)), in: .rect(cornerRadius: 18))
            }
        case .assistant:
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "brain")
                    .foregroundStyle(.tint)
                    .font(.title3)
                    .padding(.top, 2)
                VStack(alignment: .leading, spacing: 8) {
                    assistantBody
                    if !message.sources.isEmpty {
                        sourcesDisclosure
                    }
                }
                Spacer(minLength: 40)
            }
        }
    }

    private var bubble: some View {
        Text(message.text)
            .textSelection(.enabled)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
    }

    private var assistantBody: some View {
        VStack(alignment: .leading, spacing: 8) {
            if message.text.isEmpty, case .streaming = message.status {
                ProgressView().controlSize(.small)
            } else {
                Text(message.text)
                    .textSelection(.enabled)
            }

            if case let .failed(reason) = message.status {
                Label(reason, systemImage: "exclamationmark.triangle")
                    .font(.caption)
                    .foregroundStyle(.orange)
            }

            if case .complete = message.status, !message.text.isEmpty {
                Button {
                    onSpeak(message.text)
                } label: {
                    Label("Speak", systemImage: "speaker.wave.2")
                        .font(.caption)
                }
                .buttonStyle(.glass)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
    }

    private var sourcesDisclosure: some View {
        DisclosureGroup(isExpanded: $showSources) {
            VStack(alignment: .leading, spacing: 6) {
                ForEach(message.sources) { hit in
                    SourceRow(hit: hit)
                }
            }
            .padding(.top, 4)
        } label: {
            Label("\(message.sources.count) source\(message.sources.count == 1 ? "" : "s")",
                  systemImage: "doc.text.magnifyingglass")
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 6)
    }
}

private struct SourceRow: View {
    let hit: ChunkHit

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption.weight(.semibold))
            Text(hit.content)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .glassEffect(.regular, in: .rect(cornerRadius: 10))
    }

    private var label: String {
        if let heading = hit.heading, !heading.isEmpty {
            "\(hit.itemTitle) · \(heading)"
        } else {
            hit.itemTitle
        }
    }
}
