//
//  SoundEffectTests.swift
//  M1K3AvatarTests
//
//  The earcon catalogue + bundling contract: every effect the app can fire
//  must resolve to a bundled WAV (same "bundled == playable" discipline as the
//  companion clips). The AVAudioPlayer playback itself is verify-by-launch.
//

@testable import M1K3Avatar
import Testing

struct SoundEffectTests {
    @Test("the catalogue is the earcon set plus the dial-up loop")
    func catalogue() {
        #expect(Set(SoundEffect.allCases) == [.error, .save, .voiceEnter, .dialup])
    }

    @Test("each effect names a distinct resource")
    func resourceNamesDistinct() {
        let names = SoundEffect.allCases.map(\.resourceName)
        #expect(Set(names).count == names.count)
    }

    @Test("every effect resolves to a bundled WAV — bundled == playable")
    func everyEffectBundled() {
        for effect in SoundEffect.allCases {
            #expect(SoundEffectAssets.url(for: effect) != nil, "no bundled WAV for \(effect)")
        }
        #expect(SoundEffectAssets.allInstalled)
    }
}
