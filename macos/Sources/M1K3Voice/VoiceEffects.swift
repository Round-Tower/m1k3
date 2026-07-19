//
//  VoiceEffects.swift
//  M1K3Voice
//
//  Concrete VoiceEffect stages, ported from M1K3's Python audio-effects pipeline
//  (src/tts/effects/audio_effects.py). All pure DSP — deterministic, no hardware,
//  unit-tested.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.8, Prior: Unknown

import Foundation

/// Scales the buffer so its peak sits at `level` (0…1). Quietens nothing if the
/// buffer is already silent. Port of NormalizationEffect.
public struct NormalizationEffect: VoiceEffect {
    public let name = "normalization"
    public let level: Float

    public init(level: Float = 0.8) {
        self.level = min(max(level, 0), 1)
    }

    public func apply(to samples: [Float], sampleRate _: Double) -> [Float] {
        let peak = samples.map(abs).max() ?? 0
        guard peak > 0 else { return samples }
        let gain = level / peak
        return samples.map { $0 * gain }
    }
}

/// Instantaneous (per-sample) soft-clip: amplitudes above `threshold` have their
/// excess scaled by `ratio` — so `ratio` is the slope above the knee (0.4 ≈ a
/// 2.5:1 curve), not a conventional attack/release compressor. It shapes waveform
/// peaks (adding gentle harmonic "grit"), it does not track an envelope. Needs the
/// signal to actually reach `threshold` to do anything — the preset normalises up
/// into it first (see `m1k3Character`). Port of CompressionEffect.
public struct CompressionEffect: VoiceEffect {
    public let name = "compression"
    public let threshold: Float
    public let ratio: Float

    public init(threshold: Float = 0.6, ratio: Float = 0.3) {
        self.threshold = min(max(threshold, 0), 1)
        self.ratio = min(max(ratio, 0), 1)
    }

    public func apply(to samples: [Float], sampleRate _: Double) -> [Float] {
        samples.map { sample in
            let magnitude = abs(sample)
            guard magnitude > threshold else { return sample }
            let over = magnitude - threshold
            return (sample < 0 ? -1 : 1) * (threshold + over * ratio)
        }
    }
}

/// The "intercom"/telephone band that gives the voice its transmitted character —
/// a genuine band limit, not a scoop. Two cascaded RBJ biquads (Butterworth
/// Q = 1/√2): a high-pass at `lowFrequency` and a low-pass at `highFrequency`,
/// giving a flat-ish passband with 12 dB/oct skirts and near-unity gain THROUGH
/// the band. (The earlier single constant-skirt bandpass had peak gain ≈ Q ≈ 0.33,
/// i.e. it attenuated the whole band by ~10 dB — the level then had to be clawed
/// back downstream, and it starved the compressor that followed it.)
/// Port of IntercomEffect; each biquad is a stable Direct Form I IIR pass.
public struct BandpassEffect: VoiceEffect {
    public let name = "bandpass"
    public let lowFrequency: Double
    public let highFrequency: Double

    public init(lowFrequency: Double = 300, highFrequency: Double = 3400) {
        self.lowFrequency = lowFrequency
        self.highFrequency = highFrequency
    }

    public func apply(to samples: [Float], sampleRate: Double) -> [Float] {
        guard sampleRate > 0, !samples.isEmpty else { return samples }
        // Keep both corners inside (0, Nyquist) and low < high, so the coefficients
        // stay well-defined even for odd sample rates / mis-ordered inits.
        let nyquist = sampleRate / 2
        let highPassCutoff = max(1, min(lowFrequency, nyquist - 1))
        let lowPassCutoff = max(highPassCutoff + 1, min(highFrequency, nyquist * 0.99))

        // Butterworth (maximally flat) skirts.
        let quality = 1.0 / 2.0.squareRoot()
        let highPassed = Self.biquadHighpass(samples, cutoff: highPassCutoff, sampleRate: sampleRate, quality: quality)
        return Self.biquadLowpass(highPassed, cutoff: lowPassCutoff, sampleRate: sampleRate, quality: quality)
    }

    // MARK: - RBJ biquads (Direct Form I)

    // Short names (b0, a1, x1…) are the canonical cookbook notation — clearer here
    // than verbose renames. Coefficients per Robert Bristow-Johnson's Audio EQ
    // Cookbook; `a0` divides the rest, then a two-sample-history recurrence runs.

    private static func biquadHighpass(
        _ samples: [Float], cutoff: Double, sampleRate: Double, quality: Double
    ) -> [Float] {
        let w0 = 2 * Double.pi * cutoff / sampleRate
        let cosW0 = cos(w0)
        let alpha = sin(w0) / (2 * quality)
        return runBiquad(
            samples,
            b0: (1 + cosW0) / 2, b1: -(1 + cosW0), b2: (1 + cosW0) / 2,
            a0: 1 + alpha, a1: -2 * cosW0, a2: 1 - alpha
        )
    }

    private static func biquadLowpass(
        _ samples: [Float], cutoff: Double, sampleRate: Double, quality: Double
    ) -> [Float] {
        let w0 = 2 * Double.pi * cutoff / sampleRate
        let cosW0 = cos(w0)
        let alpha = sin(w0) / (2 * quality)
        return runBiquad(
            samples,
            b0: (1 - cosW0) / 2, b1: 1 - cosW0, b2: (1 - cosW0) / 2,
            a0: 1 + alpha, a1: -2 * cosW0, a2: 1 - alpha
        )
    }

    private static func runBiquad(
        _ samples: [Float],
        b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double
    ) -> [Float] {
        let nb0 = b0 / a0, nb1 = b1 / a0, nb2 = b2 / a0
        let na1 = a1 / a0, na2 = a2 / a0

        var output = [Float](repeating: 0, count: samples.count)
        var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0
        for index in samples.indices {
            let x0 = Double(samples[index])
            let y0 = nb0 * x0 + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
            output[index] = Float(y0)
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
        }
        return output
    }
}

/// Amplitude modulation — a subtle shimmer that reads as "machine voice" without
/// destroying intelligibility. `depth` 0…1 is how deep the modulation cuts;
/// `rate` is the LFO frequency in Hz.
public struct TremoloEffect: VoiceEffect {
    public let name = "tremolo"
    public let rate: Double
    public let depth: Float

    public init(rate: Double = 20, depth: Float = 0.15) {
        self.rate = rate
        self.depth = min(max(depth, 0), 1)
    }

    public func apply(to samples: [Float], sampleRate: Double) -> [Float] {
        guard sampleRate > 0, depth > 0 else { return samples }
        let halfDepth = depth / 2
        return samples.enumerated().map { index, sample in
            let phase = 2 * Double.pi * rate * Double(index) / sampleRate
            // Envelope swings in [1 - depth, 1].
            let envelope = 1 - halfDepth + halfDepth * Float(sin(phase))
            return sample * envelope
        }
    }
}
