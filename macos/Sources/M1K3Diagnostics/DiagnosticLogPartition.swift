//
//  DiagnosticLogPartition.swift
//  M1K3Diagnostics
//
//  When an issue report captures recent logs it must fit a bounded line budget —
//  but a blind "keep the last N" can evict the very error that triggered the
//  report (the agent loop + per-generation snapshots can flood hundreds of
//  .notice lines in seconds, scrolling an earlier .error off the tail).
//
//  This pure selector keeps EVERY important entry (errors/faults) plus the most
//  recent tail of ordinary entries, capped at the budget and in original order.
//  Pure + generic so it's unit-pinned and the OSLog wiring stays in the app glue.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.9 (pure, TDD-pinned;
//  the OSLogStore mapping is verify-by-launch in IssueReporter). Prior: Unknown.
//

import Foundation

public enum DiagnosticLogPartition {
    /// Select which log entries to keep for a bounded report.
    ///
    /// - Entries are oldest→newest. Order is preserved in the result.
    /// - Every entry for which `isImportant` is true is kept (so a triggering
    ///   error is never evicted), unless important entries alone exceed `maxLines`
    ///   — then the most RECENT `maxLines` important entries are kept.
    /// - Remaining budget is filled with the most recent ordinary entries.
    ///
    /// - Parameters:
    ///   - entries: the candidate entries, oldest first.
    ///   - maxLines: the hard cap on returned entries (≤ 0 returns empty).
    ///   - isImportant: marks an entry as must-keep (e.g. error/fault level).
    public static func select<T>(
        _ entries: [T],
        maxLines: Int,
        isImportant: (T) -> Bool
    ) -> [T] {
        guard maxLines > 0 else { return [] }
        guard entries.count > maxLines else { return entries }

        let importantIndices = entries.indices.filter { isImportant(entries[$0]) }

        // Important entries already overflow the budget: keep the most recent ones.
        if importantIndices.count >= maxLines {
            let keep = Set(importantIndices.suffix(maxLines))
            return entries.indices.filter { keep.contains($0) }.map { entries[$0] }
        }

        // Keep all important + fill the rest with the most recent ordinary entries.
        let budget = maxLines - importantIndices.count
        let ordinaryTail = entries.indices
            .filter { !isImportant(entries[$0]) }
            .suffix(budget)
        let keep = Set(importantIndices).union(ordinaryTail)
        return entries.indices.filter { keep.contains($0) }.map { entries[$0] }
    }
}
