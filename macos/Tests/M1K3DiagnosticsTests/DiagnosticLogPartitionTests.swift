//
//  DiagnosticLogPartitionTests.swift
//  M1K3DiagnosticsTests
//
//  Signed: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.9. Prior: Unknown.
//

import Foundation
@testable import M1K3Diagnostics
import Testing

struct DiagnosticLogPartitionTests {
    /// A line tagged with whether it's an important (error/fault) entry.
    private struct Entry: Equatable {
        let id: Int
        let important: Bool
    }

    private func entries(_ spec: [(Int, Bool)]) -> [Entry] {
        spec.map { Entry(id: $0.0, important: $0.1) }
    }

    @Test("under budget returns everything, in order")
    func underBudgetKeepsAll() {
        let input = entries([(1, false), (2, true), (3, false)])
        let out = DiagnosticLogPartition.select(input, maxLines: 10, isImportant: \.important)
        #expect(out == input)
    }

    @Test("a triggering error is kept even when it would scroll off a blind tail")
    func importantEntryNeverEvicted() {
        // 1 early error, then 10 ordinary lines; budget 5. A blind suffix(5) drops
        // the error; the partition keeps it + the 4 most recent ordinary lines.
        var input = [Entry(id: 0, important: true)]
        input += (1 ... 10).map { Entry(id: $0, important: false) }

        let out = DiagnosticLogPartition.select(input, maxLines: 5, isImportant: \.important)

        #expect(out.count == 5)
        #expect(out.contains(Entry(id: 0, important: true))) // the error survives
        #expect(out.map(\.id) == [0, 7, 8, 9, 10]) // error + recent tail, in order
    }

    @Test("when important entries exceed the budget, keep the most recent important")
    func importantOverflowKeepsMostRecent() {
        let input = (1 ... 8).map { Entry(id: $0, important: true) }
        let out = DiagnosticLogPartition.select(input, maxLines: 3, isImportant: \.important)
        #expect(out.map(\.id) == [6, 7, 8]) // most recent 3, in order
    }

    @Test("order is always preserved across the kept set")
    func orderPreserved() {
        let input = entries([(1, false), (2, true), (3, false), (4, false), (5, true), (6, false)])
        let out = DiagnosticLogPartition.select(input, maxLines: 4, isImportant: \.important)
        #expect(out.map(\.id) == out.map(\.id).sorted()) // monotonic ids ⇒ original order
        #expect(out.contains(Entry(id: 2, important: true)))
        #expect(out.contains(Entry(id: 5, important: true)))
    }

    @Test("non-positive budget returns empty")
    func zeroBudget() {
        let input = entries([(1, true), (2, false)])
        #expect(DiagnosticLogPartition.select(input, maxLines: 0, isImportant: \.important).isEmpty)
    }

    @Test("budget of 1 keeps the single most recent important entry")
    func budgetOfOnePrefersImportant() {
        let input = entries([(1, false), (2, true), (3, true), (4, false)])
        let out = DiagnosticLogPartition.select(input, maxLines: 1, isImportant: \.important)
        #expect(out.map(\.id) == [3]) // most recent important, not the most recent line
    }
}
