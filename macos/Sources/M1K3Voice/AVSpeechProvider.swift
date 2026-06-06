//
//  AVSpeechProvider.swift
//  M1K3Voice
//
//  Native TTS via AVSpeechSynthesizer (AVFoundation — ships with macOS, no
//  third-party dependency). The MVP voice. Thin OS adapter: the synthesizer is
//  touched only on the main actor, so the @unchecked Sendable is sound. The
//  testable shaping lives in SpeechUtterance; this file is verified by name +
//  conformance, not by emitting audio.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import AVFoundation
import Foundation

public final class AVSpeechProvider: SpeechProvider, @unchecked Sendable {
    public let name = "av-speech"

    private let synthesizer = AVSpeechSynthesizer()

    public init() {}

    public nonisolated var isAvailable: Bool {
        true
    }

    public func speak(_ utterance: SpeechUtterance) async {
        await MainActor.run {
            let spoken = AVSpeechUtterance(string: utterance.text)
            spoken.rate = utterance.rate
            spoken.pitchMultiplier = utterance.pitch
            if let id = utterance.voiceIdentifier,
               let voice = AVSpeechSynthesisVoice(identifier: id)
            {
                spoken.voice = voice
            }
            synthesizer.speak(spoken)
        }
    }

    public func stop() async {
        await MainActor.run {
            _ = synthesizer.stopSpeaking(at: .immediate)
        }
    }

    public func isSpeaking() async -> Bool {
        await MainActor.run { synthesizer.isSpeaking }
    }
}
