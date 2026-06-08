//
//  SpeechProvider.swift
//  M1K3Voice
//
//  The TTS seam. AVSpeechProvider (Apple, native, zero-dep) backs the MVP; a
//  KokoroSpeechProvider bridging M1K3's existing Python Kokoro engine swaps in
//  post-MVP — same protocol, so the avatar lip-sync and chat read-aloud don't
//  change. Mirrors the pluggability of InferenceProvider.
//
//  SpeechUtterance is a pure value type (clamped ranges, no AVFoundation), so
//  the request-shaping logic is testable without audio hardware.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation

/// A request to speak text, with normalised prosody. Ranges match AVSpeech's
/// (rate 0…1, pitch 0.5…2.0) but carry no framework dependency — the adapter
/// maps these straight through.
public struct SpeechUtterance: Sendable, Equatable {
    public static let minRate: Float = 0.0
    public static let maxRate: Float = 1.0
    public static let defaultRate: Float = 0.5
    public static let minPitch: Float = 0.5
    public static let maxPitch: Float = 2.0
    public static let defaultPitch: Float = 1.0

    public let text: String
    public let rate: Float
    public let pitch: Float
    /// Optional platform voice identifier; nil uses the system default voice.
    public let voiceIdentifier: String?

    public init(
        text: String,
        rate: Float = defaultRate,
        pitch: Float = defaultPitch,
        voiceIdentifier: String? = nil
    ) {
        self.text = text
        self.rate = min(max(rate, Self.minRate), Self.maxRate)
        self.pitch = min(max(pitch, Self.minPitch), Self.maxPitch)
        self.voiceIdentifier = voiceIdentifier
    }
}

public protocol SpeechProvider: Sendable {
    /// Stable identifier for routing/UI.
    var name: String { get }
    /// Whether the backend can speak right now.
    var isAvailable: Bool { get }
    /// Enqueue an utterance for speech.
    func speak(_ utterance: SpeechUtterance) async
    /// Stop any in-progress and queued speech immediately.
    func stop() async
    /// Whether speech is currently playing.
    func isSpeaking() async -> Bool
}

public extension SpeechProvider {
    /// Convenience: speak plain text with default prosody.
    func speak(_ text: String) async {
        await speak(SpeechUtterance(text: text))
    }
}

/// A speech backend that reports when synthesis starts and stops. The avatar's
/// speaking-state animation hangs off these. Class-bound so a façade
/// (SwappableSpeechProvider) can re-apply the callbacks onto whichever concrete
/// provider is active after a tier swap.
public protocol SpeechProviderWithLifecycle: SpeechProvider, AnyObject {
    /// Invoked on the main thread when synthesis begins.
    var onSpeakingStarted: (@Sendable () -> Void)? { get set }
    /// Invoked on the main thread when synthesis finishes or is stopped.
    var onSpeakingEnded: (@Sendable () -> Void)? { get set }
}
