//
//  SoundEffectPlayerTests.swift
//  M1K3AvatarTests
//
//  The play POLICY — fully unit-tested via an injected sink (the AVAudioPlayer
//  pool is the production sink, verify-by-launch). Earcons must never play when
//  the user turned them off, and never step on M1K3's own voice.
//

@testable import M1K3Avatar
import Testing

@MainActor
private final class Recorder {
    var played: [SoundEffect] = []
    func sink(_ effect: SoundEffect) {
        played.append(effect)
    }
}

struct SoundGateTests {
    @Test("plays only when enabled AND not mid-speech")
    func truthTable() {
        #expect(SoundGate.allows(enabled: true, isSpeaking: false))
        #expect(!SoundGate.allows(enabled: false, isSpeaking: false))
        #expect(!SoundGate.allows(enabled: true, isSpeaking: true))
        #expect(!SoundGate.allows(enabled: false, isSpeaking: true))
    }
}

@MainActor
struct SoundEffectPlayerTests {
    @Test("plays through the sink when enabled and silent")
    func playsWhenEnabled() {
        let rec = Recorder()
        let player = SoundEffectPlayer(isEnabled: true, isSpeaking: { false }, sink: rec.sink)
        player.play(.save)
        #expect(rec.played == [.save])
    }

    @Test("disabled is a no-op")
    func disabledNoOp() {
        let rec = Recorder()
        let player = SoundEffectPlayer(isEnabled: false, isSpeaking: { false }, sink: rec.sink)
        player.play(.error)
        #expect(rec.played.isEmpty)
    }

    @Test("never plays over M1K3's voice")
    func mutedDuringSpeech() {
        let rec = Recorder()
        let player = SoundEffectPlayer(isEnabled: true, isSpeaking: { true }, sink: rec.sink)
        player.play(.voiceEnter)
        #expect(rec.played.isEmpty)
    }

    @Test("toggling isEnabled at runtime takes effect on the next play")
    func runtimeToggle() {
        let rec = Recorder()
        let player = SoundEffectPlayer(isEnabled: false, isSpeaking: { false }, sink: rec.sink)
        player.play(.save)
        player.isEnabled = true
        player.play(.save)
        #expect(rec.played == [.save])
    }
}
