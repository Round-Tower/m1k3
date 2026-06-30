//
//  SettingsView.swift
//  M1K3App
//
//  The runtime picker stub. Apple Foundation Models is the only wired backend for
//  the MVP; MLX and LiteRT Gemma are shown as reserved slots so the comparison
//  surface Kev wants is visible from day one, even before those backends land.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import AppKit
import M1K3Avatar
import M1K3Chat
import M1K3Inference
import M1K3Launch
import M1K3Voice
import M1K3WhisperKit
import SwiftUI

struct SettingsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(LaunchAtLogin.self) private var launchAtLogin
    @AppStorage(ReadingMode.storageKey) private var readingMode: ReadingMode = .standard
    @AppStorage(AppEnvironment.webSearchEnabledKey) private var webSearchEnabled = true
    @AppStorage(AppEnvironment.memoryAutoCaptureKey) private var memoryAutoCapture = true
    @AppStorage(AppEnvironment.soundEffectsEnabledKey) private var soundEffectsEnabled = true
    @AppStorage(AppEnvironment.notifyOnLongTurnKey) private var notifyOnLongTurn = false
    @State private var showMemories = false
    @State private var showResetOnboarding = false
    @AppStorage(AppEnvironment.thinkingModeKey) private var thinkingMode = ThinkingMode.auto.rawValue
    @AppStorage(AppEnvironment.voiceCompanionKey) private var voiceCompanion = ""
    @AppStorage(AppEnvironment.avatarDisplayKey) private var avatarDisplay = AvatarDisplay.panel
    @AppStorage(AppEnvironment.companionShadingKey) private var companionShading = CompanionShadingStyle.off.rawValue
    @AppStorage(AppEnvironment.autoRouteBrainKey) private var autoRouteBrain = false
    @AppStorage(AppEnvironment.preferAppleOnDeviceKey) private var preferAppleOnDevice = false
    @AppStorage(AppEnvironment.coolHeadEaseKey) private var coolHeadEase = false
    @AppStorage(StartupPreferences.menuBarOnlyKey) private var menuBarOnly = false
    @AppStorage(MenuBarGlyphStyle.storageKey) private var glyphStyle = MenuBarGlyphStyle.pixelM
    @State private var profileDraft = ""
    @State private var showLicenses = false
    @State private var issueReported = false
    @State private var issueTruncated = false
    @State private var whatHappened = ""

    var body: some View {
        @Bindable var env = env

        // No custom header / Done button: this is a native Settings window now,
        // so the system title bar and ⌘W own the chrome. The Form is the content.
        Group {
            Form {
                Section {
                    LabeledContent {
                        Text(env.selectedBrain.tagline).foregroundStyle(.secondary)
                    } label: {
                        Label(env.selectedBrain.displayName, systemImage: env.selectedBrain.glyph)
                            .symbolRenderingMode(.hierarchical)
                    }
                    modelLoadRow
                    Button("Change brain…") {
                        // Brain-only re-pick: deep-link to the brain step and finish on
                        // wake, instead of replaying the empty "Who am I talking to?"
                        // screen (the old re-trigger bug). One home for the deep-link.
                        env.routeToOnboardingBrainPicker()
                    }
                    .buttonStyle(.glass)
                    Toggle("Auto-route (M1K3 picks the brain)", isOn: $autoRouteBrain)
                        .onChange(of: autoRouteBrain) { _, _ in env.applyAutoRouteIfEnabled() }
                    if autoRouteBrain {
                        Toggle("Prefer Apple on-device", isOn: $preferAppleOnDevice)
                            .onChange(of: preferAppleOnDevice) { _, _ in env.applyAutoRouteIfEnabled() }
                    }
                    Toggle("Ease off when my Mac runs hot", isOn: $coolHeadEase)
                } header: {
                    Text("Brain")
                } footer: {
                    // A multiline literal (no `+` chain) keeps this copy out of the
                    // ViewBuilder's overload resolution — the old 7-segment `String`
                    // concatenation tripped "unable to type-check in reasonable time",
                    // which then cascaded into phantom "cannot find … in scope" errors
                    // elsewhere in the file. `\` joins wrapped lines; blank-free line
                    // breaks become the paragraph `\n`s.
                    let copy = """
                    Mini is Apple's built-in model (instant). Lil and Big are local models \
                    that download once. On-device only — nothing leaves this Mac.
                    Auto-route lets M1K3 pick: its own tuned model by default (stronger at \
                    chat), or Apple on-device if you prefer it — always sized to what this \
                    Mac runs comfortably.
                    Easing off trims M1K3's effort under thermal or low-power pressure. \
                    It never changes your brain.
                    """
                    Text(copy)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section {
                    LabeledContent("Mode",
                                   value: env.usingMLXEmbeddings ? "MLX Qwen3-Embedding (semantic)" : "Hashing (offline)")
                    if env.isReindexing {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text(env.embeddingStatus ?? "Rebuilding index…")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    } else {
                        let embeddingToggleTitle = env.usingMLXEmbeddings
                            ? "Switch to Hashing (rebuild index)"
                            : "Switch to MLX semantic embeddings (rebuild index)"
                        Button(embeddingToggleTitle) {
                            Task { await env.switchEmbeddings(toMLX: !env.usingMLXEmbeddings) }
                        }
                        .buttonStyle(.glass)
                        if let status = env.embeddingStatus {
                            Text(status).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                } header: {
                    Text("Embeddings")
                } footer: {
                    Text("Semantic MLX embeddings improve retrieval but download a model on "
                        + "first use and re-embed every stored chunk.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    LabeledContent("Active engine", value: env.activeTranscriberName)
                    Picker("Accuracy", selection: Binding(
                        get: { env.selectedWhisperModel },
                        set: { env.selectWhisperModel($0) }
                    )) {
                        ForEach(WhisperModelVariant.allCases) { variant in
                            Text("\(variant.displayName) · \(variant.sizeHint)").tag(variant)
                        }
                    }
                    switch env.whisperLoad {
                    case .idle, .failed:
                        Button("Enable WhisperKit (downloads model)") {
                            Task { await env.enableWhisperKit() }
                        }
                        .buttonStyle(.glass)
                        if case let .failed(msg) = env.whisperLoad {
                            Label(msg, systemImage: "exclamationmark.triangle")
                                .symbolRenderingMode(.hierarchical)
                                .font(.caption)
                                .foregroundStyle(.orange)
                        }
                    case let .downloading(fraction):
                        VStack(alignment: .leading, spacing: 4) {
                            ProgressView(value: fraction)
                            Text(env.whisperLoad.label(modelName: "WhisperKit"))
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                    case .preparing:
                        VStack(alignment: .leading, spacing: 4) {
                            ProgressView() // indeterminate — load has no honest fraction
                            Text(env.whisperLoad.label(modelName: "WhisperKit"))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    case .ready:
                        Label("WhisperKit ready", systemImage: "checkmark.circle.fill")
                            .symbolRenderingMode(.hierarchical)
                            .font(.callout)
                            .foregroundStyle(.green)
                    }
                } header: {
                    Text("Voice input")
                } footer: {
                    Text("Tap the mic in the chat bar to dictate — tap again to send. Apple Speech "
                        + "works out of the box; WhisperKit is higher accuracy after a one-time "
                        + "model download (Small is the default). Changing the accuracy tier "
                        + "applies on the next launch. On-device only.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    LabeledContent("Active voice", value: env.selectedVoiceTier.displayName)
                    voiceOutputControl
                    Button("Hear a sample") { Task { await env.speakSample() } }
                        .buttonStyle(.glass)
                } header: {
                    Text("Voice output")
                } footer: {
                    Text("How M1K3 sounds when it speaks. Built-in is Apple's clear default; "
                        + "M1K3 Voice runs the speech through M1K3's own voice character and "
                        + "downloads the neural voice model for offline use. On-device only.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                companionSection

                startupSection

                Section {
                    Toggle("Sound effects", isOn: $soundEffectsEnabled)
                        .onChange(of: soundEffectsEnabled) { _, on in
                            env.soundEffects.isEnabled = on
                        }
                } header: {
                    Text("Sound effects")
                } footer: {
                    Text("Short earcons for a few moments — an error, a memory saved, "
                        + "voice mode waking up. They never play over M1K3's voice. "
                        + "On-device only.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    Toggle("Notify when a long answer is ready", isOn: $notifyOnLongTurn)
                        .onChange(of: notifyOnLongTurn) { _, on in
                            Task { await env.setLongTurnNotifications(on) }
                        }
                } header: {
                    Text("Notifications")
                } footer: {
                    Text("If you tab away mid-answer, M1K3 pings you when a long reply "
                        + "is ready — only while the app is in the background. The "
                        + "notification never includes the reply itself: on-device, "
                        + "private. Off by default.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    if env.isTranscribingCall {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text("Transcribing call…").font(.caption).foregroundStyle(.secondary)
                        }
                    } else if env.isPreparingBatchTranscription {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text(env.lastCallStatus ?? "Preparing call transcription…")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    } else if env.batchTranscriptionReady {
                        LabeledContent("Transcription", value: "Ready")
                    } else {
                        Button("Enable call transcription (downloads model)") {
                            Task { await env.enableCallTranscription() }
                        }
                        .buttonStyle(.glass)
                    }
                    if let status = env.lastCallStatus, !env.isPreparingBatchTranscription {
                        Text(status).font(.caption).foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Call recording")
                } footer: {
                    Text("Record a call from the chat toolbar (consent-gated). With transcription "
                        + "enabled, a stopped recording is transcribed, summarised, encrypted, and "
                        + "indexed — on-device.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    Toggle("Web search (DuckDuckGo)", isOn: $webSearchEnabled)
                } header: {
                    Text("Tools")
                } footer: {
                    // Multiline literal (not a + chain): the SwiftUI ViewBuilder
                    // type-checker times out on multi-segment String concatenation
                    // (the PR #92 lesson) — backslash continuations keep it one line.
                    Text("""
                    M1K3 can search the web (DuckDuckGo) and read result pages \
                    mid-answer. This is the one capability that sends anything off \
                    this Mac — every search and page read is shown in the reply as \
                    it happens. Date, time and system status tools stay fully local. \
                    Off means the model can't see the web tools at all.
                    """)
                    .font(.caption).foregroundStyle(.secondary)
                }

                mcpSection

                aboutYouSection

                memorySection

                Section {
                    Picker("Reasoning", selection: $thinkingMode) {
                        Text("Auto").tag(ThinkingMode.auto.rawValue)
                        Text("Always think").tag(ThinkingMode.always.rawValue)
                        Text("Fast answers").tag(ThinkingMode.fast.rawValue)
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text("Reasoning")
                } footer: {
                    // Multiline literal, not a + chain (see the web-search footer above).
                    Text("""
                    Reasoning models think out loud before answering — great for \
                    hard questions, slow for small talk. Auto skips the thinking \
                    on casual turns and keeps it for grounded or analytic ones. \
                    Voice mode has its own thinking toggle (the brain button) \
                    and ignores this setting while active.
                    """)
                    .font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    Picker("Reading mode", selection: $readingMode) {
                        ForEach(ReadingMode.allCases) { mode in
                            Text(mode.displayName).tag(mode)
                        }
                    }
                    Text(readingMode.detail)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    // Live preview in the chosen mode.
                    ReadingText("M1K3 keeps your words on this Mac — ask it anything.",
                                mode: readingMode)
                        .padding(.vertical, 4)
                } header: {
                    Text("Reading")
                } footer: {
                    Text("How M1K3's replies are typeset. Dyslexia-friendly uses OpenDyslexic; "
                        + "Bionic reader bolds the start of each word to guide the eye.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section("Memory") {
                    LabeledContent("Indexed items", value: "\(env.indexedItemCount)")
                        .monospacedDigit()
                    LabeledContent("Model availability",
                                   value: env.providerAvailable ? "Ready" : "Unavailable")
                }

                Section {
                    TextField("What happened? (optional)", text: $whatHappened, axis: .vertical)
                        .lineLimit(2 ... 5)
                        .textFieldStyle(.roundedBorder)
                    Button("Report an issue…") {
                        Task {
                            issueTruncated = await IssueReporter.reportIssue(
                                whatHappened: whatHappened,
                                activeBrain: env.selectedBrain.displayName,
                                userProfile: M1K3Persona.userProfile
                            )
                            issueReported = true
                        }
                    }
                    .buttonStyle(.glass)
                } header: {
                    Text("Diagnostics")
                } footer: {
                    Text(issueReported
                        ? (issueTruncated
                            ? "Full report copied to your clipboard — paste it into the issue body on GitHub."
                            : "Opened a prefilled issue on GitHub (also copied to your clipboard). Review before you submit.")
                        : "Copies recent logs + this Mac's details, scrubbed of paths, emails and "
                        + "your name, then opens a prefilled GitHub issue. Nothing is sent until you submit.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    Button("Third-party licenses") { showLicenses = true }
                        .buttonStyle(.glass)
                } footer: {
                    Text("M1K3 is built with open-source components. "
                        + "Everything runs on this Mac.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .formStyle(.grouped)
            .scrollContentBackground(.hidden)
        }
        .frame(width: 480, height: 520)
        .glassBackdrop()
        .sheet(isPresented: $showLicenses) {
            LicensesView()
        }
        // Destructive re-run confirm, hoisted off the leaf Button (Startup section)
        // so it presents reliably — a confirmationDialog on a Button inside a Form
        // can silently fail to show on macOS, and this gate guards a full reset.
        .confirmationDialog(
            "Re-run the first-run setup?",
            isPresented: $showResetOnboarding,
            titleVisibility: .visible
        ) {
            Button("Re-run onboarding", role: .destructive) {
                // Full flow from the start (You → Brain → Voice → Speech) — NOT the
                // brain-only re-pick. Saved profile + downloaded models are kept.
                UserDefaults.standard.set(false, forKey: M1K3App.onboardingStartAtBrainKey)
                UserDefaults.standard.set(false, forKey: AppEnvironment.hasChosenBrainKey)
            }
        } message: {
            Text("Shows the full You → Brain → Voice → Speech flow again. "
                + "Your saved profile and downloaded models are kept.")
        }
        // Re-read the live login-item status each time Settings opens, so a grant
        // the user just made in System Settings (which we can't observe) is
        // reflected without them having to toggle it again.
        .onAppear { launchAtLogin.refresh() }
    }

    /// The voice-output tier control: progress while the M1K3 Voice model
    /// downloads, otherwise a Built-in ↔ M1K3 Voice toggle (+ any failure).
    @ViewBuilder
    private var voiceOutputControl: some View {
        switch env.voiceLoad {
        case let .downloading(fraction):
            VStack(alignment: .leading, spacing: 4) {
                ProgressView(value: fraction)
                Text(env.voiceLoad.label(modelName: "M1K3 Voice"))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
        default:
            if env.selectedVoiceTier == .m1k3Voice {
                Button("Switch to Built-in voice") { env.selectVoiceTier(.builtin) }
                    .buttonStyle(.glass)
            } else {
                Button("Upgrade to M1K3 Voice (downloads model)") { env.selectVoiceTier(.m1k3Voice) }
                    .buttonStyle(.glass)
            }
            if case let .failed(message) = env.voiceLoad {
                Label(message, systemImage: "exclamationmark.triangle")
                    .symbolRenderingMode(.hierarchical)
                    .font(.caption)
                    .foregroundStyle(.orange)
            }
        }
    }
}

// MARK: - Sections

/// Same-file extension — sees the struct's private @State / @Environment while
/// keeping the main struct body under SwiftLint's type_body_length ceiling.
extension SettingsView {
    /// Voice-mode face: the pixel face (default) or an opt-in 3D companion. Only
    /// companions with bundled assets are offered. The pixel face stays M1K3's
    /// face everywhere else — this is just the voice-mode skin. (Own property — the
    /// Form is at the type-checker budget; see aboutYouSection.)
    private var companionSection: some View {
        Section {
            Picker("Companion", selection: $voiceCompanion) {
                Text("Pixel face").tag("")
                Text("Memory constellation").tag(AppEnvironment.voiceCompanionConstellation)
                ForEach(CompanionSpec.all.filter(CompanionAssets.isInstalled)) { spec in
                    Text(spec.displayName).tag(spec.id)
                }
            }
            Picker("Display", selection: $avatarDisplay) {
                ForEach(AvatarDisplay.allCases) { mode in
                    Text(mode.label).tag(mode)
                }
            }
            Picker("3D creature skin", selection: $companionShading) {
                ForEach(CompanionShadingStyle.allCases) { Text($0.displayName).tag($0.rawValue) }
            }
        } header: {
            Text("Companion")
        } footer: {
            Text("M1K3's face in the avatar panel and voice mode. The pixel face is the "
                + "default; the memory constellation shows your knowledge growing in 3D; "
                + "or pick a 3D creature. The menu-bar mark stays the pixel M either way.\n"
                + "Display: a panel above the chat, a full-window background (recedes while "
                + "you read or type so text stays legible), or off. 3D creature skin restyles a "
                + "creature live: Phosphor (a glowing rim that shifts with M1K3's state) or Cel "
                + "(a toon banding of the creature's own texture).")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    /// Launch-at-login + the menu-bar companion note. Own property to keep the
    /// Form body under the type-checker budget (see aboutYouSection). The toggle
    /// drives the reconcile policy in LaunchAtLogin (idempotent + error-catching);
    /// requiresApproval / lastError surface inline so a blocked grant isn't silent.
    private var startupSection: some View {
        Section {
            Toggle("Launch M1K3 at login", isOn: Binding(
                get: { launchAtLogin.isEnabled },
                set: { launchAtLogin.setEnabled($0) }
            ))
            if launchAtLogin.requiresApproval {
                Button("Approve in System Settings…") { openLoginItemsSettings() }
                    .buttonStyle(.glass)
            }
            if let error = launchAtLogin.lastError {
                Text(error).font(.caption).foregroundStyle(.red)
            }
            // Live: flip the Dock icon now for instant feedback. The window-at-
            // launch suppression is applied by defaultLaunchBehavior next launch.
            Toggle("Show in menu bar only (hide Dock icon)", isOn: $menuBarOnly)
                .onChange(of: menuBarOnly) { _, on in
                    // Same decision gate as the AppDelegate's launch path.
                    let hidesDock = StartupVisibility(menuBarOnly: on).hidesDockIcon
                    NSApp.setActivationPolicy(hidesDock ? .accessory : .regular)
                }
            Picker("Menu bar icon", selection: $glyphStyle) {
                ForEach(MenuBarGlyphStyle.allCases) { style in
                    Label { Text(style.label) } icon: { Image(nsImage: style.image(pointSize: 14)) }
                        .tag(style)
                }
            }
            // Action only — the destructive confirm is hoisted onto the Form (see
            // `body`) so it presents reliably; a confirmationDialog on a leaf Button
            // inside a Form can silently fail to show on macOS, and this gate guards
            // a full onboarding reset.
            Button("Re-run onboarding…", role: .destructive) { showResetOnboarding = true }
                .buttonStyle(.glass)
        } header: {
            Text("Startup")
        } footer: {
            Text("Keep M1K3 in your menu bar and start it automatically when you log "
                + "in, so it's always a click away. \"Menu bar only\" hides the Dock "
                + "icon and starts M1K3 quietly (no window) — open it any time from the "
                + "menu. M1K3 stays on-device either way.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    /// Open System Settings at Login Items. The deep-link pane id has drifted
    /// across macOS releases, so if the specific URL won't open we fall back to
    /// System Settings' root rather than leave the button silently dead.
    private func openLoginItemsSettings() {
        let deepLink = URL(string: "x-apple.systempreferences:com.apple.LoginItems-Settings.extension")
        if let deepLink, NSWorkspace.shared.open(deepLink) { return }
        if let root = URL(string: "x-apple.systempreferences:") {
            NSWorkspace.shared.open(root)
        }
    }

    /// In-process MCP server controls. Own property — the Form expression
    /// is over the type-checker budget when sections are inlined.
    private var mcpSection: some View {
        Section {
            Toggle("MCP server (HTTP, localhost)", isOn: Binding(
                get: { env.mcpHost.isEnabled },
                set: { env.mcpHost.setEnabled($0) }
            ))
            if let status = env.mcpHost.statusText {
                LabeledContent("Status", value: status)
            }
        } header: {
            Text("MCP server")
        } footer: {
            Text("Lets Claude (or any MCP client) on THIS Mac use M1K3's "
                + "knowledge search, voice, and microphone. Loopback only — "
                + "never reachable from the network. One client at a time. "
                + "Connect with:  claude mcp add --transport http m1k3 "
                + "http://127.0.0.1:\(env.mcpHost.port)/mcp")
                .font(.caption).foregroundStyle(.secondary)
                .textSelection(.enabled)
        }
    }

    /// The consent surface for the persona's About-the-user block: fully
    /// visible, editable, clearable. (Own property — inlining it tipped the
    /// Form expression over the type-checker's budget.)
    private var aboutYouSection: some View {
        Section {
            TextField(
                "Nothing yet — tell M1K3 who you are",
                text: $profileDraft,
                axis: .vertical
            )
            .lineLimit(2 ... 5)
            .onSubmit { env.saveUserProfile(profileDraft) }
            HStack {
                Button("Save") { env.saveUserProfile(profileDraft) }
                    .buttonStyle(.glass)
                    .disabled(profileDraft == (M1K3Persona.userProfile ?? ""))
                Button("Clear", role: .destructive) {
                    profileDraft = ""
                    env.saveUserProfile(nil)
                }
                .buttonStyle(.glass)
                .disabled((M1K3Persona.userProfile ?? "").isEmpty)
            }
        } header: {
            Text("About you")
        } footer: {
            Text("What M1K3 knows about you — it rides every conversation's "
                + "system prompt. Stored on this Mac only, never retrieved or "
                + "cited, yours to edit or clear.")
                .font(.caption).foregroundStyle(.secondary)
        }
        .onAppear { profileDraft = M1K3Persona.userProfile ?? "" }
    }

    /// Memory auto-capture consent + the door to the review/forget surface.
    /// (Own property — the Form is at the type-checker budget.)
    private var memorySection: some View {
        Section {
            Toggle("Learn from conversations", isOn: $memoryAutoCapture)
            Button("View memories…") { showMemories = true }
                .buttonStyle(.glass)
        } header: {
            Text("Memories")
        } footer: {
            Text("When a conversation ends, M1K3 extracts durable facts about "
                + "you — preferences, decisions, people — into its memory. "
                + "Fully on-device. You can review and delete every memory.")
                .font(.caption).foregroundStyle(.secondary)
        }
        .sheet(isPresented: $showMemories) {
            MemoriesView().environment(env)
        }
    }

    /// Shows the MLX Gemma weight download as a real progress bar while it
    /// streams (~1GB on first use), or the failure, so selecting MLX never looks
    /// like a silent hang. Renders nothing when idle or ready.
    @ViewBuilder private var modelLoadRow: some View {
        switch env.modelLoad {
        case let .downloading(fraction):
            HStack(spacing: 8) {
                ProgressView(value: fraction)
                    .controlSize(.small)
                    .frame(maxWidth: 160)
                Text(env.modelLoad.label(modelName: env.downloadingBrainName))
                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
        case .preparing:
            HStack(spacing: 8) {
                ProgressView().controlSize(.small) // indeterminate
                Text(env.modelLoad.label(modelName: env.downloadingBrainName))
                    .font(.caption).foregroundStyle(.secondary)
            }
        case .failed:
            Label(env.modelLoad.label(modelName: env.downloadingBrainName), systemImage: "exclamationmark.triangle")
                .symbolRenderingMode(.hierarchical)
                .font(.caption).foregroundStyle(.orange)
        case .idle, .ready:
            EmptyView()
        }
    }
}
