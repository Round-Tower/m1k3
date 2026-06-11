//
//  DuckDuckGoHTMLParser.swift
//  M1K3AgentTools
//
//  Pure string-scanning parser for lite.duckduckgo.com result pages — no HTML
//  library, no third-party dep. Anchors carry class='result-link' (title +
//  href); the following <td class='result-snippet'> holds the snippet. The
//  bot-challenge ("anomaly") page contains none of these markers, so it
//  degrades to zero results, never garbage. Markup drift = fixture refresh.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.8, Prior: Unknown

import Foundation

enum DuckDuckGoHTMLParser {
    private static let linkMarker = "class='result-link'"
    private static let snippetMarker = "class='result-snippet'"

    static func parse(html: String) -> [WebSearchResult] {
        var results: [WebSearchResult] = []
        var cursor = html.startIndex

        while let marker = html.range(of: linkMarker, range: cursor ..< html.endIndex) {
            cursor = marker.upperBound
            guard let anchor = enclosingAnchor(in: html, around: marker) else { continue }

            let href = attribute("href", in: anchor.attributes)
            let title = plainText(from: String(anchor.innerText))
            guard let href, !href.isEmpty, !title.isEmpty else { continue }

            let snippet = snippetText(in: html, after: marker.upperBound)
            results.append(WebSearchResult(
                title: title,
                url: decodeRedirectURL(href),
                snippet: snippet ?? ""
            ))
            cursor = marker.upperBound
        }
        return results
    }

    /// True when DDG served its bot-challenge ("anomaly") page instead of
    /// results — seen live with status 202 after heavy use. Distinguishing it
    /// from a genuinely-empty result page keeps the agent's observation honest.
    static func isChallengePage(_ html: String) -> Bool {
        html.contains("anomaly.js") || html.contains("challenge-form")
    }

    /// DDG sometimes wraps result links as "//duckduckgo.com/l/?uddg=<dest>&rut=…".
    /// Unwrap to the destination; direct links pass through.
    static func decodeRedirectURL(_ href: String) -> String {
        guard href.contains("uddg=") else { return href }
        let absolute = href.hasPrefix("//") ? "https:" + href : href
        guard let components = URLComponents(string: absolute),
              let destination = components.queryItems?.first(where: { $0.name == "uddg" })?.value
        else { return href }
        return destination
    }

    /// Strip tags, decode the entities DDG emits, collapse whitespace.
    static func plainText(from htmlFragment: String) -> String {
        var text = htmlFragment.replacingOccurrences(
            of: "<[^>]+>", with: "", options: .regularExpression
        )
        let entities: [(String, String)] = [
            ("&nbsp;", " "), ("&quot;", "\""), ("&#x27;", "'"), ("&#39;", "'"),
            ("&lt;", "<"), ("&gt;", ">"), ("&amp;", "&"),
        ]
        for (entity, character) in entities {
            text = text.replacingOccurrences(of: entity, with: character)
        }
        return text
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Scanning helpers

    private static func enclosingAnchor(
        in html: String, around marker: Range<String.Index>
    ) -> (attributes: Substring, innerText: Substring)? {
        guard let open = html.range(of: "<a ", options: .backwards, range: html.startIndex ..< marker.lowerBound),
              let tagClose = html.range(of: ">", range: marker.upperBound ..< html.endIndex),
              let close = html.range(of: "</a>", range: tagClose.upperBound ..< html.endIndex)
        else { return nil }
        return (
            attributes: html[open.upperBound ..< tagClose.lowerBound],
            innerText: html[tagClose.upperBound ..< close.lowerBound]
        )
    }

    private static func attribute(_ attributeName: String, in attributes: Substring) -> String? {
        guard let start = attributes.range(of: "\(attributeName)=\""),
              let end = attributes.range(of: "\"", range: start.upperBound ..< attributes.endIndex)
        else { return nil }
        return String(attributes[start.upperBound ..< end.lowerBound])
    }

    private static func snippetText(in html: String, after position: String.Index) -> String? {
        guard let marker = html.range(of: snippetMarker, range: position ..< html.endIndex),
              let tagClose = html.range(of: ">", range: marker.upperBound ..< html.endIndex),
              let cellClose = html.range(of: "</td>", range: tagClose.upperBound ..< html.endIndex)
        else { return nil }
        // A snippet belonging to the NEXT result would sit beyond its own
        // result-link marker; only accept a snippet before the next link.
        let nextLink = html.range(of: linkMarker, range: position ..< html.endIndex)
        if let nextLink, nextLink.lowerBound < marker.lowerBound { return nil }
        return plainText(from: String(html[tagClose.upperBound ..< cellClose.lowerBound]))
    }
}
