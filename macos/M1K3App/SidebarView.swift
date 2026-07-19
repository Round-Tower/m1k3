//
//  SidebarView.swift
//  M1K3App
//
//  The leading NavigationSplitView column: Chat, a Workspace section
//  (Documents/Memories/Calls — echoes the iOS shell's TabSection("Workspace")
//  grouping), and a Conversations section that IS the old HistoryView's
//  content, migrated verbatim (title/relative-date rows, active checkmark,
//  swipe/context-menu delete, the historyRevision re-render trick). History
//  as a toolbar-button-behind-a-sheet made sense when the sidebar didn't
//  exist; once navigation is always visible, "browse past conversations" is
//  just what a sidebar's own row list already is on this platform (Mail's
//  mailbox list, Notes' folder list) — a 5th parallel destination would have
//  been redundant with it.
//
//  No .glassBackdrop() here — .listStyle(.sidebar) already paints macOS's own
//  vibrant sidebar material; stacking our own NSVisualEffectView underneath
//  would double-blur and seam at the divider for no visual gain.
//
//  Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.85 (thin view over
//  tested session logic — ChatSession+Conversations.swift's API is unchanged;
//  layout/feel verify-at-⌘R, incl. the .hiddenTitleBar top-edge gotcha the
//  trailing .inspector already hit once). Prior: Unknown
//

import M1K3Chat
import SwiftUI

struct SidebarView: View {
    @Environment(AppEnvironment.self) private var env
    @Binding var selection: SidebarSelection?

    var body: some View {
        List(selection: $selection) {
            Label("Chat", systemImage: "bubble.left.and.bubble.right")
                .tag(SidebarSelection.chat)

            Section("Workspace") {
                Label("Documents", systemImage: "books.vertical")
                    .tag(SidebarSelection.documents)
                Label("Memories", systemImage: "brain")
                    .tag(SidebarSelection.memories)
                Label("Calls", systemImage: "phone.bubble")
                    .tag(SidebarSelection.calls)
            }

            Section("Conversations") {
                if summaries.isEmpty {
                    Text("No conversations yet")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(summaries) { summary in
                        conversationRow(for: summary)
                    }
                }
            }
        }
        .listStyle(.sidebar)
        // NOTE: deliberately NOT .ignoresSafeArea(.container, edges: .top)
        // here, unlike the trailing .inspector — tried it (2026-07-19), and
        // unlike the inspector's bespoke ReviewPanel, this List's own first
        // ROW slid under the traffic lights (a NavigationSplitView sidebar's
        // vibrancy already extends to the window top on its own; the
        // inspector's fix doesn't transfer — that column has no built-in
        // material). Leaving safe-area respected keeps "Chat" clear of the
        // lights; the .sidebar list style already reads correctly to the edge.
        .toolbar {
            ToolbarItem(placement: .navigation) {
                Button {
                    env.chat.startNewConversation()
                    selection = .chat
                } label: {
                    Label("New chat", systemImage: "square.and.pencil")
                }
                .disabled(env.chat.messages.isEmpty || env.chat.isResponding)
                .help("Start a fresh conversation — this one stays in Conversations")
            }
        }
    }

    private func conversationRow(for summary: ConversationSummary) -> some View {
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
        .tag(SidebarSelection.conversation(summary.id))
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
