//
//  ModelEvalTests.swift
//  M1K3InferenceTests
//
//  The pure core of the per-model eval harness: every model family arrives
//  with its own behavioral landmines (Gemma-3n's template silently dropped
//  tools; Qwen3.5 pre-opens <think>; dialects differ per family), so each
//  model gets the same checklist and the report makes pass/fail/skip obvious.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
@testable import M1K3Inference
import Testing

struct ModelEvalTests {
    @Test("a report renders one line per check with the verdict and detail")
    func reportRenders() {
        let records = [
            ModelEvalRecord(check: "generates", outcome: .pass, detail: "42 chars"),
            ModelEvalRecord(check: "native tool call", outcome: .fail, detail: "no call emitted"),
            ModelEvalRecord(check: "think contract", outcome: .skip, detail: "not a reasoning family"),
        ]
        let report = ModelEvalReport(modelID: "mlx-community/Test-1B", records: records)
        let rendered = report.rendered

        #expect(rendered.contains("mlx-community/Test-1B"))
        #expect(rendered.contains("✓ generates"))
        #expect(rendered.contains("✗ native tool call — no call emitted"))
        #expect(rendered.contains("– think contract"))
    }

    @Test("a report passes only when nothing failed (skips don't fail it)")
    func passAggregation() {
        let passing = ModelEvalReport(modelID: "m", records: [
            ModelEvalRecord(check: "a", outcome: .pass, detail: ""),
            ModelEvalRecord(check: "b", outcome: .skip, detail: ""),
        ])
        let failing = ModelEvalReport(modelID: "m", records: [
            ModelEvalRecord(check: "a", outcome: .pass, detail: ""),
            ModelEvalRecord(check: "b", outcome: .fail, detail: "boom"),
        ])
        #expect(passing.passed)
        #expect(!failing.passed)
    }

    @Test("think blocks strip for answer assertions (lone close included)")
    func thinkStrip() {
        #expect(ModelEvalReport.strippingThink("<think>plan</think>OK") == "OK")
        #expect(ModelEvalReport.strippingThink("reasoning</think>OK") == "OK")
        #expect(ModelEvalReport.strippingThink("OK") == "OK")
    }
}
