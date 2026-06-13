//
//  SeparationEvalFixturesTests.swift
//  M1K3KnowledgeTests
//
//  Pins the pure half of the embedder A/B separation harness: the margin
//  computation (in-domain floor − off-domain ceiling) and the head-to-head
//  formatter that decides whether a candidate embedder widens or narrows the
//  grounding dead-zone vs the incumbent. The embedding pass itself is
//  verify-by-launch; this is the math + reporting that interprets it.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-13, Confidence 0.9, Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

struct SeparationEvalFixturesTests {
    @Test("fixtures carry in-domain and off-domain query/document pairs")
    func fixturesPopulated() {
        #expect(!SeparationEvalFixtures.inDomain.isEmpty)
        #expect(!SeparationEvalFixtures.offDomain.isEmpty)
        // Every pair is non-empty on both sides — an empty string would embed to
        // garbage and silently corrupt the margin.
        for pair in SeparationEvalFixtures.inDomain + SeparationEvalFixtures.offDomain {
            #expect(!pair.query.isEmpty)
            #expect(!pair.document.isEmpty)
        }
    }

    @Test("margin is in-domain floor minus off-domain ceiling")
    func marginIsFloorMinusCeiling() {
        // in floor 0.70, off ceiling 0.63 → +0.07 clean dead-zone
        let m = SeparationEvalReport.margin(inDomain: [0.74, 0.70, 0.80], offDomain: [0.55, 0.63, 0.60])
        #expect(abs((m ?? -9) - 0.07) < 1e-5)
    }

    @Test("overlapping classes produce a negative margin")
    func overlapNegativeMargin() {
        // in floor 0.60 below off ceiling 0.65 → the noise band swallows a real hit
        let m = SeparationEvalReport.margin(inDomain: [0.60, 0.72], offDomain: [0.50, 0.65])
        #expect((m ?? 9) < 0)
    }

    @Test("margin is nil when either class is empty")
    func marginNilOnEmpty() {
        #expect(SeparationEvalReport.margin(inDomain: [], offDomain: [0.5]) == nil)
        #expect(SeparationEvalReport.margin(inDomain: [0.7], offDomain: []) == nil)
    }

    @Test("head-to-head names the wider-margin embedder as the winner")
    func headToHeadPicksWider() {
        let bge = SeparationEvalReport.Result(
            label: "bge-small-384", inDomain: [0.74, 0.72], offDomain: [0.63, 0.60]
        )
        let candidate = SeparationEvalReport.Result(
            label: "qwen3-embed-512", inDomain: [0.78, 0.80], offDomain: [0.40, 0.45]
        )
        let report = SeparationEvalReport.render([bge, candidate])
        #expect(report.contains("bge-small-384"))
        #expect(report.contains("qwen3-embed-512"))
        // candidate margin (0.78-0.45=0.33) > bge margin (0.72-0.63=0.09) → wider
        #expect(report.lowercased().contains("wider"))
        #expect(report.contains("qwen3-embed-512 margin"))
    }

    @Test("true median averages the two middle values for an even count")
    func evenMedianIsAveraged() {
        // n=4 → average of the 2nd and 3rd (0.6, 0.8) = 0.7, NOT the upper 0.8.
        #expect(abs(SeparationEvalReport.trueMedian([0.2, 0.6, 0.8, 0.9]) - 0.7) < 1e-6)
        // n=3 → the middle value.
        #expect(abs(SeparationEvalReport.trueMedian([0.2, 0.6, 0.9]) - 0.6) < 1e-6)
    }

    @Test("render reports each embedder's in/off distribution and margin")
    func renderShowsDistributions() {
        let r = SeparationEvalReport.Result(
            label: "test", inDomain: [0.7, 0.8], offDomain: [0.4, 0.5]
        )
        let out = SeparationEvalReport.render([r])
        #expect(out.contains("in-domain"))
        #expect(out.contains("off-domain"))
        #expect(out.contains("margin"))
    }
}
