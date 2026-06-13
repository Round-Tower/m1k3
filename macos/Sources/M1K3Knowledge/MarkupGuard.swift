//
//  MarkupGuard.swift
//  M1K3Knowledge
//
//  Rejects raw-markup payloads at the ingest gate. Born of the "Artboard"
//  finding (MCP test report 2026-06-11, F1): a 988KB Sketch SVG indexed as a
//  document, polluting FTS keyword hits with XML noise. Two signals:
//  a known markup prefix, or tag density no prose ever reaches.
//
//  Deliberately permissive for prose mentioning tags ("<think>") or using
//  angle brackets as math — only whole-file markup should trip it.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 (signals
//  test-pinned; thresholds are empirical starting points). Prior: Unknown.
//

import Foundation

public enum MarkupGuard {
    /// True when the text is markup (SVG/XML/HTML), not prose.
    public static func looksLikeMarkup(_ text: String) -> Bool {
        let trimmed = text.drop { $0.isWhitespace }
        guard !trimmed.isEmpty else { return false }

        let lowered = trimmed.prefix(64).lowercased()
        for prefix in ["<?xml", "<svg", "<!doctype", "<html"] where lowered.hasPrefix(prefix) {
            return true
        }

        // Density: count '<' in the first 2KB. Prose with a few inline tags
        // sits far below 1-per-50-chars; generated markup sits far above.
        // The absolute floor keeps short prose ("use the <think> tag") from
        // tripping a ratio computed over a tiny sample.
        let sample = trimmed.prefix(2048)
        let tagCount = sample.count { $0 == "<" }
        return tagCount >= 8 && tagCount > sample.count / 50
    }
}
