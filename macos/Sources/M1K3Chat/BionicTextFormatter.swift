//
//  BionicTextFormatter.swift
//  M1K3Chat
//
//  Bionic-reading transform: embolden the leading characters of each word so the
//  eye fixates on word-starts and the brain fills the rest — a reading aid that
//  helps some dyslexic / ADHD readers. Pure splitting logic (no SwiftUI); the view
//  turns the runs into an AttributedString. Concatenating every run's bold+rest
//  reproduces the original text exactly, so nothing is dropped or reordered.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.85, Prior: Unknown

import Foundation

public enum BionicTextFormatter {
    /// How many leading characters of a word to embolden, by the word's
    /// leading-letter length: 1–3 → 1, 4–6 → 2, 7+ → ~40%.
    public static func boldPrefixCount(for word: Substring) -> Int {
        let count = word.count
        switch count {
        case 0: return 0
        case 1 ... 3: return 1
        case 4 ... 6: return 2
        default: return Int(ceil(Double(count) * 0.4))
        }
    }

    /// Split `text` into ordered (bold, rest) runs. Whitespace tokens pass through
    /// untouched (empty bold); for word tokens the leading letters are emboldened
    /// and any trailing punctuation stays in `rest`.
    public static func runs(_ text: String) -> [(bold: Substring, rest: Substring)] {
        guard !text.isEmpty else { return [] }
        var result: [(bold: Substring, rest: Substring)] = []

        var index = text.startIndex
        while index < text.endIndex {
            let isSpace = text[index].isWhitespace
            // Consume a maximal run of same-kind characters (all whitespace, or all
            // non-whitespace) as one token.
            var end = index
            while end < text.endIndex, text[end].isWhitespace == isSpace {
                end = text.index(after: end)
            }
            let token = text[index ..< end]

            if isSpace {
                result.append((bold: text[index ..< index], rest: token))
            } else {
                // Bold the leading letters of the token; punctuation/digits after
                // the letter run stay in `rest`.
                let letters = token.prefix { $0.isLetter }
                let boldCount = boldPrefixCount(for: letters)
                let split = token.index(token.startIndex, offsetBy: boldCount)
                result.append((bold: token[token.startIndex ..< split], rest: token[split ..< token.endIndex]))
            }
            index = end
        }
        return result
    }
}
