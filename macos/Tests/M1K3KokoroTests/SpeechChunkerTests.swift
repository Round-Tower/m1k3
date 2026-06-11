import Foundation
@testable import M1K3Kokoro
import Testing

/// Pins the sentence-aware chunker that keeps every Kokoro input under the
/// model's 510-token context. Ranges are UTF-16 offsets that tile the input
/// losslessly; splits prefer sentence enders, then commas, then word
/// boundaries. The token counter is injected — tests count letters.
struct SpeechChunkerTests {
    /// One "token" per letter — deterministic and dictionary-free.
    private let letterCount: (Substring) -> Int = { $0.filter(\.isLetter).count }

    private func chunks(_ text: String, max: Int) -> [Range<Int>] {
        SpeechChunker.chunkRanges(text, tokenCount: letterCount, maxTokens: max)
    }

    private func substrings(_ text: String, _ ranges: [Range<Int>]) -> [String] {
        let ns = text as NSString
        return ranges.map { ns.substring(with: NSRange(location: $0.lowerBound, length: $0.count)) }
    }

    @Test("a short text is a single chunk covering the whole string")
    func singleChunk() {
        let text = "One two. Three four."
        let ranges = chunks(text, max: 100)
        #expect(ranges == [0 ..< (text as NSString).length])
    }

    @Test("sentences pack greedily up to the budget")
    func greedyPacking() {
        let text = "One two. Three four. Five six."
        let pieces = substrings(text, chunks(text, max: 16))
        #expect(pieces == ["One two. Three four. ", "Five six."])
    }

    @Test("ranges tile the text losslessly — concatenation reproduces the input")
    func losslessTiling() {
        let text = "One two. Three four. Five six. Seven eight nine ten!"
        for max in [8, 12, 20, 100] {
            let pieces = substrings(text, chunks(text, max: max))
            #expect(pieces.joined() == text, "max=\(max)")
        }
    }

    @Test("every chunk respects the budget, measured on the joined substring")
    func budgetRespected() {
        let text = "Alpha beta gamma. Delta epsilon, zeta eta theta, iota kappa. Lambda mu!"
        for max in [10, 15, 25] {
            for piece in substrings(text, chunks(text, max: max)) {
                #expect(letterCount(piece[...]) <= max, "piece '\(piece)' max=\(max)")
            }
        }
    }

    @Test("an oversized sentence splits at commas before word boundaries")
    func commaSplit() {
        let text = "aaa bbb, ccc ddd, eee fff."
        let pieces = substrings(text, chunks(text, max: 14))
        #expect(pieces == ["aaa bbb, ccc ddd, ", "eee fff."])
    }

    @Test("a wall of words with no punctuation still splits at word boundaries")
    func wallOfWords() {
        let text = Array(repeating: "aa", count: 11).joined(separator: " ")
        let ranges = chunks(text, max: 9)
        #expect(ranges.count > 1)
        let pieces = substrings(text, ranges)
        #expect(pieces.joined() == text)
        for piece in pieces {
            #expect(letterCount(piece[...]) <= 9, "piece '\(piece)'")
        }
        // No piece splits mid-word.
        for piece in pieces {
            #expect(!piece.hasPrefix("a ") || piece.first != " ")
            #expect(piece.trimmingCharacters(in: .whitespaces).split(separator: " ").allSatisfy { $0 == "aa" })
        }
    }

    @Test("offsets are UTF-16 — emoji-laden text still tiles losslessly")
    func utf16Tiling() {
        let text = "Great 🎉 news today. More 🚀 to come. The end."
        let pieces = substrings(text, chunks(text, max: 12))
        #expect(pieces.joined() == text)
        #expect(pieces.count > 1)
    }

    @Test("empty and whitespace-only inputs produce no chunks")
    func emptyInput() {
        #expect(chunks("", max: 10).isEmpty)
        #expect(chunks("   ", max: 10).isEmpty)
    }
}
