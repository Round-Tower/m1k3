//
//  SpotlightDonorPolicyTests.swift
//  M1K3KnowledgeTests
//
//  Pins the pure donation policy for the OS (CoreSpotlight) search index.
//  The load-bearing rules, challenger-shaped (2026-07-13):
//    - ALLOWLIST, not blocklist: only .document and .call are donatable.
//      .memory is excluded because distilled facts store the fact text AS the
//      title (title == body), so "title-only donation" would put private facts
//      verbatim into system-wide Spotlight. .quarantined is excluded per the
//      PR #28 index-segregation doctrine. Unknown/future kinds default to
//      NOT donated — a new kind must opt in here, never leak by default.
//    - Donated content is data-minimal: title + a generic kind label + date.
//      The kind label must never carry item content.
//
//  Signed: Kev + claude-fable-5, 2026-07-13, Confidence 0.9, Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

struct SpotlightDonorPolicyTests {
    private func item(kind: KnowledgeKind, title: String = "Q3 planning notes") -> KnowledgeItem {
        KnowledgeItem(kind: kind, title: title)
    }

    // MARK: - Allowlist

    @Test func documentIsDonatable() {
        let doc = item(kind: .document)
        let entry = SpotlightDonorPolicy.entry(for: doc)
        #expect(entry != nil)
        #expect(entry?.title == "Q3 planning notes")
        #expect(entry?.kindLabel == "Document")
        #expect(entry?.createdAt == doc.createdAt)
    }

    @Test func callIsDonatable() {
        let entry = SpotlightDonorPolicy.entry(for: item(kind: .call))
        #expect(entry != nil)
        #expect(entry?.kindLabel == "Call")
    }

    @Test func memoryIsNeverDonated() {
        // Distilled facts have title == fact text — donating the "title" would
        // put the private fact itself into the OS index.
        #expect(SpotlightDonorPolicy.entry(for: item(kind: .memory, title: "Kev lives in Ardmore")) == nil)
    }

    @Test func quarantinedIsNeverDonated() {
        #expect(SpotlightDonorPolicy.entry(for: item(kind: .quarantined)) == nil)
    }

    @Test func noteIsNotDonatedInLeanScope() {
        // Notes are user-typed content whose titles can BE the content;
        // excluded from PR 1's lean scope alongside .memory.
        #expect(SpotlightDonorPolicy.entry(for: item(kind: .note)) == nil)
    }

    @Test func unknownFutureKindDefaultsToNotDonated() {
        let email = KnowledgeKind(rawValue: "email")
        #expect(SpotlightDonorPolicy.entry(for: item(kind: email)) == nil)
    }

    // MARK: - Identity

    @Test func uniqueIDIsStableAndPrefixed() {
        let doc = item(kind: .document)
        let entry = SpotlightDonorPolicy.entry(for: doc)
        #expect(entry?.uniqueID == "knowledge:\(doc.id.uuidString)")
        #expect(SpotlightDonorPolicy.uniqueID(for: doc.id) == "knowledge:\(doc.id.uuidString)")
    }

    // MARK: - Batch filtering

    @Test func entriesFilterToTheAllowlist() {
        let items = [
            item(kind: .document, title: "A"),
            item(kind: .memory, title: "private fact"),
            item(kind: .call, title: "B"),
            item(kind: .quarantined, title: "internal QA"),
            item(kind: .note, title: "C"),
        ]
        let entries = SpotlightDonorPolicy.entries(for: items)
        #expect(entries.map(\.title) == ["A", "B"])
    }

    // MARK: - Data minimisation

    @Test func kindLabelNeverCarriesContent() {
        // The label is a fixed vocabulary derived from the kind alone.
        let doc = SpotlightDonorPolicy.entry(for: item(kind: .document, title: "secret plan"))
        let call = SpotlightDonorPolicy.entry(for: item(kind: .call, title: "secret call"))
        #expect(doc?.kindLabel == "Document")
        #expect(call?.kindLabel == "Call")
        #expect(doc?.kindLabel.contains("secret") == false)
        #expect(call?.kindLabel.contains("secret") == false)
    }
}
