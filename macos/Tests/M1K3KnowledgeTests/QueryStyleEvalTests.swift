//
//  QueryStyleEvalTests.swift
//  M1K3KnowledgeTests
//
//  Pins for the keyword-query gap instrument (M1K3_SELFTEST_KEYEVAL):
//  the EmbeddingText.forQuery composer (Qwen3-Embedding's official asymmetric
//  query instruction), the fixture triples, and the pure two-arm report.
//  The embedding pass itself is verify-by-launch (metallib wall).
//
//  Signed: Kev + claude-fable-5, 2026-07-09, Confidence 0.85 (template pinned
//  card-verbatim incl. the no-space-after-"Query:" detail; report math pinned
//  on synthetic scores). Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

// MARK: - The query composer

struct EmbeddingTextForQueryTests {
    @Test("forQuery wraps card-verbatim: Instruct + newline + Query: with NO space after the colon")
    func templateIsCardVerbatim() {
        #expect(EmbeddingText.forQuery("what's my sister's name?") == "Instruct: Given a web search query, retrieve relevant passages that answer the query\nQuery:what's my sister's name?")
    }

    @Test("forQuery never touches the query text itself")
    func queryTextPreserved() {
        let awkward = "  Golden Gate milestone…  "
        #expect(EmbeddingText.forQuery(awkward).hasSuffix("Query:\(awkward)"))
    }

    @Test("the doc-side composition is untouched by the query composer — no fingerprint change")
    func docSideUntouched() {
        // A query-side composer must never trigger the corpus re-embed:
        // compositionVersion salts DOC vectors only and stays at title-v1.
        #expect(EmbeddingText.compositionVersion == "title-v1")
        #expect(EmbeddingText.storeFingerprint(embedder: "e") == "e+title-v1")
    }
}

// MARK: - Fixtures

struct QueryStyleEvalFixturesTests {
    @Test("a healthy probe set: enough triples, keywords genuinely terser than questions")
    func probeShape() {
        #expect(QueryStyleEvalFixtures.probes.count >= 8)
        for probe in QueryStyleEvalFixtures.probes {
            #expect(!probe.keyword.isEmpty)
            #expect(probe.keyword.count < probe.question.count)
            #expect(!probe.content.isEmpty)
        }
    }

    @Test("probe keywords are unique — no double-counted evidence")
    func keywordsUnique() {
        let keywords = QueryStyleEvalFixtures.probes.map(\.keyword)
        #expect(Set(keywords).count == keywords.count)
    }

    @Test("fact-style probes (title == content) embed BARE through forChunk — production parity")
    func factProbesEmbedBare() {
        let factProbes = QueryStyleEvalFixtures.probes.filter { $0.title == $0.content }
        #expect(!factProbes.isEmpty)
        for probe in factProbes {
            #expect(EmbeddingText.forChunk(title: probe.title, content: probe.content) == probe.content)
        }
    }

    @Test("titled probes embed title-prefixed through forChunk — the layer-3 shape")
    func titledProbesEmbedPrefixed() {
        let titled = QueryStyleEvalFixtures.probes.filter { $0.title != $0.content }
        #expect(!titled.isEmpty)
        for probe in titled {
            let composed = EmbeddingText.forChunk(title: probe.title, content: probe.content)
            #expect(composed == "\(probe.title)\n\(probe.content)")
        }
    }

    @Test("the noise set is present and keyword-styled")
    func noiseShape() {
        #expect(QueryStyleEvalFixtures.noise.count >= 5)
        for pair in QueryStyleEvalFixtures.noise {
            #expect(!pair.keyword.isEmpty)
            #expect(!pair.content.isEmpty)
        }
    }
}

// MARK: - Report

struct QueryStyleEvalReportTests {
    private func arm(
        label: String = "bare",
        keyword: [Float],
        question: [Float],
        noise: [Float]
    ) -> QueryStyleEvalReport.Arm {
        .init(label: label, keyword: keyword, question: question, noise: noise)
    }

    @Test("margin = lowest positive (keyword AND question pooled) minus highest noise")
    func marginPoolsBothQueryStyles() {
        let a = arm(keyword: [0.30, 0.55], question: [0.60, 0.70], noise: [0.20, 0.25])
        // pooled positive min = 0.30 (a keyword miss counts — it IS the gap)
        #expect(QueryStyleEvalReport.margin(a) == Float(0.30) - Float(0.25))
    }

    @Test("clean separation suggests the midpoint floor")
    func suggestsMidpointOnCleanSeparation() {
        let a = arm(keyword: [0.50, 0.55], question: [0.60], noise: [0.20, 0.30])
        let rendered = QueryStyleEvalReport.render([a], floors: [0.39])
        #expect(rendered.contains("suggested floor: 0.400"))
    }

    @Test("overlap is reported as overlap, never a fake midpoint")
    func overlapReported() {
        let a = arm(keyword: [0.25, 0.55], question: [0.60], noise: [0.30])
        let rendered = QueryStyleEvalReport.render([a], floors: [0.39])
        #expect(rendered.contains("OVERLAP"))
        #expect(!rendered.contains("suggested floor:"))
    }

    @Test("floor lines count keyword hits, question hits, and noise leaks per floor")
    func floorLinesCount() {
        let a = arm(keyword: [0.30, 0.45], question: [0.55, 0.35], noise: [0.42, 0.10])
        let rendered = QueryStyleEvalReport.render([a], floors: [0.39])
        #expect(rendered.contains("floor 0.390: keyword 1/2 · question 1/2 · noise leaks 1"))
    }

    @Test("two arms get a head-to-head with keyword-mean, noise-ceiling, and margin deltas")
    func headToHeadDeltas() {
        let bare = arm(label: "bare", keyword: [0.30, 0.40], question: [0.60], noise: [0.20])
        let instructed = arm(label: "instructed", keyword: [0.45, 0.55], question: [0.70], noise: [0.28])
        let rendered = QueryStyleEvalReport.render([bare, instructed], floors: [0.39])
        #expect(rendered.contains("head-to-head"))
        // keyword mean 0.35 → 0.50; noise ceiling 0.20 → 0.28; margin 0.10 → 0.17
        #expect(rendered.contains("keyword mean +0.150"))
        #expect(rendered.contains("noise ceiling +0.080"))
        #expect(rendered.contains("margin +0.070"))
    }

    @Test("the keyword–question gap is the mean difference, reported per arm")
    func gapIsMeanDifference() {
        let a = arm(keyword: [0.30, 0.40], question: [0.60, 0.70], noise: [0.10])
        let rendered = QueryStyleEvalReport.render([a], floors: [0.39])
        // mean question 0.65 − mean keyword 0.35 = 0.30
        #expect(rendered.contains("keyword→question gap: 0.300"))
    }
}
