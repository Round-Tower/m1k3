//
//  UtteranceCompletenessTests.swift
//  M1K3VoiceTests
//
//  Pins the heuristic that decides whether a live partial looks like a finished
//  thought. It exists to stop the endpointer cutting a multi-clause utterance at
//  the first mid-thought pause ("tell me about the" <pause> "weather") — the
//  prime cause of the model reasoning over fragments.
//
//  Design: only POSITIVE evidence of incompleteness (a dangling connective, a
//  trailing comma) marks a partial incomplete. Anything else is treated as
//  complete, so we never over-hold an ordinary utterance.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.85, Prior: Unknown

import M1K3Voice
import Testing

struct UtteranceCompletenessTests {
    @Test("a plain finished clause is complete")
    func plainComplete() {
        #expect(UtteranceCompleteness.looksComplete("what's the weather"))
        #expect(UtteranceCompleteness.looksComplete("Dublin"))
        #expect(UtteranceCompleteness.looksComplete("I think so."))
    }

    @Test("common short answers ending in so/yet are complete WITHOUT a period")
    func shortAnswersComplete() {
        // Live partials don't carry terminal punctuation; "so"/"yet" must not hold.
        #expect(UtteranceCompleteness.looksComplete("I think so"))
        #expect(UtteranceCompleteness.looksComplete("I hope so"))
        #expect(UtteranceCompleteness.looksComplete("not yet"))
    }

    @Test("a trailing contraction is complete")
    func trailingContraction() {
        #expect(UtteranceCompleteness.looksComplete("no I don't"))
        #expect(UtteranceCompleteness.looksComplete("I can't"))
    }

    @Test("terminal punctuation always reads as complete (recognizer marked a sentence)")
    func terminalPunctuation() {
        #expect(UtteranceCompleteness.looksComplete("what time is it?"))
        #expect(UtteranceCompleteness.looksComplete("stop!"))
        #expect(UtteranceCompleteness.looksComplete("ok."))
        #expect(UtteranceCompleteness.looksComplete("...")) // punctuation-only ends on a terminal mark
    }

    @Test("a dangling conjunction is incomplete")
    func danglingConjunction() {
        #expect(!UtteranceCompleteness.looksComplete("I want the weather and"))
        #expect(!UtteranceCompleteness.looksComplete("check it because"))
        #expect(!UtteranceCompleteness.looksComplete("do it but"))
    }

    @Test("a dangling preposition or article is incomplete")
    func danglingPrepositionOrArticle() {
        #expect(!UtteranceCompleteness.looksComplete("tell me about the"))
        #expect(!UtteranceCompleteness.looksComplete("the meeting is at"))
        #expect(!UtteranceCompleteness.looksComplete("send it to"))
        #expect(!UtteranceCompleteness.looksComplete("a"))
    }

    @Test("a dangling filler word is incomplete")
    func danglingFiller() {
        #expect(!UtteranceCompleteness.looksComplete("um"))
        #expect(!UtteranceCompleteness.looksComplete("so I was thinking uh"))
    }

    @Test("a trailing comma is incomplete")
    func trailingComma() {
        #expect(!UtteranceCompleteness.looksComplete("first of all,"))
    }

    @Test("empty or whitespace is not complete")
    func emptyNotComplete() {
        #expect(!UtteranceCompleteness.looksComplete(""))
        #expect(!UtteranceCompleteness.looksComplete("   "))
    }

    @Test("dangling check is case-insensitive and tolerates trailing spaces")
    func caseAndSpace() {
        #expect(!UtteranceCompleteness.looksComplete("tell me about THE  "))
        #expect(!UtteranceCompleteness.looksComplete("And"))
    }

    @Test("a word that merely contains a stopword is still complete")
    func notAStopwordSubstring() {
        // "android" ends in "and" letters but is not the word "and".
        #expect(UtteranceCompleteness.looksComplete("install android"))
        #expect(UtteranceCompleteness.looksComplete("go to the theatre"))
    }
}
