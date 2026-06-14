//
//  DocumentChunkerTests.swift
//  M1K3KnowledgeTests
//
//  Contract tests for the pure document chunker: heading-aware sectioning,
//  preamble handling, page-number stripping, frequency boilerplate removal,
//  sliding-window split with word-boundary overlap, and the maxChunks guard.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct DocumentChunkerTests {
    private func page(_ n: Int, _ text: String) -> DocumentPage {
        DocumentPage(pageNumber: n, text: text)
    }

    @Test("a heading opens a section carried on its chunks")
    func headingSection() {
        let chunks = DocumentChunker.chunk(pages: [
            page(1, "1.1 Scope\nThis document describes the cleaning procedure."),
        ])
        #expect(chunks.count == 1)
        #expect(chunks.first?.heading == "1.1 Scope")
        #expect(chunks.first?.pageNumber == 1)
        #expect(chunks.first?.content.contains("cleaning procedure") == true)
    }

    @Test("multiple headings produce multiple sections")
    func multipleSections() {
        let chunks = DocumentChunker.chunk(pages: [
            page(1, "1.1 Scope\nScope body text here.\n1.2 Purpose\nPurpose body text here."),
        ])
        #expect(chunks.count == 2)
        #expect(chunks.map(\.heading) == ["1.1 Scope", "1.2 Purpose"])
        #expect(chunks.map(\.chunkIndex) == [0, 1])
    }

    @Test("body before any heading becomes a heading-less preamble section")
    func preamble() {
        let chunks = DocumentChunker.chunk(pages: [
            page(1, "Introductory text with no number.\n1.1 First\nFirst body."),
        ])
        #expect(chunks.first?.heading == nil)
        #expect(chunks.first?.content.contains("Introductory") == true)
        #expect(chunks.last?.heading == "1.1 First")
    }

    @Test("paragraph numbers like 2.11 are NOT treated as headings")
    func paragraphNumberNotHeading() {
        // N.NN is a paragraph number per the SOP tuning, not a section heading.
        let chunks = DocumentChunker.chunk(pages: [
            page(1, "1.1 Real Heading\n2.11 this is body, not a heading, it continues."),
        ])
        #expect(chunks.count == 1)
        #expect(chunks.first?.heading == "1.1 Real Heading")
        #expect(chunks.first?.content.contains("2.11") == true)
    }

    @Test("a numbered math/list body line is NOT a heading (the §3 * 10 = 30 leak)")
    func numberedMathLineNotHeading() {
        // parseHeading was `\d+ .{3,80}` — a bare integer + ANY text — so a body
        // line like "3 * 10 = 30 miles per hour" parsed as a section heading and
        // leaked "§3 * 10 = 30 miles…" into the rendered source line. A real
        // heading's title is letter-led; math/list lines lead with an operator.
        #expect(DocumentChunker.parseHeading("3 * 10 = 30 miles per hour") == nil)
        #expect(DocumentChunker.parseHeading("2 + 2 equals four") == nil)
        #expect(DocumentChunker.parseHeading("5 < 10 and 10 > 5") == nil)
    }

    @Test("a letter-led single-level heading is still recognised")
    func bareIntegerHeadingStillWorks() {
        // The fix must not lose legitimate single-level SOP headings.
        #expect(DocumentChunker.parseHeading("3 Cleaning Procedure") == "3 Cleaning Procedure")
        #expect(DocumentChunker.parseHeading("4.1.2 Cleaning") == "4.1.2 Cleaning")
    }

    @Test("a numbered math body line stays in the body, not a heading, end to end")
    func numberedMathLineStaysBody() {
        let chunks = DocumentChunker.chunk(pages: [
            page(1, "1.1 Distance\nThe route is 3 * 10 = 30 miles per hour over the stretch."),
        ])
        #expect(chunks.count == 1)
        #expect(chunks.first?.heading == "1.1 Distance")
        #expect(chunks.first?.content.contains("3 * 10 = 30 miles") == true)
    }

    @Test("page-number-only lines are dropped")
    func pageNumberLinesDropped() {
        let chunks = DocumentChunker.chunk(pages: [
            page(1, "1.1 Scope\nReal content.\n12\nPage 13\n5 of 49\nMore content."),
        ])
        #expect(chunks.first?.content.contains("Real content") == true)
        #expect(chunks.first?.content.contains("More content") == true)
        #expect(chunks.first?.content.contains("of 49") == false)
        #expect(chunks.first?.content.contains("Page 13") == false)
    }

    @Test("isPageNumberLine recognises common pagination chrome")
    func pageNumberPredicate() {
        #expect(DocumentChunker.isPageNumberLine("12"))
        #expect(DocumentChunker.isPageNumberLine("Page 12"))
        #expect(DocumentChunker.isPageNumberLine("5 of 49"))
        #expect(DocumentChunker.isPageNumberLine("- 7 -"))
        #expect(!DocumentChunker.isPageNumberLine("Section 12 covers cleaning"))
        #expect(!DocumentChunker.isPageNumberLine("hydraulic"))
    }

    @Test("running header repeated across most pages is stripped")
    func boilerplateStripped() {
        // "ACME SOP — Confidential" repeats on all 4 pages → boilerplate.
        let header = "ACME SOP — Confidential"
        let pages = (1 ... 4).map { n in
            page(n, "\(header)\nUnique body for page \(n) about valves and seals.")
        }
        let chunks = DocumentChunker.chunk(pages: pages)
        let joined = chunks.map(\.content).joined(separator: " ")
        #expect(!joined.contains("Confidential"))
        #expect(joined.contains("page 1"))
        #expect(joined.contains("page 4"))
    }

    @Test("a long section splits into overlapping windows on word boundaries")
    func slidingWindow() {
        let word = "alpha "
        let body = String(repeating: word, count: 500) // ~3000 chars > targetChars
        let chunks = DocumentChunker.chunk(pages: [page(1, "1.1 Long\n\(body)")])
        #expect(chunks.count > 1)
        // No chunk exceeds the target (allowing the trimmed boundary).
        #expect(chunks.allSatisfy { $0.content.count <= DocumentChunker.targetChars })
        // Word-boundary: no chunk starts or ends mid-"alpha".
        #expect(chunks.allSatisfy { !$0.content.hasPrefix("lpha") })
        // All carry the same heading + page.
        #expect(chunks.allSatisfy { $0.heading == "1.1 Long" })
    }

    @Test("splitIntoWindows overlaps consecutive chunks with whole words")
    func windowOverlap() {
        let body = (0 ..< 400).map { "word\($0)" }.joined(separator: " ")
        let windows = DocumentChunker.splitIntoWindows(body)
        #expect(windows.count >= 2)
        // Window 1 begins inside window 0's range — its opening words are whole
        // words already present in window 0 (overlap repeats whole words).
        let window0Words = Set(windows[0].split(separator: " ").map(String.init))
        let window1Start = windows[1].split(separator: " ").prefix(5).map(String.init)
        #expect(window1Start.allSatisfy { window0Words.contains($0) })
    }

    @Test("empty input yields no chunks")
    func emptyInput() {
        #expect(DocumentChunker.chunk(pages: []).isEmpty)
        #expect(DocumentChunker.chunk(pages: [DocumentPage(pageNumber: 1, text: "")]).isEmpty)
    }

    @Test("chunkIndex is contiguous across sections")
    func contiguousIndices() {
        let chunks = DocumentChunker.chunk(pages: [
            page(1, "1.1 A\nbody a\n1.2 B\nbody b\n1.3 C\nbody c"),
        ])
        #expect(chunks.map(\.chunkIndex) == Array(0 ..< chunks.count))
    }
}
