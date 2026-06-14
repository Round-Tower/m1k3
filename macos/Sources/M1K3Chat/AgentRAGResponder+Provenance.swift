//
//  AgentRAGResponder+Provenance.swift
//  M1K3Chat
//
//  Deterministic source provenance for agent answers — the "Web sources:" and
//  "Wikipedia sources:" blocks. URLs are lifted from the tool observations in
//  the reasoning trace, never the model, so a grounded answer carries a real
//  citation that can't be hallucinated. Extracted from AgentRAGResponder to
//  keep that type under the type-body length ceiling.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9, Prior: Unknown
//  (webSourcesBlock + WebSourceExtractor originate in the web-tool work;
//  factSources added 2026-06-14.)

import Foundation
import M1K3Agent

extension AgentRAGResponder {
    /// Deterministic provenance for web answers — extracted from the trace,
    /// not the model, so it can't be hallucinated.
    static func webSourcesBlock(for result: AgentResult) -> String {
        guard result.toolsUsed.contains("web_search") else { return "" }
        let urls = WebSourceExtractor.urls(from: result.reasoningTrace)
        guard !urls.isEmpty else { return "" }
        let lines = urls.prefix(3).map { "• \($0)" }.joined(separator: "\n")
        return "\n\nWeb sources:\n\(lines)"
    }

    /// Deterministic provenance for lookup_fact answers — the Wikipedia URL is
    /// lifted from the tool observation's `Source:` line, never the model, so a
    /// Wikipedia-grounded answer carries a real citation. Failed lookups have no
    /// Source line and so contribute nothing (no phantom citation).
    static func factSourcesBlock(for result: AgentResult) -> String {
        guard result.toolsUsed.contains("lookup_fact") else { return "" }
        let urls = FactSourceExtractor.urls(from: result.reasoningTrace)
        guard !urls.isEmpty else { return "" }
        let lines = urls.prefix(3).map { "• \($0)" }.joined(separator: "\n")
        return "\n\nWikipedia sources:\n\(lines)"
    }
}

/// Pulls the result URLs out of web_search observations ("Title — https://…").
enum WebSourceExtractor {
    static func urls(from trace: [ReasoningStep]) -> [String] {
        var seen = Set<String>()
        var ordered: [String] = []
        for step in trace where step.action?.hasPrefix("web_search(") == true {
            guard let observation = step.observation else { continue }
            for match in observation.matches(of: /— (https?:\/\/\S+)/) {
                // \S+ greedily eats sentence punctuation after the URL
                // ("… — https://example.com/page." captures the dot) — trim
                // trailing punctuation that is never meaningful at a URL end.
                let url = String(match.1).trimmedOfTrailingPunctuation()
                if seen.insert(url).inserted {
                    ordered.append(url)
                }
            }
        }
        return ordered
    }
}

/// Pulls the canonical URL out of lookup_fact observations — the trailing
/// "Source: https://…" line that WikipediaFormatter appends. Only scans
/// lookup_fact steps, so a web_search observation can't leak in here.
enum FactSourceExtractor {
    static func urls(from trace: [ReasoningStep]) -> [String] {
        var seen = Set<String>()
        var ordered: [String] = []
        for step in trace where step.action?.hasPrefix("lookup_fact(") == true {
            guard let observation = step.observation else { continue }
            for match in observation.matches(of: /Source: (https?:\/\/\S+)/) {
                let url = String(match.1).trimmedOfTrailingPunctuation()
                if seen.insert(url).inserted {
                    ordered.append(url)
                }
            }
        }
        return ordered
    }
}

private extension String {
    /// Known trade-off: a URL legitimately ENDING in a balanced `)`/`]`
    /// (Wikipedia "…_(disambiguation)") gets over-trimmed. Acceptable —
    /// sentence punctuation dominates DDG observations, and these are
    /// display/citation URLs, never fetch targets.
    func trimmedOfTrailingPunctuation() -> String {
        var trimmed = Substring(self)
        while let last = trimmed.last, ".,;:)]»'\"".contains(last) {
            trimmed = trimmed.dropLast()
        }
        return String(trimmed)
    }
}
