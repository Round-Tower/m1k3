//
//  WikipediaFact.swift
//  M1K3AgentTools
//
//  The pure model + parser + formatter behind the lookup_fact tool. The
//  MediaWiki query API hands back a page keyed by pageid; we lift the intro
//  extract and a canonical URL so the agent can quote AND cite, rather than
//  confabulate. Parser returns nil for "no article" (not an error) — the tool
//  turns that into an honest observation.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9, Prior: Unknown

import Foundation

public struct WikipediaFact: Sendable, Equatable {
    public let title: String
    public let extract: String
    public let url: String

    public init(title: String, extract: String, url: String) {
        self.title = title
        self.extract = extract
        self.url = url
    }
}

public enum WikipediaSummaryParser {
    /// Parse a MediaWiki `action=query` response (generator=search + extracts +
    /// info). Returns the best page with a non-empty intro extract, or nil when
    /// the search found nothing usable.
    public static func parse(_ data: Data) -> WikipediaFact? {
        guard let response = try? JSONDecoder().decode(Response.self, from: data),
              let pages = response.query?.pages
        else { return nil }

        // generator=search&gsrlimit=1 yields at most one page, but dictionary
        // order is undefined — pick the first page that actually has prose.
        let page = pages.values.first { page in
            !(page.extract ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        guard let page else { return nil }

        let extract = (page.extract ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !extract.isEmpty else { return nil }
        let title = (page.title ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let url = page.fullurl ?? page.canonicalurl ?? Self.canonicalURL(for: title)
        return WikipediaFact(title: title, extract: extract, url: url)
    }

    /// Fallback when the API omits info URLs: en.wikipedia.org/wiki/Title_Cased.
    /// Excludes `#`/`?` from the allowed set — left unencoded they'd be parsed
    /// as a fragment/query and the fetch would request the wrong resource.
    static func canonicalURL(for title: String) -> String {
        let allowed = CharacterSet.urlPathAllowed.subtracting(CharacterSet(charactersIn: "#?"))
        let path = title.replacingOccurrences(of: " ", with: "_")
            .addingPercentEncoding(withAllowedCharacters: allowed) ?? title
        return "https://en.wikipedia.org/wiki/\(path)"
    }

    private struct Response: Decodable { let query: Query? }
    private struct Query: Decodable { let pages: [String: Page]? }
    private struct Page: Decodable {
        let title: String?
        let extract: String?
        let fullurl: String?
        let canonicalurl: String?
    }
}

public enum WikipediaFormatter {
    /// Intro cap — a Wikipedia lead can run long; keep an observation small-model safe.
    static let extractCap = 1200

    /// "<extract>\n\nSource: <url>" — the trailing Source line is the citation
    /// anchor, so the agent grounds its answer instead of confabulating.
    public static func format(_ fact: WikipediaFact) -> String {
        "\(truncate(fact.extract))\n\nSource: \(fact.url)"
    }

    private static func truncate(_ extract: String) -> String {
        guard extract.count > extractCap else { return extract }
        return extract.prefix(extractCap).trimmingCharacters(in: .whitespaces) + "…"
    }
}
