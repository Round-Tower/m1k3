//
//  CitationValidatorTests.swift
//  M1K3KnowledgeTests
//
//  Contract tests for citation validation: parse both delimiter forms, keep
//  real citations, strip hallucinated ones from the text, dedupe repeats, and
//  the empty case. Existence is an injected predicate — no store needed.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct CitationValidatorTests {
    /// Predicate over a known-good set of citations.
    private func knows(_ real: Set<Citation>) -> @Sendable (Citation) async -> Bool {
        { real.contains($0) }
    }

    @Test("a real bracket citation is validated and left in place")
    func realBracket() async {
        let real: Set<Citation> = [Citation(source: "ICH-Q7", heading: "5.2 Cleaning")]
        let result = await CitationValidator.validate(
            responseText: "Follow the procedure [ICH-Q7 §5.2 Cleaning] carefully.",
            exists: knows(real)
        )
        #expect(result.validated == [Citation(source: "ICH-Q7", heading: "5.2 Cleaning")])
        #expect(result.stripped.isEmpty)
        #expect(result.cleanedText.contains("[ICH-Q7 §5.2 Cleaning]"))
    }

    @Test("paren-delimited citations are recognised (Apple FM style)")
    func parenForm() async {
        let real: Set<Citation> = [Citation(source: "CFR-211", heading: "63 Equipment")]
        let result = await CitationValidator.validate(
            responseText: "Equipment must be clean (CFR-211 §63 Equipment).",
            exists: knows(real)
        )
        #expect(result.validated.count == 1)
        #expect(result.stripped.isEmpty)
    }

    @Test("a hallucinated citation is stripped from the text and reported")
    func hallucinatedStripped() async {
        let real: Set<Citation> = [] // nothing is real
        let result = await CitationValidator.validate(
            responseText: "This claim [ICH-Q7 §99.9 Invented] is fabricated.",
            exists: knows(real)
        )
        #expect(result.validated.isEmpty)
        #expect(result.stripped == [Citation(source: "ICH-Q7", heading: "99.9 Invented")])
        #expect(!result.cleanedText.contains("99.9 Invented"))
        #expect(result.cleanedText.contains("This claim"))
        #expect(result.cleanedText.contains("is fabricated."))
    }

    @Test("mixed real and hallucinated: keep one, strip the other")
    func mixed() async {
        let real: Set<Citation> = [Citation(source: "ICH-Q7", heading: "5.2 Cleaning")]
        let result = await CitationValidator.validate(
            responseText: "Real [ICH-Q7 §5.2 Cleaning] and fake [ICH-Q7 §0.0 Nope].",
            exists: knows(real)
        )
        #expect(result.validated.count == 1)
        #expect(result.stripped.count == 1)
        #expect(result.cleanedText.contains("5.2 Cleaning"))
        #expect(!result.cleanedText.contains("0.0 Nope"))
    }

    @Test("a citation repeated twice is validated once")
    func dedupe() async {
        let real: Set<Citation> = [Citation(source: "ICH-Q7", heading: "5.2 Cleaning")]
        let result = await CitationValidator.validate(
            responseText: "See [ICH-Q7 §5.2 Cleaning] and again [ICH-Q7 §5.2 Cleaning].",
            exists: knows(real)
        )
        #expect(result.validated.count == 1)
    }

    @Test("text with no citations is returned unchanged")
    func noCitations() async {
        let result = await CitationValidator.validate(
            responseText: "Just plain prose with no references.",
            exists: knows([])
        )
        #expect(result.validated.isEmpty)
        #expect(result.stripped.isEmpty)
        #expect(result.cleanedText == "Just plain prose with no references.")
    }

    @Test("citationHits parses source and trims heading whitespace")
    func parsing() {
        let hits = CitationValidator.citationHits(in: "x [ICH-Q7 §5.2 Cleaning ] y")
        #expect(hits.count == 1)
        #expect(hits.first?.citation == Citation(source: "ICH-Q7", heading: "5.2 Cleaning"))
    }

    // MARK: - Mixed-case / multi-word titles (the citation leak)

    //
    // citationLabel renders titles VERBATIM ("[Plant Notes §3.2 Seals]") but the
    // old patterns only parsed single-token ALL-CAPS sources — so almost every
    // real-world citation was invisible to validation and hallucinated ones
    // sailed into the rendered answer. § is the discriminator (the same rule
    // SpeechTextPolish strips by), not the title's casing.

    @Test("mixed-case multi-word titles parse: real kept, invented stripped")
    func mixedCaseTitles() async {
        let real: Set<Citation> = [Citation(source: "Plant Notes", heading: "3.2 Seals")]
        let result = await CitationValidator.validate(
            responseText: "Seal failed [Plant Notes §3.2 Seals]; but [Shannon 1951 §2 Entropy] is invented.",
            exists: knows(real)
        )
        #expect(result.validated == [Citation(source: "Plant Notes", heading: "3.2 Seals")])
        #expect(result.stripped == [Citation(source: "Shannon 1951", heading: "2 Entropy")])
        #expect(result.cleanedText.contains("[Plant Notes §3.2 Seals]"))
        #expect(!result.cleanedText.contains("Shannon"))
    }

    @Test("paren-delimited mixed-case hallucination is stripped")
    func mixedCaseParenStripped() async {
        let result = await CitationValidator.validate(
            responseText: "Clean per (Quality Manual §4 Hygiene).",
            exists: knows([])
        )
        #expect(result.stripped == [Citation(source: "Quality Manual", heading: "4 Hygiene")])
        #expect(!result.cleanedText.contains("Quality Manual"))
    }

    @Test("source whitespace is trimmed like the heading's")
    func sourceTrimmed() {
        let hits = CitationValidator.citationHits(in: "x [ Plant Notes §3.2 Seals ] y")
        #expect(hits.first?.citation == Citation(source: "Plant Notes", heading: "3.2 Seals"))
    }

    @Test("a markdown link whose text contains § is not a citation")
    func markdownLinkUntouched() async {
        let text = "Read [chapter §5](https://example.com/ch5) for background."
        let result = await CitationValidator.validate(responseText: text, exists: knows([]))
        #expect(result.stripped.isEmpty)
        #expect(result.cleanedText == text)
    }

    @Test("an extra § lands in the heading — the malformed citation over-strips, never crashes")
    func extraSectionMarkOverStrips() async {
        // Source stops at the FIRST §; the rest rides in the heading, which
        // can't match any real chunk heading. Safe failure direction: the
        // malformed token is stripped rather than leaking unvalidated.
        let result = await CitationValidator.validate(
            responseText: "See [Plant Notes §3.1 §3.2 overlap] here.",
            exists: knows([])
        )
        #expect(result.stripped == [Citation(source: "Plant Notes", heading: "3.1 §3.2 overlap")])
        #expect(!result.cleanedText.contains("Plant Notes"))
    }

    @Test("plain bracketed prose without a § is not a citation")
    func bracketsWithoutSectionMarkUntouched() async {
        let result = await CitationValidator.validate(
            responseText: "Use the [think] tag and read [chapter four] closely.",
            exists: knows([])
        )
        #expect(result.stripped.isEmpty)
        #expect(result.cleanedText == "Use the [think] tag and read [chapter four] closely.")
    }
}
