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

public final class AVSpeechProvider: NSObject, SpeechProvider, @unchecked Sendable {
    public let name = "av-speech"

    private let synthesizer = AVSpeechSynthesizer()

    /// Called on the main thread when synthesis starts. Set by AppEnvironment to
    /// drive the avatar into the .speaking activity state.
    public var onSpeakingStarted: (@Sendable () -> Void)?
    /// Called on the main thread when synthesis finishes or is stopped.
    public var onSpeakingEnded: (@Sendable () -> Void)?

    override public init() {
        super.init()
        synthesizer.delegate = self
    }

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

// MARK: - AVSpeechSynthesizerDelegate

// Delegate methods are delivered on the main queue by AVFoundation.

extension AVSpeechProvider: AVSpeechSynthesizerDelegate {
    public nonisolated func speechSynthesizer(
        _: AVSpeechSynthesizer,
        didStart _: AVSpeechUtterance
    ) {
        let cb = onSpeakingStarted
        Task { @MainActor in cb?() }
    }

    public nonisolated func speechSynthesizer(
        _: AVSpeechSynthesizer,
        didFinish _: AVSpeechUtterance
    ) {
        let cb = onSpeakingEnded
        Task { @MainActor in cb?() }
    }

    public nonisolated func speechSynthesizer(
        _: AVSpeechSynthesizer,
        didCancel _: AVSpeechUtterance
    ) {
        let cb = onSpeakingEnded
        Task { @MainActor in cb?() }
    }
}
