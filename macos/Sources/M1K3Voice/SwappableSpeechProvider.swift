//
//  SwappableSpeechProvider.swift
//  M1K3Voice
//
//  A SpeechProvider façade whose backing engine can change at runtime, so
//  switching the chosen voice tier (Built-in AVSpeech ↔ M1K3 Voice / Kokoro)
//  re-points TTS without rebuilding the AppEnvironment callers that hold it.
//  Mirrors SwappableInferenceProvider / SwappableEmbeddingService.
//
//  It also owns the canonical onSpeakingStarted/Ended callbacks (which drive the
//  avatar's speaking animation) and re-applies them onto whichever concrete
//  provider is active after a swap — so the avatar keeps reacting across a tier
//  change without the caller re-wiring anything.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.85, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-11 — stores + re-applies the word-timing
//  callbacks on swap, same pattern as lifecycle. Confidence 0.9.

import Foundation

public final class SwappableSpeechProvider: SpeechProviderWithWordTiming, @unchecked Sendable {
    public let name = "swappable-speech"

    private let lock = NSLock()
    private var current: any SpeechProvider
    private var startedCallback: (@Sendable () -> Void)?
    private var endedCallback: (@Sendable () -> Void)?
    private var timelineCallback: (@Sendable (SpokenWordTimeline) -> Void)?
    private var wordCallback: (@Sendable (Range<Int>) -> Void)?

    public init(_ initial: any SpeechProvider) {
        current = initial
    }

    /// The currently-active backend.
    public var active: any SpeechProvider {
        lock.withLock { current }
    }

    /// Swap the backing engine. The lifecycle callbacks are re-applied to the new
    /// provider so the avatar keeps reacting to speech after the swap.
    public func setProvider(_ provider: any SpeechProvider) {
        lock.withLock {
            current = provider
            applyCallbacksLocked()
        }
    }

    public var onSpeakingStarted: (@Sendable () -> Void)? {
        get { lock.withLock { startedCallback } }
        set { lock.withLock { startedCallback = newValue; applyCallbacksLocked() } }
    }

    public var onSpeakingEnded: (@Sendable () -> Void)? {
        get { lock.withLock { endedCallback } }
        set { lock.withLock { endedCallback = newValue; applyCallbacksLocked() } }
    }

    public var onTimelineReady: (@Sendable (SpokenWordTimeline) -> Void)? {
        get { lock.withLock { timelineCallback } }
        set { lock.withLock { timelineCallback = newValue; applyCallbacksLocked() } }
    }

    public var onWordSpoken: (@Sendable (Range<Int>) -> Void)? {
        get { lock.withLock { wordCallback } }
        set { lock.withLock { wordCallback = newValue; applyCallbacksLocked() } }
    }

    /// Forward the stored callbacks onto the active provider if it reports
    /// lifecycle (and word timing — a lifecycle-only tier simply never emits
    /// timing, so the karaoke view stays inert there). Caller must hold `lock`.
    private func applyCallbacksLocked() {
        if let lifecycle = current as? SpeechProviderWithLifecycle {
            lifecycle.onSpeakingStarted = startedCallback
            lifecycle.onSpeakingEnded = endedCallback
        }
        if let timing = current as? SpeechProviderWithWordTiming {
            timing.onTimelineReady = timelineCallback
            timing.onWordSpoken = wordCallback
        }
    }

    public var isAvailable: Bool {
        active.isAvailable
    }

    public func speak(_ utterance: SpeechUtterance) async {
        await active.speak(utterance)
    }

    public func stop() async {
        await active.stop()
    }

    public func isSpeaking() async -> Bool {
        await active.isSpeaking()
    }
}
