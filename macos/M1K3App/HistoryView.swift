//
//  HistoryView.swift
//  M1K3App
//
//  The chat history drawer: browse conversations, switch, start fresh, delete.
//  Dumb glue over ChatSession's conversation surface (M1K3Chat, test-pinned) —
//  matches the CallsView drawer shape. Reading `historyRevision` is what makes
//  the list re-render after saves/titles/deletes (the callCount trick).
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 (thin view over
//  tested session logic; feel-pass at ⌘R). Prior: Unknown.
//

import M1K3Chat
import SwiftUI

struct HistoryView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            header
            content
        }
        .frame(width: 480, height: 540)
        .glassBackdrop()
    }

    private var header: some View {
        HStack {
            Label("History", systemImage: "clock.arrow.circlepath")
                .symbolRenderingMode(.hierarchical)
                .font(.headline)
            Text("\(summaries.count)")
                .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            Spacer()
            Button {
                env.chat.startNewConversation()
                dismiss()
            } label: {
                Label("New chat", systemImage: "square.and.pencil")
            }
            .disabled(env.chat.messages.isEmpty || env.chat.isResponding)
            Button("Done") { dismiss() }
                .buttonStyle(.glassProminent)
        }
        .padding(16)
    }

    @ViewBuilder
    private var content: some View {
        let conversations = summaries
        if conversations.isEmpty {
            ContentUnavailableView {
                Label("No conversations yet", systemImage: "clock.arrow.circlepath")
            } description: {
                Text("Every chat is saved here automatically. Start talking and "
                    + "M1K3 will name the conversation for you.")
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            List {
                ForEach(conversations) { summary in
                    row(for: summary)
                }
            }
            .scrollContentBackground(.hidden)
        }
    }

    private func row(for summary: ConversationSummary) -> some View {
        Button {
            env.chat.switchTo(summary.id)
            dismiss()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(summary.title ?? "New chat")
                        .font(.body)
                        .lineLimit(1)
                    Text(summary.updatedAt, format: .relative(presentation: .named))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if summary.id == env.chat.activeConversationID {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.tint)
                        .accessibilityLabel("Current conversation")
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(env.chat.isResponding)
        .swipeActions {
            Button("Delete", role: .destructive) { env.chat.deleteConversation(summary.id) }
        }
        .contextMenu {
            Button("Delete", role: .destructive) { env.chat.deleteConversation(summary.id) }
        }
    }

    /// historyRevision is the observable invalidator — reading it here makes
    /// SwiftUI re-call conversationSummaries() after every save/title/delete.
    private var summaries: [ConversationSummary] {
        _ = env.chat.historyRevision
        return env.chat.conversationSummaries()
    }
}
