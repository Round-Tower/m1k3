//
//  AgentRAGResponderTests.swift
//  M1K3ChatTests
//
//  The always-on tool-calling responder: retrieve-first grounding (sources/
//  citations pipeline unchanged) → LocalAgent ReAct loop with injected tools →
//  streamed conclusion, with a plain-RAG fallback when the agent throws or
//  comes back empty. Real store + hashing embedder + scripted provider, same
//  fixture pattern as RAGResponderTests.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Agent
@testable import M1K3Chat
import M1K3Inference
import M1K3Knowledge
import Synchronization
import Testing

// MARK: - Fakes

/// Scripted thoughts stream word-by-word (cumulative, like AFM); generate()
/// serves the synthesis/fallback-free paths. Records every prompt.
private final class AgentScriptedProvider: InferenceProvider, @unchecked Sendable {
    let name = "agent-scripted"
    let isAvailable = true

    private let lock = NSLock()
    private var responses: [String]
    private(set) var prompts: [String] = []

    init(_ responses: [String]) {
        self.responses = responses
    }

    private func next(_ prompt: String) -> String {
        lock.withLock {
            prompts.append(prompt)
            return responses.isEmpty ? "" : responses.removeFirst()
        }
    }

    func generate(prompt: String) async throws -> String {
        next(prompt)
    }

    func generateStreaming(prompt: String) -> AsyncStream<String> {
        let response = next(prompt)
        return AsyncStream { continuation in
            var emitted = ""
            for word in response.split(separator: " ", omittingEmptySubsequences: false) {
                emitted += (emitted.isEmpty ? "" : " ") + word
                continuation.yield(emitted)
            }
            continuation.finish()
        }
    }

    var allPrompts: [String] {
        lock.withLock { prompts }
    }
}

private struct FixedTool: AgentTool {
    let name: String
    let response: String
    var description: String {
        "fixed tool \(name)"
    }

    let parameters = [ToolParameter(name: "query", description: "q")]

    func execute(input _: [String: String]) async throws -> ToolResult {
        ToolResult(output: response)
    }
}

private func ingestedStore() async throws -> (KnowledgeStore, HashingEmbeddingService) {
    let store = try KnowledgeStore()
    let embedder = HashingEmbeddingService()
    let ingester = DocumentIngester(store: store, embedder: embedder)
    try await ingester.ingest(
        title: "Plant Notes",
        pages: [DocumentPage(pageNumber: 1, text: "3.2 Seals\nThe hydraulic seal on the conveyor failed under load.")]
    )
    return (store, embedder)
}

private func collect(_ stream: AsyncStream<String>) async -> String {
    var text = ""
    for await chunk in stream {
        text = chunk.hasPrefix(text) ? chunk : text + chunk
    }
    return text
}

// MARK: - Tests

struct AgentRAGResponderTests {
    @Test("immediate conclusion: sources up front, grounded prompt, streamed answer")
    func immediateConclusion() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "CONCLUSION: The seal failed. [Plant Notes §3.2 Seals]",
        ])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider, tools: []
        )

        let (sources, stream) = try await responder.answerStreaming("What failed on the conveyor?")
        #expect(sources.first?.content.contains("hydraulic seal") == true)

        let answer = await collect(stream)
        #expect(answer.contains("The seal failed."))
        #expect(answer.contains("[Plant Notes §3.2 Seals]"))

        let firstPrompt = try #require(provider.allPrompts.first)
        #expect(firstPrompt.contains("KNOWLEDGE"))
        #expect(firstPrompt.contains("hydraulic seal"))
        #expect(firstPrompt.contains("[Plant Notes §3.2 Seals]"))
        #expect(firstPrompt.contains("What failed on the conveyor?"))
        // The behavioral rules made it in.
        #expect(firstPrompt.contains("CONCLUSION:"))
        // Exactly one generation — same cost as plain RAG for the common case.
        #expect(provider.allPrompts.count == 1)
    }

    @Test("tool use is reported as activity and reaches the answer")
    func toolUseFlow() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "ACTION: datetime(now)",
            "CONCLUSION: It is Tuesday.",
        ])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "datetime", response: "Tuesday, 9 June 2026")]
        )

        let recorder = ActivityRecorder()
        let (_, stream) = try await responder.answerStreaming("what day is it?") { activity in
            recorder.record(activity)
        }
        let answer = await collect(stream)
        #expect(answer.contains("It is Tuesday."))
        #expect(recorder.activities.contains(.retrieving))
        #expect(recorder.activities.contains(.usingTool(name: "datetime", argument: "now")))
        #expect(recorder.activities.contains(.thinking(iteration: 0)))
    }

    @Test("web search appends a deterministic web-sources block")
    func webSourcesBlock() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "ACTION: web_search(seal suppliers)",
            "CONCLUSION: Acme sells seals.",
        ])
        let webObservation = """
        1. Acme Seals — https://acme.example/seals
           Industrial seals.
        2. SealCo — https://sealco.example
           More seals.
        """
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "web_search", response: webObservation)]
        )

        let (_, stream) = try await responder.answerStreaming("who sells seals?")
        let answer = await collect(stream)
        #expect(answer.contains("Acme sells seals."))
        #expect(answer.contains("Web sources:"))
        #expect(answer.contains("https://acme.example/seals"))
        #expect(answer.contains("https://sealco.example"))
    }

    @Test("an off-topic query injects NO knowledge and points the model at search_knowledge")
    func gatedQueryInjectsNothing() async throws {
        // The screenshot bug: "what model are you?" must not drag stored
        // document chunks into the prompt just because top-K returned them.
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider(["CONCLUSION: I'm M1K3, a local assistant."])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "search_knowledge", response: "(unused)")]
        )

        let (sources, stream) = try await responder.answerStreaming("what model are you?")
        _ = await collect(stream)

        #expect(sources.isEmpty)
        let prompt = try #require(provider.allPrompts.first)
        #expect(!prompt.contains("KNOWLEDGE (the user's own documents"))
        #expect(prompt.contains("NOT in this context"))
    }

    @Test("agent coming back empty with NO gathered info falls back to the plain RAG prompt")
    func emptyFallsBack() async throws {
        let (store, embedder) = try await ingestedStore()
        // 3 empty thoughts (cap), empty synthesis, then the fallback stream.
        let provider = AgentScriptedProvider(["", "", "", "", "Plain grounded answer."])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider, tools: []
        )

        // A query with strong lexical overlap so the grounding gate keeps the
        // chunk (the gate dropping weak hits is pinned in GroundingGateTests).
        let (_, stream) = try await responder.answerStreaming("hydraulic seal conveyor failed under load?")
        let answer = await collect(stream)
        #expect(answer == "Plain grounded answer.")

        let lastPrompt = try #require(provider.allPrompts.last)
        // ChatPromptBuilder's grounded template, not the ReAct scaffold.
        #expect(lastPrompt.contains("HOW TO ANSWER:"))
    }

    @Test("agent coming back empty AFTER gathering info synthesises from the observations")
    func emptyFallbackUsesGatheredObservations() async throws {
        // The Boston-weather bug: web_search returned real results, the model
        // produced a scaffolding-only conclusion, and the fallback threw the
        // results away. The fallback must answer FROM the gathered info.
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "ACTION: web_search(weather boston)",
            "", // prose chance burnt
            "", // cap
            "", // empty synthesis
            "Sunny and 25 all week.", // the gathered-info fallback stream
        ])
        let webObservation = "1. Boston 10-day — https://weather.example/boston\n   Sunny, 25C."
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "web_search", response: webObservation)]
        )

        let (_, stream) = try await responder.answerStreaming("weather in boston?")
        let answer = await collect(stream)
        #expect(answer.contains("Sunny and 25 all week."))
        // Deterministic provenance survives the fallback path too.
        #expect(answer.contains("Web sources:"))
        #expect(answer.contains("https://weather.example/boston"))

        let lastPrompt = try #require(provider.allPrompts.last)
        #expect(lastPrompt.contains("INFORMATION GATHERED"))
        #expect(lastPrompt.contains("Sunny, 25C."))
        #expect(lastPrompt.contains("weather in boston?"))
    }

    @Test("with web search available, the rules route current-world questions to it")
    func rulesRouteToWebSearch() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider(["CONCLUSION: ok."])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "web_search", response: "x")]
        )
        _ = try await collect(await responder.answerStreaming("weather?").stream)
        let prompt = try #require(provider.allPrompts.first)
        #expect(prompt.contains("weather, news"))
        #expect(prompt.contains("use web_search"))
        #expect(prompt.contains("already stored on this Mac"))
    }

    @Test("with fetch_page available, the rules teach the search→read→conclude flow")
    func rulesTeachFetchFlow() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider(["CONCLUSION: ok."])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "web_search", response: "x"),
                    FixedTool(name: "fetch_page", response: "y")]
        )
        _ = try await collect(await responder.answerStreaming("weather?").stream)
        let prompt = try #require(provider.allPrompts.first)
        #expect(prompt.contains("fetch_page"))
        #expect(prompt.contains("most relevant result"))
    }

    @Test("without web search, the rules say so instead of advertising a missing tool")
    func rulesHonestWithoutWebSearch() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider(["CONCLUSION: ok."])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider, tools: []
        )
        _ = try await collect(await responder.answerStreaming("weather?").stream)
        let prompt = try #require(provider.allPrompts.first)
        #expect(!prompt.contains("use web_search"))
        #expect(prompt.contains("no web access"))
    }

    @Test("the tools provider is consulted fresh each turn (settings toggles apply immediately)")
    func toolsProviderPerTurn() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "CONCLUSION: one.",
            "CONCLUSION: two.",
        ])
        let calls = Mutex(0)
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            toolsProvider: {
                calls.withLock { $0 += 1 }
                return []
            }
        )
        _ = try await collect(await responder.answerStreaming("first?").stream)
        _ = try await collect(await responder.answerStreaming("second?").stream)
        #expect(calls.withLock { $0 } == 2)
    }

    @Test("prose without markers becomes the answer after one structured chance")
    func proseImplicitConclusion() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "Let me look at the knowledge provided here.",
            "The hydraulic seal failed under load, per your notes.",
        ])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider, tools: []
        )
        let (_, stream) = try await responder.answerStreaming("what failed?")
        let answer = await collect(stream)
        #expect(answer == "The hydraulic seal failed under load, per your notes.")
    }
}

private final class ActivityRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: [ResponderActivity] = []

    func record(_ activity: ResponderActivity) {
        lock.withLock { stored.append(activity) }
    }

    var activities: [ResponderActivity] {
        lock.withLock { stored }
    }
}
