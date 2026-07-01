//
//  SpeakingState.swift
//  M1K3Voice
//
//  The cached speaking flag behind EffectfulSpeechProvider.isSpeaking(). It is
//  flipped by begin()/end() at the SAME lifecycle seams that fire
//  onSpeakingStarted/onSpeakingEnded (via the provider's fireSpeaking* helpers),
//  so it can never drift from the callbacks. isSpeaking() reads this instead of
//  polling player.isPlaying / synthesizer.isSpeaking — those take an internal
//  AVAudioEngine lock held by the Default-QoS render thread, so a high-QoS caller
//  polling them at ~30 fps was a priority-inversion hang risk (Thread Performance
//  Checker). A pure value type: no AVFoundation reach, so the transition logic is
//  unit-tested while the wiring stays verify-by-launch.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.9, Prior: Unknown

/// Tracks whether the effect-voice provider is audibly speaking, without asking
/// the audio engine.
struct SpeakingState: Equatable {
    private(set) var playbackActive = false

    mutating func begin() {
        playbackActive = true
    }

    mutating func end() {
        playbackActive = false
    }

    /// The answer isSpeaking() returns: an active playback, OR a live streaming
    /// session that has not scheduled its first buffer yet (`streamingActive`).
    func isSpeaking(streamingActive: Bool) -> Bool {
        playbackActive || streamingActive
    }
}
