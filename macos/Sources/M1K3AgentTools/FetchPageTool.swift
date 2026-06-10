//
//  FetchPageTool.swift
//  M1K3AgentTools
//
//  web_search finds pages; fetch_page READS one — the difference between a
//  link list and an actual answer (the Boston-weather follow-up). Pure
//  HTMLTextExtractor turns a page into readable text; the tool guards the
//  scheme (http/https only), sends browser headers, caps the output so a
//  small model's context survives, and reports failures as observations the
//  loop can recover from.
//
//  Privacy: fetching a page is egress, same as searching — the app only
//  injects this tool alongside web_search behind the same Settings toggle,
//  and the activity label shows which page is being read.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Agent
import os

/// Pure HTML → readable text: drop head/script/style/comments, turn block
/// boundaries into line breaks, strip tags, decode entities, tidy whitespace.
enum HTMLTextExtractor {
    static func text(from html: String) -> String {
        var work = html
        let removals = [
            "(?is)<script\\b.*?</script>",
            "(?is)<style\\b.*?</style>",
            "(?is)<head\\b.*?</head>",
            // Page chrome eats the output cap before content starts (seen live).
            "(?is)<nav\\b.*?</nav>",
            "(?is)<header\\b.*?</header>",
            "(?is)<footer\\b.*?</footer>",
            "(?s)<!--.*?-->",
        ]
        for pattern in removals {
            work = work.replacingOccurrences(of: pattern, with: " ", options: .regularExpression)
        }
        work = work.replacingOccurrences(
            of: "(?i)</(p|div|h[1-6]|li|tr|section|article|ul|ol|table)>|<br[^>]*>",
            with: "\n",
            options: .regularExpression
        )
        work = work.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        work = decodeEntities(work)
        let lines = work
            .components(separatedBy: "\n")
            .map { line in
                line.replacingOccurrences(of: "[ \\t]+", with: " ", options: .regularExpression)
                    .trimmingCharacters(in: .whitespaces)
            }
            .filter { !$0.isEmpty }
        return lines.joined(separator: "\n")
    }

    static func decodeEntities(_ text: String) -> String {
        var output = text
        output = output.replacing(/&#(\d+);/) { match in
            guard let code = UInt32(match.1), let scalar = Unicode.Scalar(code) else {
                return String(match.0)
            }
            return String(Character(scalar))
        }
        output = output.replacing(/&#x([0-9a-fA-F]+);/) { match in
            guard let code = UInt32(match.1, radix: 16), let scalar = Unicode.Scalar(code) else {
                return String(match.0)
            }
            return String(Character(scalar))
        }
        let named: [(String, String)] = [
            ("&nbsp;", " "), ("&quot;", "\""), ("&lt;", "<"), ("&gt;", ">"), ("&amp;", "&"),
        ]
        for (entity, character) in named {
            output = output.replacingOccurrences(of: entity, with: character)
        }
        return output
    }
}

public struct FetchPageTool: AgentTool {
    private static let log = Logger(subsystem: M1K3Log.subsystem, category: "fetch-page")

    public let name = "fetch_page"
    public let description =
        "Read a web page's actual content. Use after web_search: pass the most "
            + "relevant result URL to get the page's text. Argument: the page URL."
    public let parameters = [
        ToolParameter(name: "url", description: "the http(s) page URL"),
    ]

    private let fetcher: any HTTPFetching
    private let maxCharacters: Int

    public init(fetcher: any HTTPFetching = URLSessionHTTPFetcher(), maxCharacters: Int = 1500) {
        self.fetcher = fetcher
        self.maxCharacters = maxCharacters
    }

    public func execute(input: [String: String]) async throws -> ToolResult {
        let raw = (input["url"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: raw),
              let scheme = url.scheme?.lowercased(),
              ["http", "https"].contains(scheme),
              url.host() != nil
        else {
            return ToolResult(output: "Error: fetch_page needs a full http(s) URL "
                + "(use one from the web_search results).")
        }

        var request = URLRequest(url: url)
        request.setValue(WebSearchTool.browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        request.setValue("en-GB,en;q=0.9", forHTTPHeaderField: "Accept-Language")

        do {
            let (data, response) = try await fetcher.fetch(request)
            let html = String(data: data, encoding: .utf8) ?? ""
            let text = HTMLTextExtractor.text(from: html)
            Self.log.info("""
            fetched \(url.host() ?? "?", privacy: .public): HTTP \(response.statusCode), \
            \(html.count) bytes → \(text.count) chars readable
            """)
            guard !text.isEmpty else {
                return ToolResult(output: "The page at \(url.host() ?? "that address") had "
                    + "no readable text (it may need JavaScript). Try another result.")
            }
            guard text.count > maxCharacters else {
                return ToolResult(output: text)
            }
            let capped = text.prefix(maxCharacters)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return ToolResult(output: capped + "…")
        } catch {
            Self.log.error("fetch failed for \(url.absoluteString, privacy: .public): \(error, privacy: .public)")
            return ToolResult(output: "Error: could not fetch the page — \(error.localizedDescription)")
        }
    }
}
