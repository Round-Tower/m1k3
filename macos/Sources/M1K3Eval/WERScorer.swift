//
//  WERScorer.swift
//  M1K3Eval
//
//  Word/character error rate for transcription quality — the measuring stick that
//  turns "the transcripts feel sharper" into a number. With it, the Phase-1
//  endpointing thresholds and the Phase-4 model choice (base.en vs small.en) can
//  be tuned against golden clips on evidence, not vibes.
//
//  Pure (Levenshtein over normalized tokens), so it's unit-tested off-device; the
//  on-device stage that runs real WhisperKit against golden audio and feeds this
//  scorer is the verify-by-launch half (a SelfTest stage + recorded fixtures).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.9, Prior: Unknown

import Foundation

public enum WERScorer {
    public struct Score: Equatable, Sendable {
        /// Word edits / reference words (0 = perfect; can exceed 1 with many inserts).
        public let wordErrorRate: Double
        /// Character edits / reference characters — catches sub-word slips.
        public let characterErrorRate: Double
        public let referenceWordCount: Int
        public let wordEdits: Int
    }

    public static func score(reference: String, hypothesis: String) -> Score {
        let refWords = words(reference)
        let hypWords = words(hypothesis)
        let wordEdits = levenshtein(refWords, hypWords)

        let refChars = Array(normalized(reference))
        let hypChars = Array(normalized(hypothesis))
        let charEdits = levenshtein(refChars, hypChars)

        return Score(
            wordErrorRate: rate(edits: wordEdits, reference: refWords.count, hypothesisEmpty: hypWords.isEmpty),
            characterErrorRate: rate(edits: charEdits, reference: refChars.count, hypothesisEmpty: hypChars.isEmpty),
            referenceWordCount: refWords.count,
            wordEdits: wordEdits
        )
    }

    // MARK: - Helpers

    /// edits / reference; an empty reference is 0 when output is also empty, else
    /// 1 (every output token is an insertion error against nothing).
    private static func rate(edits: Int, reference: Int, hypothesisEmpty: Bool) -> Double {
        guard reference > 0 else { return hypothesisEmpty ? 0 : 1 }
        return Double(edits) / Double(reference)
    }

    /// Lowercase, strip punctuation to spaces, collapse whitespace — the standard
    /// WER normalization so "Hello, world!" and "hello world" score equal.
    private static func normalized(_ text: String) -> String {
        let lowered = text.lowercased()
        let stripped = String(lowered.map { $0.isLetter || $0.isNumber || $0 == " " ? $0 : " " })
        return stripped.split(separator: " ", omittingEmptySubsequences: true).joined(separator: " ")
    }

    private static func words(_ text: String) -> [Substring] {
        normalized(text).split(separator: " ", omittingEmptySubsequences: true)
    }

    /// Classic O(n·m) edit distance (insert/delete/substitute all cost 1).
    private static func levenshtein<Element: Equatable>(_ lhs: [Element], _ rhs: [Element]) -> Int {
        if lhs.isEmpty { return rhs.count }
        if rhs.isEmpty { return lhs.count }
        var previous = Array(0 ... rhs.count)
        var current = [Int](repeating: 0, count: rhs.count + 1)
        for row in 1 ... lhs.count {
            current[0] = row
            for col in 1 ... rhs.count {
                let cost = lhs[row - 1] == rhs[col - 1] ? 0 : 1
                current[col] = min(
                    previous[col] + 1, // deletion
                    current[col - 1] + 1, // insertion
                    previous[col - 1] + cost // substitution / match
                )
            }
            swap(&previous, &current)
        }
        return previous[rhs.count]
    }
}
