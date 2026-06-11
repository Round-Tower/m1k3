//
//  SpeechTextPolish.swift
//  M1K3Voice
//
//  Sanitizes assistant text for SPEECH ONLY — the chat transcript keeps the
//  full text. The polished string is what `speak()` hands the providers, so
//  the karaoke view displays it too: the SpokenWordTimeline contract
//  (displayed text == spoken text) holds automatically.
//
//  What gets removed, and why:
//  • The trailing "Web sources:" bullet block — URLs read aloud are noise.
//  • Citation tokens `[Title §heading]` / `(Title §heading)` — visual
//    affordances, not speech. Plain brackets without a § survive.
//  • Inline URLs collapse to their bare host ("weather.com") — dropping them
//    entirely orphans sentences like "according to ."
//  • Curly quotes normalize to ASCII so Kokoro's dictionary hits
//    contractions ("don't" is a dict key; "don’t" is not).
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (pure string
//  transform, every rule test-pinned; URL-host readability is a taste call).
//  Prior: Unknown.
//

import Foundation

public enum SpeechTextPolish {
    /// One pass is a fixed point: every rule only removes or normalizes,
    /// never produces new strippable material.
    public static func polish(_ text: String) -> String {
        var result = text
        result = stripWebSourcesBlock(result)
        result = stripCitations(result)
        result = collapseURLs(result)
        result = normalizeCurlyPunctuation(result)
        result = tidyWhitespace(result)
        return result
    }

    // MARK: - Rules

    /// Anchored to the end of the string: a "Web sources:" line followed only
    /// by bullet lines. Mid-prose mentions of "web sources" are untouched.
    private static func stripWebSourcesBlock(_ text: String) -> String {
        text.replacing(/(?:^|\n+)Web sources:\n(?:•[^\n]*\n?)*$/, with: "")
    }

    /// Citation tokens carry a `§` between the source title and heading —
    /// that marker is the discriminator (plain `[1]` or `(see above)` stay).
    private static func stripCitations(_ text: String) -> String {
        var result = text.replacing(/\[[^\]\n]*§[^\]\n]*\]/, with: "")
        result = result.replacing(/\([^)\n]*§[^)\n]*\)/, with: "")
        return result
    }

    /// `https://www.weather.com/today?x=1` → `weather.com`.
    private static func collapseURLs(_ text: String) -> String {
        text.replacing(/https?:\/\/(?:www\.)?([^\/\s?#]+)[^\s]*/) { match in
            // A URL swallows trailing sentence punctuation into its path —
            // peel it back off so "at https://met.ie/x." reads "at met.ie."
            let tail = match.output.0.last.map(String.init) ?? ""
            let punctuation = [".", ",", "!", "?", ";", ":"].contains(tail) ? tail : ""
            return String(match.output.1) + punctuation
        }
    }

    private static func normalizeCurlyPunctuation(_ text: String) -> String {
        text
            .replacingOccurrences(of: "\u{2019}", with: "'")
            .replacingOccurrences(of: "\u{2018}", with: "'")
            .replacingOccurrences(of: "\u{201C}", with: "\"")
            .replacingOccurrences(of: "\u{201D}", with: "\"")
    }

    private static func tidyWhitespace(_ text: String) -> String {
        var result = text.replacing(/[ \t]+/, with: " ")
        // Plain string ops, not a capture-group regex — the repo formatter
        // strips "redundant" parens inside regex literals and breaks .output.
        for punctuation in [".", ",", "!", "?", ";", ":"] {
            result = result.replacingOccurrences(of: " " + punctuation, with: punctuation)
        }
        result = result.replacing(/[ \t]+\n/, with: "\n")
        result = result.replacing(/\n[ \t]+/, with: "\n")
        result = result.replacing(/\n{3,}/, with: "\n\n")
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
