//
//  SettingsView.swift
//  M1K3App
//
//  The runtime picker stub. Apple Foundation Models is the only wired backend for
//  the MVP; MLX and LiteRT Gemma are shown as reserved slots so the comparison
//  surface Kev wants is visible from day one, even before those backends land.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import M1K3Avatar
import M1K3Chat
import M1K3Inference
import M1K3Voice
import SwiftUI

struct SettingsView: View {
    @Environment(AppEnvironment.self) private var env
    @AppStorage(ReadingMode.storageKey) private var readingMode: ReadingMode = .standard
    @AppStorage(AppEnvironment.webSearchEnabledKey) private var webSearchEnabled = true
    @AppStorage(AppEnvironment.memoryAutoCaptureKey) private var memoryAutoCapture = true
    @AppStorage(AppEnvironment.soundEffectsEnabledKey) private var soundEffectsEnabled = true
    @State private var showMemories = false
    @AppStorage(AppEnvironment.thinkingModeKey) private var thinkingMode = ThinkingMode.auto.rawValue
    @AppStorage(AppEnvironment.voiceCompanionKey) private var voiceCompanion = ""
    @State private var profileDraft = ""

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
                        UserDefaults.standard.set(false, forKey: AppEnvironment.hasChosenBrainKey)
                    }
                    .buttonStyle(.glass)
                } header: {
                    Text("Brain")
                } footer: {
                    Text("Mini is Apple's built-in model (instant). Lil and Big are local models "
                        + "that download once. On-device only — nothing leaves this Mac.")
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
                        + "model download. On-device only.")
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
                    Text("M1K3 can search the web (DuckDuckGo) and read result pages "
                        + "mid-answer. This is the one capability that sends anything off "
                        + "this Mac — every search and page read is shown in the reply as "
                        + "it happens. Date, time and system status tools stay fully local. "
                        + "Off means the model can't see the web tools at all.")
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
                    Text("Reasoning models think out loud before answering — great for "
                        + "hard questions, slow for small talk. Auto skips the thinking "
                        + "on casual turns and keeps it for grounded or analytic ones. "
                        + "Voice mode has its own thinking toggle (the brain button) "
                        + "and ignores this setting while active.")
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
            }
            .formStyle(.grouped)
            .scrollContentBackground(.hidden)
        }
        .frame(width: 480, height: 520)
        .glassBackdrop()
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

    /// Voice-mode face: the pixel face (default) or an opt-in 3D companion. Only
    /// companions with bundled assets are offered. The pixel face stays M1K3's
    /// face everywhere else — this is just the voice-mode skin. (Own property — the
    /// Form is at the type-checker budget; see aboutYouSection.)
    private var companionSection: some View {
        Section {
            Picker("Voice companion", selection: $voiceCompanion) {
                Text("Pixel face").tag("")
                ForEach(CompanionSpec.all.filter(CompanionAssets.isInstalled)) { spec in
                    Text(spec.displayName).tag(spec.id)
                }
            }
        } header: {
            Text("Voice companion")
        } footer: {
            Text("Who greets you in voice mode. The pixel face is M1K3 — a companion "
                + "is an optional skin for full-window voice conversations, and only "
                + "there. Everywhere else, the pixel face stays.")
                .font(.caption).foregroundStyle(.secondary)
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
