//
//  PromptSizeReportTests.swift
//  M1K3EvalTests
//
//  Red-first for the prompt-size instrument. The point of the instrument is to
//  replace HistoryBudgetPolicy's char≈token ESTIMATE with a measured fact, so
//  these tests pin the two things a measurement must never fudge: that a
//  missing tokenizer stays honestly nil (never a silent zero), and that the
//  over-reserve verdict is computed from TOKENS when we have them.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-19, Confidence 0.8 (pure arithmetic +
//  rendering; the on-device numbers this consumes are the verify-owed).
//  Prior: Unknown
//

@testable import M1K3Eval
import Testing

struct PromptSizeReportTests {
    private func component(
        _ name: String, bytes: Int, tokens: Int?
    ) -> PromptComponentSize {
        PromptComponentSize(name: name, bytes: bytes, tokens: tokens)
    }

    @Test("totals sum the components")
    func totalsSum() {
        let m = PromptSizeMeasurement(
            label: "grounded-q",
            components: [
                component("persona", bytes: 4000, tokens: 1130),
                component("grounding", bytes: 7400, tokens: 2100),
            ],
            reserveTokens: 3000,
            windowTokens: 8192
        )
        #expect(m.totalBytes == 11400)
        #expect(m.totalTokens == 3230)
    }

    @Test("a component with no tokenizer keeps totalTokens nil — never a silent zero")
    func missingTokenizerStaysNil() {
        // AFM/mini exposes no tokenizer. Reporting 0 tokens would read as
        // "measured, and tiny" — the exact confident-wrongness this instrument
        // exists to kill. Bytes still measure fine.
        let m = PromptSizeMeasurement(
            label: "mini",
            components: [
                component("persona", bytes: 4000, tokens: nil),
                component("grounding", bytes: 7400, tokens: nil),
            ],
            reserveTokens: 3000,
            windowTokens: nil
        )
        #expect(m.totalBytes == 11400)
        #expect(m.totalTokens == nil)
        #expect(m.exceedsReserve == nil)
    }

    @Test("a partially-tokenised measurement is nil, not a partial sum")
    func partialTokenisationStaysNil() {
        let m = PromptSizeMeasurement(
            label: "mixed",
            components: [
                component("persona", bytes: 4000, tokens: 1130),
                component("grounding", bytes: 7400, tokens: nil),
            ],
            reserveTokens: 3000,
            windowTokens: 8192
        )
        #expect(m.totalTokens == nil)
    }

    @Test("exceedsReserve is true when measured tokens cross the reserve")
    func exceedsReserveWhenOver() {
        // The live shape this instrument was built to expose: grounding is
        // reserved at ~1100 tokens but measures ~2100.
        let m = PromptSizeMeasurement(
            label: "worst-case-grounding",
            components: [component("grounding", bytes: 7400, tokens: 2100)],
            reserveTokens: 1100,
            windowTokens: 8192
        )
        #expect(m.exceedsReserve == true)
        #expect(m.reserveOverrunTokens == 1000)
    }

    @Test("exceedsReserve is false at exactly the reserve — the boundary is inclusive")
    func reserveBoundaryIsInclusive() {
        let m = PromptSizeMeasurement(
            label: "exact",
            components: [component("grounding", bytes: 3850, tokens: 1100)],
            reserveTokens: 1100,
            windowTokens: 8192
        )
        #expect(m.exceedsReserve == false)
        #expect(m.reserveOverrunTokens == 0)
    }

    @Test("contextFraction reports how much of a rotating window the prompt eats")
    func contextFraction() {
        let m = PromptSizeMeasurement(
            label: "big",
            components: [component("all", bytes: 14336, tokens: 4096)],
            reserveTokens: 3000,
            windowTokens: 8192
        )
        #expect(m.contextFraction == 0.5)
    }

    @Test("contextFraction is nil without a window or without tokens")
    func contextFractionNeedsBoth() {
        let noWindow = PromptSizeMeasurement(
            label: "a", components: [component("all", bytes: 10, tokens: 5)],
            reserveTokens: nil, windowTokens: nil
        )
        let noTokens = PromptSizeMeasurement(
            label: "b", components: [component("all", bytes: 10, tokens: nil)],
            reserveTokens: nil, windowTokens: 8192
        )
        #expect(noWindow.contextFraction == nil)
        #expect(noTokens.contextFraction == nil)
    }

    @Test("measuredCharsPerToken is the ratio the estimate should be tuned to")
    func measuredCharsPerToken() {
        // HistoryBudgetPolicy assumes 3.5; DocumentChunker implies 4. This is
        // the number that settles it, per corpus.
        let m = PromptSizeMeasurement(
            label: "prose",
            components: [component("all", bytes: 4000, tokens: 1000)],
            reserveTokens: nil, windowTokens: nil
        )
        #expect(m.measuredCharsPerToken == 4.0)
    }

    @Test("measuredCharsPerToken is nil when tokens are unknown or zero")
    func measuredCharsPerTokenGuards() {
        let untokenised = PromptSizeMeasurement(
            label: "a", components: [component("all", bytes: 4000, tokens: nil)],
            reserveTokens: nil, windowTokens: nil
        )
        let empty = PromptSizeMeasurement(
            label: "b", components: [component("all", bytes: 0, tokens: 0)],
            reserveTokens: nil, windowTokens: nil
        )
        #expect(untokenised.measuredCharsPerToken == nil)
        #expect(empty.measuredCharsPerToken == nil)
    }

    @Test("the table renders one row per measurement with a component breakdown")
    func tableRenders() {
        let table = PromptSizeReport.table([
            PromptSizeMeasurement(
                label: "grounded-q",
                components: [
                    component("persona", bytes: 4000, tokens: 1130),
                    component("grounding", bytes: 7400, tokens: 2100),
                ],
                reserveTokens: 3000,
                windowTokens: 8192
            ),
        ])
        #expect(table.contains("PROMPT SIZE"))
        #expect(table.contains("grounded-q"))
        #expect(table.contains("persona"))
        #expect(table.contains("grounding"))
        #expect(table.contains("1130"))
        #expect(table.contains("2100"))
    }

    @Test("the table marks an over-reserve row so it can't be skimmed past")
    func tableFlagsOverruns() {
        let table = PromptSizeReport.table([
            PromptSizeMeasurement(
                label: "worst-case",
                components: [component("grounding", bytes: 7400, tokens: 2100)],
                reserveTokens: 1100,
                windowTokens: 8192
            ),
        ])
        #expect(table.contains("OVER"))
    }

    @Test("the table renders an untokenised measurement without claiming a count")
    func tableHandlesMissingTokens() {
        let table = PromptSizeReport.table([
            PromptSizeMeasurement(
                label: "mini",
                components: [component("persona", bytes: 4000, tokens: nil)],
                reserveTokens: nil,
                windowTokens: nil
            ),
        ])
        #expect(table.contains("mini"))
        #expect(table.contains("—"))
        #expect(!table.contains(" 0 tok"))
    }

    @Test("an empty run renders a header, not an empty string")
    func emptyRun() {
        #expect(PromptSizeReport.table([]).contains("PROMPT SIZE"))
    }
}
