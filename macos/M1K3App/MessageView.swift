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
    /// Whether to draw the avatar — true only on the first assistant turn of a
    /// run, so the mark appears once per reply, not on every consecutive line.
    var showsAvatar = true
    /// Called with the message text when the user taps speak.
    let onSpeak: (String) -> Void

    @State private var showSources = false

    var body: some View {
        switch message.role {
        case .user:
            HStack {
                Spacer(minLength: 60)
                bubble
                    .glassEffect(.regular.tint(.accentColor.opacity(0.2)), in: .rect(cornerRadius: 18))
            }
        case .assistant:
            HStack(alignment: .top, spacing: 10) {
                if showsAvatar {
                    avatar
                } else {
                    // Reserve the avatar's width so stacked replies stay aligned.
                    Color.clear.frame(width: 30, height: 1)
                }
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

    /// M1K3's mark: the brain glyph seated in a glass disc so it reads as an
    /// avatar with presence, not a thin tinted icon floating beside the bubble.
    private var avatar: some View {
        Image(systemName: "brain")
            .symbolRenderingMode(.hierarchical)
            .foregroundStyle(.tint)
            .font(.system(size: 16, weight: .semibold))
            .frame(width: 30, height: 30)
            .glassEffect(.regular, in: .circle)
            .accessibilityHidden(true)
    }

    private var bubble: some View {
        ReadingText(message.text)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
    }

    private var assistantBody: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !message.text.isEmpty {
                ReadingText(message.text)
            } else if case .streaming = message.status {
                // While the agent works (thinking, searching the web…) show what
                // it's doing — the label doubles as the privacy surface for any
                // query that leaves the device.
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    if let activity = message.activityLabel {
                        Text(activity)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .contentTransition(.opacity)
                    }
                }
            } else if case .complete = message.status {
                // A completed-but-empty reply (the model returned nothing) reads
                // as a state, not a hollow glass bubble.
                Text("No response.")
                    .italic()
                    .foregroundStyle(.secondary)
            }

            if case let .failed(reason) = message.status {
                Label(reason, systemImage: "exclamationmark.triangle")
                    .symbolRenderingMode(.hierarchical)
                    .font(.caption)
                    .foregroundStyle(.orange)
            }

            if case .complete = message.status, !message.text.isEmpty {
                Button {
                    onSpeak(message.text)
                } label: {
                    Image(systemName: "speaker.wave.2")
                        .symbolRenderingMode(.hierarchical)
                        .font(.caption)
                }
                .buttonStyle(.glass)
                .accessibilityLabel("Speak this answer")
                .help("Speak this answer")
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
                .font(.caption.weight(.medium).monospacedDigit())
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
