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
//

import Foundation

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
        var out: [Int] = []
        var prevWasToken = false
        let chars = Array(text.lowercased())
        let charCount = chars.count
        var index = 0

        while index < charCount {
            let char = chars[index]
            if char.isLetter {
                // Maximal word run: letters + internal apostrophes.
                var wordEnd = index
                while wordEnd < charCount, chars[wordEnd].isLetter || chars[wordEnd] == "'" {
                    wordEnd += 1
                }
                let raw = String(chars[index ..< wordEnd])
                index = wordEnd
                guard let toks = lookup(raw) else { continue }
                let needed = (prevWasToken ? 1 : 0) + toks.count
                if out.count + needed > Self.maxTokens { break }
                if prevWasToken { out.append(Self.space) }
                out.append(contentsOf: toks)
                prevWasToken = true
            } else if char == " " || char == "\n" || char == "\t" || char == "\r" {
                index += 1 // whitespace → implicit space, emitted only before the next word
            } else if let tok = punctuation[char] {
                if out.count + 1 > Self.maxTokens { break }
                out.append(tok)
                prevWasToken = true
                index += 1
            } else {
                index += 1 // unknown character → skip
            }
        }
        return out
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
