//
//  VoiceEffect.swift
//  M1K3Voice
//
//  A pluggable audio-effect pipeline — the Swift port of M1K3's Python voice-effects
//  engine (src/tts/effects/audio_effects.py). Pure DSP over [Float] sample buffers:
//  no AVFoundation, no hardware, fully unit-tested. The same chain processes Apple's
//  synthesized speech today and will sit downstream of Kokoro's neural PCM later, so
//  M1K3's voice carries a consistent character regardless of the engine underneath.
//
//  This is the reusable IP: the chain is a seam, effects plug in behind it, and the
//  "M1K3 character" is just one preset.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.8, Prior: Unknown

/// One stage in the voice pipeline. Transforms a mono sample buffer in place of
/// value semantics (returns a new buffer), given the sample rate for any
/// frequency-domain maths.
public protocol VoiceEffect: Sendable {
    var name: String { get }
    func apply(to samples: [Float], sampleRate: Double) -> [Float]
}

/// An ordered chain of effects. Running an empty chain is the identity.
public struct VoiceEffectChain: Sendable {
    public let effects: [any VoiceEffect]

    public init(_ effects: [any VoiceEffect]) {
        self.effects = effects
    }

    public var isEmpty: Bool {
        effects.isEmpty
    }

    /// Run the buffer through every effect in order.
    public func process(_ samples: [Float], sampleRate: Double) -> [Float] {
        effects.reduce(samples) { current, effect in
            effect.apply(to: current, sampleRate: sampleRate)
        }
    }
}

public extension VoiceEffectChain {
    /// The signature M1K3 voice: a band-limited intercom "transmitted" timbre, a
    /// touch of tremolo shimmer for machine character, then a gain-stage → soft-clip
    /// → final-trim tail that evens the level out and adds a little grit.
    ///
    /// Order matters, and specifically the normalise BEFORE the soft-clip does: the
    /// clip only acts on samples that reach its 0.6 threshold, so we normalise to
    /// full-scale FIRST to drive it into that range (otherwise it's a no-op and the
    /// stage does nothing — the bug this order fixes), then normalise again to 0.85
    /// for a consistent, headroom-safe output level across utterances. Tuned to read
    /// as "M1K3" without obscuring words; all values are ear-tunable constants.
    static var m1k3Character: VoiceEffectChain {
        VoiceEffectChain([
            BandpassEffect(lowFrequency: 320, highFrequency: 3600),
            TremoloEffect(rate: 22, depth: 0.12),
            NormalizationEffect(level: 1.0), // gain-stage INTO the soft-clip below
            CompressionEffect(threshold: 0.6, ratio: 0.4),
            NormalizationEffect(level: 0.85), // final, headroom-safe output level
        ])
    }
}
