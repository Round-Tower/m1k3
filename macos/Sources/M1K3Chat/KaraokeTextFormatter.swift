//
//  KaraokeTextFormatter.swift
//  M1K3Chat
//
//  The karaoke (follow-the-spoken-word) run classifier: text + word ranges +
//  the word currently being heard in, ordered (text, phase, boldPrefix) runs
//  out. The view paints phases (spoken = solid, current = highlighted,
//  upcoming = dimmed — the Focus-reader pattern) and applies the bionic bold
//  prefix; this stays pure like BionicTextFormatter beside it.
//
//  Losslessness is BY CONSTRUCTION: one forward character walk assigns every
//  character to exactly one run (range boundaries snap forward to the next
//  character start), so concatenating the runs reproduces the input even if
//  the ranges are unsorted, overlapping, or out of bounds.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (pure, test-pinned
//  incl. hostile-range and emoji cases). Prior: Unknown.
//

import Foundation

public enum KaraokePhase: Equatable, Sendable {
    case spoken, current, upcoming
}

public struct KaraokeRun: Equatable, Sendable {
    public let text: Substring
    public let phase: KaraokePhase
    /// Leading characters to embolden (bionic reading); 0 for gaps or bionic off.
    public let boldPrefix: Int
}

public enum KaraokeTextFormatter {
    /// Classify `text` into ordered runs. `wordRanges` are UTF-16 offsets (from
    /// a SpokenWordTimeline, or `wordRanges(of:)` when no timeline exists);
    /// `currentIndex` indexes into `wordRanges`; nil = nothing spoken yet.
    public static func runs(
        text: String,
        wordRanges: [Range<Int>],
        currentIndex: Int?,
        bionic: Bool
    ) -> [KaraokeRun] {
        guard !text.isEmpty else { return [] }
        let words = sanitized(wordRanges, utf16Count: text.utf16.count)

        var runs: [KaraokeRun] = []
        var segmentStart = text.startIndex
        var segmentWord: Int? = words.isEmpty || words[0].lowerBound > 0 ? nil : 0
        var lastClosedWord = -1 // for gap phases: the most recent word fully passed
        var wordCursor = segmentWord == nil ? 0 : 1
        var utf16Offset = 0

        func close(at index: String.Index) {
            guard segmentStart < index else { return }
            let slice = text[segmentStart ..< index]
            runs.append(makeRun(
                slice,
                wordIndex: segmentWord,
                lastClosedWord: lastClosedWord,
                currentIndex: currentIndex,
                bionic: bionic
            ))
            if let word = segmentWord { lastClosedWord = max(lastClosedWord, word) }
            segmentStart = index
        }

        for index in text.indices {
            // Close the running word segment once we pass its end…
            if let word = segmentWord, utf16Offset >= words[word].upperBound {
                close(at: index)
                segmentWord = nil
            }
            // …and open the next word segment when we reach its start.
            if segmentWord == nil, wordCursor < words.count, utf16Offset >= words[wordCursor].lowerBound {
                close(at: index)
                segmentWord = wordCursor
                wordCursor += 1
            }
            utf16Offset += text[index].utf16.count
        }
        close(at: text.endIndex)
        return runs
    }

    /// UTF-16 ranges of the non-whitespace tokens — the word map for backends
    /// that push live ranges without a timeline (plain Built-in tier).
    public static func wordRanges(of text: String) -> [Range<Int>] {
        var ranges: [Range<Int>] = []
        var offset = 0
        var wordStart: Int?
        for character in text {
            if character.isWhitespace {
                if let start = wordStart {
                    ranges.append(start ..< offset)
                    wordStart = nil
                }
            } else if wordStart == nil {
                wordStart = offset
            }
            offset += character.utf16.count
        }
        if let start = wordStart {
            ranges.append(start ..< offset)
        }
        return ranges
    }

    /// Which word a spoken range belongs to — first overlap wins. Binary search
    /// (ranges are sorted, non-overlapping): this runs on every word-clock tick.
    public static func index(containing range: Range<Int>, in ranges: [Range<Int>]) -> Int? {
        var low = 0
        var high = ranges.count - 1
        while low <= high {
            let mid = (low + high) / 2
            if ranges[mid].overlaps(range) { return mid }
            if ranges[mid].upperBound <= range.lowerBound {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return nil
    }

    // MARK: - Internals

    private static func makeRun(
        _ slice: Substring,
        wordIndex: Int?,
        lastClosedWord: Int,
        currentIndex: Int?,
        bionic: Bool
    ) -> KaraokeRun {
        let phase: KaraokePhase
        if let wordIndex {
            phase = wordPhase(wordIndex, currentIndex: currentIndex)
        } else if currentIndex == nil {
            phase = .upcoming
        } else {
            // Gaps ride with the clause they close: solid once the preceding
            // word has been reached (current's own trailing gap reads as passed).
            let preceding = wordPhase(max(lastClosedWord, 0), currentIndex: currentIndex)
            phase = (preceding == .upcoming && lastClosedWord >= 0) ? .upcoming : .spoken
        }
        let boldPrefix = (bionic && wordIndex != nil)
            ? BionicTextFormatter.boldPrefixCount(for: slice.prefix { $0.isLetter })
            : 0
        return KaraokeRun(text: slice, phase: phase, boldPrefix: boldPrefix)
    }

    private static func wordPhase(_ index: Int, currentIndex: Int?) -> KaraokePhase {
        guard let currentIndex else { return .upcoming }
        if index < currentIndex { return .spoken }
        return index == currentIndex ? .current : .upcoming
    }

    /// Sort, clamp, and drop overlapping/empty ranges so the walk sees a clean
    /// strictly-increasing sequence whatever the caller supplied.
    private static func sanitized(_ ranges: [Range<Int>], utf16Count: Int) -> [Range<Int>] {
        var result: [Range<Int>] = []
        var floor = 0
        for range in ranges.sorted(by: { $0.lowerBound < $1.lowerBound }) {
            let lower = max(range.lowerBound, floor)
            let upper = min(range.upperBound, utf16Count)
            guard lower < upper else { continue }
            result.append(lower ..< upper)
            floor = upper
        }
        return result
    }
}
