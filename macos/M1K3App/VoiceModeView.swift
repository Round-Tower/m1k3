//
//  VoiceModeView.swift
//  M1K3
//
//  Voice-first mode: the living face as the hero, a spoken conversation
//  underneath. State content follows the loop — live transcript while
//  listening, a quiet activity line while thinking (deliberately NOT the
//  streaming tokens: the karaoke pass IS the reading aid; raw token streaming
//  is the hostile-to-read version), the karaoke reading view while speaking.
//
//  ESC exits · Space/click begins or barges in (no-op while thinking, v1) ·
//  the mic button parks/wakes the loop.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.75 (thin view over
//  the tested loop; layout + feel verify-at-⌘R). Prior: Unknown.
//

import M1K3Avatar
import M1K3Voice
import SwiftUI

struct VoiceModeView: View {
    @Environment(AppEnvironment.self) private var env
    /// Persisted voice-mode avatar: "" (default) = the pixel face, else a companion id.
    /// Voice mode's own brain switch — replaces the Settings Reasoning picker
    /// while the loop is active. Off (default) = fast replies; flips apply
    /// from the next turn (the provider reads it per turn).
    @AppStorage(AppEnvironment.voiceModeThinkingKey) private var voiceThinking = false

    var body: some View {
        VStack(spacing: 12) {
            avatar
                .frame(maxWidth: 420, maxHeight: 260)
                .padding(.top, 12)

            stateContent
                .frame(maxWidth: 560, maxHeight: .infinity)

            controlBar
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .onTapGesture(perform: primaryAction)
        .onKeyPress(.space) {
            primaryAction()
            return .handled
        }
        .onExitCommand { env.exitVoiceMode() }
        .focusable()
        .onChange(of: env.voiceLoop?.state) { _, newState in
            syncAvatar(with: newState)
        }
        .onChange(of: env.chat.messages.last?.text) {
            bumpToGeneratingIfStreaming()
        }
    }

    // MARK: - Avatar (pixel face by default; opt-in companion)

    /// The voice-mode hero: the pixel face unless the user opted into a companion
    /// that's actually installed. An unknown or uninstalled id falls back to the
    /// pixel face, so a stale setting can never blank the hero.
    private var avatar: some View {
        AvatarSurface(env: env)
    }

    // MARK: - State content

    @ViewBuilder
    private var stateContent: some View {
        switch env.voiceLoop?.state {
        case let .listening(partial):
            VStack(spacing: 8) {
                Text(partial.isEmpty ? "Listening…" : partial)
                    .font(.title3)
                    .foregroundStyle(partial.isEmpty ? .secondary : .primary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .animation(.easeOut(duration: 0.15), value: partial)
            }
            .frame(maxHeight: .infinity)

        case .awaitingAnswer:
            VStack(spacing: 10) {
                ProgressView().controlSize(.small)
                Text(env.chat.messages.last?.activityLabel ?? "Thinking…")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            .frame(maxHeight: .infinity)

        case .speaking:
            if let text = env.speechHighlight.utteranceText {
                KaraokeReadingText(
                    text: text,
                    timeline: env.speechHighlight.timeline,
                    currentWordRange: env.speechHighlight.currentWordRange
                )
                .accessibilityLabel("M1K3 is speaking")
            } else {
                Spacer()
                    .accessibilityLabel("M1K3 is speaking")
            }

        case .idle:
            VStack(spacing: 8) {
                Text("Click the mic or press Space to talk")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                if let error = env.voiceLoop?.lastError {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.orange)
                        .multilineTextAlignment(.center)
                }
            }
            .frame(maxHeight: .infinity)

        case .ended, nil:
            Spacer().accessibilityHidden(true) // transient teardown frame
        }
    }

    // MARK: - Chrome

    private var controlBar: some View {
        HStack(spacing: 16) {
            Button(action: micAction) {
                Image(systemName: micSymbol)
                    .imageScale(.large)
                    .fontWeight(.semibold)
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .tint(isListening ? .red : nil)
            .help(isListening ? "Stop listening" : "Start listening")
            .accessibilityLabel(isListening ? "Mute microphone" : "Start listening")

            Button {
                voiceThinking.toggle()
            } label: {
                Image(systemName: "brain")
                    .imageScale(.large)
                    .fontWeight(.semibold)
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .tint(voiceThinking ? .purple : nil)
            .help(voiceThinking
                ? "Thinking on — deeper answers, slower. Applies next turn"
                : "Thinking off — fast replies. Applies next turn")
            .accessibilityLabel("Thinking")
            .accessibilityValue(voiceThinking ? "On" : "Off")
            .accessibilityAddTraits(.isToggle)

            Button {
                env.exitVoiceMode()
            } label: {
                Image(systemName: "xmark")
                    .imageScale(.large)
                    .fontWeight(.semibold)
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .help("Leave voice mode (Esc)")
            .accessibilityLabel("Leave voice mode")
        }
        .padding(.bottom, 4)
    }

    // MARK: - Intents

    private var isListening: Bool {
        if case .listening = env.voiceLoop?.state { return true }
        return false
    }

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

    /// Click/Space: wake from idle, or barge in while M1K3 speaks.
    private func primaryAction() {
        switch env.voiceLoop?.state {
        case .idle: env.voiceLoop?.begin()
        case .speaking: env.voiceLoop?.interrupt()
        default: break // listening/thinking: no-op (v1)
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
        case .speaking, .ended, nil:
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
