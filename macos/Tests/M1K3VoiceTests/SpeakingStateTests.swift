//
//  SpeakingStateTests.swift
//  M1K3VoiceTests
//
//  The pure transition machine behind EffectfulSpeechProvider.isSpeaking().
//  Extracted so the speaking-state logic is verified off-device (the AVAudio
//  wiring that flips it is verify-by-launch) — and so isSpeaking() stops reading
//  player.isPlaying across a MainActor hop (the cross-QoS engine-lock the Thread
//  Performance Checker flagged as a hang risk).
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.9, Prior: Unknown

@testable import M1K3Voice
import Testing

struct SpeakingStateTests {
    @Test("begins inactive")
    func startsInactive() {
        #expect(SpeakingState().isSpeaking(streamingActive: false) == false)
    }

    @Test("begin marks it speaking")
    func beginActivates() {
        var state = SpeakingState()
        state.begin()
        #expect(state.isSpeaking(streamingActive: false) == true)
    }

    @Test("end clears it")
    func endDeactivates() {
        var state = SpeakingState()
        state.begin()
        state.end()
        #expect(state.isSpeaking(streamingActive: false) == false)
    }

    @Test("a live streaming session counts as speaking even before playback flips on")
    func streamingImpliesSpeaking() {
        // Covers the window where speak(stream:) has a session but hasn't scheduled
        // its first buffer yet (chunk 1 still synthesizing) — isSpeaking() must be
        // true so a concurrent speak() interrupts it.
        var state = SpeakingState()
        #expect(state.isSpeaking(streamingActive: true) == true)
        state.begin()
        state.end()
        #expect(state.isSpeaking(streamingActive: true) == true)
    }

    @Test("double begin / double end are idempotent")
    func idempotentTransitions() {
        var state = SpeakingState()
        state.begin()
        state.begin()
        #expect(state.isSpeaking(streamingActive: false) == true)
        state.end()
        state.end()
        #expect(state.isSpeaking(streamingActive: false) == false)
    }
}
