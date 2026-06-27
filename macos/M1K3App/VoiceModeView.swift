//
//  VoiceModeView.swift
//  M1K3App
//
//  Voice-first mode as a FULL-WINDOW hero: the living avatar (pixel face /
//  companion creature / memory constellation) fills the entire window, and the
//  spoken-line Focus-reader + mic / think / leave controls float on a glass card
//  over it. The immersive face-you-talk-to.
//
//  This SUPERSEDES the 2026-06-21 bottom dock, which shrank the avatar to a ~92pt
//  corner card beside the live transcript — a regression Kev caught in use ("the
//  avatar needs to be full screen"). Same VoiceLoopController + speechHighlight +
//  AvatarSurface + KaraokeReadingText the dock drove, so this is recomposition,
//  not new behaviour — only the avatar reclaims the window as the hero.
//
//  The avatar OWNS the window and is the primary tap / Space barge-in target; the
//  floating chrome sits above it on glass. Empty space around the glass card does
//  NOT capture taps, so a tap anywhere on the face still barges in.
//
//  ESC leaves · Space or a tap on the face begins / barges in (no-op while
//  thinking, v1) · the mic button parks / wakes the loop.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-26, Confidence 0.75 (thin view over the
//  tested loop; the full-window look + legibility over a bright avatar are the
//  verify-at-⌘R taste gate Kev owns). Prior: Kev + claude-opus-4-8 (VoiceDock, the
//  bottom-dock interlude) → Kev + claude-fable-5 (VoiceModeView, the original
//  full-window forebear this restores).
//

import M1K3Avatar
import M1K3Voice
import SwiftUI

struct VoiceModeView: View {
    @Environment(AppEnvironment.self) private var env
    /// Voice mode's own brain switch — replaces the Settings Reasoning picker
    /// while the loop is active. Off (default) = fast replies; flips apply from
    /// the next turn (the provider reads it per turn).
    @AppStorage(AppEnvironment.voiceModeThinkingKey) private var voiceThinking = false
    /// The hero must CLAIM first-responder on appear, or Space/Esc go nowhere —
    /// it's mounted as a full-window overlay over the chat, so `.focusable()`
    /// alone doesn't route key presses here until something grants focus.
    @FocusState private var focused: Bool

    var body: some View {
        ZStack {
            // A deep backdrop so the chat behind the overlay never bleeds through
            // and a bright face / stars pop. The avatar is the room now.
            VoiceBackdrop()

            // The avatar IS the window — full-bleed hero, and the primary
            // tap / Space barge-in surface.
            AvatarSurface(env: env)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: primaryAction)
                .accessibilityLabel("M1K3")
                .accessibilityHint("Tap to start talking, or to interrupt")

            // Floating chrome, bottom-anchored on glass so the spoken line stays
            // legible over the avatar. The outer VStack/Spacer is non-interactive
            // (no background) → taps in the empty area fall through to the face.
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                VStack(spacing: 16) {
                    stateContent
                        .frame(maxWidth: .infinity)
                    controlBar
                }
                .padding(20)
                .frame(maxWidth: 720)
                .glassEffect(.regular, in: .rect(cornerRadius: 28))
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .focusable()
        .focused($focused)
        .onAppear { focused = true } // grab first-responder so Space/Esc fire
        .onKeyPress(.space) {
            primaryAction()
            return .handled
        }
        .onExitCommand { env.exitVoiceMode() }
        .onChange(of: env.voiceLoop?.state) { _, newState in
            syncAvatar(with: newState)
            // One bounce per answer — fire only on the transition INTO speaking
            // (a Bool value: would double-fire on entry and exit).
            if case .speaking = newState { speakDownbeat &+= 1 }
        }
        .onChange(of: env.chat.messages.last?.text) {
            bumpToGeneratingIfStreaming()
        }
    }

    // MARK: - State content (the karaoke line / live partial / status)

    @ViewBuilder
    private var stateContent: some View {
        switch env.voiceLoop?.state {
        case let .listening(partial):
            Text(partial.isEmpty ? "Listening…" : partial)
                .font(.title3)
                .foregroundStyle(partial.isEmpty ? .secondary : .primary)
                .multilineTextAlignment(.center)
                .lineLimit(4)
                .frame(maxWidth: .infinity)
                .animation(.easeOut(duration: 0.15), value: partial)

        case .awaitingAnswer:
            HStack(spacing: 10) {
                ProgressView().controlSize(.small)
                Text(env.chat.messages.last?.activityLabel ?? "Thinking…")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            // One spoken element — the spinner alone says nothing to VoiceOver.
            .accessibilityElement(children: .combine)
            .accessibilityLabel(env.chat.messages.last?.activityLabel ?? "Thinking")

        case .speaking:
            if let text = env.speechHighlight.utteranceText {
                // The dyslexia Focus-reader — the spoken line follows word-by-word.
                // The window is the hero now, so it gets real height to read in.
                KaraokeReadingText(
                    text: text,
                    timeline: env.speechHighlight.timeline,
                    currentWordRange: env.speechHighlight.currentWordRange
                )
                .frame(maxHeight: 220)
                .accessibilityLabel("M1K3 is speaking")
            } else {
                Text("Speaking…")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("M1K3 is speaking")
            }

        case .idle:
            VStack(spacing: 6) {
                Text("Tap the face or press Space to talk")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                if let error = env.voiceLoop?.lastError {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.orange)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                }
                // First voice session downloads WhisperKit (the sharper engine);
                // until it's ready the loop runs on the Apple Speech fallback and
                // upgrades on the next listen. Say so, so a rougher first transcript
                // reads as "warming up", not "broken".
                if case .preparing = env.whisperLoad {
                    Label("Warming up sharper voice…", systemImage: "waveform")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .symbolEffect(.variableColor.iterative)
                }
            }
            .frame(maxWidth: .infinity)

        case .ended, .none:
            Text("")
                .accessibilityHidden(true) // transient teardown frame
        }
    }

    // MARK: - Controls (mic / think / leave) — the loop's own intents

    private var controlBar: some View {
        HStack(spacing: 16) {
            Button(action: micAction) {
                Image(systemName: micSymbol)
                    .imageScale(.large).fontWeight(.semibold)
                    .frame(width: 24, height: 24)
                    // Smooth mic↔mic.fill swap, and a gentle living breathe while
                    // M1K3 is actually listening — the chrome reflects the loop.
                    .contentTransition(.symbolEffect(.replace))
                    .symbolEffect(.breathe, isActive: isListening)
            }
            .buttonStyle(.glass).buttonBorderShape(.circle)
            .tint(isListening ? .red : nil)
            .help(isListening ? "Stop listening" : "Start listening")
            .accessibilityLabel(isListening ? "Mute microphone" : "Start listening")

            Button { voiceThinking.toggle() } label: {
                Image(systemName: "brain")
                    .imageScale(.large).fontWeight(.semibold)
                    .frame(width: 24, height: 24)
                    // A travelling shimmer across the brain's layers WHILE thinking,
                    // and a single bounce the moment the answer lands.
                    .symbolEffect(.variableColor.iterative.hideInactiveLayers, isActive: isThinking)
                    .symbolEffect(.bounce, value: speakDownbeat)
            }
            .buttonStyle(.glass).buttonBorderShape(.circle)
            .tint(voiceThinking ? .purple : nil)
            .help(voiceThinking
                ? "Thinking on — deeper answers, slower. Applies next turn"
                : "Thinking off — fast replies. Applies next turn")
            .accessibilityLabel("Thinking")
            .accessibilityValue(voiceThinking ? "On" : "Off")
            .accessibilityAddTraits(.isToggle)

            Button { env.exitVoiceMode() } label: {
                Image(systemName: "xmark")
                    .imageScale(.large).fontWeight(.semibold)
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.glass).buttonBorderShape(.circle)
            .help("Leave voice mode (Esc)")
            .accessibilityLabel("Leave voice mode")
        }
    }

    // MARK: - Intents (lifted from the full-window forebear / dock — unchanged)

    private var isListening: Bool {
        if case .listening = env.voiceLoop?.state { return true }
        return false
    }

    /// True while M1K3 is working on the answer — drives the brain's shimmer.
    private var isThinking: Bool {
        if case .awaitingAnswer = env.voiceLoop?.state { return true }
        return false
    }

    /// A monotonic token bumped once each time a turn enters `.speaking`, so the
    /// brain bounces exactly once per answer (a discrete `.bounce` trigger).
    @State private var speakDownbeat = 0

    private var micSymbol: String {
        isListening ? "mic.fill" : "mic"
    }

    private func micAction() {
        switch env.voiceLoop?.state {
        case .listening: env.voiceLoop?.mute()
        case .idle: env.voiceLoop?.begin()
        case .speaking: env.voiceLoop?.interrupt()
        default: break
        }
    }

    /// Tap the face / Space: wake from idle, or barge in while M1K3 speaks.
    private func primaryAction() {
        switch env.voiceLoop?.state {
        case .idle: env.voiceLoop?.begin()
        case .speaking: env.voiceLoop?.interrupt()
        default: break // listening / thinking: no-op (v1)
        }
    }

    // MARK: - Avatar mapping

    private func syncAvatar(with state: VoiceLoopState?) {
        switch state {
        case .listening:
            env.avatar.setActivity(.listening)
        case .awaitingAnswer:
            env.avatar.setActivity(.thinking)
        case .idle:
            env.avatar.resetToIdle()
        case .speaking, .ended, .none:
            break // speech callbacks own .speaking; exit cleans up after itself
        }
    }

    /// While awaiting the answer, the first streamed tokens bump thinking →
    /// generating (observable signal — no timer).
    private func bumpToGeneratingIfStreaming() {
        guard case .awaitingAnswer = env.voiceLoop?.state,
              let last = env.chat.messages.last,
              last.role == .assistant, !last.text.isEmpty
        else { return }
        env.avatar.setActivity(.generating)
    }
}

/// A deep, near-opaque backdrop for the full-window voice hero — it hides the
/// chat behind the overlay and makes a bright face / the constellation's stars
/// read. Non-interactive (taps belong to the avatar above it).
private struct VoiceBackdrop: View {
    var body: some View {
        LinearGradient(
            colors: [
                Color(red: 0.04, green: 0.04, blue: 0.08),
                Color(red: 0.07, green: 0.05, blue: 0.12),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}
