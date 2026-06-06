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
}
