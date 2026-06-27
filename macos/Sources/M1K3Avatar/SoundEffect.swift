//
//  SoundEffect.swift
//  M1K3Avatar
//
//  M1K3's UI earcons — short, playful sounds for a few key moments (an error,
//  a memory saved, voice mode coming alive). Deliberately a SMALL set: sound is
//  delight in small doses and noise in large ones. Each effect maps to one
//  bundled WAV; "bundled == playable" is pinned by SoundEffectTests, the same
//  discipline as the companion clips.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.85 (catalogue +
//  bundling test-pinned; the WAV choices are by-ear, swap a line to retune).
//  Prior: Unknown (sounds salvaged from the Python-era ./sounds/ library).
//

import Foundation

/// A named UI sound. The raw value is stable identity; `resourceName` is the
/// bundled WAV that voices it (decoupled so a sound can be re-cast without
/// touching call sites).
public enum SoundEffect: String, CaseIterable, Sendable {
    /// Something failed — a turn errored, a tool died.
    case error
    /// A durable memory was written (the MCP `remember` confirmation).
    case save
    /// Voice mode came alive — M1K3 materialising.
    case voiceEnter

    /// The bundled WAV (in `SoundEffects/`) that voices this effect.
    var resourceName: String {
        switch self {
        case .error: "badBoing"
        case .save: "coin"
        case .voiceEnter: "materialise"
        }
    }
}

/// Locates the bundled WAVs. Mirrors `CompanionAssets` — resources are copied
/// verbatim into `Bundle.module` under `SoundEffects/`.
public enum SoundEffectAssets {
    public static func url(for effect: SoundEffect) -> URL? {
        Bundle.module.url(
            forResource: effect.resourceName,
            withExtension: "wav",
            subdirectory: "SoundEffects"
        )
    }

    /// True when every catalogue entry has its WAV — the call sites can never
    /// name an unbundled sound (asserted in tests, like the companion clips).
    public static var allInstalled: Bool {
        SoundEffect.allCases.allSatisfy { url(for: $0) != nil }
    }
}
