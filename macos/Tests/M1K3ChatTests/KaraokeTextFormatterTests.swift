import Foundation
@testable import M1K3Chat
import Testing

/// Pins the karaoke run classifier: text + word ranges + the current word in,
/// ordered (text, phase, boldPrefix) runs out. The hard invariant: every
/// character of the input lands in exactly one run — concatenating the runs
/// reproduces the text, whatever the ranges look like.
struct KaraokeTextFormatterTests {
    // "The rain in Spain" — words at UTF-16 0..<3, 4..<8, 9..<11, 12..<17.
    private let text = "The rain in Spain"
    private var ranges: [Range<Int>] {
        [0 ..< 3, 4 ..< 8, 9 ..< 11, 12 ..< 17]
    }

    private func joined(_ runs: [KaraokeRun]) -> String {
        runs.map { String($0.text) }.joined()
    }

    @Test("concatenating all runs reproduces the input exactly")
    func lossless() {
        for current in [nil, 0, 1, 3] {
            let runs = KaraokeTextFormatter.runs(
                text: text, wordRanges: ranges, currentIndex: current, bionic: false
            )
            #expect(joined(runs) == text, "current=\(String(describing: current))")
        }
    }

    @Test("phases split at the current word; gaps inherit solid (spoken) styling")
    func phaseAssignment() {
        let runs = KaraokeTextFormatter.runs(
            text: text, wordRanges: ranges, currentIndex: 1, bionic: false
        )
        // The: spoken · " ": spoken · rain: current · " ": spoken · in: upcoming …
        #expect(runs.first(where: { $0.text == "The" })?.phase == .spoken)
        #expect(runs.first(where: { $0.text == "rain" })?.phase == .current)
        #expect(runs.first(where: { $0.text == "in" })?.phase == .upcoming)
        #expect(runs.first(where: { $0.text == "Spain" })?.phase == .upcoming)
        // Exactly one current run.
        #expect(runs.filter { $0.phase == .current }.count == 1)
    }

    @Test("no current word yet → everything upcoming")
    func nilCurrentAllUpcoming() {
        let runs = KaraokeTextFormatter.runs(
            text: text, wordRanges: ranges, currentIndex: nil, bionic: false
        )
        #expect(runs.allSatisfy { $0.phase == .upcoming })
    }

    @Test("punctuation between words rides with the preceding clause")
    func punctuationGapPhase() {
        let punctuated = "Yes, indeed" // Yes 0..<3, indeed 5..<11; gap ", "
        let runs = KaraokeTextFormatter.runs(
            text: punctuated, wordRanges: [0 ..< 3, 5 ..< 11], currentIndex: 0, bionic: false
        )
        #expect(runs.first(where: { $0.text == ", " })?.phase == .spoken)
        #expect(runs.first(where: { $0.text == "indeed" })?.phase == .upcoming)
    }

    @Test("bionic composes: word runs carry bold prefixes, gaps carry zero")
    func bionicComposition() {
        let runs = KaraokeTextFormatter.runs(
            text: text, wordRanges: ranges, currentIndex: 1, bionic: true
        )
        #expect(runs.first(where: { $0.text == "The" })?.boldPrefix == 1) // 3 letters → 1
        #expect(runs.first(where: { $0.text == "rain" })?.boldPrefix == 2) // 4 letters → 2
        #expect(runs.first(where: { $0.text == "Spain" })?.boldPrefix == 2) // 5 letters → 2
        #expect(runs.filter { $0.text == " " }.allSatisfy { $0.boldPrefix == 0 })
        // Bionic off → no bolding anywhere.
        let plain = KaraokeTextFormatter.runs(
            text: text, wordRanges: ranges, currentIndex: 1, bionic: false
        )
        #expect(plain.allSatisfy { $0.boldPrefix == 0 })
    }

    @Test("emoji and combining characters stay whole — still lossless")
    func emojiLossless() {
        let fancy = "Go 🎉 café now"
        let words = KaraokeTextFormatter.wordRanges(of: fancy)
        let runs = KaraokeTextFormatter.runs(
            text: fancy, wordRanges: words, currentIndex: 2, bionic: true
        )
        #expect(joined(runs) == fancy)
    }

    @Test("hostile ranges (unsorted, overlapping, out of bounds) never break losslessness")
    func hostileRanges() {
        let runs = KaraokeTextFormatter.runs(
            text: text, wordRanges: [12 ..< 17, 0 ..< 5, 3 ..< 8, 40 ..< 50], currentIndex: 0, bionic: false
        )
        #expect(joined(runs) == text)
    }

    // MARK: - Helpers for the no-timeline tier

    @Test("the whitespace tokenizer yields UTF-16 word ranges")
    func tokenizer() {
        let words = KaraokeTextFormatter.wordRanges(of: text)
        #expect(words == ranges)
        let ns = text as NSString
        #expect(ns.substring(with: NSRange(location: words[3].lowerBound, length: words[3].count)) == "Spain")
    }

    @Test("index(containing:) maps a spoken range onto its word")
    func indexContaining() {
        #expect(KaraokeTextFormatter.index(containing: 4 ..< 8, in: ranges) == 1)
        #expect(KaraokeTextFormatter.index(containing: 5 ..< 7, in: ranges) == 1) // partial overlap
        #expect(KaraokeTextFormatter.index(containing: 30 ..< 33, in: ranges) == nil)
    }
}
