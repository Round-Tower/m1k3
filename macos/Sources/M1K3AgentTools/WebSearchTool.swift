//
//  WebSearchTool.swift
//  M1K3AgentTools
//
//  DuckDuckGo web search behind the AgentTool seam. Instant Answer API first
//  (clean JSON, but empty for most natural queries), then the lite HTML
//  endpoint as the real workhorse. Chosen empirically 2026-06-09: the classic
//  html.duckduckgo.com GET serves a bot-challenge ("anomaly") page to
//  non-browser clients, while lite.duckduckgo.com returns results with
//  DIRECT hrefs. If lite gets flagged too, the parser sees no result markers
//  and we answer honestly with "No web results".
//
//  Privacy: the query leaves the device (to DuckDuckGo) — the app only
//  injects this tool when the user's web-search setting is on, and the chat
//  UI surfaces every search visibly. Errors are returned as observations,
//  never thrown, so the agent loop can recover and conclude from what it has.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Agent

public struct WebSearchTool: AgentTool {
    public let name = "web_search"
    public let description =
        "Search the web via DuckDuckGo for current or external information "
            + "(news, facts, anything not in stored knowledge). Argument: the search query."
    public let parameters = [
        ToolParameter(name: "query", description: "the web search query"),
    ]

    static let browserUserAgent =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.4 Safari/605.1.15"

    private let fetcher: any HTTPFetching
    private let maxResults: Int

    public init(fetcher: any HTTPFetching = URLSessionHTTPFetcher(), maxResults: Int = 5) {
        self.fetcher = fetcher
        self.maxResults = maxResults
    }

    public func execute(input: [String: String]) async throws -> ToolResult {
        let query = (input["query"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else {
            return ToolResult(output: "Error: empty query.")
        }
        do {
            let results = try await search(query: query)
            guard !results.isEmpty else {
                return ToolResult(output: "No web results for \"\(query)\".")
            }
            return ToolResult(output: WebSearchFormatter.format(results, limit: maxResults))
        } catch {
            return ToolResult(output: "Error: web search failed — \(error.localizedDescription)")
        }
    }

    private func search(query: String) async throws -> [WebSearchResult] {
        let instant = try DuckDuckGoInstantAnswerParser.parse(
            await fetch(instantAnswerURL(for: query)).data
        )
        if !instant.isEmpty { return instant }

        let fallback = try await fetch(liteURL(for: query))
        let html = String(data: fallback.data, encoding: .utf8) ?? ""
        return DuckDuckGoHTMLParser.parse(html: html)
    }

    private func fetch(_ url: URL) async throws -> (data: Data, response: HTTPURLResponse) {
        var request = URLRequest(url: url)
        request.setValue(Self.browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/json", forHTTPHeaderField: "Accept")
        request.setValue("en-GB,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        return try await fetcher.fetch(request)
    }

    private func instantAnswerURL(for query: String) -> URL {
        var components = URLComponents(string: "https://api.duckduckgo.com/")!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "no_html", value: "1"),
            URLQueryItem(name: "skip_disambig", value: "1"),
        ]
        return components.url!
    }

    private func liteURL(for query: String) -> URL {
        var components = URLComponents(string: "https://lite.duckduckgo.com/lite/")!
        components.queryItems = [URLQueryItem(name: "q", value: query)]
        return components.url!
    }
}
