//
//  VoiceDock.swift
//  M1K3App
//
//  Voice-first as a non-exclusive DOCK over the live chat — not a full-window
//  body-swap. A compact glass card pinned to the bottom (via the chat's
//  `safeAreaInset`) holds the living face, the karaoke Focus-reader for the
//  spoken line, and the mic / think / leave controls. The transcript stays
//  visible and scrollable ABOVE it, so you see the conversation AND M1K3
//  talking. Supersedes the full-window VoiceModeView; the SAME
//  VoiceLoopController + speechHighlight drive it, so this is recomposition,
//  not new behaviour.
//
//  ESC leaves · Space or a tap on the FACE begins / barges in (no-op while
//  thinking, v1) · the mic button parks/wakes the loop. Barge-in lives on the
//  face only (not the whole card) so it can never swallow taps meant for the
//  chat bubbles above — and the dock is a bottom inset, so it doesn't overlap
//  the transcript at all.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.75 (thin view over the
//  tested loop; the dock layout + feel are verify-at-⌘R — the taste gate Kev
//  owns). Prior: Kev + claude-fable-5 (VoiceModeView, the full-window forebear).

import M1K3Avatar
import M1K3Voice
import SwiftUI

struct VoiceDock: View {
    @Environment(AppEnvironment.self) private var env
    /// Voice mode's own brain switch — replaces the Settings Reasoning picker
    /// while the loop is active. Off (default) = fast replies; flips apply from
    /// the next turn (the provider reads it per turn).
    @AppStorage(AppEnvironment.voiceModeThinkingKey) private var voiceThinking = false
    /// The dock must CLAIM first-responder on appear, or Space/Esc go nowhere:
    /// unlike the full-window forebear (which got focus by replacing everything),
    /// the dock coexists with the live transcript ScrollView, so `.focusable()`
    /// alone doesn't route key presses here until something grants focus.
    @FocusState private var dockFocused: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            AvatarSurface(env: env)
                .frame(width: 92, height: 92)
                // Barge-in on the FACE only — the card mustn't capture taps meant
                // for the chat bubbles (they live above, but keep the surface tight).
                .contentShape(Rectangle())
                .onTapGesture(perform: primaryAction)
                .accessibilityLabel("M1K3")
                .accessibilityHint("Tap to start talking, or to interrupt")

            stateContent
                .frame(maxWidth: .infinity, alignment: .leading)

            controlBar
        }
        .padding(14)
        .glassEffect(.regular, in: .rect(cornerRadius: 24))
        .frame(maxWidth: 720)
        .frame(maxWidth: .infinity, alignment: .center) // centre the capped card
        .padding(.horizontal, 12)
        .padding(.bottom, 10)
        .onKeyPress(.space) {
            primaryAction()
            return .handled
        }
        .onExitCommand { env.exitVoiceMode() }
        .focusable()
        .focused($dockFocused)
        .onAppear { dockFocused = true } // grab first-responder so Space/Esc fire
        .onChange(of: env.voiceLoop?.state) { _, newState in
            syncAvatar(with: newState)
        }
        .onChange(of: env.chat.messages.last?.text) {
            bumpToGeneratingIfStreaming()
        }
    }

    // MARK: - State content (compact: the karaoke line / live partial / status)

    @ViewBuilder
    private var stateContent: some View {
        switch env.voiceLoop?.state {
        case let .listening(partial):
            Text(partial.isEmpty ? "Listening…" : partial)
                .font(.callout)
                .foregroundStyle(partial.isEmpty ? .secondary : .primary)
                .lineLimit(3)
                .animation(.easeOut(duration: 0.15), value: partial)

        case .awaitingAnswer:
            HStack(spacing: 8) {
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
                // The dyslexia Focus-reader — the same component the full-window
                // mode used; in the dock its pane is short (the conversation is the
                // hero), so it scrolls within a bounded height.
                KaraokeReadingText(
                    text: text,
                    timeline: env.speechHighlight.timeline,
                    currentWordRange: env.speechHighlight.currentWordRange
                )
                .frame(maxHeight: 92)
                .accessibilityLabel("M1K3 is speaking")
            } else {
                Text("Speaking…")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("M1K3 is speaking")
            }

        case .idle:
            VStack(alignment: .leading, spacing: 4) {
                Text("Tap the face or press Space to talk")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                if let error = env.voiceLoop?.lastError {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.orange)
                        .lineLimit(2)
                }
            }

        case .ended, .none:
            Text("")
                .accessibilityHidden(true) // transient teardown frame
        }
    }

    // MARK: - Controls (mic / think / leave) — the loop's own intents

    private var controlBar: some View {
        HStack(spacing: 12) {
            Button(action: micAction) {
                Image(systemName: micSymbol)
                    .imageScale(.large).fontWeight(.semibold)
                    .frame(width: 22, height: 22)
            }
            .buttonStyle(.glass).buttonBorderShape(.circle)
            .tint(isListening ? .red : nil)
            .help(isListening ? "Stop listening" : "Start listening")
            .accessibilityLabel(isListening ? "Mute microphone" : "Start listening")

            Button { voiceThinking.toggle() } label: {
                Image(systemName: "brain")
                    .imageScale(.large).fontWeight(.semibold)
                    .frame(width: 22, height: 22)
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
                    .frame(width: 22, height: 22)
            }
            .buttonStyle(.glass).buttonBorderShape(.circle)
            .help("Leave voice mode (Esc)")
            .accessibilityLabel("Leave voice mode")
        }
    }

    // MARK: - Intents (lifted from the full-window forebear)

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
