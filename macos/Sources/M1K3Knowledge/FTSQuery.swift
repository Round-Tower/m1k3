//
//  FTSQuery.swift
//  M1K3Knowledge
//
//  The one home for FTS5 MATCH-string construction, shared by the knowledge
//  corpus (KnowledgeStore) and the memory graph (MemoryStore): double-quote
//  each token so FTS5 treats it as a literal, plus the OR-joined relaxation
//  for the zero-hit fallback — FTS5's implicit AND starves natural multi-term
//  queries (the B5 retrieval gap, live 2026-07-02).
//
//  Lifted verbatim from KnowledgeStore, where it was TDD'd; MemoryStore's
//  private copy had already drifted behind the relaxation fix — exactly the
//  divergence its own review note predicted when it deferred this lift.
//
//  Signed: Kev + claude-fable-5, 2026-07-02, Confidence 0.9 (pure string
//  policy, test-pinned in FTSQueryTests; both stores now share one rule).
//  Prior: Kev + claude-opus-4-8 (KnowledgeStore.sanitizeFTSQuery).
//

import Foundation

/// FTS5 MATCH-string construction: strict (implicit AND) form plus the
/// relaxed (OR) fallback for zero-hit retries.
public enum FTSQuery {
    /// Double-quote each whitespace-separated token so FTS5 treats them as
    /// literals (neutralises `*`, `:`, `"`, `-` etc.). Returns nil if the
    /// query has no usable tokens.
    public static func sanitized(_ query: String) -> String? {
        let tokens = ftsTokens(query)
        guard !tokens.isEmpty else { return nil }
        return tokens.map { "\"\($0)\"" }.joined(separator: " ")
    }

    /// The zero-hit fallback for `sanitized`: same quoted tokens, OR-joined so
    /// any term can match (BM25 ranks coverage). Nil below two tokens — a
    /// single-token OR is identical to the strict query.
    public static func relaxed(_ query: String) -> String? {
        let tokens = ftsTokens(query)
        guard tokens.count >= 2 else { return nil }
        return tokens.map { "\"\($0)\"" }.joined(separator: " OR ")
    }

    private static func ftsTokens(_ query: String) -> [String] {
        query
            .components(separatedBy: .whitespacesAndNewlines)
            .map { $0.replacingOccurrences(of: "\"", with: "") }
            .filter { !$0.isEmpty }
    }
}
