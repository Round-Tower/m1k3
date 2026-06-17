//
//  MemoryGraphEvalTests.swift
//  M1K3MemoryTests
//
//  Pins the integration-eval harness off-device. The hashing embedder is
//  keyword-only, so we assert the STRUCTURAL probes (recall/supersession/
//  traversal) all pass, and that the SEMANTIC probes are correctly skipped when
//  `includeSemantic: false` — and would (mostly) fail if forced through the fake,
//  which is exactly why they're reserved for the real-embedder self-test.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.85. Prior: this file.

import Foundation
@testable import M1K3Knowledge
@testable import M1K3Memory
import Testing

struct MemoryGraphEvalTests {
    @Test("the structural probe set is all-green with the keyword embedder")
    func structuralAllPass() async throws {
        let report = try await MemoryGraphEval.run(
            MemoryGraphFixtures.lifeGraph,
            embedder: HashingEmbeddingService(),
            includeSemantic: false
        )
        #expect(report.allPassed, "structural probes should pass off-device: \(report.summary())")
        // Sanity: the three structural probes ran, the semantic ones did not.
        let ran = Set(report.results.map(\.id))
        #expect(ran.contains("recall-sister"))
        #expect(ran.contains("supersede-city"))
        #expect(ran.contains("traverse-homelife"))
        #expect(!ran.contains("recall-sibling-paraphrase")) // semantic, skipped
    }

    @Test("includeSemantic runs the full probe set, not just the structural slice")
    func semanticProbesRun() async throws {
        let full = try await MemoryGraphEval.run(
            MemoryGraphFixtures.lifeGraph,
            embedder: HashingEmbeddingService(),
            includeSemantic: true
        )
        let structural = try await MemoryGraphEval.run(
            MemoryGraphFixtures.lifeGraph,
            embedder: HashingEmbeddingService(),
            includeSemantic: false
        )
        // The semantic probes are reserved for the real-embedder self-test; their
        // PASS/FAIL on the keyword fake is meaningless (a paraphrase can score on
        // incidental token overlap, e.g. "Kev's"/"is"), so we only assert they
        // RUN here. The gap they measure is proven on-device, not in this unit.
        #expect(full.results.count > structural.results.count)
        #expect(Set(full.results.map(\.id)).contains("recall-sibling-paraphrase"))
    }

    @Test("an unknown label in a scenario surfaces a clear error")
    func unknownLabelThrows() async throws {
        let broken = MemoryGraphScenario(
            facts: [.init(label: "a", text: "alpha")],
            links: [.init(fromLabel: "a", toLabel: "ghost", relation: "x")],
            probes: []
        )
        await #expect(throws: MemoryGraphEval.EvalError.unknownLabel("ghost")) {
            _ = try await MemoryGraphEval.run(
                broken, embedder: HashingEmbeddingService(), includeSemantic: false
            )
        }
    }

    @Test("the report summary lists every probe with a pass/fail glyph")
    func summaryShape() async throws {
        let report = try await MemoryGraphEval.run(
            MemoryGraphFixtures.lifeGraph,
            embedder: HashingEmbeddingService(),
            includeSemantic: false
        )
        let summary = report.summary()
        #expect(summary.contains("MEMGRAPH 3/3 passed"))
        #expect(summary.contains("✓ recall-sister"))
    }
}
