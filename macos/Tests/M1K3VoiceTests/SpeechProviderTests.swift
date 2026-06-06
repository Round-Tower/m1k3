//
//  SpeechProviderTests.swift
//  M1K3VoiceTests
//
//  SpeechUtterance clamping (pure), the protocol via a recording fake, and the
//  AVSpeechProvider's stable identity/availability. The adapter's audio output
//  isn't exercised (no hardware in tests).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Voice
import Testing

// MARK: - SpeechUtterance (pure)

struct SpeechUtteranceTests {
    @Test("defaults sit in range")
    func defaults() {
        let u = SpeechUtterance(text: "hi")
        #expect(u.rate == SpeechUtterance.defaultRate)
        #expect(u.pitch == SpeechUtterance.defaultPitch)
        #expect(u.voiceIdentifier == nil)
    }

    @Test("rate is clamped to 0...1")
    func rateClamped() {
        #expect(SpeechUtterance(text: "x", rate: 5).rate == SpeechUtterance.maxRate)
        #expect(SpeechUtterance(text: "x", rate: -1).rate == SpeechUtterance.minRate)
    }

    @Test("pitch is clamped to 0.5...2.0")
    func pitchClamped() {
        #expect(SpeechUtterance(text: "x", pitch: 9).pitch == SpeechUtterance.maxPitch)
        #expect(SpeechUtterance(text: "x", pitch: 0).pitch == SpeechUtterance.minPitch)
    }

    @Test("in-range values pass through unchanged")
    func passThrough() {
        let u = SpeechUtterance(text: "x", rate: 0.6, pitch: 1.2, voiceIdentifier: "com.apple.voice")
        #expect(u.rate == 0.6)
        #expect(u.pitch == 1.2)
        #expect(u.voiceIdentifier == "com.apple.voice")
    }
}

// MARK: - Protocol via a recording fake

private actor RecordingSpeech: SpeechProvider {
    nonisolated let name = "recording"
    nonisolated var isAvailable: Bool {
        true
    }

    private(set) var spoken: [SpeechUtterance] = []
    private var speaking = false

    func speak(_ utterance: SpeechUtterance) async {
        spoken.append(utterance)
        speaking = true
    }

    func stop() async {
        speaking = false
    }

    func isSpeaking() async -> Bool {
        speaking
    }
}

struct SpeechProviderProtocolTests {
    @Test("the plain-text convenience wraps text with default prosody")
    func textConvenience() async {
        let provider = RecordingSpeech()
        await provider.speak("hello world")
        let spoken = await provider.spoken
        #expect(spoken.count == 1)
        #expect(spoken.first?.text == "hello world")
        #expect(spoken.first?.rate == SpeechUtterance.defaultRate)
    }

    @Test("speak then stop toggles isSpeaking")
    func speakStop() async {
        let provider = RecordingSpeech()
        #expect(await provider.isSpeaking() == false)
        await provider.speak("talk")
        #expect(await provider.isSpeaking() == true)
        await provider.stop()
        #expect(await provider.isSpeaking() == false)
    }
}

// MARK: - AVSpeechProvider (identity only)

struct AVSpeechProviderTests {
    @Test("has the expected stable name and is available")
    func identity() {
        let provider = AVSpeechProvider()
        #expect(provider.name == "av-speech")
        #expect(provider.isAvailable)
    }

    @Test("conforms to SpeechProvider")
    func conforms() {
        let provider: any SpeechProvider = AVSpeechProvider()
        #expect(provider.name == "av-speech")
    }
}
