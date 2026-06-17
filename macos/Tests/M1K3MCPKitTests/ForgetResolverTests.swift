//
//  ForgetResolverTests.swift
//  M1K3MCPKitTests
//
//  The forget decision is irreversible, so its bar is pinned hard: the top hit
//  must clear the forget floor (above recall's threshold) on its own, a near-miss
//  surfaces the closest instead of deleting, and an FTS-only (no-cosine) hit is
//  never confident.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.95 (pure decision over
//  constructed hits). Prior: Unknown.
//

@testable import M1K3MCPKit
import M1K3Memory
import Testing

struct ForgetResolverTests {
    private func hit(_ text: String, similarity: Float?) -> MemoryHit {
        MemoryHit(memory: Memory(kind: .note, text: text, source: "test"), similarity: similarity)
    }

    @Test("a confident top hit (≥ floor) resolves to forget")
    func confidentForgets() {
        let hits = [hit("Kev's sister is Aoife.", similarity: 0.82)]
        guard case let .forget(memory) = ForgetResolver.resolve(hits: hits) else {
            Issue.record("expected .forget"); return
        }
        #expect(memory.text == "Kev's sister is Aoife.")
    }

    @Test("a hit exactly at the floor (0.6) resolves to forget (>= is inclusive)")
    func atFloorForgets() {
        let hits = [hit("Kev's team is Round Tower.", similarity: ForgetResolver.floor)]
        guard case .forget = ForgetResolver.resolve(hits: hits) else {
            Issue.record("expected .forget at the floor"); return
        }
    }

    @Test("a near-miss (below floor) keeps the fact and returns it as closest")
    func nearMissKeeps() {
        let hits = [hit("Kev likes tea.", similarity: 0.55)] // cleared recall (0.51), not forget (0.6)
        guard case let .notConfident(closest) = ForgetResolver.resolve(hits: hits) else {
            Issue.record("expected .notConfident"); return
        }
        #expect(closest?.text == "Kev likes tea.")
    }

    @Test("no hits at all resolves to notConfident with no closest")
    func nothingMatched() {
        #expect(ForgetResolver.resolve(hits: []) == .notConfident(closest: nil))
    }

    @Test("an FTS-only hit (no cosine) is never confident enough to delete")
    func ftsOnlyIsNeverConfident() {
        let hits = [hit("Kev's sister is Aoife.", similarity: nil)]
        guard case let .notConfident(closest) = ForgetResolver.resolve(hits: hits) else {
            Issue.record("expected .notConfident"); return
        }
        #expect(closest?.text == "Kev's sister is Aoife.")
    }

    @Test("the top hit is the one judged (best-first ordering is honoured)")
    func judgesTheTopHit() {
        let hits = [
            hit("Kev's sister is Aoife.", similarity: 0.9),
            hit("Aoife's birthday is in March.", similarity: 0.7),
        ]
        guard case let .forget(memory) = ForgetResolver.resolve(hits: hits) else {
            Issue.record("expected .forget"); return
        }
        #expect(memory.text == "Kev's sister is Aoife.")
    }
}
