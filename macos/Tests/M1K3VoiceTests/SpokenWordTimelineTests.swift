import Foundation
import M1K3Voice
import Testing

struct SpokenWordTimelineTests {
    /// "Hello brave new world" — UTF-16 offsets: Hello 0..<5, brave 6..<11, new 12..<15, world 16..<21
    private let text = "Hello brave new world"

    private func timeline() -> SpokenWordTimeline {
        SpokenWordTimeline(
            text: text,
            words: [
                SpokenWord(textRange: 0 ..< 5, start: 0.0, duration: 0.4),
                SpokenWord(textRange: 6 ..< 11, start: 0.4, duration: 0.3),
                SpokenWord(textRange: 12 ..< 15, start: 0.9, duration: 0.2), // gap 0.7–0.9
                SpokenWord(textRange: 16 ..< 21, start: 1.1, duration: 0.5),
            ],
            totalDuration: 1.8
        )
    }

    // MARK: - wordIndex(at:)

    @Test("a time inside a word's span resolves to that word")
    func insideSpan() {
        #expect(timeline().wordIndex(at: 0.2) == 0)
        #expect(timeline().wordIndex(at: 1.3) == 3)
    }

    @Test("a time exactly at a word's start resolves to that word")
    func atStart() {
        #expect(timeline().wordIndex(at: 0.4) == 1)
        #expect(timeline().wordIndex(at: 0.9) == 2)
    }

    @Test("the highlight is sticky: a gap after a word keeps that word current")
    func stickyThroughGaps() {
        // 0.7–0.9 is a pause between "brave" and "new" — "brave" stays current.
        #expect(timeline().wordIndex(at: 0.8) == 1)
        // Past "world"'s span but before totalDuration — "world" stays current.
        #expect(timeline().wordIndex(at: 1.7) == 3)
    }

    @Test("before the first word and negative times resolve to nil")
    func beforeFirst() {
        let shifted = timeline().offset(by: 0.5)
        #expect(shifted.wordIndex(at: 0.2) == nil)
        #expect(timeline().wordIndex(at: -0.1) == nil)
    }

    @Test("times at or past totalDuration resolve to nil")
    func pastEnd() {
        #expect(timeline().wordIndex(at: 1.8) == nil)
        #expect(timeline().wordIndex(at: 5.0) == nil)
    }

    @Test("zero-duration words are never current — the previous speakable word holds")
    func zeroDurationSkipped() {
        let withOOV = SpokenWordTimeline(
            text: text,
            words: [
                SpokenWord(textRange: 0 ..< 5, start: 0.0, duration: 0.4),
                SpokenWord(textRange: 6 ..< 11, start: 0.4, duration: 0), // OOV
                SpokenWord(textRange: 12 ..< 15, start: 0.4, duration: 0.3),
            ],
            totalDuration: 0.7
        )
        #expect(withOOV.wordIndex(at: 0.4) == 2)
        #expect(withOOV.wordIndex(at: 0.5) == 2)
    }

    @Test("a leading zero-duration word yields nil until a speakable word starts")
    func leadingZeroDuration() {
        let leadingOOV = SpokenWordTimeline(
            text: text,
            words: [
                SpokenWord(textRange: 0 ..< 5, start: 0.0, duration: 0),
                SpokenWord(textRange: 6 ..< 11, start: 0.3, duration: 0.4),
            ],
            totalDuration: 0.7
        )
        #expect(leadingOOV.wordIndex(at: 0.1) == nil)
        #expect(leadingOOV.wordIndex(at: 0.3) == 1)
    }

    @Test("an empty timeline always resolves to nil")
    func emptyTimeline() {
        let empty = SpokenWordTimeline(text: "", words: [], totalDuration: 0)
        #expect(empty.wordIndex(at: 0) == nil)
        #expect(empty.wordIndex(at: 1) == nil)
    }

    // MARK: - offset(by:)

    @Test("offset shifts every start and the total duration, ranges untouched")
    func offsetShifts() {
        let shifted = timeline().offset(by: 2.0)
        #expect(shifted.words[0].start == 2.0)
        #expect(shifted.words[3].start == 3.1)
        #expect(shifted.words[0].duration == 0.4)
        #expect(shifted.words[0].textRange == 0 ..< 5)
        #expect(shifted.totalDuration == 3.8)
        #expect(shifted.text == text)
    }

    // MARK: - appending(_:)

    @Test("appending an offset later chunk concatenates words and extends the end")
    func appendingChunks() {
        let first = SpokenWordTimeline(
            text: text,
            words: [SpokenWord(textRange: 0 ..< 5, start: 0.0, duration: 0.4)],
            totalDuration: 0.5
        )
        let second = SpokenWordTimeline(
            text: text,
            words: [SpokenWord(textRange: 6 ..< 11, start: 0.0, duration: 0.3)],
            totalDuration: 0.4
        ).offset(by: 0.5)

        let joined = first.appending(second)
        #expect(joined.words.count == 2)
        #expect(joined.words[1].start == 0.5)
        #expect(joined.totalDuration == 0.9)
        #expect(joined.wordIndex(at: 0.6) == 1)
        // The first chunk's words still resolve after the join.
        #expect(joined.wordIndex(at: 0.2) == 0)
    }

    @Test("appending keeps an earlier, longer end if the chunk ends sooner")
    func appendingKeepsLaterEnd() {
        let long = SpokenWordTimeline(text: text, words: [], totalDuration: 2.0)
        let early = SpokenWordTimeline(text: text, words: [], totalDuration: 1.0)
        #expect(long.appending(early).totalDuration == 2.0)
    }
}
