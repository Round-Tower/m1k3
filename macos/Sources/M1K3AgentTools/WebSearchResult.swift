//
//  WebSearchResult.swift
//  M1K3AgentTools
//
//  The shared result model both DDG parsers emit, plus the pure formatter that
//  turns results into an agent observation. Snippets are truncated so a web
//  observation can't blow a small model's context window mid-loop.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.9, Prior: Unknown

import Foundation

public struct WebSearchResult: Sendable, Equatable {
    public let title: String
    public let url: String
    public let snippet: String

    public init(title: String, url: String, snippet: String) {
        self.title = title
        self.url = url
        self.snippet = snippet
    }
}

public enum WebSearchFormatter {
    /// Per-result snippet cap — keeps a full observation small-model safe.
    static let snippetCap = 250

    /// "1. Title — https://url\n   snippet" for the top `limit` results.
    public static func format(_ results: [WebSearchResult], limit: Int) -> String {
        results.prefix(limit).enumerated().map { index, result in
            "\(index + 1). \(result.title) — \(result.url)\n   \(truncate(result.snippet))"
        }.joined(separator: "\n")
    }

    private static func truncate(_ snippet: String) -> String {
        guard snippet.count > snippetCap else { return snippet }
        return snippet.prefix(snippetCap).trimmingCharacters(in: .whitespaces) + "…"
    }
}
