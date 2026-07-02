//
//  ChatEvalReportTests.swift
//  M1K3EvalTests
//
//  The matrix is what Kev reads to decide which brain earns its place — so the
//  aggregate maths (passed/total per kind, median latency, overall) must be
//  right and the layout must carry every brain column and kind row.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9. Prior: Unknown

@testable import M1K3Eval
import Testing

struct ChatEvalReportTests {
    private func score(
        _ id: String, _ kind: TaskKind, passed: Bool, latency: Int
    ) -> ChatEvalScore {
        // A single check whose outcome encodes pass/fail.
        ChatEvalScore(
            fixtureID: id, kind: kind,
            checks: [EvalCheck(name: "c", outcome: passed ? .pass : .fail)],
            latencyMS: latency
        )
    }

    @Test("median is the lower-middle element and 0 when empty")
    func median() {
        #expect(medianOf([]) == 0)
        #expect(medianOf([5]) == 5)
        #expect(medianOf([10, 20, 30]) == 20)
        #expect(medianOf([10, 20, 30, 40]) == 20) // lower-middle on even counts
    }

    @Test("a BrainRun counts passes and totals across its scores")
    func brainRunCounts() {
        let run = ChatEvalReport.BrainRun(brainID: "lil", scores: [
            score("a", .openChat, passed: true, latency: 100),
            score("b", .openChat, passed: false, latency: 300),
            score("c", .reasoning, passed: true, latency: 200),
        ])
        #expect(run.passedCount == 2)
        #expect(run.total == 3)
        #expect(run.medianLatencyMS == 200)
    }

    @Test("the matrix carries a column per brain and a row per kind plus overall")
    func matrixShape() {
        let mini = ChatEvalReport.BrainRun(brainID: "mini", scores: [
            score("o1", .openChat, passed: false, latency: 50),
        ])
        let lil = ChatEvalReport.BrainRun(brainID: "lil", scores: [
            score("o1", .openChat, passed: true, latency: 500),
        ])
        let out = ChatEvalReport.matrix([mini, lil])
        #expect(out.contains("mini"))
        #expect(out.contains("lil"))
        for kind in TaskKind.allCases {
            #expect(out.contains(kind.label), "matrix missing row \(kind.label)")
        }
        #expect(out.contains("overall"))
    }

    @Test("a kind cell reads passed/total with median latency")
    func cellMaths() {
        let run = ChatEvalReport.BrainRun(brainID: "big", scores: [
            score("o1", .openChat, passed: true, latency: 100),
            score("o2", .openChat, passed: true, latency: 200),
            score("o3", .openChat, passed: false, latency: 300),
        ])
        let out = ChatEvalReport.matrix([run])
        // 2 of 3 open-chat passed, median latency 200.
        #expect(out.contains("2/3 200ms"))
    }

    @Test("an empty kind renders a dash, not 0/0")
    func emptyKindDash() {
        let run = ChatEvalReport.BrainRun(brainID: "big", scores: [
            score("o1", .openChat, passed: true, latency: 100),
        ])
        let out = ChatEvalReport.matrix([run])
        #expect(out.contains("—"))
    }

    @Test("verbose output names each brain and its pass tally")
    func verboseNamesBrains() {
        let run = ChatEvalReport.BrainRun(brainID: "big", scores: [
            score("o1", .openChat, passed: true, latency: 100),
        ])
        let out = ChatEvalReport.verbose([run])
        #expect(out.contains("big"))
        #expect(out.contains("1/1"))
    }

    @Test("an empty run list is handled, not crashed")
    func emptyRuns() {
        #expect(ChatEvalReport.matrix([]) == "chateval: no runs")
    }
}
