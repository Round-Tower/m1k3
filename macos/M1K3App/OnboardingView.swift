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
import M1K3Voice
import SwiftUI

struct OnboardingView: View {
    @Environment(AppEnvironment.self) private var env
    let onComplete: () -> Void

    private enum Step { case brain, voice, speech }
    private enum VoiceEngine { case apple, whisperKit }

    @State private var step: Step = .brain
    @State private var selectedBrain: BrainTier = .recommendedForThisMac
    @State private var isWakingBrain = false
    @State private var selectedVoice: VoiceEngine = .apple
    @State private var isDownloadingWhisper = false
    @State private var selectedSpeechTier: VoiceTier = .builtin
    @State private var isDownloadingVoice = false

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
            case .speech:
                header(
                    glyph: "speaker.wave.3.fill",
                    title: "Speech",
                    subtitle: "How M1K3 sounds when it speaks. Built-in is instant; "
                        + "M1K3 Voice is a richer, on-device voice with its own character."
                )
                if isDownloadingVoice { speechDownload } else { speechPicker }
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
            if case .ready = state, isDownloadingWhisper {
                isDownloadingWhisper = false
                step = .speech
            }
        }
        .onChange(of: env.voiceLoad) { _, state in
            if case .ready = state, isDownloadingVoice { onComplete() }
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
}

// MARK: - Steps

//
// Same-file extension (keeps the OnboardingView struct body under SwiftLint's
// type_body_length now that there are three steps). Step views read the struct's
// @State directly — private members of a same-file extension are visible to `body`.

private extension OnboardingView {
    // MARK: - Brain step

    var brainPicker: some View {
        VStack(spacing: 16) {
            ForEach(BrainTier.allCases) { tier in
                BrainCard(
                    tier: tier,
                    isSelected: selectedBrain == tier,
                    isRecommended: tier == recommended,
                    isLocked: !tier.isSelectableOnThisMac
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

            Button("Skip — set up voice later in Settings") { step = .speech }
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
                        step = .speech
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
            step = .speech
        } else {
            isDownloadingWhisper = true
            Task { await env.enableWhisperKit() }
        }
    }

    // MARK: - Speech (TTS output) step

    private var speechPicker: some View {
        VStack(spacing: 16) {
            ForEach(VoiceTier.allCases) { tier in
                SpeechVoiceCard(
                    tier: tier,
                    isSelected: selectedSpeechTier == tier
                ) { selectedSpeechTier = tier }
            }

            Button(action: proceedSpeech) {
                Text(selectedSpeechTier.requiresDownload
                    ? "Download \(selectedSpeechTier.displayName) →"
                    : "Continue with \(selectedSpeechTier.displayName) →")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .padding(.top, 4)

            Button("Hear a sample") { Task { await env.speakSample() } }
                .buttonStyle(.glass)
                .font(.callout)

            Button("Skip — choose a voice later in Settings") {
                env.selectVoiceTier(.builtin)
                onComplete()
            }
            .buttonStyle(.plain)
            .font(.caption)
            .foregroundStyle(.secondary)
        }
    }

    private var speechDownload: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "speaker.wave.3.fill")
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(.tint)
                .symbolEffect(.pulse)
            Text("Preparing the M1K3 voice…")
                .font(.title2.weight(.semibold))

            switch env.voiceLoad {
            case let .downloading(fraction):
                VStack(spacing: 6) {
                    ProgressView(value: fraction).frame(maxWidth: 320)
                    Text(env.voiceLoad.label(modelName: "M1K3 Voice"))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            case .failed:
                VStack(spacing: 10) {
                    Label(env.voiceLoad.label(modelName: "M1K3 Voice"),
                          systemImage: "exclamationmark.triangle")
                        .symbolRenderingMode(.hierarchical)
                        .font(.callout)
                        .foregroundStyle(.orange)
                    Button("Try again") { proceedSpeech() }
                        .buttonStyle(.glass)
                    Button("Use Built-in instead") {
                        isDownloadingVoice = false
                        env.selectVoiceTier(.builtin)
                        onComplete()
                    }
                    .buttonStyle(.plain)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            case .idle, .ready:
                ProgressView()
            }

            Text("The voice runs entirely on this Mac — nothing is sent to a server.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
        }
    }

    private func proceedSpeech() {
        if selectedSpeechTier == .builtin {
            env.selectVoiceTier(.builtin)
            onComplete()
        } else {
            isDownloadingVoice = true
            Task { await env.prepareM1K3Voice() }
        }
    }
}
