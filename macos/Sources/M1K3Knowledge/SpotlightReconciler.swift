//
//  SpotlightReconciler.swift
//  M1K3Knowledge
//
//  The OS-index reconcile state machine, extracted from the app target so the
//  trickiest behaviour — the re-entrancy latch + coalesce-rerun loop — is
//  unit-pinned (PR #29 review finding 1). The app supplies live dependencies
//  as closures per call (enabled flag, thermal gate, store fetch) and owns
//  logging via the returned outcomes; this type owns ordering and state.
//
//  Ordering doctrine:
//    - OFF short-circuits BEFORE the thermal gate: clearing the index is a
//      correctness action (the OS index survives container resets), never
//      deferrable background work.
//    - ON rebuild is deleteAll-then-donate, per-kind fetches at an explicit
//      cap — hitting the cap is reported, never silent.
//    - A sync arriving mid-run marks the state dirty; the running sync loops
//      exactly once more, so the LAST caller's state always wins and a rapid
//      ON→OFF toggle can never leave the index enforcing a stale choice.
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.9, Prior: Unknown
//

import Foundation

/// What one reconcile pass did — the app maps these to log lines.
public enum SpotlightReconcileOutcome: Equatable, Sendable {
    /// OFF path: the domain was cleared.
    case cleared
    /// OFF path: deleteAll failed — the one failure that most needs a
    /// breadcrumb (titles would linger in ⌘Space after opt-out).
    case clearFailed(String)
    /// Rebuild skipped under thermal/low-power pressure; the caller should
    /// arm its recovery observer.
    case deferredForHeat
    /// Rebuild completed. `capsHit` names any kind whose fetch returned
    /// exactly the limit (older items beyond it were NOT donated).
    case reconciled(donated: Int, capsHit: [String])
    /// Rebuild failed mid-flight.
    case failed(String)
}

@MainActor
public final class SpotlightReconciler {
    /// Per-kind fetch ceiling. allItems defaults to 200 — and with kind: nil
    /// ever-growing distilled .memory rows would compete for those slots,
    /// silently crowding documents/calls out of the sync. Per-kind fetches
    /// at an explicit generous limit sidestep the crowding; hitting the cap
    /// is reported via the outcome, loudly.
    public static let fetchLimit = 2000

    private let indexer: any SystemSearchIndexing
    private var isSyncing = false
    private var needsRerun = false

    public init(indexer: any SystemSearchIndexing) {
        self.indexer = indexer
    }

    /// Reconcile the OS index with the store. Dependencies are read live on
    /// every pass (not captured once) so a coalesced rerun sees the latest
    /// toggle/thermal state. Returns one outcome per pass run by THIS call;
    /// a call that arrives mid-run returns `[]` — the running sync owns the
    /// extra pass.
    @discardableResult
    public func sync(
        enabled: @MainActor () -> Bool,
        backgroundWorkAllowed: @MainActor () -> Bool,
        fetch: @MainActor (KnowledgeKind, Int) throws -> [KnowledgeItem]
    ) async -> [SpotlightReconcileOutcome] {
        guard !isSyncing else {
            needsRerun = true
            return []
        }
        isSyncing = true
        defer { isSyncing = false }

        var outcomes: [SpotlightReconcileOutcome] = []
        repeat {
            needsRerun = false
            outcomes.append(await reconcileOnce(
                enabled: enabled,
                backgroundWorkAllowed: backgroundWorkAllowed,
                fetch: fetch
            ))
        } while needsRerun
        return outcomes
    }

    private func reconcileOnce(
        enabled: @MainActor () -> Bool,
        backgroundWorkAllowed: @MainActor () -> Bool,
        fetch: @MainActor (KnowledgeKind, Int) throws -> [KnowledgeItem]
    ) async -> SpotlightReconcileOutcome {
        guard enabled() else {
            do {
                try await indexer.deleteAll()
                return .cleared
            } catch {
                return .clearFailed(error.localizedDescription)
            }
        }
        guard backgroundWorkAllowed() else {
            return .deferredForHeat
        }
        do {
            // deleteAll-then-donate: self-heals orphans from container resets
            // and store rebuilds (the OS index has no default-deny of its own
            // — full reconcile is the guarantee). Known narrow race: an
            // ingest/delete hook landing mid-reconcile can be transiently
            // missed by this snapshot; the next trigger self-heals it.
            try await indexer.deleteAll()
            let documents = try fetch(.document, Self.fetchLimit)
            let calls = try fetch(.call, Self.fetchLimit)
            var capsHit: [String] = []
            if documents.count == Self.fetchLimit { capsHit.append("documents") }
            if calls.count == Self.fetchLimit { capsHit.append("calls") }
            let entries = SpotlightDonorPolicy.entries(for: documents + calls)
            try await indexer.donate(entries)
            return .reconciled(donated: entries.count, capsHit: capsHit)
        } catch {
            return .failed(error.localizedDescription)
        }
    }
}
