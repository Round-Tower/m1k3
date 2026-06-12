//
//  ContentView.swift
//  M1K3App
//
//  The chat surface: a streaming transcript grounded in the user's own knowledge,
//  a Liquid Glass input bar, drop-to-ingest, and a settings sheet for the runtime
//  picker. The view is intentionally dumb — it reads `env.chat.messages` and calls
//  package methods; all the stateful folding lives in the tested ChatSession.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-11 — voice-first mode: full-window body
//  swap to VoiceModeView, toolbar entry + ⌘⇧V, chat actions hidden while active.
//  Confidence 0.8.

import M1K3Avatar
import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var draft = ""
    @State private var showDocuments = false
    @State private var showMemories = false
    @State private var showCalls = false
    @State private var showHistory = false
    @State private var showImporter = false
    @State private var showConsentDialog = false
    @State private var isDropTargeted = false
    @AppStorage("showAvatar") private var showAvatar = true

    var body: some View {
        Group {
            if env.isVoiceModeActive {
                VoiceModeView()
                    .transition(.opacity.combined(with: .scale(scale: 0.97)))
            } else {
                VStack(spacing: 0) {
                    avatarPanel
                    transcript
                    inputBar
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: env.isVoiceModeActive)
        .frame(minWidth: 600, minHeight: 520)
        .background {
            // Ambient drifting orbs while capturing audio (recording / dictation)
            // or throughout voice-first mode, fading in over the glass. Sits
            // above the window glass, behind content.
            if showsAmbientBackdrop {
                AudioCaptureBackdrop().transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.45), value: showsAmbientBackdrop)
        .glassBackdrop()
        .dropDestination(for: URL.self) { urls, _ in
            for url in urls {
                Task { await env.ingest(url: url) }
            }
            return true
        } isTargeted: { isDropTargeted = $0 }
        .overlay { if isDropTargeted { DropHintView() } }
        .toolbar { toolbarContent }
        .sheet(isPresented: $showDocuments) {
            DocumentsView().environment(env)
        }
        .sheet(isPresented: $showMemories) {
            MemoriesView().environment(env)
        }
        .sheet(isPresented: $showCalls) {
            CallsView().environment(env)
        }
        .sheet(isPresented: $showHistory) {
            HistoryView().environment(env)
        }
        .confirmationDialog("Record this call?", isPresented: $showConsentDialog, titleVisibility: .visible) {
            Button("Record once") { Task { await env.affirmConsentAndRecord(scope: .once) } }
            Button("Always allow") { Task { await env.affirmConsentAndRecord(scope: .remembered) } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You’re responsible for having consent from everyone on the call. "
                + "Recording is on-device only — audio never leaves this Mac.")
        }
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.pdf, .plainText, .text, .rtf],
            allowsMultipleSelection: true
        ) { result in
            if case let .success(urls) = result {
                for url in urls {
                    Task { await env.ingest(url: url) }
                }
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            if let status = env.lastIngestStatus {
                IngestBanner(text: status, busy: env.isIngesting)
            }
        }
    }

    // MARK: - Avatar panel

    @ViewBuilder
    private var avatarPanel: some View {
        if showAvatar {
            AvatarView(controller: env.avatar)
                .frame(height: 200)
                .padding(.horizontal, 12)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
        }
    }

    // MARK: - Transcript

    @ViewBuilder
    private var transcript: some View {
        if env.chat.messages.isEmpty {
            EmptyChatView(itemCount: env.indexedItemCount)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(Array(env.chat.messages.enumerated()), id: \.element.id) { index, message in
                            let previous = index > 0 ? env.chat.messages[index - 1] : nil
                            MessageView(
                                message: message,
                                showsAvatar: previous?.role != .assistant
                            ) { text in
                                Task { await env.speak(text) }
                            }
                            .id(message.id)
                        }
                    }
                    .padding(20)
                }
                .onChange(of: env.chat.messages.last?.text) {
                    if let last = env.chat.messages.last?.id {
                        withAnimation(.easeOut(duration: 0.15)) {
                            proxy.scrollTo(last, anchor: .bottom)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Input

    private var inputBar: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                if env.isListening {
                    Text(env.liveTranscript.isEmpty ? "Listening…" : env.liveTranscript)
                        .foregroundStyle(env.liveTranscript.isEmpty ? .secondary : .primary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .lineLimit(1 ... 5)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .glassEffect(.regular, in: .rect(cornerRadius: 22))
                } else {
                    TextField("Ask M1K3 about your documents…", text: $draft, axis: .vertical)
                        .textFieldStyle(.plain)
                        .lineLimit(1 ... 5)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .glassEffect(.regular, in: .rect(cornerRadius: 22))
                        .onSubmit(send)
                }

                Button { env.toggleDictation() } label: {
                    Image(systemName: env.isListening ? "mic.fill" : "mic")
                        .imageScale(.large)
                        .fontWeight(.semibold)
                        .frame(width: 22, height: 22)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .tint(env.isListening ? .red : nil)
                .disabled(!env.canDictate && !env.isListening)
                .help(env.canDictate ? "Voice input — tap to speak, tap to send" : "Microphone unavailable")
                .accessibilityLabel("Voice input")
                .accessibilityValue(env.isListening ? "Listening" : "Off")
                .accessibilityHint("Dictate a message")

                Button(action: send) {
                    Image(systemName: "arrow.up")
                        .imageScale(.large)
                        .fontWeight(.semibold)
                        .frame(width: 22, height: 22)
                }
                .buttonStyle(.glassProminent)
                .buttonBorderShape(.circle)
                .disabled(!canSend)
                .keyboardShortcut(.return, modifiers: [])
                .accessibilityLabel("Send")
            }
            .padding(16)
        }
    }

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !env.chat.isResponding
    }

    /// Cue for the ambient animated backdrop: audio capture (dictation / call
    /// recording) or the WHOLE of voice-first mode. The mode is one continuous
    /// audio conversation — gating per phase would fade the orbs out every time
    /// M1K3 starts speaking, which reads as the app going dead mid-sentence.
    private var showsAmbientBackdrop: Bool {
        env.isListening || env.isRecording || env.isVoiceModeActive
    }

    /// One spoken label for the toolbar status pill — the colour-coded dots carry
    /// no meaning to VoiceOver on their own.
    private var statusAccessibilityLabel: String {
        if env.isRecording { return "Recording in progress" }
        if env.modelLoad.isActive { return env.modelLoad.label(modelName: env.downloadingBrainName) }
        return "Model unavailable, runtime \(env.selectedRuntime.rawValue)"
    }

    /// Status earns its place only when it deviates from "ready and quiet":
    /// recording, a model download in flight, or an unavailable backend. When all
    /// is well it renders nothing — the runtime lives in Settings, not the chrome.
    @ViewBuilder
    private var statusIndicator: some View {
        if env.isRecording || env.modelLoad.isActive || !env.providerAvailable {
            statusContent
                .padding(.horizontal, 12)
                .padding(.vertical, 3)
                .accessibilityElement(children: .combine)
                .accessibilityLabel(statusAccessibilityLabel)
        }
    }

    /// The status pill's content; the wrapper above gives it breathing room inside
    /// the toolbar's glass capsule so text/spinner aren't jammed against the edges.
    @ViewBuilder
    private var statusContent: some View {
        if env.isRecording {
            Label("Recording", systemImage: "record.circle.fill")
                .symbolRenderingMode(.hierarchical)
                .font(.caption)
                .foregroundStyle(.red)
        } else if env.modelLoad.isActive {
            HStack(spacing: 6) {
                ProgressView().controlSize(.small)
                Text(env.modelLoad.label(modelName: env.downloadingBrainName))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
        } else if !env.providerAvailable {
            Label("Model unavailable", systemImage: "exclamationmark.triangle")
                .symbolRenderingMode(.hierarchical)
                .font(.caption)
                .foregroundStyle(.orange)
        }
    }

    private func send() {
        guard canSend else { return }
        let text = draft
        draft = ""
        Task { await env.send(text) }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            statusIndicator
        }
        ToolbarItemGroup(placement: .primaryAction) {
            Button {
                if env.isVoiceModeActive {
                    env.exitVoiceMode()
                } else {
                    env.enterVoiceMode()
                }
            } label: {
                Label(
                    env.isVoiceModeActive ? "Leave voice mode" : "Voice mode",
                    systemImage: env.isVoiceModeActive ? "person.wave.2.fill" : "person.wave.2"
                )
            }
            .keyboardShortcut("v", modifiers: [.command, .shift])
            .disabled(!env.isVoiceModeActive && (!env.canDictate || env.chat.isResponding || env.isListening))
            .help(env.isVoiceModeActive
                ? "Back to the chat (⌘⇧V)"
                : "Talk with M1K3 — hands-free conversation (⌘⇧V)")
            if !env.isVoiceModeActive {
                chatToolbarItems
            }
        }
    }

    /// The chat-surface actions — hidden while voice mode owns the window.
    private var chatToolbarItems: some View {
        Group {
            Button { env.chat.startNewConversation() } label: {
                Label("New chat", systemImage: "square.and.pencil")
            }
            .help("Start a fresh conversation — this one stays in History")
            .disabled(env.chat.messages.isEmpty || env.chat.isResponding)
            Button { showHistory = true } label: {
                Label("History", systemImage: "clock.arrow.circlepath")
            }
            .help("Browse and switch between past conversations")
            Button {
                withAnimation(.spring(duration: 0.35)) { showAvatar.toggle() }
            } label: {
                Label(
                    showAvatar ? "Hide Sparrow" : "Show Sparrow",
                    systemImage: showAvatar ? "bird.fill" : "bird"
                )
            }
            .help(showAvatar ? "Hide the avatar panel" : "Show the avatar panel")
            Button { showImporter = true } label: {
                Label("Import", systemImage: "doc.badge.plus")
            }
            Button { showDocuments = true } label: {
                Label("Documents", systemImage: "books.vertical")
            }
            Button { showMemories = true } label: {
                Label("Memories", systemImage: "brain")
            }
            Button { showCalls = true } label: {
                Label("Calls", systemImage: "phone.bubble")
            }
            Button {
                if env.isRecording {
                    Task { await env.stopRecording() }
                } else if env.recordingPreAuthorised {
                    Task { await env.startRecording() }
                } else {
                    showConsentDialog = true
                }
            } label: {
                Label(env.isRecording ? "Stop recording" : "Record call",
                      systemImage: env.isRecording ? "stop.circle.fill" : "record.circle")
            }
            .tint(env.isRecording ? .red : nil)
            SettingsLink {
                Label("Settings", systemImage: "gearshape")
            }
        }
    }
}

// MARK: - Supporting views

private struct EmptyChatView: View {
    let itemCount: Int

    var body: some View {
        ContentUnavailableView {
            Label("M1K3", systemImage: "brain")
        } description: {
            Text(itemCount == 0
                ? "Drop a PDF or text file to give M1K3 something to remember, then ask away."
                : "\(itemCount) item\(itemCount == 1 ? "" : "s") in memory. Ask a question to begin.")
        }
    }
}

private struct DropHintView: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 20)
            .strokeBorder(.tint, style: StrokeStyle(lineWidth: 2, dash: [8]))
            .overlay {
                Label("Drop to ingest", systemImage: "tray.and.arrow.down")
                    .font(.title3.weight(.semibold))
                    .padding(20)
                    .glassEffect(.regular, in: .rect(cornerRadius: 16))
            }
            .padding(24)
            .allowsHitTesting(false)
    }
}

private struct IngestBanner: View {
    let text: String
    let busy: Bool

    var body: some View {
        HStack(spacing: 8) {
            if busy {
                ProgressView().controlSize(.small)
            } else {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
            }
            Text(text).font(.callout).lineLimit(2)
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .glassEffect(.regular, in: .rect(cornerRadius: 12))
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}
