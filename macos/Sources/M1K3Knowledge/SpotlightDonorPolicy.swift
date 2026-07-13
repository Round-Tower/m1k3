//
//  SpotlightDonorPolicy.swift
//  M1K3Knowledge
//
//  Pure policy for what M1K3 donates to the OS (CoreSpotlight) search index —
//  the CSSearchableIndex adapter lives in the app target; this layer is
//  dependency-free so the privacy rules are unit-pinned.
//
//  Doctrine (challenger-shaped, 2026-07-13):
//    - ALLOWLIST: only .document and .call are donatable. .memory is excluded
//      because distilled facts store the fact text AS the title (title == body)
//      — "title-only" donation would put private facts verbatim into ⌘Space.
//      .note shares that title-can-be-content shape. .quarantined is excluded
//      per the PR #28 index-segregation doctrine. Unknown/future kinds default
//      to NOT donated: a new kind must opt in here, never leak by default.
//    - Data-minimal entries: title + a fixed kind label + created date. No
//      chunk/body text ever reaches the OS index.
//    - The OS index is device-local and app-scoped. Nothing here may ever be
//      donated via NSUserActivity or marked eligible for public indexing /
//      Handoff — those are the only paths off the device.
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.9, Prior: Unknown
//

import Foundation

/// A data-minimal, framework-free description of one Spotlight donation.
public struct SearchIndexEntry: Equatable, Sendable {
    /// Stable identity in the OS index: "knowledge:<uuid>".
    public let uniqueID: String
    public let title: String
    /// Fixed vocabulary ("Document" / "Call") shown as the result subtitle.
    /// Derived from the kind alone — never from item content.
    public let kindLabel: String
    public let createdAt: Date

    public init(uniqueID: String, title: String, kindLabel: String, createdAt: Date) {
        self.uniqueID = uniqueID
        self.title = title
        self.kindLabel = kindLabel
        self.createdAt = createdAt
    }
}

/// Seam the app-target CoreSpotlight adapter conforms to, so store/lifecycle
/// logic can be tested against a fake without importing CoreSpotlight.
public protocol SystemSearchIndexing: Sendable {
    func donate(_ entries: [SearchIndexEntry]) async throws
    func deindex(uniqueIDs: [String]) async throws
    func deleteAll() async throws
}

public enum SpotlightDonorPolicy {
    /// The one Spotlight domain all M1K3 donations live under, so a single
    /// domain delete clears everything the app ever donated.
    public static let domainIdentifier = "app.m1k3.knowledge"

    /// Explicit opt-in set. Everything else — including kinds that don't
    /// exist yet — is not donated.
    private static let donatableLabels: [KnowledgeKind: String] = [
        .document: "Document",
        .call: "Call",
    ]

    public static func uniqueID(for id: UUID) -> String {
        "knowledge:\(id.uuidString)"
    }

    /// Returns the donation for an item, or nil when the item must not reach
    /// the OS index.
    public static func entry(for item: KnowledgeItem) -> SearchIndexEntry? {
        guard let label = donatableLabels[item.kind] else { return nil }
        return SearchIndexEntry(
            uniqueID: uniqueID(for: item.id),
            title: item.title,
            kindLabel: label,
            createdAt: item.createdAt
        )
    }

    public static func entries(for items: [KnowledgeItem]) -> [SearchIndexEntry] {
        items.compactMap(entry(for:))
    }
}
