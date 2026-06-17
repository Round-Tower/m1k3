//
//  UtteranceCompleteness.swift
//  M1K3Voice
//
//  Decides whether a live partial looks like a finished thought. The endpointer
//  uses it to wait LONGER before ending a listen that clearly trails off
//  mid-sentence ("tell me about the" <pause> "weather"), so a natural pause no
//  longer fragments a multi-clause utterance into half-thoughts the model then
//  reasons over incorrectly.
//
//  Conservative by design: only POSITIVE evidence of incompleteness — a dangling
//  connective word or a trailing comma — marks a partial incomplete. Terminal
//  punctuation (the recognizer signalling a sentence) always reads complete, and
//  anything else defaults to complete, so an ordinary utterance is never held
//  longer than it needs to be.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.85, Prior: Unknown

import Foundation

public enum UtteranceCompleteness {
    /// True when `text` reads like a finished thought; false when it trails off on
    /// a connective/filler or a comma (so the caller should keep listening).
    public static func looksComplete(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let last = trimmed.last else { return false } // empty → nothing yet
        if ".!?".contains(last) { return true } // recognizer marked a sentence end
        if last == "," { return false }

        // The final word, lowercased and stripped of any non-letter edges.
        let lastWord = trimmed
            .split(whereSeparator: { $0 == " " || $0 == "\n" || $0 == "\t" })
            .last
            .map { $0.lowercased().trimmingCharacters(in: danglingTrim) } ?? ""
        return !danglingWords.contains(lastWord)
    }

    /// Punctuation peeled off the final word before the stopword check.
    private static let danglingTrim = CharacterSet.letters.inverted

    /// Words that, when they END an unpunctuated utterance, signal more is coming.
    /// Kept to clear connectives/determiners/fillers — ambiguous words are
    /// deliberately EXCLUDED to avoid false holds on complete clauses:
    ///   • pronouns his/her/its, this/that ("what was that")
    ///   • "so"/"yet" — far more often sentence-final short answers ("I think so",
    ///     "not yet"), which live partials emit without a period, than dangling
    ///     conjunctions; holding them 3 s punishes the most common replies.
    /// "like" is kept (filler "it was, like," / "I'd like" usually continues),
    /// accepting the rare false-hold on "the way I like".
    private static let danglingWords: Set<String> = [
        // conjunctions
        "and", "but", "or", "nor", "because", "although", "though",
        "while", "if", "unless", "since", "whereas", "plus",
        // prepositions
        "to", "of", "in", "on", "at", "by", "for", "with", "from", "into",
        "onto", "upon", "about", "as", "than",
        // articles / possessive determiners
        "a", "an", "the", "my", "your", "our", "their",
        // fillers
        "um", "uh", "er", "erm", "hmm", "eh", "like",
    ]
}
