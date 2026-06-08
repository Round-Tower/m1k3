//
//  OnboardingView.swift
//  M1K3App
//
//  Two-step first-run flow:
//    1. Brain — Mini / Lil / Big M1K3 (existing). Download for Lil/Big, instant for Mini.
//    2. Voice — Apple Speech (default, built-in) or WhisperKit (higher accuracy, ~142 MB).
//
//  Voice is always a choice; Apple Speech is the safe default so nobody is forced
//  through a second download after their brain choice. WhisperKit can be started here
//  or deferred to Settings → Voice input.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.8,
//  Prior: Kev + claude-opus-4-8 2026-06-08 (single-step brain-only version)

import M1K3Inference
import SwiftUI

struct OnboardingView: View {
    @Environment(AppEnvironment.self) private var env
    let onComplete: () -> Void

    private enum Step { case brain, voice }
    private enum VoiceEngine { case apple, whisperKit }

    @State private var step: Step = .brain
    @State private var selectedBrain: BrainTier = .recommendedForThisMac
    @State private var isWakingBrain = false
    @State private var selectedVoice: VoiceEngine = .apple
    @State private var isDownloadingWhisper = false

    private let recommended = BrainTier.recommendedForThisMac

    var body: some View {
        VStack(spacing: 24) {
            switch step {
            case .brain:
                header(
                    glyph: "brain.head.profile.fill",
                    title: "M1K3",
                    subtitle: "Your local intelligence machine. Choose a brain — it runs entirely on this Mac."
                )
                if isWakingBrain { brainAwakening } else { brainPicker }
            case .voice:
                header(
                    glyph: "waveform",
                    title: "Voice",
                    subtitle: "Tap the mic to dictate. Apple Speech works now; WhisperKit is a higher-accuracy upgrade."
                )
                if isDownloadingWhisper { voiceDownload } else { voicePicker }
            }
        }
        .padding(32)
        .frame(minWidth: 580, minHeight: 640)
        .glassBackdrop()
        .onChange(of: env.modelLoad) { _, state in
            if case .ready = state, isWakingBrain {
                isWakingBrain = false
                step = .voice
            }
        }
        .onChange(of: env.whisperLoad) { _, state in
            if case .ready = state, isDownloadingWhisper { onComplete() }
        }
    }

    // MARK: - Shared header

    private func header(glyph: String, title: String, subtitle: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: glyph)
                .font(.system(size: 40, weight: .semibold))
                .foregroundStyle(.tint)
                .padding(.bottom, 4)
            Text(title)
                .font(.pixel(40))
                .kerning(2)
            Text(subtitle)
                .font(.title3)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 460)
        }
    }

    // MARK: - Brain step

    private var brainPicker: some View {
        VStack(spacing: 16) {
            ForEach(BrainTier.allCases) { tier in
                BrainCard(
                    tier: tier,
                    isSelected: selectedBrain == tier,
                    isRecommended: tier == recommended
                ) { selectedBrain = tier }
            }

            Button(action: wakeBrain) {
                Text(selectedBrain.requiresDownload
                    ? "Download \(selectedBrain.displayName) →"
                    : "Wake up \(selectedBrain.displayName) →")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .padding(.top, 4)

            Text("On-device only. Your conversations never leave this Mac.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var brainAwakening: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: selectedBrain.glyph)
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(.tint)
                .symbolEffect(.pulse)
            Text("Waking up \(selectedBrain.displayName)…")
                .font(.title2.weight(.semibold))

            switch env.modelLoad {
            case let .downloading(fraction):
                VStack(spacing: 6) {
                    ProgressView(value: fraction).frame(maxWidth: 320)
                    Text(env.modelLoad.label(modelName: selectedBrain.displayName))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            case .failed:
                VStack(spacing: 10) {
                    Label(env.modelLoad.label(modelName: selectedBrain.displayName),
                          systemImage: "exclamationmark.triangle")
                        .symbolRenderingMode(.hierarchical)
                        .font(.callout)
                        .foregroundStyle(.orange)
                    Button("Try again") { wakeBrain() }
                        .buttonStyle(.glass)
                    Button("Use Mini (built-in) instead") {
                        selectedBrain = .mini
                        isWakingBrain = false
                        env.selectBrain(.mini)
                        step = .voice
                    }
                    .buttonStyle(.plain)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            case .idle, .ready:
                ProgressView()
            }

            Text("Your data stays on your device, always.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
        }
    }

    private func wakeBrain() {
        env.selectBrain(selectedBrain)
        if selectedBrain.requiresDownload {
            isWakingBrain = true
        } else {
            step = .voice
        }
    }

    // MARK: - Voice step

    private var voicePicker: some View {
        VStack(spacing: 16) {
            VoiceCard(
                engine: .apple,
                isSelected: selectedVoice == .apple
            ) { selectedVoice = .apple }

            VoiceCard(
                engine: .whisperKit,
                isSelected: selectedVoice == .whisperKit
            ) { selectedVoice = .whisperKit }

            Button(action: proceedVoice) {
                Text(selectedVoice == .whisperKit
                    ? "Download WhisperKit →"
                    : "Continue with Apple Speech →")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .padding(.top, 4)

            Button("Skip — set up voice later in Settings") { onComplete() }
                .buttonStyle(.plain)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var voiceDownload: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "waveform.badge.star")
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(.tint)
                .symbolEffect(.pulse)
            Text("Downloading WhisperKit…")
                .font(.title2.weight(.semibold))

            switch env.whisperLoad {
            case let .downloading(fraction):
                VStack(spacing: 6) {
                    ProgressView(value: fraction).frame(maxWidth: 320)
                    Text(env.whisperLoad.label(modelName: "WhisperKit"))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            case .failed:
                VStack(spacing: 10) {
                    Label(env.whisperLoad.label(modelName: "WhisperKit"),
                          systemImage: "exclamationmark.triangle")
                        .symbolRenderingMode(.hierarchical)
                        .font(.callout)
                        .foregroundStyle(.orange)
                    Button("Try again") { proceedVoice() }
                        .buttonStyle(.glass)
                    Button("Use Apple Speech instead") {
                        isDownloadingWhisper = false
                        onComplete()
                    }
                    .buttonStyle(.plain)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            case .idle, .ready:
                ProgressView()
            }

            Text("Your voice never leaves this Mac.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
        }
    }

    private func proceedVoice() {
        if selectedVoice == .apple {
            onComplete()
        } else {
            isDownloadingWhisper = true
            Task { await env.enableWhisperKit() }
        }
    }
}

// MARK: - Brain card (unchanged)

private struct BrainCard: View {
    let tier: BrainTier
    let isSelected: Bool
    let isRecommended: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 16) {
                Image(systemName: tier.glyph)
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.tint)
                    .frame(width: 40)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(tier.displayName).font(.pixel(15))
                        if isRecommended { recommendedBadge }
                    }
                    .padding(.bottom, 2)
                    Text(tier.tagline)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.tint)
                    Text(tier.detail)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(sizeLabel)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.tertiary)
                        .padding(.top, 2)
                }
                Spacer(minLength: 0)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                    .font(.title3)
                    .accessibilityHidden(true)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassEffect(
                isSelected ? .regular.tint(.accentColor.opacity(0.22)) : .regular,
                in: .rect(cornerRadius: 18)
            )
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(tier.displayName), \(tier.tagline). \(sizeLabel)")
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }

    private var recommendedBadge: some View {
        Text("Recommended")
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .glassEffect(.regular.tint(.accentColor.opacity(0.3)), in: .capsule)
    }

    private var sizeLabel: String {
        guard let megabytes = tier.approxDownloadMB else { return "Built-in · no download" }
        if megabytes >= 1000 {
            return String(format: "~%.1f GB · one-time download", Double(megabytes) / 1000)
        }
        return "~\(megabytes) MB · one-time download"
    }
}

// MARK: - Voice card

private struct VoiceCard: View {
    enum Engine {
        case apple, whisperKit

        var glyph: String {
            switch self {
            case .apple: "waveform"
            case .whisperKit: "waveform.badge.star"
            }
        }

        var displayName: String {
            switch self {
            case .apple: "Apple Speech"
            case .whisperKit: "WhisperKit"
            }
        }

        var tagline: String {
            switch self {
            case .apple: "Ready now · no download"
            case .whisperKit: "Higher accuracy · ~142 MB"
            }
        }

        var detail: String {
            switch self {
            case .apple: "Built into macOS. Works immediately, always on-device."
            case .whisperKit: "Open-source model. More accurate in noise and with accents. One download, then always offline."
            }
        }
    }

    let engine: Engine
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 16) {
                Image(systemName: engine.glyph)
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.tint)
                    .frame(width: 40)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    Text(engine.displayName).font(.pixel(15)).padding(.bottom, 2)
                    Text(engine.tagline)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.tint)
                    Text(engine.detail)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                    .font(.title3)
                    .accessibilityHidden(true)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassEffect(
                isSelected ? .regular.tint(.accentColor.opacity(0.22)) : .regular,
                in: .rect(cornerRadius: 18)
            )
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(engine.displayName), \(engine.tagline)")
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
