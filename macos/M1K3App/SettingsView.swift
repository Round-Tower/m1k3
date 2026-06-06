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
                } header: {
                    Text("Inference runtime")
                } footer: {
                    Text("On-device only. Your documents and questions never leave this Mac.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Memory") {
                    LabeledContent("Indexed items", value: "\(env.indexedItemCount)")
                    LabeledContent("Embeddings", value: "Hashing (offline)")
                    LabeledContent("Model availability",
                                   value: env.providerAvailable ? "Ready" : "Unavailable")
                }
            }
            .formStyle(.grouped)
        }
        .frame(width: 440, height: 440)
    }

    private var header: some View {
        HStack {
            Label("Settings", systemImage: "gearshape")
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
                    .frame(width: 24)
                    .foregroundStyle(option.isReady ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                VStack(alignment: .leading, spacing: 2) {
                    Text(option.rawValue)
                        .foregroundStyle(option.isReady ? .primary : .secondary)
                    Text(option.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.tint)
                } else if !option.isReady {
                    Text("Soon").font(.caption2).foregroundStyle(.secondary)
                }
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .disabled(!option.isReady)
    }
}
