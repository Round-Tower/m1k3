//
//  WERScorerTests.swift
//  M1K3EvalTests
//
//  Pins the word/character error-rate scorer that will turn "the transcripts feel
//  better" into a number — so the Phase-1 endpointing thresholds and the Phase-4
//  model choice can be tuned on evidence, not feel.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.9, Prior: Unknown

@testable import M1K3Eval
import Testing

struct WERScorerTests {
    @Test("identical text scores zero error")
    func identical() {
        let score = WERScorer.score(reference: "the cat sat on the mat", hypothesis: "the cat sat on the mat")
        #expect(score.wordErrorRate == 0)
        #expect(score.characterErrorRate == 0)
    }

    @Test("one substitution is one edit over the reference word count")
    func substitution() {
        let score = WERScorer.score(reference: "the cat sat", hypothesis: "the dog sat")
        #expect(score.wordEdits == 1)
        #expect(score.referenceWordCount == 3)
        #expect(abs(score.wordErrorRate - 1.0 / 3.0) < 1e-9)
    }

    @Test("a deletion and an insertion each count as one word edit")
    func deletionAndInsertion() {
        #expect(WERScorer.score(reference: "the cat sat", hypothesis: "the sat").wordEdits == 1)
        #expect(WERScorer.score(reference: "the cat", hypothesis: "the big cat").wordEdits == 1)
    }

    @Test("scoring is case- and punctuation-insensitive (normalized like real WER)")
    func normalized() {
        let score = WERScorer.score(reference: "Hello, world!", hypothesis: "hello world")
        #expect(score.wordErrorRate == 0)
    }

    @Test("an empty reference against output is full error; both empty is zero")
    func emptyReference() {
        #expect(WERScorer.score(reference: "", hypothesis: "something").wordErrorRate == 1)
        #expect(WERScorer.score(reference: "", hypothesis: "").wordErrorRate == 0)
    }

    @Test("character error rate catches sub-word slips")
    func characterErrorRate() {
        // "cat" vs "bat": one of three characters wrong.
        let score = WERScorer.score(reference: "cat", hypothesis: "bat")
        #expect(abs(score.characterErrorRate - 1.0 / 3.0) < 1e-9)
        // ...but at the WORD level it's a full substitution.
        #expect(score.wordErrorRate == 1)
    }
}
