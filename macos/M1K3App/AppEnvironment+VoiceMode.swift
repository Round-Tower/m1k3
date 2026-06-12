//
//  AppEnvironment+VoiceMode.swift
//  M1K3
//
//  Voice-first mode's adapter: builds the VoiceLoopController against the
//  app's existing seams. `runTurn` wraps `chat.send` DIRECTLY (not env.send —
//  its idle-reset would fight the loop's avatar ownership) and reads the
//  answer off `chat.messages.last`. The mic closure caches the provider
//  instance it started (mirroring `dictationProvider`) so stop hits the same
//  engine. While the mode is active, the in-mode brain toggle replaces the
//  global Reasoning setting entirely — off (default) forces fast even over an
//  explicit Always, on yields auto (see VoiceThinkingPolicy).
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.8 (adapter glue over
//  test-pinned loop + seams; verify-at-⌘R for the full beat). Prior: Unknown.
//

import Foundation
import M1K3Voice

extension AppEnvironment {
    /// Transient flag consulted by thinkingModeProvider (voice mode swaps the
    /// global Reasoning setting for the in-mode thinking toggle).
    nonisolated static let voiceModeActiveKey = "voiceMode.active"

    /// Persisted voice-mode thinking toggle (default off = fast replies).
    /// While voice mode is active this REPLACES the Settings Reasoning picker
    /// (see VoiceThinkingPolicy). VoiceModeView's brain button writes it.
    nonisolated static let voiceModeThinkingKey = "voiceMode.thinking"

    /// Persisted voice-mode avatar choice. Empty string (default) = the pixel face;
    /// otherwise a CompanionSpec id (e.g. "Fox"). The picker writes it; VoiceModeView
    /// reads it. The pixel face stays M1K3's default everywhere else.
    nonisolated static let voiceCompanionKey = "voiceMode.companion"

    /// One-time wiring (from init): speech lifecycle drives the avatar's speaking
    /// state, and the word-timing callbacks feed the karaoke highlight. Lives here
    /// (with speak/stopSpeaking) so the swap façade re-applies everything onto
    /// whichever tier is active and AppEnvironment.swift stays under its ceilings.
    func wireSpeechCallbacks() {
        speech.onSpeakingStarted = { [weak self] in
            Task { @MainActor [weak self] in self?.avatar.setActivity(.speaking) }
        }
        speech.onSpeakingEnded = { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                speechHighlight.clear()
                if let voiceLoop {
                    // The loop owns the next state (auto-relisten) — no idle flash.
                    voiceLoop.speechDidEnd()
                } else {
                    avatar.resetToIdle()
                }
            }
        }
        speech.onTimelineReady = { [weak self] timeline in
            Task { @MainActor [weak self] in self?.speechHighlight.apply(timeline: timeline) }
        }
        speech.onWordSpoken = { [weak self] range in
            Task { @MainActor [weak self] in self?.speechHighlight.wordSpoken(range) }
        }
    }

    /// Speak text via the TTS provider. The onSpeakingStarted/Ended delegate
    /// callbacks drive avatar .speaking → .idle; no manual state change needed here.
    ///
    /// Text is sanitized for speech (URLs → hosts, citation tokens and the
    /// Web-sources block dropped) BEFORE the providers see it, so every
    /// downstream word timeline is built against the same string the karaoke
    /// view displays.
    func speak(_ text: String) async {
        let polished = SpeechTextPolish.polish(text)
        // A message that is ONLY a sources block polishes to empty; never hand
        // providers "" — the voice loop waits on a speechDidEnd that would
        // not arrive.
        let spoken = polished.isEmpty ? text : polished
        speechHighlight.beginUtterance(text: spoken)
        await speech.speak(spoken)
    }

    func stopSpeaking() async {
        await speech.stop()
        avatar.resetToIdle()
        speechHighlight.clear()
    }

    /// Launch-time hygiene (from init): voice mode is never restored across
    /// launches, so a stale `true` here — a crash/force-quit while the mode was
    /// active — would silently force fast-mode thinking on every normal chat
    /// turn until the user happened to enter and leave voice mode again.
    nonisolated static func resetVoiceModeFlagAtLaunch() {
        UserDefaults.standard.set(false, forKey: voiceModeActiveKey)
    }

    var isVoiceModeActive: Bool {
        voiceLoop != nil
    }

    /// Enter voice-first mode and start listening. Refuses while a typed turn
    /// is streaming or chat-mode dictation is live (the toolbar button is
    /// disabled in both states — this guard is the belt to that suspender).
    func enterVoiceMode() {
        guard voiceLoop == nil, !chat.isResponding, !isListening else { return }
        UserDefaults.standard.set(true, forKey: Self.voiceModeActiveKey)
        let controller = VoiceLoopController(dependencies: makeVoiceLoopDependencies())
        voiceLoop = controller
        controller.begin()
    }

    /// Leave the mode: tears down mic + speech. An in-flight turn is NOT
    /// cancelled — its answer still lands in the chat transcript, unspoken.
    func exitVoiceMode() {
        guard let controller = voiceLoop else { return }
        controller.exit()
        voiceLoop = nil
        UserDefaults.standard.set(false, forKey: Self.voiceModeActiveKey)
        avatar.resetToIdle()
        speechHighlight.clear()
    }

    private func makeVoiceLoopDependencies() -> VoiceLoopController.Dependencies {
        // Cache the recogniser each listen started, so stop hits that engine
        // even if the router's preference changes mid-session.
        var activeProvider: (any TranscriptionProvider)?
        return VoiceLoopController.Dependencies(
            startListening: { [weak self] in
                guard let provider = self?.transcription.activeProvider else {
                    throw VoiceTurnFailure(message: "No speech recogniser is available.")
                }
                let stream = try provider.startListening()
                activeProvider = provider
                return stream
            },
            stopListening: {
                activeProvider?.stopListening()
                activeProvider = nil
            },
            runTurn: { [weak self] question in
                guard let self else {
                    return .failure(VoiceTurnFailure(message: "M1K3 is shutting down."))
                }
                await chat.send(question)
                guard let last = chat.messages.last, last.role == .assistant else {
                    return .failure(VoiceTurnFailure(message: "No answer arrived."))
                }
                if case let .failed(message) = last.status {
                    return .failure(VoiceTurnFailure(message: message))
                }
                guard !last.text.isEmpty else {
                    return .failure(VoiceTurnFailure(message: "The model had nothing to say."))
                }
                return .success(last.text)
            },
            speak: { [weak self] answer in
                await self?.speak(answer)
            },
            stopSpeaking: { [weak self] in
                // The highlight clears via wireSpeechCallbacks' onSpeakingEnded
                // (stop() guarantees exactly one); exitVoiceMode covers the
                // not-speaking case. No clear needed here.
                await self?.speech.stop()
            }
        )
    }
}
