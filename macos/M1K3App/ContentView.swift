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
//  Review: Kev + claude-opus-4-8, 2026-06-21 — voice-first MERGED into the chat:
//  the full-window body-swap is replaced by a bottom VoiceDock overlay
//  (safeAreaInset) over the still-visible transcript. The top avatar yields to the
//  dock's face, the input bar hides under voice, follow-latest pauses (scroll-back),
//  and chat actions stay reachable. Confidence 0.75 (layout/feel verify-at-⌘R).
//  Review: Kev + claude-opus-4-8, 2026-06-26 — the 06-21 dock was a regression in
//  use (avatar shrank to a 92pt corner card). Reverted to a FULL-WINDOW voice hero
//  (VoiceModeView) mounted as a full-window overlay: the avatar fills the window,
//  karaoke + controls float on glass. Confidence 0.75 (look verify-at-⌘R).

import M1K3Avatar
import M1K3Inference
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
    @AppStorage(AppEnvironment.avatarDisplayKey) private var avatarDisplay = AvatarDisplay.panel
    /// Auto-route on → M1K3 picks the brain → a manual toolbar pick would snap back
    /// next turn, so the switcher disables itself. Reactive so toggling Auto-route in
    /// Settings updates the chat toolbar live.
    @AppStorage(AppEnvironment.autoRouteBrainKey) private var autoRouteBrain = false

    var body: some View {
        VStack(spacing: 0) {
            avatarPanel
            transcript
            // The input bar hides while voice owns the conversation — the dock
            // below IS the bottom chrome then, so there's no typed-vs-spoken clash.
            if !env.isVoiceModeActive {
                inputBar
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.35), value: avatarDisplay)
        .frame(minWidth: 600, minHeight: 520)
        // Voice-first as a FULL-WINDOW hero: the avatar fills the window with the
        // karaoke line + controls floating on glass, covering the chat while
        // active (the chat answer still lands in the transcript underneath). This
        // replaced the 06-21 bottom dock — Kev wanted the face full screen, not a
        // 92pt corner card. Mounted as an overlay (not a body-swap) so the toolbar
        // chrome stays reachable and the transition is a clean fade.
        .overlay {
            if env.isVoiceModeActive {
                VoiceModeView()
                    // Grow open from where the panel sat (top-anchored) instead of a
                    // flat cross-fade — the felt "hero zoom" without matchedGeometry's
                    // empty-frame trap: the hero is a FRESH RealityView that rebuilds
                    // its scene, so a matched-frame morph would zoom an empty rect and
                    // pop the face in after. A plain scale+opacity spring is robust
                    // (no namespace, no two-scenes), and the opacity masks the scene
                    // build during the grow. Collapses back on exit.
                    .transition(.scale(scale: 0.88, anchor: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.42, dampingFraction: 0.82), value: env.isVoiceModeActive)
        // Global readiness gate: until the active brain is actually loaded into
        // memory, swallow interaction behind a loading/failure surface — a turn
        // fired against still-downloading weights is the "interacted before ready"
        // latent bug. The toolbar (incl. Settings) stays reachable as chrome.
        .overlay {
            if !env.isReady {
                ModelGateView(
                    readiness: env.readiness,
                    brainName: env.downloadingBrainName
                ) { Task { await env.warmUpSelectedBrainOnLaunch() } }
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: env.isReady)
        // Warm the restored brain on launch so readiness can reach .ready without
        // the user firing the first turn (init's direct assignment skips the
        // didSet that would otherwise kick the load). Idempotent.
        .task { await env.warmUpSelectedBrainOnLaunch() }
        .background {
            // Ambient drifting orbs while capturing audio (recording / dictation)
            // or throughout voice-first mode, fading in over the glass. Sits
            // above the window glass, behind content.
            if showsAmbientBackdrop {
                AudioCaptureBackdrop().transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.45), value: showsAmbientBackdrop)
        .background {
            // Opt-in: the avatar as a full-window backdrop behind the glass
            // bubbles. Standard chat mode only — voice mode has its own hero. It
            // recedes reactively while you read/type so text stays legible.
            if avatarDisplay == .background, !env.isVoiceModeActive {
                AvatarChatBackground(env: env, isTyping: !draft.isEmpty)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.35), value: avatarDisplay)
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
        // A trailing side panel for quick review of links and files, beside the
        // conversation. Native macOS inspector — resizable, collapsible chrome.
        // State lives in env.review so chat-chips / MCP / the agent can drive it.
        .inspector(isPresented: Binding(
            get: { env.review.isPresented },
            set: { env.review.isPresented = $0 }
        )) {
            ReviewPanel(review: env.review)
                .inspectorColumnWidth(min: 320, ideal: 420, max: 720)
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
        // The top panel yields to the voice dock's own avatar while voice is active
        // — one face on screen, never two.
        if avatarDisplay == .panel, !env.isVoiceModeActive {
            AvatarSurface(env: env)
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
                        ForEach(env.chat.messages, id: \.id) { message in
                            MessageView(
                                message: message,
                                onSpeak: { text in Task { await env.speak(text) } },
                                onOpenLink: { url in env.review.open(url: url) }
                            )
                            .id(message.id)
                        }
                    }
                    .padding(20)
                }
                .onChange(of: env.chat.messages.last?.text) { followLatest(proxy) }
                // Follow the live reasoning too — during the think phase `text` is
                // empty, so without this the auto-expanded reasoning grows off the
                // bottom edge. Throttled upstream (~20Hz), so the follow eases.
                .onChange(of: env.chat.messages.last?.reasoning) { followLatest(proxy) }
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

                Button { env.chat.startNewConversation() } label: {
                    Image(systemName: "square.and.pencil")
                        .imageScale(.large)
                        .fontWeight(.semibold)
                        .frame(width: 22, height: 22)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .disabled(env.chat.messages.isEmpty || env.chat.isResponding)
                .help("Start a fresh conversation — this one stays in History")
                .accessibilityLabel("New chat")

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
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !env.chat.isResponding
            && env.isReady
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

    /// Keep the newest turn pinned to the bottom as it streams (text or reasoning).
    /// Paused while voice is active so the transcript becomes the calm scroll-back
    /// record — the dock's karaoke is the live read — and the user can read earlier
    /// turns without being yanked to the bottom mid-utterance.
    private func followLatest(_ proxy: ScrollViewProxy) {
        guard !env.isVoiceModeActive, let last = env.chat.messages.last?.id else { return }
        withAnimation(.easeOut(duration: 0.15)) {
            proxy.scrollTo(last, anchor: .bottom)
        }
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
            .disabled(!env.isVoiceModeActive
                && (!env.canDictate || env.chat.isResponding || env.isListening || !env.isReady))
            .help(env.isVoiceModeActive
                ? "Back to the chat (⌘⇧V)"
                : "Talk with M1K3 — hands-free conversation (⌘⇧V)")
            // The chat is co-visible under the voice dock now, so its actions stay
            // reachable (they used to hide for the full-window body-swap).
            chatToolbarItems
        }
    }

    /// Brain hot-swap + the "currently using X" indicator. This intentionally
    /// REVERSES the older "runtime lives in Settings, not the chrome" choice
    /// (`statusIndicator` renders nothing when ready): the brain you're on is now
    /// visible at a glance and one tap to change — Kev's "easy optics on which
    /// we're using". The label reads the ONE live source (`env.selectedBrain`)
    /// composed with `env.modelLoad` so it never claims a brain that's still
    /// downloading. A switch to a downloaded tier (or Mini) is instant via
    /// `selectBrain` (transcript preserved); an un-downloaded tier routes to the
    /// onboarding brain step (the honest download UI) rather than silently pulling
    /// gigabytes from a single tap.
    /// Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.8, Prior: Unknown
    private var brainSwitcher: some View {
        Menu {
            ForEach(BrainSwitcher.rows(
                active: env.selectedBrain,
                isDownloaded: { env.isBrainDownloaded($0) },
                isLocked: { !$0.isSelectableOnThisMac }
            )) { row in
                Button {
                    if row.needsDownload {
                        // Don't start a multi-GB pull from one toolbar tap — route to
                        // the onboarding brain step (the honest download UI). Same
                        // entry point Settings "Change brain…" uses.
                        env.routeToOnboardingBrainPicker()
                    } else {
                        env.selectBrain(row.tier)
                    }
                } label: {
                    Label(row.menuTitle, systemImage: row.isActive ? "checkmark" : row.tier.glyph)
                }
                .disabled(row.isLocked)
            }
        } label: {
            // The Menu label IS the persistent indicator. During a download it shows
            // "Big M1K3 · 34%" — deliberately the same figure the transient principal
            // statusIndicator shows; both read env.modelLoad, so they always agree.
            Label(
                BrainSwitcher.indicatorLabel(active: env.selectedBrain, load: env.modelLoad),
                systemImage: env.selectedBrain.glyph
            )
            .symbolRenderingMode(.hierarchical)
        }
        .disabled(autoRouteBrain)
        .help(autoRouteBrain
            ? "Auto-route is on — M1K3 picks the brain. Turn it off in Settings to choose manually."
            : "Switch brain — currently \(env.selectedBrain.displayName)")
    }

    /// The chat-surface actions — always available now the chat stays on screen
    /// beneath the voice dock.
    private var chatToolbarItems: some View {
        Group {
            brainSwitcher

            // New chat lives in the input bar now (next to mic/send) — not here.
            Button { showHistory = true } label: {
                Label("History", systemImage: "clock.arrow.circlepath")
            }
            .help("Browse and switch between past conversations")

            // One control for the avatar: panel / full-window background / off.
            Menu {
                Picker("Avatar", selection: $avatarDisplay) {
                    ForEach(AvatarDisplay.allCases) { mode in
                        Label(mode.label, systemImage: mode.systemImage).tag(mode)
                    }
                }
                .pickerStyle(.inline)
            } label: {
                Label("Avatar", systemImage: avatarDisplay.systemImage)
            }
            .help("How M1K3's avatar appears: panel, full-window background, or off")

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

            Button { env.review.isPresented.toggle() } label: {
                Label("Review panel", systemImage: "sidebar.right")
            }
            .help("Open a side panel to review links and files (⌥⌘R)")
            .keyboardShortcut("r", modifiers: [.command, .option])

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

/// The global readiness gate: shown over the chat surface while the active brain
/// is still loading, failed to load, or can't run on this Mac. Swallows
/// interaction so a turn can't be fired before the model is warm. The window
/// toolbar (Settings) stays reachable as chrome above this overlay.
private struct ModelGateView: View {
    let readiness: AppReadiness
    let brainName: String
    let retry: () -> Void

    var body: some View {
        ZStack {
            Rectangle().fill(.ultraThinMaterial).ignoresSafeArea()
            card
                .padding(28)
                .glassEffect(.regular, in: .rect(cornerRadius: 20))
                .padding(40)
        }
        .contentShape(Rectangle()) // swallow taps to the gated surface beneath
    }

    @ViewBuilder
    private var card: some View {
        switch readiness {
        case let .loading(state):
            VStack(spacing: 16) {
                if let fraction = state.fraction {
                    ProgressView(value: fraction) {
                        Label("Waking M1K3", systemImage: "brain")
                    }
                    .progressViewStyle(.linear)
                    .frame(maxWidth: 280)
                } else {
                    ProgressView().controlSize(.large)
                }
                Text(loadingLabel(state))
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        case let .failed(message):
            VStack(spacing: 14) {
                Label("Couldn’t load \(brainName)", systemImage: "exclamationmark.triangle")
                    .font(.headline)
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                HStack(spacing: 12) {
                    Button("Try again", action: retry)
                        .buttonStyle(.borderedProminent)
                    SettingsLink { Text("Open Settings") }
                }
            }
        case .unavailable:
            VStack(spacing: 14) {
                Label("\(brainName) isn’t available here", systemImage: "questionmark.circle")
                    .font(.headline)
                Text("This Mac can’t run the selected brain. Choose a different one in Settings.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                SettingsLink { Text("Open Settings") }
            }
        case .ready:
            EmptyView() // unreachable: the overlay is only mounted while !env.isReady
        }
    }

    /// Prefer the load state's own label (with %); fall back to a plain line when
    /// it has none (e.g. `.idle`, the instant before warm-up starts).
    private func loadingLabel(_ state: ModelLoadState) -> String {
        let label = state.label(modelName: brainName)
        return label.isEmpty ? "Loading \(brainName)…" : label
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
