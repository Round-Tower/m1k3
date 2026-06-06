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

import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @Environment(AppEnvironment.self) private var env

    @State private var draft = ""
    @State private var showSettings = false
    @State private var showDocuments = false
    @State private var showCalls = false
    @State private var showImporter = false
    @State private var showConsentDialog = false
    @State private var isDropTargeted = false

    var body: some View {
        VStack(spacing: 0) {
            transcript
            inputBar
        }
        .frame(minWidth: 600, minHeight: 520)
        .background(BackdropView())
        .dropDestination(for: URL.self) { urls, _ in
            for url in urls {
                Task { await env.ingest(url: url) }
            }
            return true
        } isTargeted: { isDropTargeted = $0 }
        .overlay { if isDropTargeted { DropHintView() } }
        .toolbar { toolbarContent }
        .sheet(isPresented: $showSettings) {
            SettingsView().environment(env)
        }
        .sheet(isPresented: $showDocuments) {
            DocumentsView().environment(env)
        }
        .sheet(isPresented: $showCalls) {
            CallsView().environment(env)
        }
        .confirmationDialog("Record this call?", isPresented: $showConsentDialog, titleVisibility: .visible) {
            Button("Record once") { env.affirmConsentAndRecord(scope: .once) }
            Button("Always allow") { env.affirmConsentAndRecord(scope: .remembered) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You’re responsible for having consent from everyone on the call. Recording is on-device only — audio never leaves this Mac.")
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
                        ForEach(env.chat.messages) { message in
                            MessageView(message: message) { text in
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
                        .font(.system(size: 16, weight: .semibold))
                        .frame(width: 22, height: 22)
                }
                .buttonStyle(.glass)
                .tint(env.isListening ? .red : nil)
                .disabled(!env.canDictate && !env.isListening)
                .help(env.canDictate ? "Voice input — tap to speak, tap to send" : "Microphone unavailable")

                Button(action: send) {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(width: 22, height: 22)
                }
                .buttonStyle(.glassProminent)
                .disabled(!canSend)
                .keyboardShortcut(.return, modifiers: [])
            }
            .padding(16)
        }
    }

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !env.chat.isResponding
    }

    private func send() {
        guard canSend else { return }
        let text = draft
        draft = ""
        Task { await env.chat.send(text) }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            HStack(spacing: 6) {
                if env.isRecording {
                    Circle().fill(.red).frame(width: 8, height: 8)
                    Text("Recording").font(.caption).foregroundStyle(.red)
                } else if env.modelLoad.isActive {
                    ProgressView().controlSize(.small)
                    Text(env.modelLoad.label(modelName: "Gemma 3"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    Circle()
                        .fill(env.providerAvailable ? .green : .orange)
                        .frame(width: 8, height: 8)
                    Text(env.selectedRuntime.rawValue)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        ToolbarItemGroup(placement: .primaryAction) {
            Button { env.chat.clear() } label: {
                Label("New chat", systemImage: "square.and.pencil")
            }
            .disabled(env.chat.messages.isEmpty || env.chat.isResponding)
            Button { showImporter = true } label: {
                Label("Import", systemImage: "doc.badge.plus")
            }
            Button { showDocuments = true } label: {
                Label("Documents", systemImage: "books.vertical")
            }
            Button { showCalls = true } label: {
                Label("Calls", systemImage: "phone.bubble")
            }
            Button {
                if env.isRecording { env.stopRecording() }
                else if env.recordingPreAuthorised { env.startRecording() }
                else { showConsentDialog = true }
            } label: {
                Label(env.isRecording ? "Stop recording" : "Record call",
                      systemImage: env.isRecording ? "stop.circle.fill" : "record.circle")
            }
            .tint(env.isRecording ? .red : nil)
            Button { showSettings = true } label: {
                Label("Settings", systemImage: "gearshape")
            }
        }
    }
}

// MARK: - Supporting views

/// Soft gradient backdrop so the glass has something to refract.
private struct BackdropView: View {
    var body: some View {
        LinearGradient(
            colors: [Color(red: 0.06, green: 0.07, blue: 0.12), Color(red: 0.10, green: 0.08, blue: 0.16)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
}

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
