//
//  WebSearchToolTests.swift
//  M1K3AgentToolsTests
//
//  Orchestration pinned with a scripted fetcher: IA hit ⇒ one request; IA
//  empty ⇒ lite fallback with browser headers; network failure ⇒ an "Error:"
//  observation (never a throw — the agent loop must recover); both empty ⇒
//  an honest "no results".
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3AgentTools
import Synchronization
import Testing

/// Scripted HTTPFetching fake: responds per-host, records every request.
private final class FakeHTTPFetcher: HTTPFetching, Sendable {
    private let requestLog = Mutex<[URLRequest]>([])
    private let respond: @Sendable (URLRequest) throws -> Data

    init(respond: @escaping @Sendable (URLRequest) throws -> Data) {
        self.respond = respond
    }

    var requests: [URLRequest] {
        requestLog.withLock { $0 }
    }

    func fetch(_ request: URLRequest) async throws -> (data: Data, response: HTTPURLResponse) {
        requestLog.withLock { $0.append(request) }
        let data = try respond(request)
        let response = HTTPURLResponse(
            url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil
        )!
        return (data, response)
    }
}

private let populatedIA = """
{"Answer":"","AbstractText":"Swift is a language.","AbstractURL":"https://en.wikipedia.org/wiki/Swift",
 "Heading":"Swift","RelatedTopics":[]}
"""

private let emptyIA = """
{"Answer":"","AbstractText":"","AbstractURL":"","Heading":"","RelatedTopics":[]}
"""

private let liteHTML = """
<html><body><table><tr><td>
<a rel="nofollow" href="https://example.com/one" class='result-link'>Result One</a>
</td></tr><tr><td class='result-snippet'>First snippet.</td></tr></table></body></html>
"""

struct WebSearchToolTests {
    @Test("instant answer hit makes exactly one request")
    func instantAnswerHit() async throws {
        let fetcher = FakeHTTPFetcher { _ in Data(populatedIA.utf8) }
        let tool = WebSearchTool(fetcher: fetcher)
        let result = try await tool.execute(input: ["query": "swift language"])
        #expect(result.output.contains("Swift — https://en.wikipedia.org/wiki/Swift"))
        #expect(fetcher.requests.count == 1)
        #expect(fetcher.requests.first?.url?.host == "api.duckduckgo.com")
    }

    @Test("empty instant answer falls back to the lite endpoint with browser headers")
    func fallsBackToLite() async throws {
        let fetcher = FakeHTTPFetcher { request in
            request.url?.host == "api.duckduckgo.com" ? Data(emptyIA.utf8) : Data(liteHTML.utf8)
        }
        let tool = WebSearchTool(fetcher: fetcher)
        let result = try await tool.execute(input: ["query": "anything else"])
        #expect(result.output.contains("Result One — https://example.com/one"))
        #expect(result.output.contains("First snippet."))
        #expect(fetcher.requests.count == 2)
        let fallback = try #require(fetcher.requests.last)
        #expect(fallback.url?.host == "lite.duckduckgo.com")
        #expect(fallback.value(forHTTPHeaderField: "User-Agent")?.contains("Safari") == true)
        #expect(fallback.url?.query?.contains("q=anything%20else") == true)
    }

    @Test("network failure becomes a recoverable Error observation, not a throw")
    func networkFailure() async throws {
        struct Boom: Error {}
        let fetcher = FakeHTTPFetcher { _ in throw Boom() }
        let tool = WebSearchTool(fetcher: fetcher)
        let result = try await tool.execute(input: ["query": "anything"])
        #expect(result.output.hasPrefix("Error: web search failed"))
    }

    @Test("a rate-limit challenge page is reported as unavailable, not 'no results'")
    func challengePageReported() async throws {
        let challengeHTML = """
        <html><form id="challenge-form" action="//duckduckgo.com/anomaly.js?q=x"></form></html>
        """
        let fetcher = FakeHTTPFetcher { request in
            request.url?.host == "api.duckduckgo.com" ? Data(emptyIA.utf8) : Data(challengeHTML.utf8)
        }
        let tool = WebSearchTool(fetcher: fetcher)
        let result = try await tool.execute(input: ["query": "anything"])
        #expect(result.output.hasPrefix("Error: web search is temporarily unavailable"))
    }

    @Test("no results anywhere is reported honestly")
    func noResults() async throws {
        let fetcher = FakeHTTPFetcher { request in
            request.url?.host == "api.duckduckgo.com" ? Data(emptyIA.utf8) : Data("<html></html>".utf8)
        }
        let tool = WebSearchTool(fetcher: fetcher)
        let result = try await tool.execute(input: ["query": "obscurest"])
        #expect(result.output == "No web results for \"obscurest\".")
    }

    @Test("empty query is rejected without any request")
    func emptyQuery() async throws {
        let fetcher = FakeHTTPFetcher { _ in Data() }
        let tool = WebSearchTool(fetcher: fetcher)
        let result = try await tool.execute(input: ["query": "   "])
        #expect(result.output == "Error: empty query.")
        #expect(fetcher.requests.isEmpty)
    }

    @Test("declares the agent-facing contract")
    func declaresContract() {
        let tool = WebSearchTool()
        #expect(tool.name == "web_search")
        #expect(tool.description.contains("DuckDuckGo"))
        #expect(tool.parameters.first?.name == "query")
    }
}
