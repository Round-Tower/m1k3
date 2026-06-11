//
//  KokoroWordTiming.swift
//  M1K3Kokoro
//
//  Proportional word timing for Kokoro audio. The ONNX model emits only the
//  waveform — no duration tensors — so per-word timing is ESTIMATED: the
//  measured audio duration is distributed over the G2P token stream by weight.
//  A speakable word's weight span runs from its first own phoneme token to the
//  next speakable word's first token, so inter-word spaces and trailing
//  punctuation (rendered as pause audio) are attributed to the word they
//  follow. OOV words weigh nothing; tokens before the first speakable word
//  (leading punctuation) are unattributed pre-word audio.
//
//  Accuracy is ~word-level, and chunked synthesis re-anchors the estimate at
//  every chunk boundary, so drift never accumulates across an utterance.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 (maths test-pinned;
//  the phonemes-take-equal-time assumption is empirical — verified live at ⌘R).
//  Prior: Unknown.
//

import Foundation
import M1K3Voice

public enum KokoroWordTiming {
    /// Build a timeline for one synthesized chunk.
    ///
    /// - Parameters:
    ///   - text: the FULL utterance text the ranges should index (the timeline's
    ///     display contract). For single-chunk synthesis this is the input text.
    ///   - result: the chunk's G2P assembly (ranges local to the chunk's text).
    ///   - audioDuration: measured seconds of synthesized audio for the chunk.
    ///   - textOffset: UTF-16 offset of the chunk within `text`, shifting the
    ///     chunk-local word ranges into the full string.
    public static func timeline(
        text: String,
        result: G2PResult,
        audioDuration: TimeInterval,
        textOffset: Int = 0
    ) -> SpokenWordTimeline {
        let totalTokens = result.tokens.count
        guard totalTokens > 0, audioDuration > 0 else {
            let silent = result.words.map { word in
                SpokenWord(textRange: shifted(word.textRange, by: textOffset), start: 0, duration: 0)
            }
            return SpokenWordTimeline(text: text, words: silent, totalDuration: max(audioDuration, 0))
        }

        let secondsPerToken = audioDuration / Double(totalTokens)
        let speakable = result.words.enumerated().filter { !$0.element.tokenRange.isEmpty }
        // Each speakable word's span ends where the next speakable word begins.
        var spanEnds = [Int: Int]()
        for (position, entry) in speakable.enumerated() {
            let next = position + 1 < speakable.count
                ? speakable[position + 1].element.tokenRange.lowerBound
                : totalTokens
            spanEnds[entry.offset] = next
        }

        let words = result.words.enumerated().map { index, word -> SpokenWord in
            let range = shifted(word.textRange, by: textOffset)
            let start = Double(word.tokenRange.lowerBound) * secondsPerToken
            guard let spanEnd = spanEnds[index] else {
                return SpokenWord(textRange: range, start: start, duration: 0)
            }
            let weight = spanEnd - word.tokenRange.lowerBound
            return SpokenWord(textRange: range, start: start, duration: Double(weight) * secondsPerToken)
        }
        return SpokenWordTimeline(text: text, words: words, totalDuration: audioDuration)
    }

    private static func shifted(_ range: Range<Int>, by offset: Int) -> Range<Int> {
        (range.lowerBound + offset) ..< (range.upperBound + offset)
    }
}
