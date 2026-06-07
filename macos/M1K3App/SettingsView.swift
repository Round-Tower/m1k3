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
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        @Bindable var env = env

        VStack(spacing: 0) {
            header
            Form {
                Section {
                    ForEach(RuntimeOption.allCases) { option in
                        RuntimeRow(
                            option: option,
                            isSelected: env.selectedRuntime == option
                        ) {
                            if option.isReady { env.selectedRuntime = option }
                        }
                    }
                    modelLoadRow
                } header: {
                    Text("Inference runtime")
                } footer: {
                    Text("On-device only. Your documents and questions never leave this Mac.")
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
                        Button(env.usingMLXEmbeddings ? "Switch to Hashing (rebuild index)"
                            : "Switch to MLX semantic embeddings (rebuild index)")
                        {
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
                    Text("Semantic MLX embeddings improve retrieval but download a model on first use and re-embed every stored chunk.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    LabeledContent("Active engine", value: env.activeTranscriberName)
                    if env.isPreparingWhisper {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text(env.whisperStatus ?? "Preparing WhisperKit…")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    } else {
                        Button("Enable WhisperKit (downloads model)") {
                            Task { await env.enableWhisperKit() }
                        }
                        .buttonStyle(.glass)
                        if let status = env.whisperStatus {
                            Text(status).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                } header: {
                    Text("Voice input")
                } footer: {
                    Text("Tap the mic in the chat bar to dictate — tap again to send. Apple Speech works out of the box; WhisperKit is higher accuracy after a one-time model download. On-device only.")
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
                    Text("Record a call from the chat toolbar (consent-gated). With transcription enabled, a stopped recording is transcribed, summarised, encrypted, and indexed — on-device.")
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
        }
        .frame(width: 440, height: 440)
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
                Text(env.modelLoad.label(modelName: "Gemma 3"))
                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
        case .failed:
            Label(env.modelLoad.label(modelName: "Gemma 3"), systemImage: "exclamationmark.triangle")
                .symbolRenderingMode(.hierarchical)
                .font(.caption).foregroundStyle(.orange)
        case .idle, .ready:
            EmptyView()
        }
    }

    private var header: some View {
        HStack {
            Label("Settings", systemImage: "gearshape")
                .symbolRenderingMode(.hierarchical)
                .font(.headline)
            Spacer()
            Button("Done") { dismiss() }
                .buttonStyle(.glassProminent)
        }
        .padding(16)
    }
}

private struct RuntimeRow: View {
    let option: RuntimeOption
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: option.systemImage)
                    .symbolRenderingMode(.hierarchical)
                    .frame(width: 24)
                    .foregroundStyle(option.isReady ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(option.rawValue)
                        .foregroundStyle(option.isReady ? .primary : .secondary)
                    Text(option.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .symbolRenderingMode(.hierarchical)
                        .foregroundStyle(.tint)
                        .accessibilityHidden(true)
                } else if !option.isReady {
                    Text("Soon").font(.caption2).foregroundStyle(.secondary)
                }
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .disabled(!option.isReady)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
