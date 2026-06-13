//
//  MemoryEvalFixturesTests.swift
//  M1K3KnowledgeTests
//
//  Shape pins for the MEMEVAL fixture set + the pure report formatter. The
//  actual cosine distributions come from the on-device probe
//  (M1K3_SELFTEST_MEMEVAL=1) with the real BGE embedder.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct MemoryEvalFixturesTests {
    @Test("fixture set is big enough to mean something")
    func fixtureShape() {
        #expect(MemoryEvalFixtures.positives.count >= 20)
        #expect(MemoryEvalFixtures.negatives.count >= 10)
        for pair in MemoryEvalFixtures.positives + MemoryEvalFixtures.negatives {
            #expect(!pair.memory.trimmingCharacters(in: .whitespaces).isEmpty)
            #expect(!pair.query.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }

    @Test("report renders min/median/max and a suggested threshold on clean separation")
    func reportCleanSeparation() {
        let report = MemoryEvalReport.render(
            positives: [0.70, 0.75, 0.80],
            negatives: [0.40, 0.50, 0.55]
        )
        #expect(report.contains("memeval positives: min 0.700 / median 0.750 / max 0.800 (n=3)"))
        #expect(report.contains("memeval negatives: min 0.400 / median 0.500 / max 0.550 (n=3)"))
        // Midpoint of neg-max 0.55 and pos-min 0.70.
        #expect(report.contains("suggested threshold: 0.625"))
    }

    @Test("report flags overlap instead of suggesting a misleading midpoint")
    func reportOverlap() {
        let report = MemoryEvalReport.render(
            positives: [0.55, 0.75, 0.80],
            negatives: [0.40, 0.60]
        )
        #expect(report.contains("OVERLAP: 1 positive(s) at or below neg max 0.600"))
        #expect(!report.contains("suggested threshold"))
    }

    @Test("empty score arrays render honestly")
    func reportEmpty() {
        let report = MemoryEvalReport.render(positives: [], negatives: [])
        #expect(report.contains("no scores"))
    }
}
