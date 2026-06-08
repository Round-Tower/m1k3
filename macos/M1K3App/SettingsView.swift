//
//  SettingsView.swift
//  M1K3App
//
//  The runtime picker stub. Apple Foundation Models is the only wired backend for
//  the MVP; MLX and LiteRT Gemma are shown as reserved slots so the comparison
//  surface Kev wants is visible from day one, even before those backends land.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import SwiftUI

struct SettingsView: View {
    @Environment(AppEnvironment.self) private var env

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
                                   value: env.usingMLXEmbeddings ? "MLX bge_small (semantic)" : "Hashing (offline)")
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

    /// Shows the MLX Gemma weight download as a real progress bar while it
    /// streams (~1GB on first use), or the failure, so selecting MLX never looks
    /// like a silent hang. Renders nothing when idle or ready.
    @ViewBuilder
    private var modelLoadRow: some View {
        switch env.modelLoad {
        case let .downloading(fraction):
            HStack(spacing: 8) {
                ProgressView(value: fraction)
                    .controlSize(.small)
                    .frame(maxWidth: 160)
                Text(env.modelLoad.label(modelName: env.downloadingBrainName))
                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
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
