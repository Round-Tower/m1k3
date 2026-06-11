import Foundation
@testable import M1K3Kokoro
import M1K3Voice
import Testing

/// Pins the proportional word-timing estimator: audio duration distributed over
/// words by phoneme-token weight. A speakable word's weight span runs from its
/// first own token to the next speakable word's first token — inter-word spaces
/// and trailing punctuation are attributed to the word they follow. OOV words
/// weigh nothing.
struct KokoroWordTimingTests {
    private let dict: [String: [Int]] = [
        "aa": [101],
        "bb": [102],
        "ccc": [103, 104, 105],
    ]

    private func annotated(_ text: String) -> G2PResult {
        KokoroG2P(dictionary: dict).annotatedTokens(text)
    }

    @Test("durations split by token weight and sum to the audio duration")
    func proportionalSplit() {
        // "aa bb" → tokens [101, 16, 102]: aa spans 0..<2 (space attributed), bb 2..<3.
        let text = "aa bb"
        let timeline = KokoroWordTiming.timeline(
            text: text, result: annotated(text), audioDuration: 3.0
        )
        #expect(timeline.text == text)
        #expect(timeline.totalDuration == 3.0)
        #expect(timeline.words.count == 2)
        #expect(abs(timeline.words[0].start - 0.0) < 0.001)
        #expect(abs(timeline.words[0].duration - 2.0) < 0.001)
        #expect(abs(timeline.words[1].start - 2.0) < 0.001)
        #expect(abs(timeline.words[1].duration - 1.0) < 0.001)
    }

    @Test("trailing punctuation weight belongs to the preceding word")
    func punctuationAttribution() {
        // "aa, bb!" → [101, 3, 16, 102, 5]: aa spans 0..<3, bb spans 3..<5.
        let text = "aa, bb!"
        let timeline = KokoroWordTiming.timeline(
            text: text, result: annotated(text), audioDuration: 5.0
        )
        #expect(abs(timeline.words[0].duration - 3.0) < 0.001)
        #expect(abs(timeline.words[1].start - 3.0) < 0.001)
        #expect(abs(timeline.words[1].duration - 2.0) < 0.001)
    }

    @Test("OOV words weigh nothing; neighbours absorb the stream around them")
    func oovZeroDuration() {
        // "aa zzz bb" → tokens [101, 16, 102]: aa spans 0..<2, bb 2..<3, zzz zero.
        let text = "aa zzz bb"
        let timeline = KokoroWordTiming.timeline(
            text: text, result: annotated(text), audioDuration: 3.0
        )
        #expect(timeline.words.count == 3)
        #expect(timeline.words[1].duration == 0)
        #expect(abs(timeline.words[0].duration - 2.0) < 0.001)
        #expect(abs(timeline.words[2].start - 2.0) < 0.001)
        #expect(abs(timeline.words[2].duration - 1.0) < 0.001)
        // Sticky lookup never lands on the OOV word.
        #expect(timeline.wordIndex(at: 1.9) == 0)
        #expect(timeline.wordIndex(at: 2.1) == 2)
    }

    @Test("heavier words get proportionally more time")
    func weightedWords() {
        // "ccc aa" → [103, 104, 105, 16, 101]: ccc spans 0..<4, aa 4..<5.
        let text = "ccc aa"
        let timeline = KokoroWordTiming.timeline(
            text: text, result: annotated(text), audioDuration: 5.0
        )
        #expect(abs(timeline.words[0].duration - 4.0) < 0.001)
        #expect(abs(timeline.words[1].duration - 1.0) < 0.001)
    }

    @Test("textOffset shifts word ranges into the full original string")
    func textOffsetShifts() {
        let chunk = "aa bb" // imagine this chunk starts at UTF-16 offset 40
        let timeline = KokoroWordTiming.timeline(
            text: chunk, result: annotated(chunk), audioDuration: 3.0, textOffset: 40
        )
        #expect(timeline.words[0].textRange == 40 ..< 42)
        #expect(timeline.words[1].textRange == 43 ..< 45)
    }

    @Test("leading punctuation is unattributed — the first word starts late")
    func leadingPunctuation() {
        // ", aa" → [3, 16, 101]: aa's own token is index 2 → starts at 2/3 of the audio.
        let text = ", aa"
        let timeline = KokoroWordTiming.timeline(
            text: text, result: annotated(text), audioDuration: 3.0
        )
        #expect(timeline.words.count == 1)
        #expect(abs(timeline.words[0].start - 2.0) < 0.001)
        #expect(abs(timeline.words[0].duration - 1.0) < 0.001)
        #expect(timeline.wordIndex(at: 1.0) == nil) // pre-word audio highlights nothing
    }

    @Test("no tokens at all yields an empty-word timeline, not a crash")
    func emptyTokens() {
        let text = "zzz qqq"
        let timeline = KokoroWordTiming.timeline(
            text: text, result: annotated(text), audioDuration: 0
        )
        #expect(timeline.words.count == 2)
        #expect(timeline.words.allSatisfy { $0.duration == 0 })
        #expect(timeline.wordIndex(at: 0) == nil)
    }
}
