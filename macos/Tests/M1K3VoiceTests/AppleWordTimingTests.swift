import Foundation
@testable import M1K3Voice
import Testing

/// Pins the Apple-path timeline built from offline-render word onsets: each
/// word runs from its onset to the next word's onset; the last word runs to
/// the end of the audio.
struct AppleWordTimingTests {
    @Test("words span onset-to-onset; the last word runs to the end")
    func onsetSpans() {
        // "The rain falls" @ 22050 Hz — onsets at 0, 0.1s, 0.4s; total 0.8s.
        let timeline = AppleWordTiming.timeline(
            text: "The rain falls",
            onsets: [
                WordOnset(textRange: 0 ..< 3, sampleOffset: 0),
                WordOnset(textRange: 4 ..< 8, sampleOffset: 2205),
                WordOnset(textRange: 9 ..< 14, sampleOffset: 8820),
            ],
            totalSamples: 17640,
            sampleRate: 22050
        )
        #expect(timeline.words.count == 3)
        #expect(abs(timeline.words[0].duration - 0.1) < 0.0001)
        #expect(abs(timeline.words[1].start - 0.1) < 0.0001)
        #expect(abs(timeline.words[1].duration - 0.3) < 0.0001)
        #expect(abs(timeline.words[2].duration - 0.4) < 0.0001)
        #expect(abs(timeline.totalDuration - 0.8) < 0.0001)
        #expect(timeline.wordIndex(at: 0.5) == 2)
    }

    @Test("no onsets (delegate never fired) yields a wordless timeline")
    func noOnsets() {
        let timeline = AppleWordTiming.timeline(
            text: "Hello", onsets: [], totalSamples: 22050, sampleRate: 22050
        )
        #expect(timeline.words.isEmpty)
        #expect(timeline.totalDuration == 1.0)
        #expect(timeline.wordIndex(at: 0.5) == nil)
    }

    @Test("out-of-order duplicate onsets never produce negative durations")
    func clampedDurations() {
        let timeline = AppleWordTiming.timeline(
            text: "a b",
            onsets: [
                WordOnset(textRange: 0 ..< 1, sampleOffset: 1000),
                WordOnset(textRange: 2 ..< 3, sampleOffset: 1000),
            ],
            totalSamples: 2000,
            sampleRate: 1000
        )
        #expect(timeline.words[0].duration == 0)
        #expect(timeline.words[1].duration == 1.0)
    }
}
