//
//  HTTPHardeningTests.swift
//  M1K3AgentToolsTests
//
//  Pins the robustness primitives: status classification (transient vs not),
//  charset-aware decoding (header / meta / UTF-8 / Latin-1, never empty), and
//  the one-bounded-retry decorator (retries a transient throw or 503, then
//  surfaces the result; never retries a 4xx or a non-transient error).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3AgentTools
import Synchronization
import Testing

struct HTTPStatusTests {
    @Test("classifies status codes into ok / transient / client / server")
    func classify() {
        #expect(HTTPStatus.classify(200) == .ok)
        #expect(HTTPStatus.classify(202) == .transient) // DDG throttle
        #expect(HTTPStatus.classify(429) == .transient)
        #expect(HTTPStatus.classify(503) == .transient)
        #expect(HTTPStatus.classify(404) == .clientError)
        #expect(HTTPStatus.classify(500) == .serverError)
    }

    @Test("flags momentary network errors as transient")
    func transientErrors() {
        #expect(HTTPStatus.isTransient(URLError(.timedOut)))
        #expect(HTTPStatus.isTransient(URLError(.networkConnectionLost)))
        #expect(!HTTPStatus.isTransient(URLError(.unsupportedURL)))
        struct Other: Error {}
        #expect(!HTTPStatus.isTransient(Other()))
    }
}

struct BodyDecoderTests {
    @Test("reads UTF-8 and strips a BOM")
    func utf8AndBOM() {
        #expect(BodyDecoder.decode(Data("héllo".utf8), contentType: "text/html; charset=utf-8") == "héllo")
        var bom = Data([0xEF, 0xBB, 0xBF])
        bom.append(Data("café".utf8))
        #expect(BodyDecoder.decode(bom, contentType: nil) == "café")
    }

    @Test("honours a Latin-1 page instead of returning empty")
    func latin1FromHeader() {
        // 0xE9 is é in ISO-8859-1 but invalid as standalone UTF-8.
        let data = Data([0x63, 0x61, 0x66, 0xE9]) // "caf<é>"
        let decoded = BodyDecoder.decode(data, contentType: "text/html; charset=iso-8859-1")
        #expect(decoded == "café")
        // Without a header, the same bytes must still not come back empty.
        #expect(!BodyDecoder.decode(data, contentType: nil).isEmpty)
    }

    @Test("sniffs a <meta charset> when the header is absent")
    func metaSniff() {
        var bytes = Data("<html><head><meta charset=\"iso-8859-1\"></head><body>caf".utf8)
        bytes.append(0xE9) // é
        bytes.append(Data("</body></html>".utf8))
        let decoded = BodyDecoder.decode(bytes, contentType: nil)
        #expect(decoded.contains("café"))
    }

    @Test("empty data decodes to empty, never crashes")
    func empty() {
        #expect(BodyDecoder.decode(Data(), contentType: "text/html").isEmpty)
    }
}

/// Inner fetcher that yields a scripted sequence of outcomes (throw or status).
private final class ScriptedFetcher: HTTPFetching, Sendable {
    enum Step { case status(Int), throwTransient, throwFatal }
    private let steps: Mutex<[Step]>
    private let calls = Mutex(0)

    init(_ steps: [Step]) {
        self.steps = Mutex(steps)
    }

    var callCount: Int {
        calls.withLock { $0 }
    }

    func fetch(_ request: URLRequest) async throws -> (data: Data, response: HTTPURLResponse) {
        calls.withLock { $0 += 1 }
        let step = steps.withLock { $0.isEmpty ? .status(200) : $0.removeFirst() }
        switch step {
        case .throwTransient: throw URLError(.timedOut)
        case .throwFatal: throw URLError(.unsupportedURL)
        case let .status(code):
            let response = HTTPURLResponse(url: request.url!, statusCode: code, httpVersion: nil, headerFields: nil)!
            return (Data("ok".utf8), response)
        }
    }
}

struct RetryingHTTPFetcherTests {
    private let noSleep: @Sendable (Int) async -> Void = { _ in }
    private let request = URLRequest(url: URL(string: "https://example.com")!)

    @Test("retries once after a transient throw, then succeeds")
    func retriesTransientThrow() async throws {
        let inner = ScriptedFetcher([.throwTransient, .status(200)])
        let fetcher = RetryingHTTPFetcher(inner, backoff: noSleep)
        let result = try await fetcher.fetch(request)
        #expect(result.response.statusCode == 200)
        #expect(inner.callCount == 2)
    }

    @Test("retries a transient status (503), then returns the second result")
    func retriesTransientStatus() async throws {
        let inner = ScriptedFetcher([.status(503), .status(200)])
        let fetcher = RetryingHTTPFetcher(inner, backoff: noSleep)
        let result = try await fetcher.fetch(request)
        #expect(result.response.statusCode == 200)
        #expect(inner.callCount == 2)
    }

    @Test("does NOT retry a 4xx — surfaces it immediately")
    func doesNotRetryClientError() async throws {
        let inner = ScriptedFetcher([.status(404), .status(200)])
        let fetcher = RetryingHTTPFetcher(inner, backoff: noSleep)
        let result = try await fetcher.fetch(request)
        #expect(result.response.statusCode == 404)
        #expect(inner.callCount == 1)
    }

    @Test("does NOT retry a non-transient error — rethrows")
    func doesNotRetryFatal() async {
        let inner = ScriptedFetcher([.throwFatal])
        let fetcher = RetryingHTTPFetcher(inner, backoff: noSleep)
        await #expect(throws: URLError.self) { try await fetcher.fetch(request) }
        #expect(inner.callCount == 1)
    }

    @Test("gives up after maxAttempts and surfaces the last transient status")
    func surfacesLastTransient() async throws {
        let inner = ScriptedFetcher([.status(503), .status(503)])
        let fetcher = RetryingHTTPFetcher(inner, backoff: noSleep)
        let result = try await fetcher.fetch(request)
        #expect(result.response.statusCode == 503)
        #expect(inner.callCount == 2)
    }

    @Test("a cancelled backoff propagates — no spurious retry")
    func cancellationDuringBackoffPropagates() async {
        let inner = ScriptedFetcher([.status(503), .status(200)])
        let cancellingBackoff: @Sendable (Int) async throws -> Void = { _ in throw CancellationError() }
        let fetcher = RetryingHTTPFetcher(inner, backoff: cancellingBackoff)
        await #expect(throws: CancellationError.self) { try await fetcher.fetch(request) }
        #expect(inner.callCount == 1) // first attempt only; retry never fired
    }
}
