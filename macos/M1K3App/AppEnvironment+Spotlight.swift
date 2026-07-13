//
//  AppEnvironment+Spotlight.swift
//  M1K3
//
//  Opt-in ⌘Space: donate document/call TITLES to the OS (CoreSpotlight) index.
//  The rules live in the pure, unit-pinned SpotlightDonorPolicy (M1K3Knowledge):
//  allowlist .document/.call only — never memories (title == fact text), never
//  .quarantined, never unknown kinds. Data-minimal: title + kind label + date;
//  no body text ever reaches the OS index.
//
//  Lifecycle doctrine (challenger-shaped, 2026-07-13):
//    - Default OFF (Phase 17a: nil is a NO). ON is a calm Settings opt-in.
//    - One idempotent reconcile owns every trigger (launch, toggle, thermal
//      recovery): ON → deleteAll + full re-donate; OFF → deleteAll,
//      UNCONDITIONALLY — the OS index survives container resets the store
//      doesn't, so OFF must actively enforce emptiness, not assume it.
//    - Donation is CSSearchableIndex only. Never NSUserActivity, never
//      public-indexing/Handoff eligibility — those are the only paths that
//      would take a title off this Mac.
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.85 (policy layer is
//  unit-pinned; CSSearchableIndex behaviour — expiry pin, domain delete —
//  is verify-by-launch). Prior: Unknown.
//

import CoreSpotlight
import Foundation
import M1K3Knowledge
import M1K3LogCore
import OSLog

/// CSSearchableIndex adapter behind the pure `SystemSearchIndexing` seam.
final class SpotlightIndexer: SystemSearchIndexing {
    func donate(_ entries: [SearchIndexEntry]) async throws {
        guard CSSearchableIndex.isIndexingAvailable(), !entries.isEmpty else { return }
        let items = entries.map { entry in
            let attributes = CSSearchableItemAttributeSet(contentType: .item)
            attributes.title = entry.title
            attributes.contentDescription = "M1K3 \(entry.kindLabel.lowercased())"
            attributes.contentCreationDate = entry.createdAt
            let item = CSSearchableItem(
                uniqueIdentifier: entry.uniqueID,
                domainIdentifier: SpotlightDonorPolicy.domainIdentifier,
                attributeSet: attributes
            )
            // Spotlight silently expires items (~30 days) unless pinned. The
            // launch rebuild refreshes anyway; the pin covers Macs that just
            // sleep past the default expiry.
            item.expirationDate = .distantFuture
            return item
        }
        try await CSSearchableIndex.default().indexSearchableItems(items)
    }

    func deindex(uniqueIDs: [String]) async throws {
        guard !uniqueIDs.isEmpty else { return }
        try await CSSearchableIndex.default().deleteSearchableItems(withIdentifiers: uniqueIDs)
    }

    func deleteAll() async throws {
        try await CSSearchableIndex.default()
            .deleteSearchableItems(withDomainIdentifiers: [SpotlightDonorPolicy.domainIdentifier])
    }
}

extension AppEnvironment {
    /// Settings toggle — absent/false means OFF (nil is a NO).
    nonisolated static let spotlightIndexingKey = "spotlight.indexingEnabled"

    private static let spotlightLog = M1K3Log.logger(.spotlight)

    var spotlightIndexingEnabled: Bool {
        UserDefaults.standard.bool(forKey: Self.spotlightIndexingKey)
    }

    /// Per-kind fetch ceiling for the reconcile. allItems defaults to 200 —
    /// and with kind: nil the ever-growing distilled .memory rows compete for
    /// those slots, silently crowding documents/calls out of the sync (the
    /// quality-review catch). Per-kind fetches at an explicit generous limit
    /// sidestep the crowding; hitting the cap logs loudly instead of lying.
    private static let spotlightFetchLimit = 2000

    /// The one reconcile every trigger funnels through (launch, Settings
    /// toggle, thermal recovery). Re-entry coalesces: a call that arrives
    /// mid-sync marks the state dirty and the running sync loops once more,
    /// so a rapid ON→OFF (or OFF→ON) toggle always converges on the LAST
    /// state — a dropped second call would leave the index enforcing a
    /// stale choice until the next launch.
    func syncSpotlightIndex() async {
        guard !isSpotlightSyncing else {
            spotlightSyncNeedsRerun = true
            return
        }
        isSpotlightSyncing = true
        defer { isSpotlightSyncing = false }
        repeat {
            spotlightSyncNeedsRerun = false
            await reconcileSpotlightIndexOnce()
        } while spotlightSyncNeedsRerun
    }

    private func reconcileSpotlightIndexOnce() async {
        guard spotlightIndexingEnabled else {
            // OFF actively enforces an empty index — a container reset wipes
            // the store and defaults but NOT the OS index, so "nothing was
            // ever donated" is not a safe assumption. Cheap domain delete.
            try? await spotlightIndexer.deleteAll()
            return
        }
        // Rebuild is background work: skip under thermal/low-power pressure;
        // the recovery observer re-runs this reconcile on cooldown.
        guard Self.backgroundWorkAllowed() else {
            armThermalRecovery()
            return
        }
        do {
            // deleteAll-then-donate every launch: self-heals orphans from
            // container resets and store rebuilds (the OS index has no
            // default-deny of its own — full reconcile is the guarantee).
            // Known narrow race: an ingest/delete hook landing mid-reconcile
            // can be transiently missed by this snapshot; the next reconcile
            // trigger (launch at the latest) self-heals it.
            try await spotlightIndexer.deleteAll()
            let documents = try store.allItems(kind: .document, limit: Self.spotlightFetchLimit)
            let calls = try store.allItems(kind: .call, limit: Self.spotlightFetchLimit)
            let slices = [("documents", documents), ("calls", calls)]
            for (label, slice) in slices where slice.count == Self.spotlightFetchLimit {
                Self.spotlightLog.error(
                    """
                    spotlight reconcile hit the \(Self.spotlightFetchLimit, privacy: .public) \
                    \(label, privacy: .public) cap — older items are NOT donated
                    """
                )
            }
            let entries = SpotlightDonorPolicy.entries(for: documents + calls)
            try await spotlightIndexer.donate(entries)
            Self.spotlightLog.notice("spotlight reconciled: \(entries.count, privacy: .public) item(s) donated")
        } catch {
            Self.spotlightLog.error("spotlight reconcile failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Donate one freshly-ingested item (no-op when OFF or not donatable —
    /// the policy owns the kind rules).
    func spotlightDonate(itemID: UUID) async {
        guard spotlightIndexingEnabled else { return }
        // item(id:) has no kind filter by design — the policy is the filter here.
        guard let item = try? store.item(id: itemID),
              let entry = SpotlightDonorPolicy.entry(for: item) else { return }
        do {
            try await spotlightIndexer.donate([entry])
        } catch {
            Self.spotlightLog.error("spotlight donate failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Remove one deleted item from the OS index. Runs regardless of the
    /// toggle — a deindex is always safe and a delete must never leave a ghost.
    func spotlightDeindex(id: UUID) async {
        do {
            try await spotlightIndexer.deindex(uniqueIDs: [SpotlightDonorPolicy.uniqueID(for: id)])
        } catch {
            Self.spotlightLog.error("spotlight deindex failed: \(error.localizedDescription, privacy: .public)")
        }
    }
}
