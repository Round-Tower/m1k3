//
//  ConclusionStreamSplitterTests.swift
//  M1K3AgentTests
//
//  The splitter is why the agent's final answer streams token-by-token
//  instead of arriving as one dump: it buffers a thought until the
//  CONCLUSION: marker appears, then passes everything after it through
//  live. Must handle BOTH provider stream contracts (cumulative snapshots
//  and deltas) and a marker split across token boundaries.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Agent
import M1K3Inference
import Testing

struct ConclusionStreamSplitterTests {
    @Test("delta tokens: buffers until the marker, then passes the tail live")
    func deltaTokens() {
        var splitter = ConclusionStreamSplitter()
        #expect(splitter.feed("I think ").isEmpty)
        #expect(splitter.feed("CONCLU").isEmpty)
        #expect(splitter.feed("SION: The answer") == "The answer")
        #expect(splitter.feed(" is 42.") == " is 42.")
        #expect(splitter.isConclusion)
        #expect(splitter.thought == "I think CONCLUSION: The answer is 42.")
    }

    @Test("cumulative snapshots (AFM contract) are normalised to deltas")
    func cumulativeSnapshots() {
        var splitter = ConclusionStreamSplitter()
        #expect(splitter.feed("CONCLUSION: The") == "The")
        #expect(splitter.feed("CONCLUSION: The answer") == " answer")
        #expect(splitter.thought == "CONCLUSION: The answer")
    }

    @Test("a thought with no marker emits nothing")
    func noMarker() {
        var splitter = ConclusionStreamSplitter()
        #expect(splitter.feed("ACTION: search(x)").isEmpty)
        #expect(splitter.feed(" more reasoning").isEmpty)
        #expect(!splitter.isConclusion)
        #expect(splitter.thought == "ACTION: search(x) more reasoning")
    }

    @Test("leading whitespace after the marker is not emitted as a stray first token")
    func trimsAfterMarker() {
        var splitter = ConclusionStreamSplitter()
        #expect(splitter.feed("CONCLUSION:   ").isEmpty)
        #expect(splitter.feed("Clean answer.") == "Clean answer.")
    }
}

struct LocalAgentConclusionStreamingTests {
    /// Streams scripted thoughts word-by-word via generateStreaming; generate()
    /// only serves the synthesis path.
    private final class StreamingScriptedProvider: InferenceProvider, @unchecked Sendable {
        let name = "streaming-scripted"
        let isAvailable = true

        private let lock = NSLock()
        private var responses: [String]
        private(set) var prompts: [String] = []

        init(_ responses: [String]) {
            self.responses = responses
        }

        func generate(prompt: String) async throws -> String {
            lock.withLock {
                prompts.append(prompt)
                return responses.isEmpty ? "synthesised" : responses.removeFirst()
            }
        }

        func generateStreaming(prompt: String) -> AsyncStream<String> {
            let response: String = lock.withLock {
                prompts.append(prompt)
                return responses.isEmpty ? "CONCLUSION: (fallback)" : responses.removeFirst()
            }
            return AsyncStream { continuation in
                var emitted = ""
                for word in response.split(separator: " ", omittingEmptySubsequences: false) {
                    emitted += (emitted.isEmpty ? "" : " ") + word
                    continuation.yield(emitted) // cumulative, like AFM
                }
                continuation.finish()
            }
        }
    }

    @Test("the conclusion's tail streams live through onConclusionToken")
    func conclusionStreamsLive() async throws {
        let provider = StreamingScriptedProvider([
            "ACTION: lookup(x)",
            "CONCLUSION: The seal failed under load.",
        ])
        let tool = EchoTool(name: "lookup", description: "looks up", response: "found")
        let agent = LocalAgent(inferenceProvider: provider, tools: [tool])

        let collector = TokenCollector()
        let result = try await agent.run(goal: "what failed?", onConclusionToken: { token in
            collector.append(token)
        })

        #expect(result.conclusion == "The seal failed under load.")
        // The action thought never leaked; the conclusion tail did, in pieces.
        let streamed = collector.joined
        #expect(streamed == "The seal failed under load.")
        #expect(collector.count > 1)
    }

    @Test("without the callback the loop still uses blocking generation")
    func blockingPathUnchanged() async throws {
        let provider = StreamingScriptedProvider(["CONCLUSION: fine"])
        let agent = LocalAgent(inferenceProvider: provider, tools: [])
        let result = try await agent.run(goal: "x")
        #expect(result.conclusion == "fine")
    }
}

private final class TokenCollector: @unchecked Sendable {
    private let lock = NSLock()
    private var tokens: [String] = []

    func append(_ token: String) {
        lock.withLock { tokens.append(token) }
    }

    var joined: String {
        lock.withLock { tokens.joined() }
    }

    var count: Int {
        lock.withLock { tokens.count }
    }
}
