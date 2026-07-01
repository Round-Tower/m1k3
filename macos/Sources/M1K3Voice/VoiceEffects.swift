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

/// Soft-knee compressor: amplitudes above `threshold` are pulled in by `ratio`.
/// Port of CompressionEffect.
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

/// A second-order (biquad) bandpass — the "intercom"/telephone band that gives the
/// voice its transmitted character. RBJ cookbook coefficients, Direct Form I.
/// Port of IntercomEffect, implemented as a stable single-pass IIR filter.
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
        // Geometric centre + bandwidth → RBJ bandpass (constant skirt gain).
        let centre = (lowFrequency * highFrequency).squareRoot()
        let bandwidth = max(highFrequency - lowFrequency, 1)
        let quality = centre / bandwidth

        // RBJ biquad coefficients + Direct Form I state. Short names (b0, a1, x1…)
        // are the canonical cookbook notation — clearer here than verbose renames.
        let w0 = 2 * Double.pi * centre / sampleRate
        let alpha = sin(w0) / (2 * quality)
        let cosW0 = cos(w0)

        let b0 = alpha
        let b1 = 0.0
        let b2 = -alpha
        let a0 = 1 + alpha
        let a1 = -2 * cosW0
        let a2 = 1 - alpha

        // Normalise.
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
