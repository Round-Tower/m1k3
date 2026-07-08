//
//  SettingsScreen.swift
//  M1K3iOS / M1K3visionOS
//
//  Brain choice, grounding options, and the honest about box. The brain picker
//  offers only the mobile-safe tiers (Mini = Apple Intelligence, Lil = MLX
//  Qwen3-4B); Big (gemma-4-e4b, ~7 GB at inference) exceeds any current mobile
//  budget and is deliberately not offered (BrainTier.recommended(platform:.mobile)).
//
//  Signed: Kev + claude-opus-4-8, 2026-07-06, Confidence 0.8. Prior: Unknown.
//

import M1K3Inference
import SwiftUI

struct SettingsScreen: View {
    @Environment(AppCore.self) private var core
    @AppStorage(AppCore.webSearchEnabledKey) private var webSearchEnabled = true

    /// Mobile-safe tiers only (see file header).
    private let brains: [BrainTier] = [.mini, .lil]

    var body: some View {
        NavigationStack {
            Form {
                Section("Brain") {
                    ForEach(brains) { tier in
                        Button {
                            core.selectBrain(tier)
                        } label: {
                            HStack(spacing: 12) {
                                // `glyph` is an SF Symbol NAME, not an emoji.
                                Image(systemName: tier.glyph)
                                    .font(.title3)
                                    .foregroundStyle(.tint)
                                    .frame(width: 28)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tier.displayName).foregroundStyle(.primary)
                                    Text(tier.tagline)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if core.selectedBrain == tier {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.tint)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                    if let note = core.brainNote {
                        Text(note).font(.caption).foregroundStyle(.orange)
                    }
                    if let hint = miniHint {
                        Text(hint).font(.caption).foregroundStyle(.secondary)
                    }
                }

                Section {
                    Toggle("Web search in chat", isOn: $webSearchEnabled)
                } header: {
                    Text("Grounding")
                } footer: {
                    Text("When on, M1K3 can search the web to answer. The only capability that sends chat-derived queries off this device.")
                }

                Section("Knowledge") {
                    LabeledContent("Indexed documents", value: "\(core.indexedItemCount)")
                }

                Section {
                    LabeledContent("Version", value: appVersion)
                    Link("m1k3.app", destination: URL(string: "https://m1k3.app")!)
                } header: {
                    Text("About")
                } footer: {
                    Text("M1K3 — a local, private AI companion. Everything runs on your device.")
                }
            }
            .navigationTitle("Settings")
        }
    }

    private var miniHint: String? {
        guard core.selectedBrain == .mini else { return nil }
        switch core.miniAvailability {
        case .available: return nil
        case .notReady: return "Apple Intelligence is still downloading on this device."
        case let .blocked(userFixable):
            return userFixable
                ? "Turn on Apple Intelligence in Settings, or choose Lil."
                : "This device can't run Apple Intelligence — choose Lil."
        }
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(version) (\(build))"
    }
}
