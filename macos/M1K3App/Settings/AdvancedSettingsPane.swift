//
//  AdvancedSettingsPane.swift
//  M1K3App
//
//  The "Advanced" Settings tab: embeddings backend, voice input (WhisperKit),
//  call transcription, the generation-stats testing aid, the Agent
//  Interaction Log, index status, diagnostics, and licenses. Split out of the
//  old single-Form SettingsView (2026-07-13) — see SettingsView.swift for the
//  shell. The index-status readout is retitled "Status" (was "Memory") to
//  kill the duplicate-Memory-name problem now that the Memories consent
//  section lives on the You tab.
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.85 (a straight move
//  — every footer/copy verbatim except the Status retitle). Prior: Kev +
//  claude-opus-4-8 (SettingsView.swift lineage, 2026-06-06).
//

import M1K3Inference
import M1K3WhisperKit
import SwiftUI

struct AdvancedSettingsPane: View {
    @Environment(AppEnvironment.self) private var env
    @AppStorage(AppEnvironment.showGenerationStatsKey) private var showGenerationStats = false
    @AppStorage(AppEnvironment.conversationLogEnabledKey) private var conversationLogEnabled = false
    @State private var showLicenses = false
    @State private var issueReported = false
    @State private var issueTruncated = false
    @State private var whatHappened = ""

    var body: some View {
        Form {
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
                Toggle("Show generation stats", isOn: $showGenerationStats)
            } header: {
                Text("Generation stats")
            } footer: {
                Text("A testing aid: shows context tokens and tokens/sec under each answer "
                    + "from Lil or Big. Mini (Apple's on-device model) doesn't report "
                    + "generation metrics.")
                    .font(.caption).foregroundStyle(.secondary)
            }

            conversationLogSection

            Section("Status") {
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
        .sheet(isPresented: $showLicenses) {
            LicensesView()
        }
    }

    /// The Agent Interaction Log opt-in: full MCP request+response text is
    /// captured only while this is on (default OFF).
    private var conversationLogSection: some View {
        Section {
            Toggle("Log agent conversations", isOn: $conversationLogEnabled)
            Button("Clear log", role: .destructive) {
                try? env.conversationLog?.clear()
            }
            .buttonStyle(.glass)
        } header: {
            Text("Agent conversation log")
        } footer: {
            Text("On-device only, off by default. When on, M1K3 keeps the last "
                + "500 tool calls a connected agent makes — see them in Window → "
                + "Agent Log.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }
}
