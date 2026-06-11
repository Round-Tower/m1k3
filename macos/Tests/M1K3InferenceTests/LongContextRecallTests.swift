//
//  LongContextRecallTests.swift
//  M1K3InferenceTests
//
//  Pure core of the eval harness's long-context check: the needle prompt is
//  deterministic (needle EARLY — the end of the cache that quantization or
//  rotation would degrade first), and the pass criterion tolerates the code
//  formats models actually echo. Short eval prompts cannot catch KV-cache
//  quality loss; this one can.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8, Prior: Unknown
//

@testable import M1K3Inference
import Testing

struct LongContextRecallTests {
    @Test("prompt is deterministic")
    func deterministic() {
        #expect(LongContextRecall.prompt() == LongContextRecall.prompt())
    }

    @Test("needle appears exactly once, early in the prompt")
    func needleOnceAndEarly() throws {
        let prompt = LongContextRecall.prompt()
        let occurrences = prompt.components(separatedBy: LongContextRecall.needleCode).count - 1
        #expect(occurrences == 1)
        let position = try #require(prompt.range(of: LongContextRecall.needleCode)?.lowerBound)
        let fraction = Double(prompt.distance(from: prompt.startIndex, to: position))
            / Double(prompt.count)
        #expect(fraction < 0.1, "needle must land in the early cache region")
    }

    @Test("question comes last and filler scales the length")
    func questionLastAndLengthScales() {
        let prompt = LongContextRecall.prompt()
        #expect(prompt.hasSuffix("Reply with the code only."))
        // ~150 filler sentences ≈ thousands of tokens; rough floor in chars.
        #expect(prompt.count > 10000)
        #expect(LongContextRecall.prompt(fillerSentences: 50).count < prompt.count)
    }

    @Test("pass criterion accepts the code in the formats models echo")
    func passAcceptsEchoFormats() {
        #expect(LongContextRecall.passes("KESTREL-47"))
        #expect(LongContextRecall.passes("kestrel-47"))
        #expect(LongContextRecall.passes("The access code is KESTREL 47."))
        #expect(LongContextRecall.passes("Code: `KESTREL47`"))
    }

    @Test("pass criterion rejects wrong or empty answers")
    func passRejectsWrongAnswers() {
        #expect(!LongContextRecall.passes(""))
        #expect(!LongContextRecall.passes("I do not recall a code in the log."))
        #expect(!LongContextRecall.passes("KESTREL-48"))
        #expect(!LongContextRecall.passes("kestrel"))
    }
}
