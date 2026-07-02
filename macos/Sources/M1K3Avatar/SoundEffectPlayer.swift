//
//  SoundEffectPlayer.swift
//  M1K3Avatar
//
//  Plays the UI earcons, behind two gates: the user's on/off preference and
//  "is M1K3 talking right now" (an earcon must never step on the voice). The
//  POLICY is a pure, fully-tested decision; the actual playback is an injected
//  sink — AVAudioPlayer in production (verify-by-launch), a recorder in tests.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.85 (gate + dispatch
//  test-pinned; AVAudioPlayer pool is verify-at-⌘R). Prior: Unknown.
//  Review: Kev + claude-fable-5, 2026-07-02 — removed the unused
//  SoundEffectPlaying protocol (nothing in the repo typed against it; sink
//  injection is the test seam, and the app holds the concrete player).
//

import AVFoundation
import Foundation
import os

/// The pure play decision: earcons sound only when enabled AND M1K3 isn't
/// mid-speech. One place, exhaustively tested, so the player stays trivial.
public enum SoundGate {
    public static func allows(enabled: Bool, isSpeaking: Bool) -> Bool {
        enabled && !isSpeaking
    }
}

@MainActor
public final class SoundEffectPlayer {
    /// The user's Settings preference, read live so a toggle applies at once.
    public var isEnabled: Bool
    private let isSpeaking: @MainActor () -> Bool
    private let sink: @MainActor (SoundEffect) -> Void

    /// Designated init — `sink` performs the actual playback. Injectable so the
    /// policy is testable without touching CoreAudio.
    public init(
        isEnabled: Bool,
        isSpeaking: @escaping @MainActor () -> Bool,
        sink: @escaping @MainActor (SoundEffect) -> Void
    ) {
        self.isEnabled = isEnabled
        self.isSpeaking = isSpeaking
        self.sink = sink
    }

    public func play(_ effect: SoundEffect) {
        guard SoundGate.allows(enabled: isEnabled, isSpeaking: isSpeaking()) else { return }
        sink(effect)
    }
}

public extension SoundEffectPlayer {
    /// Production player: preloads the bundled WAVs into a pool of prepared
    /// AVAudioPlayers and plays via CoreAudio. Independent of the voice
    /// AVAudioEngine — the system mixer composes them.
    static func bundled(
        isEnabled: Bool,
        volume: Float = 0.6,
        isSpeaking: @escaping @MainActor () -> Bool
    ) -> SoundEffectPlayer {
        let pool = AVAudioEarconPool(volume: volume)
        return SoundEffectPlayer(isEnabled: isEnabled, isSpeaking: isSpeaking) { effect in
            pool.play(effect)
        }
    }
}

/// Thin AVAudioPlayer pool — one prepared player per effect, restarted on each
/// play (overlap isn't needed for these brief, infrequent earcons). All on the
/// main actor; verify-at-⌘R.
@MainActor
private final class AVAudioEarconPool {
    private static let log = Logger(subsystem: "app.m1k3", category: "sfx")
    private var players: [SoundEffect: AVAudioPlayer] = [:]

    init(volume: Float) {
        for effect in SoundEffect.allCases {
            guard let url = SoundEffectAssets.url(for: effect) else {
                Self.log.error("earcon WAV missing for \(effect.rawValue, privacy: .public)")
                continue
            }
            do {
                let player = try AVAudioPlayer(contentsOf: url)
                player.volume = volume
                player.prepareToPlay()
                players[effect] = player
            } catch {
                Self.log.error("earcon load failed for \(effect.rawValue, privacy: .public): \(error, privacy: .public)")
            }
        }
    }

    func play(_ effect: SoundEffect) {
        guard let player = players[effect] else { return }
        player.currentTime = 0
        player.play()
    }
}
