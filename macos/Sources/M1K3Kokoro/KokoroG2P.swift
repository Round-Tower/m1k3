//
//  KokoroG2P.swift
//  M1K3Kokoro
//
//  Pure grapheme→phoneme→token mapping for the Kokoro ONNX model. Splits text into
//  words (looked up in a bundled pronunciation dictionary) and punctuation (mapped to
//  the model's vocab tokens), assembling the int64 token sequence Kokoro expects:
//  per-word phonemes joined by the space token, punctuation attached directly, wrapped
//  in [0 … 0] pad tokens.
//
//  The DICTIONARY is data (swappable — espeak-en-gb for the spike; a public-domain
//  CMUdict-derived set can replace it for ship with zero code change). THIS assembly
//  logic is the tested IP. Per-word lookup reproduces whole-sentence espeak phonemes
//  for the common case; minor sentence-prosody stress differences on some function
//  words (ˈ vs ˌ) are expected and benign.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-09, Confidence 0.75, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-11 — walker rewritten over the ORIGINAL
//  string (per-word lowercase for lookup only) to add annotatedTokens word
//  boundaries for karaoke timing; phonemeTokens is now a capped wrapper, all
//  prior tests untouched-green. Confidence 0.85.
//

import Foundation

/// One word as the G2P walker saw it: where it sits in the original text and
/// which of the assembled tokens are its own phonemes. Inter-word spaces and
/// punctuation belong to the token stream, never to a word's `tokenRange` —
/// the timing estimator attributes them. OOV words carry an empty `tokenRange`
/// at their stream position.
public struct G2PWord: Sendable, Equatable {
    /// UTF-16 offsets into the original input text (case preserved).
    public let textRange: Range<Int>
    /// Indices into `G2PResult.tokens`; empty for out-of-vocabulary words.
    public let tokenRange: Range<Int>

    public init(textRange: Range<Int>, tokenRange: Range<Int>) {
        self.textRange = textRange
        self.tokenRange = tokenRange
    }
}

/// Token assembly plus the word boundaries that produced it.
public struct G2PResult: Sendable, Equatable {
    public let tokens: [Int]
    public let words: [G2PWord]

    public init(tokens: [Int], words: [G2PWord]) {
        self.tokens = tokens
        self.words = words
    }
}

public struct KokoroG2P: Sendable {
    /// BOS/EOS pad token the model expects wrapping every input.
    public static let pad = 0
    /// The vocab's space token, emitted between adjacent words.
    public static let space = 16
    /// Model context limit on phoneme tokens (before the pad wrap).
    public static let maxTokens = 510

    private let dictionary: [String: [Int]]
    private let punctuation: [Character: Int]

    public init(
        dictionary: [String: [Int]],
        punctuation: [Character: Int] = KokoroG2P.defaultPunctuation
    ) {
        self.dictionary = dictionary
        self.punctuation = punctuation
    }

    /// Phoneme tokens WITHOUT the pad wrap. Its `.count` is the index into the voice
    /// style array, so it is capped at `maxTokens` on a word boundary.
    public func phonemeTokens(_ text: String) -> [Int] {
        assemble(text, cap: Self.maxTokens).tokens
    }

    /// Word-boundary-annotated assembly for the karaoke word-timing estimator.
    /// UNCAPPED — chunking (SpeechChunker) owns the 510 limit; capping here would
    /// silently truncate long answers.
    public func annotatedTokens(_ text: String) -> G2PResult {
        assemble(text, cap: nil)
    }

    /// The one walker behind both APIs. Walks the ORIGINAL string (case preserved,
    /// lowercasing only each extracted word for dictionary lookup) so word text
    /// ranges are exact by construction, tracking a running UTF-16 offset.
    private func assemble(_ text: String, cap: Int?) -> G2PResult {
        var tokens: [Int] = []
        var words: [G2PWord] = []
        var prevWasToken = false
        var offset = 0 // running UTF-16 offset into the original text
        let chars = Array(text)
        let charCount = chars.count
        var index = 0

        while index < charCount {
            let char = chars[index]
            if char.isLetter {
                // Maximal word run: letters + internal apostrophes.
                var wordEnd = index
                var wordUTF16 = 0
                while wordEnd < charCount, chars[wordEnd].isLetter || chars[wordEnd] == "'" {
                    wordUTF16 += chars[wordEnd].utf16.count
                    wordEnd += 1
                }
                let raw = String(chars[index ..< wordEnd]).lowercased()
                let textRange = offset ..< offset + wordUTF16
                index = wordEnd
                offset += wordUTF16
                guard let toks = lookup(raw) else {
                    // OOV: no phonemes, no space — an empty token range at the
                    // current position keeps the word addressable for timing.
                    words.append(G2PWord(textRange: textRange, tokenRange: tokens.count ..< tokens.count))
                    continue
                }
                let needed = (prevWasToken ? 1 : 0) + toks.count
                if let cap, tokens.count + needed > cap { break }
                if prevWasToken { tokens.append(Self.space) }
                let tokenStart = tokens.count
                tokens.append(contentsOf: toks)
                words.append(G2PWord(textRange: textRange, tokenRange: tokenStart ..< tokens.count))
                prevWasToken = true
            } else if char == " " || char == "\n" || char == "\t" || char == "\r" {
                offset += 1 // whitespace → implicit space, emitted only before the next word
                index += 1
            } else if let tok = punctuation[char] {
                if let cap, tokens.count + 1 > cap { break }
                tokens.append(tok)
                prevWasToken = true
                offset += char.utf16.count
                index += 1
            } else {
                offset += char.utf16.count // unknown character → skip
                index += 1
            }
        }
        return G2PResult(tokens: tokens, words: words)
    }

    /// Model input tokens = [pad] + phonemes + [pad].
    public func modelTokens(_ text: String) -> [Int] {
        [Self.pad] + phonemeTokens(text) + [Self.pad]
    }

    /// Try the word as-is (keeps contractions like `don't`), then with surrounding
    /// apostrophes trimmed (handles quote-wrapped words like `'hello'`).
    private func lookup(_ word: String) -> [Int]? {
        if let hit = dictionary[word] { return hit }
        let trimmed = word.trimmingCharacters(in: CharacterSet(charactersIn: "'"))
        if trimmed != word, let hit = dictionary[trimmed] { return hit }
        return nil
    }

    /// Text-facing punctuation → vocab token. Prosody marks (combining tilde, arrows)
    /// are intentionally excluded — they come from the phonemizer, not raw text.
    public static let defaultPunctuation: [Character: Int] = [
        ";": 1, ":": 2, ",": 3, ".": 4, "!": 5, "?": 6,
        "—": 9, "…": 10, "\"": 11, "(": 12, ")": 13, "“": 14, "”": 15,
    ]
}
