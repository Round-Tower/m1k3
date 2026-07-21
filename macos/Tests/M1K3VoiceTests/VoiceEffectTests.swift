import Foundation
import M1K3Voice
import Testing

struct VoiceEffectTests {
    private let sampleRate = 24000.0

    /// A pure sine at `frequency` Hz, `count` samples long.
    private func sine(_ frequency: Double, count: Int = 2400, amplitude: Float = 0.5) -> [Float] {
        (0 ..< count).map { index in
            amplitude * Float(sin(2 * Double.pi * frequency * Double(index) / sampleRate))
        }
    }

    private func peak(_ samples: [Float]) -> Float {
        samples.map(abs).max() ?? 0
    }

    // MARK: - Normalization

    @Test("normalization scales the peak to the target level")
    func normalizationHitsLevel() {
        let input = sine(440, amplitude: 0.2)
        let out = NormalizationEffect(level: 0.9).apply(to: input, sampleRate: sampleRate)
        #expect(abs(peak(out) - 0.9) < 0.01)
    }

    @Test("normalization leaves silence untouched")
    func normalizationSilence() {
        let silence = [Float](repeating: 0, count: 100)
        let out = NormalizationEffect(level: 0.9).apply(to: silence, sampleRate: sampleRate)
        #expect(out.allSatisfy { $0 == 0 })
    }

    // MARK: - Compression

    @Test("compression pulls peaks below the original, keeps quiet parts")
    func compressionReducesPeaks() {
        let loud: [Float] = [0.9, -0.9, 0.2, -0.1]
        let out = CompressionEffect(threshold: 0.6, ratio: 0.3).apply(to: loud, sampleRate: sampleRate)
        // The 0.9 peaks are over threshold → reduced; the quiet 0.2/-0.1 unchanged.
        #expect(abs(out[0]) < 0.9)
        #expect(abs(out[0]) > 0.6) // still above threshold, just compressed
        #expect(out[2] == 0.2)
        #expect(out[3] == -0.1)
    }

    @Test("normalise-into-soft-clip makes the clip engage (gain-staging guard)")
    func compressorEngagesAfterNormalise() {
        // A signal whose peaks sit below the 0.6 threshold: the soft-clip alone is
        // a no-op on it — this is exactly how the stage silently went inert when the
        // bandpass in front of it scooped the level.
        let quiet: [Float] = [0.3, -0.3, 0.25, -0.2]
        let clip = CompressionEffect(threshold: 0.6, ratio: 0.4)
        #expect(clip.apply(to: quiet, sampleRate: sampleRate) == quiet)

        // Normalising up to full-scale FIRST (as m1k3Character now does) drives the
        // peak into the knee, so the clip actually pulls it down.
        let staged = clip.apply(
            to: NormalizationEffect(level: 1.0).apply(to: quiet, sampleRate: sampleRate),
            sampleRate: sampleRate
        )
        #expect(peak(staged) < 1.0)
    }

    // MARK: - Bandpass

    @Test("bandpass passes an in-band tone and rejects a very low tone")
    func bandpassShapesSpectrum() {
        let inBand = sine(1000) // within 320–3600
        let subsonic = sine(40) // well below the band
        let bandpass = BandpassEffect(lowFrequency: 320, highFrequency: 3600)

        let passed = bandpass.apply(to: inBand, sampleRate: sampleRate)
        let rejected = bandpass.apply(to: subsonic, sampleRate: sampleRate)

        // Measure energy in the back half (after the filter settles).
        #expect(peak(Array(passed.suffix(1200))) > peak(Array(rejected.suffix(1200))))
    }

    @Test("bandpass is stable (no NaN/inf, bounded output)")
    func bandpassStable() {
        let out = BandpassEffect().apply(to: sine(1000, amplitude: 0.9), sampleRate: sampleRate)
        #expect(out.allSatisfy { $0.isFinite })
        #expect(peak(out) < 5) // a sane bound — never blows up
    }

    @Test("bandpass passband is near-unity — an in-band tone is NOT scooped")
    func bandpassPassbandNearUnity() {
        // 1000 Hz sits comfortably inside 320–3600, so the HPF+LPF cascade should
        // pass it at close to its input level. The old single constant-skirt biquad
        // attenuated the whole band by ~10 dB (a 0.5 tone came out near ~0.16); the
        // cascade keeps it near unity. This locks the passband gain in.
        let inBand = sine(1000, amplitude: 0.5)
        let out = BandpassEffect(lowFrequency: 320, highFrequency: 3600)
            .apply(to: inBand, sampleRate: sampleRate)
        #expect(peak(Array(out.suffix(1200))) > 0.4) // was ~0.16 before
    }

    // MARK: - Tremolo

    @Test("tremolo modulates the amplitude envelope")
    func tremoloModulates() {
        let steady = [Float](repeating: 0.5, count: 2400)
        let out = TremoloEffect(rate: 20, depth: 0.5).apply(to: steady, sampleRate: sampleRate)
        // A constant input becomes non-constant once modulated.
        #expect(Set(out).count > 1)
        // Envelope never exceeds the input amplitude (depth attenuates).
        #expect(peak(out) <= 0.5 + 0.0001)
    }

    @Test("tremolo with zero depth is a no-op")
    func tremoloZeroDepth() {
        let input = sine(440)
        let out = TremoloEffect(rate: 20, depth: 0).apply(to: input, sampleRate: sampleRate)
        #expect(out == input)
    }

    // MARK: - Chain

    @Test("an empty chain is the identity")
    func emptyChainIdentity() {
        let input = sine(440)
        let out = VoiceEffectChain([]).process(input, sampleRate: sampleRate)
        #expect(out == input)
        #expect(VoiceEffectChain([]).isEmpty)
    }

    @Test("the chain applies effects in order")
    func chainAppliesInOrder() {
        // Normalize-then-halve differs from halve-then-normalize: prove ordering matters.
        let halve = GainStub(factor: 0.5)
        let normalize = NormalizationEffect(level: 1.0)
        let input = sine(440, amplitude: 0.3)

        let normThenHalve = VoiceEffectChain([normalize, halve]).process(input, sampleRate: sampleRate)
        let halveThenNorm = VoiceEffectChain([halve, normalize]).process(input, sampleRate: sampleRate)

        #expect(abs(peak(normThenHalve) - 0.5) < 0.01) // normalized to 1, then halved
        #expect(abs(peak(halveThenNorm) - 1.0) < 0.01) // halved, then normalized back to 1
    }

    @Test("the M1K3 character preset is a non-empty, stable chain")
    func m1k3CharacterStable() {
        let input = sine(700, amplitude: 0.6)
        let out = VoiceEffectChain.m1k3Character.process(input, sampleRate: sampleRate)
        #expect(!VoiceEffectChain.m1k3Character.isEmpty)
        #expect(out.count == input.count)
        #expect(out.allSatisfy { $0.isFinite })
        #expect(peak(out) <= 1.0001) // normalised tail keeps it in range
    }
}

/// A trivial gain stage for ordering tests.
private struct GainStub: VoiceEffect {
    let name = "gain-stub"
    let factor: Float
    func apply(to samples: [Float], sampleRate _: Double) -> [Float] {
        samples.map { $0 * factor }
    }
}
