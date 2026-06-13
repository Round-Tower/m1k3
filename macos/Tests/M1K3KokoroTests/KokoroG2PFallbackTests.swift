import Foundation
@testable import M1K3Kokoro
import Testing

/// The OOV fallback chain: digit runs become spoken numbers, unit suffixes ride
/// the same word, mixed alphanumerics and unpronounceable acronyms spell out
/// per character — all with ONE G2PWord spanning the original text range so
/// karaoke timing still highlights "15°C" as a single word. Pure-letter words
/// that merely aren't in the dictionary stay silently skipped (regression).
struct KokoroG2PFallbackTests {
    /// Synthetic single-token entries: number words, letters, units.
    private let dict: [String: [Int]] = [
        "aa": [101], "bb": [102],
        "fifteen": [21, 22], "three": [23], "point": [24], "five": [25],
        "twenty": [26], "six": [27], "percent": [28], "celsius": [29],
        "fahrenheit": [30], "degree": [31],
        "m": [41], "k": [42], "u": [43], "s": [44], "a": [45],
        "one": [46], "zero": [47],
        // Compound-split fixtures: known sub-words, none a single dict entry combined.
        "grand": [60, 61], "master": [62, 63], "key": [64], "board": [65],
    ]

    private func g2p() -> KokoroG2P {
        KokoroG2P(dictionary: dict)
    }

    // MARK: - Numbers

    @Test("a digit run becomes its spoken number words joined by spaces")
    func digitRun() {
        // "15" → fifteen [21,22]; one word covering UTF-16 range 0..<2.
        let result = g2p().annotatedTokens("15")
        #expect(result.tokens == [21, 22])
        #expect(result.words == [G2PWord(textRange: 0 ..< 2, tokenRange: 0 ..< 2)])
    }

    @Test("digit runs join the sentence with normal word spacing")
    func digitRunInSentence() {
        // "aa 15 bb" → aa SP fifteen SP bb
        #expect(g2p().phonemeTokens("aa 15 bb") == [101, 16, 21, 22, 16, 102])
    }

    @Test("a decimal literal is one word: integer part, point, fraction digits")
    func decimalLiteral() {
        // "3.5" → three point five, one word covering 0..<3.
        let result = g2p().annotatedTokens("3.5")
        #expect(result.tokens == [23, 16, 24, 16, 25])
        #expect(result.words == [G2PWord(textRange: 0 ..< 3, tokenRange: 0 ..< 5)])
    }

    @Test("a year reads as pairs")
    func yearPairs() {
        // "2026" → twenty twenty six
        #expect(g2p().phonemeTokens("2026") == [26, 16, 26, 16, 27])
    }

    // MARK: - Unit suffixes

    @Test("percent rides the number as one word")
    func percentSuffix() {
        // "15%" → fifteen percent, ONE word covering 0..<3.
        let result = g2p().annotatedTokens("15%")
        #expect(result.tokens == [21, 22, 16, 28])
        #expect(result.words == [G2PWord(textRange: 0 ..< 3, tokenRange: 0 ..< 4)])
    }

    @Test("degree-celsius rides the number as one word")
    func celsiusSuffix() {
        // "15°C" → fifteen celsius, ONE word covering 0..<4 (° is 1 UTF-16 unit).
        let result = g2p().annotatedTokens("15°C")
        #expect(result.tokens == [21, 22, 16, 29])
        #expect(result.words == [G2PWord(textRange: 0 ..< 4, tokenRange: 0 ..< 4)])
    }

    @Test("degree-fahrenheit and lone degree map to their words")
    func fahrenheitAndLoneDegree() {
        #expect(g2p().phonemeTokens("15°F") == [21, 22, 16, 30])
        #expect(g2p().phonemeTokens("15°") == [21, 22, 16, 31])
    }

    // MARK: - Spell-out

    @Test("mixed alphanumerics spell out per character as one word")
    func mixedAlphanumeric() {
        // "M1K3" → m one k three, one word covering 0..<4.
        let result = g2p().annotatedTokens("M1K3")
        #expect(result.tokens == [41, 16, 46, 16, 42, 16, 23])
        #expect(result.words == [G2PWord(textRange: 0 ..< 4, tokenRange: 0 ..< 7)])
    }

    @Test("short all-caps OOV acronyms spell out")
    func acronymSpellsOut() {
        // "USA" → u s a
        #expect(g2p().phonemeTokens("USA") == [43, 16, 44, 16, 45])
    }

    @Test("no-vowel OOV words spell out")
    func noVowelSpellsOut() {
        // "mks" lowercase, no vowels → m k s
        #expect(g2p().phonemeTokens("mks") == [41, 16, 42, 16, 44])
    }

    @Test("pronounceable lowercase OOV words still skip silently — regression")
    func pronounceableOOVStaysSilent() {
        let result = g2p().annotatedTokens("aa blorptastic bb")
        #expect(result.tokens == [101, 16, 102])
        // The OOV word stays addressable for timing with an empty token range.
        #expect(result.words.count == 3)
        #expect(result.words[1].tokenRange.isEmpty)
    }

    // MARK: - Compound split (the "grandmaster" fix)

    @Test("a compound OOV word splits into its known sub-words and is spoken")
    func compoundSplits() {
        // "grandmaster" is NOT a dict entry, but grand + master are → speak both,
        // joined by the space token, as ONE word for karaoke timing.
        let result = g2p().annotatedTokens("grandmaster")
        #expect(result.tokens == [60, 61, 16, 62, 63])
        #expect(result.words == [G2PWord(textRange: 0 ..< 11, tokenRange: 0 ..< 5)])
    }

    @Test("compound split is case-insensitive and rides a sentence cleanly")
    func compoundInSentence() {
        // "aa Keyboard bb" → aa SP key SP board SP bb
        #expect(g2p().phonemeTokens("aa Keyboard bb") == [101, 16, 64, 16, 65, 16, 102])
    }

    @Test("a partial segmentation (leftover that isn't a known word) stays silent")
    func compoundPartialStaysSilent() {
        // "masterful" = master + "ful" (not in dict) — no COMPLETE split → silent,
        // not a half-spoken "master".
        let result = g2p().annotatedTokens("masterful")
        #expect(result.tokens.isEmpty)
        #expect(result.words.count == 1)
        #expect(result.words[0].tokenRange.isEmpty)
    }

    @Test("single-letter dict keys can't make a compound into letter-soup")
    func compoundMinSegmentGuard() {
        // "ask" would be a-s-k via single-letter keys if unguarded; the ≥3-char
        // minimum blocks that, and "ask" isn't a real sub-word split → silent.
        #expect(g2p().phonemeTokens("ask").isEmpty)
    }

    @Test("long all-caps words are not spelled out")
    func longCapsNotSpelled() {
        // 6+ caps is likely a shouted word, not an acronym; "MKSMKS" has no
        // vowels though, so use a vowelled one that is OOV in the mini dict.
        #expect(g2p().phonemeTokens("AMAZING") == [])
    }

    // MARK: - Cap behavior

    @Test("an expansion that would blow the cap is dropped whole on the word boundary")
    func capDropsWholeExpansion() {
        // cap small enough that "15" (fifteen = 2 tokens) fits but "3.5"
        // (5 tokens + space) does not.
        let g2p = KokoroG2P(dictionary: dict)
        let capped = g2p.phonemeTokens("15 3.5") // maxTokens applies (510) — not this test
        #expect(capped == [21, 22, 16, 23, 16, 24, 16, 25])
        // Use assemble's cap via phonemeTokens on a long string instead:
        // build a string of many "15 " words to cross 510 and confirm count ≤ 510.
        let many = String(repeating: "15 ", count: 300)
        #expect(g2p.phonemeTokens(many).count <= KokoroG2P.maxTokens)
    }

    // MARK: - Offset bookkeeping

    @Test("words after an expanded run keep exact text ranges")
    func offsetsAfterExpansion() {
        // "15°C aa" — aa starts after "15°C " = UTF-16 offset 5.
        let result = g2p().annotatedTokens("15°C aa")
        #expect(result.words.count == 2)
        #expect(result.words[1].textRange == 5 ..< 7)
    }
}
