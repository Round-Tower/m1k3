//
//  SpotlightReconcilerTests.swift
//  M1K3KnowledgeTests
//
//  Pins the reconcile state machine that was previously untested app-target
//  logic (PR #29 review finding 1): the re-entrancy latch + coalesce-rerun
//  loop, the OFF-before-thermal ordering, the per-kind cap detection, and
//  the OFF-path deleteAll failure surfacing (finding 2).
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.9, Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

@MainActor
private final class RecordingIndexer: SystemSearchIndexing, @unchecked Sendable {
    var log: [String] = []
    var deleteAllError: Error?
    var donateError: Error?
    /// When set, donate() suspends until the continuation is resumed —
    /// lets a test inject a second sync call mid-run.
    var donateGate: CheckedContinuation<Void, Never>?
    var onDonateSuspended: (() -> Void)?

    nonisolated init() {}

    func donate(_ entries: [SearchIndexEntry]) async throws {
        log.append("donate(\(entries.count))")
        if let error = donateError { throw error }
        if onDonateSuspended != nil {
            await withCheckedContinuation { continuation in
                donateGate = continuation
                onDonateSuspended?()
                onDonateSuspended = nil
            }
        }
    }

    func deindex(uniqueIDs: [String]) async throws {
        log.append("deindex(\(uniqueIDs.count))")
    }

    func deleteAll() async throws {
        log.append("deleteAll")
        if let error = deleteAllError { throw error }
    }
}

private struct StubError: Error, LocalizedError {
    var errorDescription: String? {
        "stub failure"
    }
}

@MainActor
struct SpotlightReconcilerTests {
    private func items(_ count: Int, kind: KnowledgeKind) -> [KnowledgeItem] {
        (0 ..< count).map { KnowledgeItem(kind: kind, title: "item \($0)") }
    }

    // MARK: - OFF path

    @Test func disabledClearsTheIndexAndSkipsTheThermalGate() async {
        let indexer = RecordingIndexer()
        let reconciler = SpotlightReconciler(indexer: indexer)
        var thermalConsulted = false
        let outcomes = await reconciler.sync(
            enabled: { false },
            backgroundWorkAllowed: { thermalConsulted = true; return true },
            fetch: { _, _ in [] }
        )
        #expect(outcomes == [.cleared])
        #expect(indexer.log == ["deleteAll"])
        // OFF must short-circuit BEFORE the thermal gate — clearing is
        // correctness, not deferrable background work.
        #expect(!thermalConsulted)
    }

    @Test func offPathDeleteAllFailureIsSurfacedNotSwallowed() async {
        let indexer = RecordingIndexer()
        indexer.deleteAllError = StubError()
        let reconciler = SpotlightReconciler(indexer: indexer)
        let outcomes = await reconciler.sync(
            enabled: { false },
            backgroundWorkAllowed: { true },
            fetch: { _, _ in [] }
        )
        #expect(outcomes == [.clearFailed("stub failure")])
    }

    // MARK: - Thermal gate

    @Test func hotSkipDefersWithoutTouchingTheIndex() async {
        let indexer = RecordingIndexer()
        let reconciler = SpotlightReconciler(indexer: indexer)
        let outcomes = await reconciler.sync(
            enabled: { true },
            backgroundWorkAllowed: { false },
            fetch: { _, _ in [] }
        )
        #expect(outcomes == [.deferredForHeat])
        #expect(indexer.log.isEmpty)
    }

    // MARK: - Rebuild path

    @Test func rebuildDeletesAllThenDonatesPolicyFilteredEntries() async {
        let indexer = RecordingIndexer()
        let reconciler = SpotlightReconciler(indexer: indexer)
        let outcomes = await reconciler.sync(
            enabled: { true },
            backgroundWorkAllowed: { true },
            fetch: { kind, _ in
                kind == .document ? self.items(3, kind: .document) : self.items(2, kind: .call)
            }
        )
        #expect(outcomes == [.reconciled(donated: 5, capsHit: [])])
        #expect(indexer.log == ["deleteAll", "donate(5)"])
    }

    @Test func fetchingExactlyTheLimitReportsTheCap() async {
        let indexer = RecordingIndexer()
        let reconciler = SpotlightReconciler(indexer: indexer)
        let limit = SpotlightReconciler.fetchLimit
        let outcomes = await reconciler.sync(
            enabled: { true },
            backgroundWorkAllowed: { true },
            fetch: { kind, requested in
                #expect(requested == limit)
                return kind == .document ? self.items(limit, kind: .document) : []
            }
        )
        #expect(outcomes == [.reconciled(donated: limit, capsHit: ["documents"])])
    }

    @Test func rebuildFailureIsReported() async {
        let indexer = RecordingIndexer()
        indexer.donateError = StubError()
        let reconciler = SpotlightReconciler(indexer: indexer)
        let outcomes = await reconciler.sync(
            enabled: { true },
            backgroundWorkAllowed: { true },
            fetch: { _, _ in self.items(1, kind: .document) }
        )
        #expect(outcomes == [.failed("stub failure")])
    }

    // MARK: - Coalescing (the PR #29 review's requested pin)

    @Test func aSyncArrivingMidRunTriggersExactlyOneExtraPass() async {
        let indexer = RecordingIndexer()
        let reconciler = SpotlightReconciler(indexer: indexer)
        var enabled = true

        // First sync suspends inside donate(); while suspended, two more
        // sync calls arrive (simulating a rapid ON→OFF toggle): they must
        // coalesce into exactly ONE extra pass that sees the LATEST state.
        indexer.onDonateSuspended = {
            Task { @MainActor in
                enabled = false
                // The latched calls return [] immediately (no suspension on
                // the index), so sequential awaits model the mid-run arrivals.
                let second = await reconciler.sync(
                    enabled: { enabled },
                    backgroundWorkAllowed: { true },
                    fetch: { _, _ in [] }
                )
                let third = await reconciler.sync(
                    enabled: { enabled },
                    backgroundWorkAllowed: { true },
                    fetch: { _, _ in [] }
                )
                // Both mid-run calls return empty — the running sync owns the rerun.
                #expect((second + third).isEmpty)
                indexer.donateGate?.resume()
                indexer.donateGate = nil
            }
        }

        let outcomes = await reconciler.sync(
            enabled: { enabled },
            backgroundWorkAllowed: { true },
            fetch: { kind, _ in kind == .document ? self.items(1, kind: .document) : [] }
        )
        // Pass 1 ran the (stale) enabled rebuild; the coalesced pass 2 sees
        // enabled == false and clears. The LAST toggle state wins.
        #expect(outcomes == [.reconciled(donated: 1, capsHit: []), .cleared])
        #expect(indexer.log == ["deleteAll", "donate(1)", "deleteAll"])
    }
}
