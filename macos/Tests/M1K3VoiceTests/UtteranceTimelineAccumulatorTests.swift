import Foundation
import M1K3Voice
import Testing

/// Pins the chunk→global timeline anchoring for streamed playback. Probe-proven
/// AVAudioPlayerNode behavior this encodes: playerTime keeps advancing through
/// dry gaps (nothing queued, player running), so a chunk scheduled after a gap
/// starts at the player's NOW, not at the end of the previously scheduled audio
/// — hence the max(scheduledSamples, playerSampleNow) anchor.
struct UtteranceTimelineAccumulatorTests {
    private let text = "Hello brave new world"
    private let sampleRate = 24000.0

    private func chunk(rangeStart: Int, duration: TimeInterval) -> SpokenWordTimeline {
        SpokenWordTimeline(
            text: text,
            words: [SpokenWord(textRange: rangeStart ..< rangeStart + 5, start: 0, duration: duration)],
            totalDuration: duration
        )
    }

    @Test("the first chunk anchors at zero")
    func firstChunkAtZero() {
        var accumulator = UtteranceTimelineAccumulator(text: text, sampleRate: sampleRate)
        let global = accumulator.append(chunk: chunk(rangeStart: 0, duration: 1.0), sampleCount: 24000, playerSampleNow: nil)
        #expect(global.words[0].start == 0)
        #expect(global.totalDuration == 1.0)
        #expect(accumulator.scheduledSamples == 24000)
    }

    @Test("back-to-back chunks anchor at the scheduled-sample boundary")
    func backToBack() {
        var accumulator = UtteranceTimelineAccumulator(text: text, sampleRate: sampleRate)
        _ = accumulator.append(chunk: chunk(rangeStart: 0, duration: 1.0), sampleCount: 24000, playerSampleNow: nil)
        // Player has only reached 0.5s — chunk 2 still queues at the 1.0s boundary.
        let global = accumulator.append(chunk: chunk(rangeStart: 6, duration: 0.5), sampleCount: 12000, playerSampleNow: 12000)
        #expect(global.words.count == 2)
        #expect(abs(global.words[1].start - 1.0) < 0.0001)
        #expect(abs(global.totalDuration - 1.5) < 0.0001)
        #expect(accumulator.scheduledSamples == 36000)
    }

    @Test("a dry gap re-anchors at the player's current sample")
    func dryGapReanchors() {
        var accumulator = UtteranceTimelineAccumulator(text: text, sampleRate: sampleRate)
        _ = accumulator.append(chunk: chunk(rangeStart: 0, duration: 1.0), sampleCount: 24000, playerSampleNow: nil)
        // Synthesis fell behind: the player ran on to 1.5s before chunk 2 arrived.
        let global = accumulator.append(chunk: chunk(rangeStart: 6, duration: 1.0), sampleCount: 24000, playerSampleNow: 36000)
        #expect(abs(global.words[1].start - 1.5) < 0.0001)
        #expect(abs(global.totalDuration - 2.5) < 0.0001)
        #expect(accumulator.scheduledSamples == 60000)
    }

    @Test("the first chunk ignores a stale player clock from a previous utterance")
    func firstChunkIgnoresStaleClock() {
        // The player node is reused across utterances; a stale reading here
        // would shift every word of the new utterance past its audio.
        var accumulator = UtteranceTimelineAccumulator(text: text, sampleRate: sampleRate)
        let global = accumulator.append(chunk: chunk(rangeStart: 0, duration: 1.0), sampleCount: 24000, playerSampleNow: 600_000)
        #expect(global.words[0].start == 0)
        #expect(accumulator.scheduledSamples == 24000)
    }

    @Test("a slightly negative player sample clamps to zero")
    func negativePlayerSampleClamps() {
        // playerTime can read negative right after play() (probe-observed -558).
        var accumulator = UtteranceTimelineAccumulator(text: text, sampleRate: sampleRate)
        let global = accumulator.append(chunk: chunk(rangeStart: 0, duration: 1.0), sampleCount: 24000, playerSampleNow: -558)
        #expect(global.words[0].start == 0)
        #expect(accumulator.scheduledSamples == 24000)
    }

    @Test("the global timeline accumulates across many chunks in order")
    func growsInOrder() {
        var accumulator = UtteranceTimelineAccumulator(text: text, sampleRate: sampleRate)
        for index in 0 ..< 3 {
            _ = accumulator.append(chunk: chunk(rangeStart: index, duration: 1.0), sampleCount: 24000, playerSampleNow: nil)
        }
        let global = accumulator.global
        #expect(global.words.map(\.start) == [0.0, 1.0, 2.0])
        #expect(global.totalDuration == 3.0)
        #expect(global.text == text)
    }
}
