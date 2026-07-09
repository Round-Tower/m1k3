//
//  ForgetResolver.swift
//  M1K3MCPKit
//
//  The decision half of forget_memory — the consent primitive over MCP. Forgetting
//  is a HARD delete and irreversible, so the bar to act is deliberately higher than
//  recall's: a marginal match surfaces as "not confident, here's the closest" rather
//  than erasing the wrong fact on a guess. Pure + value-only so the irreversible
//  call is decided by tested logic, not buried in app glue.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.9 (decision pinned by
//  unit tests against constructed hits; the both-stores delete + embedder are app
//  glue, verify-at-⌘R). Prior: Unknown.
//

import Foundation
import M1K3Memory

/// What the resolver decided to do with the top recall hit for a forget query.
public enum ForgetResolution: Sendable, Equatable {
    /// The top hit cleared the (higher) forget bar on its own — delete it.
    case forget(Memory)
    /// Nothing cleared the bar. `closest` is the best recall hit that *did*
    /// clear the recall threshold but not the forget floor (so the caller can
    /// confirm it word-for-word), or `nil` when nothing matched at all.
    case notConfident(closest: Memory?)
}

/// What a forget attempt actually did — returned by the app's forget handler and
/// formatted for the caller. Forgetting is irreversible, so the outcome always
/// names exactly what was removed (an audit trail) or why nothing was.
public enum ForgetOutcome: Sendable, Equatable {
    /// The fact was hard-deleted from BOTH the memory graph and the document
    /// corpus. `text` is the exact memory removed.
    case forgotten(text: String)
    /// Nothing was deleted. `closest` (if any) is the near-miss text the caller
    /// can repeat back to forget deliberately.
    case notConfident(closest: String?)
}

/// Decides whether a recall result is a confident-enough match to hard-delete.
public enum ForgetResolver {
    /// The forget bar. Well above recall's `GroundingGate.memoryThreshold`
    /// (0.35 since the 2026-07-09 re-derivation) because deletion is
    /// irreversible — a recall match that's merely "relevant" must not be
    /// enough to erase a fact. 0.6 ≈ "clearly the same fact". Forget queries
    /// embed BARE (fact-to-fact; a verbatim repeat is cosine ≈ 1.0), so this
    /// bar deliberately did NOT move with the query-instruction floor re-tune.
    public static let floor: Float = 0.6

    /// The bar for OFFERING a near-miss ("Closest: … repeat it back to
    /// forget"). Since the 07-09 threshold-0 candidate search, recall always
    /// returns something from a populated store — below this bar the top hit
    /// is a random fact, and inviting a word-for-word repeat of a random fact
    /// is a consent hazard (the repeat would DELETE it). 0.35 mirrors the
    /// memory recall floor's register: plausibly-the-same-fact wordings sit
    /// above it, unrelated facts below.
    public static let suggestionFloor: Float = 0.35

    /// Resolve the top recall hit against the forget floor. `hits` are expected
    /// best-first (as `MemoryStore.recall` returns them). A hit with no cosine
    /// similarity (FTS-only) can never be confident → treated as a near-miss
    /// (it matched by KEYWORD, so it is a plausible suggestion even without a
    /// cosine — the pre-existing contract, unchanged).
    public static func resolve(hits: [MemoryHit], floor: Float = ForgetResolver.floor) -> ForgetResolution {
        guard let top = hits.first else { return .notConfident(closest: nil) }
        guard let similarity = top.similarity else {
            return .notConfident(closest: top.memory)
        }
        if similarity >= floor { return .forget(top.memory) }
        return .notConfident(closest: similarity >= suggestionFloor ? top.memory : nil)
    }
}
