import Foundation
@testable import M1K3Kokoro
import Testing

/// Pins `annotatedTokens` — the word-boundary-annotated assembly that feeds the
/// karaoke word-timing estimator. Ranges are UTF-16 offsets into the ORIGINAL
/// string (case preserved); `tokenRange` covers each word's own phonemes only.
struct KokoroG2PAnnotatedTests {
    private let dict: [String: [Int]] = [
        "hello": [50, 83, 54, 156, 83, 135],
        "world": [65, 156, 87, 158, 54, 46],
        "don't": [46, 156, 83, 135, 56, 62],
        "aa": [101],
        "bb": [102],
    ]

    private func g2p() -> KokoroG2P {
        KokoroG2P(dictionary: dict)
    }

    @Test("tokens match the un-annotated assembly exactly")
    func tokensMatchOracle() {
        let result = g2p().annotatedTokens("Hello world.")
        #expect(result.tokens == [50, 83, 54, 156, 83, 135, 16, 65, 156, 87, 158, 54, 46, 4])
    }

    @Test("word text ranges index the ORIGINAL string, case preserved")
    func textRangesExact() {
        let result = g2p().annotatedTokens("Hello world.")
        #expect(result.words.count == 2)
        #expect(result.words[0].textRange == 0 ..< 5) // "Hello"
        #expect(result.words[1].textRange == 6 ..< 11) // "world"
    }

    @Test("token ranges cover each word's own phonemes — space and punctuation excluded")
    func tokenRangesOwnPhonemes() {
        let result = g2p().annotatedTokens("Hello world.")
        #expect(result.words[0].tokenRange == 0 ..< 6) // hello phonemes
        #expect(result.words[1].tokenRange == 7 ..< 13) // world phonemes (16 at 6, '.' at 13)
    }

    @Test("OOV words keep their text range with an empty token range")
    func oovEmptyTokenRange() {
        let result = g2p().annotatedTokens("aa zzzqx bb")
        #expect(result.tokens == [101, 16, 102])
        #expect(result.words.count == 3)
        #expect(result.words[0].textRange == 0 ..< 2)
        #expect(result.words[0].tokenRange == 0 ..< 1)
        #expect(result.words[1].textRange == 3 ..< 8) // "zzzqx"
        #expect(result.words[1].tokenRange.isEmpty)
        #expect(result.words[2].textRange == 9 ..< 11)
        #expect(result.words[2].tokenRange == 2 ..< 3)
    }

    @Test("punctuation lands in tokens but never in the word list")
    func punctuationNotAWord() {
        let result = g2p().annotatedTokens("aa, bb!")
        #expect(result.tokens == [101, 3, 16, 102, 5])
        #expect(result.words.count == 2)
        #expect(result.words[0].tokenRange == 0 ..< 1)
        #expect(result.words[1].tokenRange == 3 ..< 4)
    }

    @Test("quote-wrapped words: the run includes the trailing apostrophe, lookup still hits")
    func quotedWord() {
        let result = g2p().annotatedTokens("'hello'")
        #expect(result.tokens == [50, 83, 54, 156, 83, 135])
        #expect(result.words.count == 1)
        // The leading ' is skipped (unknown char); the trailing ' joins the word run.
        #expect(result.words[0].textRange == 1 ..< 7)
        #expect(result.words[0].tokenRange == 0 ..< 6)
    }

    @Test("annotated assembly is uncapped — chunking owns the 510 limit now")
    func uncapped() {
        let many = Array(repeating: "hello", count: 200).joined(separator: " ")
        let result = g2p().annotatedTokens(many)
        #expect(result.tokens.count == 200 * 6 + 199) // 1399: phonemes + inter-word spaces
        #expect(result.words.count == 200)
    }

    @Test("offsets are UTF-16: an emoji counts as two units")
    func utf16Offsets() {
        let result = g2p().annotatedTokens("🎉 aa")
        #expect(result.words.count == 1)
        #expect(result.words[0].textRange == 3 ..< 5) // 🎉 is 2 UTF-16 units + space
        let ns = "🎉 aa" as NSString
        #expect(ns.substring(with: NSRange(location: 3, length: 2)) == "aa")
    }

    @Test("the capped wrapper agrees with the annotated tokens up to the cap")
    func cappedWrapperPrefix() {
        let many = Array(repeating: "hello", count: 200).joined(separator: " ")
        let capped = g2p().phonemeTokens(many)
        let full = g2p().annotatedTokens(many).tokens
        #expect(capped.count <= KokoroG2P.maxTokens)
        #expect(Array(full.prefix(capped.count)) == capped)
    }
}
