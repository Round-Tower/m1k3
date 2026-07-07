//
//  MessageBubble.swift
//  M1K3iOS / M1K3visionOS
//
//  M1K3's asymmetric transcript rows, ported from the Mac house rule: the user's
//  turns are tinted-glass bubbles on the trailing edge; M1K3's turns are FLAT —
//  no card — sitting on the backdrop so the words read cleanly (the same rule the
//  Mac app follows so reading modes work on words, not markup).
//
//  Signed: Kev + claude-fable-5, 2026-07-06, Confidence 0.8. Prior: Unknown.
//

import M1K3Chat
import M1K3Knowledge
import SwiftUI

struct MessageBubble: View {
    let message: ChatMessage

    var body: some View {
        switch message.role {
        case .user:
            HStack {
                Spacer(minLength: 48)
                Text(message.text)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .m1k3Glass(cornerRadius: 18, tint: .accentColor.opacity(0.22))
                    .textSelection(.enabled)
            }
        case .assistant:
            VStack(alignment: .leading, spacing: 8) {
                if let label = message.activityLabel, message.text.isEmpty {
                    activityRow(label)
                }
                if !message.text.isEmpty {
                    Text(message.text)
                        .font(.body)
                        .lineSpacing(4)
                        .foregroundStyle(.primary)
                        .textSelection(.enabled)
                }
                if case let .failed(reason) = message.status {
                    Text(reason)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
                if !message.sources.isEmpty {
                    sourcesRow(message.sources)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func activityRow(_ label: String) -> some View {
        HStack(spacing: 8) {
            ProgressView().controlSize(.small)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func sourcesRow(_ sources: [ChunkHit]) -> some View {
        let titles = Array(Set(sources.map(\.itemTitle))).prefix(4)
        return HStack(spacing: 6) {
            Image(systemName: "text.quote")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(titles.joined(separator: " · "))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding(.top, 2)
    }
}
