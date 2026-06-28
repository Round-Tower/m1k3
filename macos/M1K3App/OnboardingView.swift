//
//  OnboardingView.swift
//  M1K3App
//
//  Four-step first-run flow: You → Brain → Voice → Speech.
//    You    — who M1K3 is talking to (seeds the persona; skippable).
//    Brain  — Mini / Lil / Big / Huge M1K3. Download for Lil+, instant for Mini.
//    Voice  — Apple Speech (default, built-in) or WhisperKit (higher accuracy).
//    Speech — built-in TTS or the richer on-device M1K3 Voice.
//
//  Re-entry from Settings → "Change brain…" passes `startAtBrain: true`: it opens
//  on the Brain step and finishes the moment the brain is awake, so a brain change
//  never replays the empty "Who am I talking to?" screen (the old re-trigger bug).
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.8,
//  Prior: Kev + claude-opus-4-8 2026-06-08 (single-step brain-only version)
//  Review: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.85 — added the
//  brain-only re-pick entry (startAtBrain) so "Change brain…" deep-links to the
//  brain step and completes on wake instead of restarting the whole flow.

import M1K3Avatar
import M1K3Inference
import M1K3Voice
import SwiftUI

struct OnboardingView: View {
    @Environment(AppEnvironment.self) private var env
    let onComplete: () -> Void
    /// Brain-only re-pick (Settings → "Change brain…"): open on the brain step and
    /// finish on wake, instead of replaying the whole first-run flow from the empty
    /// "Who am I talking to?" screen. Default false = full first-run.
    let startAtBrain: Bool

    private enum Step { case you, brain, voice, speech }
    private enum VoiceEngine { case apple, whisperKit }

    @State private var step: Step
    @State private var userName = ""
    @State private var userNotes = ""
    @State private var selectedBrain: BrainTier = .recommendedForThisMac
    /// "Let M1K3 choose" (auto-route) selected instead of a specific tier. When
    /// true the explicit tier cards show unselected and the button hands the pick
    /// to M1K3 (ADR 0001). Restored from the saved auto-route flag on re-pick.
    @State private var autoSelected = false
    @State private var isWakingBrain = false
    @State private var selectedVoice: VoiceEngine = .apple
    @State private var isDownloadingWhisper = false
    @State private var selectedSpeechTier: VoiceTier = .builtin
    @State private var isDownloadingVoice = false

    private let recommended = BrainTier.recommendedForThisMac

    init(startAtBrain: Bool = false, onComplete: @escaping () -> Void) {
        self.startAtBrain = startAtBrain
        self.onComplete = onComplete
        _step = State(initialValue: startAtBrain ? .brain : .you)
    }

    var body: some View {
        // ScrollView: the four brain cards (plus header and button) outgrow
        // the window on smaller screens — the download button must never be
        // clipped behind a resize.
        ScrollView {
            VStack(spacing: 24) {
                switch step {
                case .you:
                    header(
                        glyph: "person.crop.circle",
                        title: "Hello",
                        subtitle: "I'm M1K3 — everything you tell me stays on this Mac. Who am I talking to?"
                    )
                    youStep
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
            .frame(maxWidth: .infinity)
        }
        .frame(minWidth: 580, minHeight: 640)
        .glassBackdrop()
        .onAppear {
            // A brain-only re-pick highlights the brain you're already running —
            // or the Auto card if auto-route is the live choice.
            if startAtBrain {
                selectedBrain = env.selectedBrain
                autoSelected = UserDefaults.standard.bool(forKey: AppEnvironment.autoRouteBrainKey)
            }
            env.avatar.setEmotion(emotion(for: step))
        }
        .onChange(of: step) { _, newStep in
            env.avatar.setEmotion(emotion(for: newStep))
        }
        .onDisappear { env.avatar.resetToIdle() }
        .onChange(of: env.modelLoad) { _, state in
            if case .ready = state, isWakingBrain {
                isWakingBrain = false
                advanceAfterBrain()
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

    /// The LIVE pixel face is the hero — M1K3 is present from the first
    /// screen, not represented by a static glyph. (`glyph` is kept in the
    /// step call sites' spirit via the per-step EMOTION instead.)
    private func header(glyph _: String, title: String, subtitle: String) -> some View {
        VStack(spacing: 8) {
//            AvatarView(controller: env.avatar)
//                .frame(width: 260, height: 150)
//                .padding(.bottom, 4)
//                .accessibilityHidden(true)
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

    /// The face reacts to where you are in the flow — meeting you, choosing
    /// a brain, listening, speaking.
    private func emotion(for step: Step) -> AvatarEmotion {
        switch step {
        case .you: .happy
        case .brain: .thinking
        case .voice: .surprised
        case .speech: .excited
        }
    }
}

// MARK: - Steps

//
// Same-file extension (keeps the OnboardingView struct body under SwiftLint's
// type_body_length now that there are three steps). Step views read the struct's
// @State directly — private members of a same-file extension are visible to `body`.

private extension OnboardingView {
    // MARK: - You step

    /// Who M1K3 is talking to — seeds the persona's About-the-user block
    /// (knowledge_meta `user.profile`, on this Mac only, visible in Settings).
    /// Skippable: anonymity is a fine answer to a privacy-first assistant.
    var youStep: some View {
        VStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 10) {
                TextField("Your name", text: $userName)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                TextField(
                    "Anything M1K3 should know about you? (optional)",
                    text: $userNotes,
                    axis: .vertical
                )
                .textFieldStyle(.roundedBorder)
                .lineLimit(3 ... 5)
            }
            .frame(maxWidth: 440)

            Button {
                // Same trim as the disabled-check below — a spaces-only name
                // must not produce "Name:   ." in the persona block.
                let name = userName.trimmingCharacters(in: .whitespaces)
                let notes = userNotes.trimmingCharacters(in: .whitespacesAndNewlines)
                let profile = [
                    name.isEmpty ? nil : "Name: \(name).",
                    notes.isEmpty ? nil : notes,
                ].compactMap(\.self).joined(separator: " ")
                env.saveUserProfile(profile)
                step = .brain
            } label: {
                Text("Nice to meet you →")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .disabled(userName.trimmingCharacters(in: .whitespaces).isEmpty)
            .frame(maxWidth: 440)

            Button("Skip — stay anonymous") { step = .brain }
                .buttonStyle(.glass)

            Text("Stored on this Mac only. View, edit or clear it any time in Settings.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Brain step

    var brainPicker: some View {
        VStack(spacing: 16) {
            AutoBrainCard(isSelected: autoSelected) { autoSelected = true }

            ForEach(BrainTier.allCases) { tier in
                BrainCard(
                    tier: tier,
                    isSelected: !autoSelected && selectedBrain == tier,
                    isRecommended: tier == recommended,
                    isLocked: !tier.isSelectableOnThisMac,
                    isDownloaded: env.isBrainDownloaded(tier)
                ) {
                    autoSelected = false
                    selectedBrain = tier
                }
            }

            Button(action: wakeBrain) {
                Text(brainButtonTitle)
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
                        advanceAfterBrain()
                    }
                    .buttonStyle(.plain)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            case .idle, .ready, .preparing:
                ProgressView()
            }

            Text("Your data stays on your device, always.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
        }
    }

    /// The wake button's label tracks the selection: auto hands the pick to M1K3,
    /// an un-fetched tier offers a download, a ready tier wakes in place.
    private var brainButtonTitle: String {
        if autoSelected { return "Let M1K3 choose →" }
        return selectedBrain.requiresDownload && !env.isBrainDownloaded(selectedBrain)
            ? "Download \(selectedBrain.displayName) →"
            : "Wake up \(selectedBrain.displayName) →"
    }

    private func wakeBrain() {
        // Auto-route: M1K3 resolves the concrete brain for this Mac, then we drive
        // the SAME download/wake path as a manual pick (the resolved tier may need
        // a download — e.g. Lil — so the wake screen shows what it chose).
        if autoSelected {
            selectedBrain = env.enableAutoRouteForOnboarding()
        } else {
            // A specific pick is sovereign — turn auto-route off so it isn't
            // overridden on the next turn (matters on a Settings → "Change brain…"
            // re-pick where auto-route was previously on).
            UserDefaults.standard.set(false, forKey: AppEnvironment.autoRouteBrainKey)
            env.selectBrain(selectedBrain)
        }
        if selectedBrain.requiresDownload {
            isWakingBrain = true
        } else {
            advanceAfterBrain()
        }
    }

    /// After the brain is awake: finish here for a brain-only re-pick, otherwise
    /// continue into voice/speech setup (the full first-run flow).
    private func advanceAfterBrain() {
        if startAtBrain { onComplete() } else { step = .voice }
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
            case .idle, .ready, .preparing:
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
            case .idle, .ready, .preparing:
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
