//
//  SpokenWordTimeline.swift
//  M1K3Voice
//
//  Word-level timing for a spoken utterance — the shared seam between every
//  speech backend and the karaoke reading view. Kokoro builds one by
//  proportional phoneme-weight estimation; the Apple offline path builds one
//  by delegate↔buffer correlation. When a timeline exists, the UI must display
//  `text` (the exact spoken string), never some other copy of the message —
//  ranges are meaningless against any other string.
//
//  `wordIndex(at:)` is deliberately STICKY: the latest started speakable word
//  stays current through pauses and estimation-jitter gaps, so the highlight
//  never flickers off mid-utterance. Zero-duration words (out-of-vocabulary —
//  G2P produced no phonemes, so no audio exists for them) are never current.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (pure, test-pinned;
//  sticky semantics chosen for highlight stability). Prior: Unknown.
//

import Foundation

/// One spoken word: where it sits in the utterance string and when it is heard.
public struct SpokenWord: Sendable, Equatable {
    /// UTF-16 offsets into the utterance text (NSRange-compatible — AVSpeech
    /// delegate ranges convert losslessly).
    public let textRange: Range<Int>
    /// Seconds from the start of the utterance's audio.
    public let start: TimeInterval
    /// Seconds of audio attributed to this word. Zero for words that produced
    /// no phonemes (out-of-vocabulary).
    public let duration: TimeInterval

    public init(textRange: Range<Int>, start: TimeInterval, duration: TimeInterval) {
        self.textRange = textRange
        self.start = start
        self.duration = duration
    }
}

/// The full word-timing map for one utterance. Pure value; all the maths the
/// playback clock and the karaoke view need, with no audio dependency.
public struct SpokenWordTimeline: Sendable, Equatable {
    /// The exact string the audio speaks. Display THIS when highlighting.
    public let text: String
    /// Words sorted by `start`.
    public let words: [SpokenWord]
    /// Total seconds of audio for the utterance (may exceed the last word's
    /// end — trailing silence belongs to the utterance).
    public let totalDuration: TimeInterval

    public init(text: String, words: [SpokenWord], totalDuration: TimeInterval) {
        self.text = text
        self.words = words
        self.totalDuration = totalDuration
    }

    /// The word current at `time`: the latest started word with audible
    /// duration, sticky through gaps. `nil` before the first speakable word
    /// starts and from `totalDuration` onwards.
    public func wordIndex(at time: TimeInterval) -> Int? {
        guard time >= 0, time < totalDuration, !words.isEmpty else { return nil }

        // Binary search: the last index with start <= time.
        var low = 0
        var high = words.count - 1
        var lastStarted = -1
        while low <= high {
            let mid = (low + high) / 2
            if words[mid].start <= time {
                lastStarted = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        guard lastStarted >= 0 else { return nil }

        // Walk back over zero-duration (OOV) words — they are never current.
        var index = lastStarted
        while index >= 0, words[index].duration <= 0 {
            index -= 1
        }
        return index >= 0 ? index : nil
    }

    /// The same timeline shifted later by `offset` seconds (chunk → global anchoring).
    public func offset(by offset: TimeInterval) -> SpokenWordTimeline {
        SpokenWordTimeline(
            text: text,
            words: words.map {
                SpokenWord(textRange: $0.textRange, start: $0.start + offset, duration: $0.duration)
            },
            totalDuration: totalDuration + offset
        )
    }

    /// Concatenate a later chunk's timeline (already offset into this
    /// timeline's clock). The end extends only if the chunk ends later.
    public func appending(_ other: SpokenWordTimeline) -> SpokenWordTimeline {
        SpokenWordTimeline(
            text: text,
            words: words + other.words,
            totalDuration: max(totalDuration, other.totalDuration)
        )
    }
}
