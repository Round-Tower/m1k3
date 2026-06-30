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

/// A `ToolCallingProvider` reproducing the gemma-4 live shapes: its session
/// streams an optional natural-language PREFACE, emits a tool call, then on the
/// post-tool turn either reasons into SILENCE or free-forms content-free FILLER
/// (configurable). Either way the loose in-loop conclusion is unreliable, so the
/// responder's gather-then-synthesise path must answer over the gathered
/// evidence instead. `generateStreaming` serves that grounded synthesis (gemma's
/// competent path) and records its prompt so the test can prove the answer came
/// FROM the evidence.
private final class PrefacingToolProvider: ToolCallingProvider, @unchecked Sendable {
    let name = "prefacing-tool"
    let isAvailable = true
    let supportsToolCalls = true

    private let preface: String
    private let toolName: String
    private let toolQuery: String
    private let fallbackAnswer: String
    private let synthesisText: String
    private let lock = NSLock()
    private var prompts: [String] = []

    init(
        preface: String, toolName: String, toolQuery: String, fallbackAnswer: String,
        synthesisText: String = ""
    ) {
        self.preface = preface
        self.toolName = toolName
        self.toolQuery = toolQuery
        self.fallbackAnswer = fallbackAnswer
        self.synthesisText = synthesisText
    }

    func makeToolTurnSession(
        tools _: [ToolDefinition], options _: ToolTurnOptions
    ) async throws -> any ToolTurnSession {
        PrefacingSession(
            preface: preface, toolName: toolName, toolQuery: toolQuery, synthesisText: synthesisText
        )
    }

    /// Never called — the native loop drives the overridden session. Fail loudly
    /// if the contract ever changes so the gap can't hide behind a silent empty turn.
    func continueToolTurn(messages _: [ToolMessage], tools _: [ToolDefinition]) async throws -> ToolTurn {
        fatalError("PrefacingToolProvider.continueToolTurn should never be called — loop uses makeToolTurnSession")
    }

    func generate(prompt: String) async throws -> String {
        lock.withLock { prompts.append(prompt) }
        return fallbackAnswer
    }

    /// One chunk (not cumulative) so it composes cleanly with the live preface in
    /// `collect`'s prefix-folding.
    func generateStreaming(prompt: String) -> AsyncStream<String> {
        lock.withLock { prompts.append(prompt) }
        let answer = fallbackAnswer
        return AsyncStream { continuation in
            continuation.yield(answer)
            continuation.finish()
        }
    }

    var allPrompts: [String] {
        lock.withLock { prompts }
    }
}

/// Streams an optional preface live, returns one tool call, then emits the
/// (configurable) post-tool turn — empty (reasons into silence) OR content-free
/// filler that ignores the observation. The two live gemma failure modes.
private final class PrefacingSession: ToolTurnSession, @unchecked Sendable {
    private let preface: String
    private let toolName: String
    private let toolQuery: String
    private let synthesisText: String
    private let lock = NSLock()
    private var turn = 0

    init(preface: String, toolName: String, toolQuery: String, synthesisText: String) {
        self.preface = preface
        self.toolName = toolName
        self.toolQuery = toolQuery
        self.synthesisText = synthesisText
    }

    func send(
        _: [ToolMessage], onToken: @escaping @Sendable (String) -> Void
    ) async throws -> ToolTurn {
        let current = lock.withLock { defer { turn += 1 }; return turn }
        guard current == 0 else {
            // Post-tool turn: filler (or nothing) that ignores the observation —
            // the responder must synthesise over the gathered evidence instead.
            if !synthesisText.isEmpty { onToken(synthesisText) }
            return .text(synthesisText)
        }
        if !preface.isEmpty { onToken(preface) } // optional answer-channel preface
        return .toolCalls([ParsedToolCall(name: toolName, arguments: ["query": .string(toolQuery)])])
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
    @Test("the history budget provider scales the replayed conversation in the prompt (long context)")
    func historyBudgetScalesReplay() async throws {
        let (store, embedder) = try await ingestedStore()
        // 10 prior turns of ~300 chars each, oldest = [1], newest = [10].
        let history = (1 ... 10).map { i in
            ChatTurn(
                role: i % 2 == 0 ? .assistant : .user,
                text: "[\(i)]" + String(repeating: "h", count: 300)
            )
        }
        func firstPrompt(_ budget: HistoryWindow.Budget) async throws -> String {
            let provider = AgentScriptedProvider(["CONCLUSION: ok"])
            let responder = AgentRAGResponder(
                store: store, embedder: embedder, provider: provider,
                toolsProvider: { [] },
                historyBudgetProvider: { budget }
            )
            let (_, stream) = try await responder.answerStreaming("follow up?", history: history) { _ in }
            _ = await collect(stream)
            return try #require(provider.allPrompts.first)
        }
        let wide = try await firstPrompt(.init(totalChars: 6000, perTurnChars: 1500, maxTurns: 20))
        let tight = try await firstPrompt(.init(totalChars: 800, perTurnChars: 1500, maxTurns: 20))
        // A wider budget replays more history → a longer prompt that still holds the oldest turn;
        // the tight budget drops the oldest. This is the long-context knob biting end-to-end.
        #expect(wide.count > tight.count)
        #expect(wide.contains("[1]"))
        #expect(!tight.contains("[1]"))
        #expect(tight.contains("[10]")) // newest always survives
    }

    @Test("immediate conclusion: sources up front, grounded prompt, streamed answer")
    func immediateConclusion() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "CONCLUSION: The seal failed. [Plant Notes §3.2 Seals]",
        ])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider, tools: []
        )

        // Query overlaps the doc enough to clear the gate under the HASHING
        // fake (bag-of-words TF cosine ≈0.80) — this is a plumbing test
        // (sources → grounded prompt → one generation), not a threshold test;
        // the real-BGE floor is pinned in GroundingGateTests.
        let (sources, stream) = try await responder.answerStreaming(
            "What hydraulic seal failed on the conveyor under load?"
        )
        #expect(sources.first?.content.contains("hydraulic seal") == true)

        let answer = await collect(stream)
        #expect(answer.contains("The seal failed."))
        #expect(answer.contains("[Plant Notes §3.2 Seals]"))

        let firstPrompt = try #require(provider.allPrompts.first)
        #expect(firstPrompt.contains("KNOWLEDGE"))
        #expect(firstPrompt.contains("hydraulic seal"))
        #expect(firstPrompt.contains("[Plant Notes §3.2 Seals]"))
        #expect(firstPrompt.contains("What hydraulic seal failed on the conveyor under load?"))
        // The behavioral rules made it in.
        #expect(firstPrompt.contains("CONCLUSION:"))
        // Exactly one generation — same cost as plain RAG for the common case.
        #expect(provider.allPrompts.count == 1)
    }

    @Test("per-turn context line injects the active brain name and today's date")
    func contextLineInjected() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "CONCLUSION: The seal failed. [Plant Notes §3.2 Seals]",
        ])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            toolsProvider: { [] },
            brainNameProvider: { "Lil M1K3" }
        )
        let (_, stream) = try await responder.answerStreaming(
            "What hydraulic seal failed on the conveyor under load?"
        )
        _ = await collect(stream)
        let prompt = try #require(provider.allPrompts.first)
        // The active brain is named (so "which model are you?" can be honest) and
        // the precise-date context line is present. (The date's exact formatting is
        // pinned deterministically in PromptContextTests — asserting the clock here
        // would couple this test to the calendar year.)
        #expect(prompt.contains("Lil M1K3"))
        #expect(prompt.contains("Right now (true for this turn)"))
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

    @Test("web source URLs shed trailing sentence punctuation")
    func webSourceTrailingPunctuation() {
        // The regex's \S+ would otherwise capture the dot/paren that belongs
        // to the sentence, not the URL ("… — https://a.example/page.").
        let trace = [ReasoningStep(
            iteration: 0,
            thought: "",
            action: "web_search(seals)",
            observation: """
            1. Acme — https://a.example/page.
            2. SealCo — https://b.example/path),
            """
        )]
        #expect(WebSourceExtractor.urls(from: trace) == [
            "https://a.example/page",
            "https://b.example/path",
        ])
    }

    @Test("lookup_fact appends a deterministic Wikipedia-sources block")
    func factSourcesBlock() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "ACTION: lookup_fact(Claude Shannon)",
            "CONCLUSION: Claude Shannon founded information theory.",
        ])
        // Exactly what WikipediaFormatter.format emits: prose then a Source line.
        let factObservation = """
        Claude Elwood Shannon was an American mathematician and engineer.

        Source: https://en.wikipedia.org/wiki/Claude_Shannon
        """
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "lookup_fact", response: factObservation)]
        )

        let (_, stream) = try await responder.answerStreaming("who founded information theory?")
        let answer = await collect(stream)
        #expect(answer.contains("Claude Shannon founded information theory."))
        #expect(answer.contains("Wikipedia sources:"))
        #expect(answer.contains("https://en.wikipedia.org/wiki/Claude_Shannon"))
    }

    @Test("a FAILED lookup_fact contributes no phantom citation")
    func factSourceFailedLookup() {
        // "No article" / error observations carry no Source line — the extractor
        // must surface nothing rather than inventing a citation.
        let trace = [ReasoningStep(
            iteration: 0,
            thought: "",
            action: "lookup_fact(Blorptastic)",
            observation: "No Wikipedia article found for \"Blorptastic\"."
        )]
        #expect(FactSourceExtractor.urls(from: trace).isEmpty)
    }

    @Test("Wikipedia source URLs shed trailing sentence punctuation")
    func factSourceTrailingPunctuation() {
        let trace = [ReasoningStep(
            iteration: 0,
            thought: "",
            action: "lookup_fact(Alan Turing)",
            observation: "Some prose.\n\nSource: https://en.wikipedia.org/wiki/Alan_Turing."
        )]
        #expect(FactSourceExtractor.urls(from: trace) == [
            "https://en.wikipedia.org/wiki/Alan_Turing",
        ])
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

    @Test("the per-turn context line also rides the plain-RAG fallback path")
    func contextLineInFallback() async throws {
        let (store, embedder) = try await ingestedStore()
        // Empty thoughts to the cap + empty synthesis → the empty-fallback fires.
        let provider = AgentScriptedProvider(["", "", "", "", "Plain grounded answer."])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            toolsProvider: { [] },
            brainNameProvider: { "Lil M1K3" }
        )
        let (_, stream) = try await responder.answerStreaming("hydraulic seal conveyor failed under load?")
        _ = await collect(stream)
        // The fallback generation carries the same date + brain context as the
        // agent path, so a "what day is it?" that collapses still answers honestly.
        let fallbackPrompt = try #require(provider.allPrompts.last)
        #expect(fallbackPrompt.contains("Lil M1K3"))
        #expect(fallbackPrompt.contains("Right now (true for this turn)"))
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

    @Test("native path: a preface then an EMPTY post-tool synthesis still answers over the evidence")
    func nativePrefaceThenEmptySynthesisAnswersOverEvidence() async throws {
        // One gemma-4 failure shape (Big M1K3, web_search): the model streams a
        // chatty preface ("Searching the web… Stand by.") on the SAME turn it calls
        // web_search, then the post-tool turn comes back EMPTY (reasons into
        // silence). Gather-then-synthesise must still answer over the gathered web
        // results — the path gemma answers well on — not leave the user with just
        // the preface + source URLs.
        let (store, embedder) = try await ingestedStore()
        let webObservation = "1. Apple (AAPL) — https://finance.example/aapl\n   Trading at $281."
        let provider = PrefacingToolProvider(
            preface: "Searching the web for that. Stand by.",
            toolName: "web_search",
            toolQuery: "apple stock price",
            fallbackAnswer: "Apple (AAPL) is trading at $281."
        )
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "web_search", response: webObservation)]
        )

        let (_, stream) = try await responder.answerStreaming("what is the apple stock price?")
        let answer = await collect(stream)

        // The real answer lands — not just the preface + sources.
        #expect(answer.contains("Apple (AAPL) is trading at $281."))
        // Deterministic provenance still rides along.
        #expect(answer.contains("Web sources:"))
        #expect(answer.contains("https://finance.example/aapl"))
        // #3: the answer was synthesised FROM the gathered web result (not a canned
        // degradation) — the fallback prompt carries the observation.
        let fallbackPrompt = try #require(provider.allPrompts.last)
        #expect(fallbackPrompt.contains("INFORMATION GATHERED"))
        #expect(fallbackPrompt.contains("Trading at $281."))
    }

    @Test("gather-then-synthesise: a filler conclusion after a tool is replaced by a synthesis over the evidence")
    func gatherThenSynthesiseReplacesFiller() async throws {
        // The dominant live gemma bug: after web_search it emits a content-free
        // "let me look" sentence (NON-empty, so the empty-only fallback never
        // fired) and ignores what the search returned. Gather-then-synthesise
        // suppresses that loose conclusion and answers explicitly over the
        // gathered observation — gemma's competent grounded path.
        let (store, embedder) = try await ingestedStore()
        let webObservation = "1. Artemis III — https://nasa.example/artemis\n   NASA named four Artemis III crew members."
        let provider = PrefacingToolProvider(
            preface: "", // no pre-tool preface this time
            toolName: "web_search",
            toolQuery: "artemis news",
            fallbackAnswer: "NASA has named four crew members for Artemis III.",
            synthesisText: "Let me see what the net has coughed up." // the filler the model free-forms post-tool
        )
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "web_search", response: webObservation)]
        )

        let (_, stream) = try await responder.answerStreaming("what's the latest on artemis?")
        let answer = await collect(stream)

        // The synthesised answer lands...
        #expect(answer.contains("NASA has named four crew members for Artemis III."))
        // ...and the content-free filler the model free-formed is NOT shown.
        #expect(!answer.contains("Let me see what the net has coughed up."))
        // Provenance rides along, and the answer was built FROM the observation.
        #expect(answer.contains("https://nasa.example/artemis"))
        let fallbackPrompt = try #require(provider.allPrompts.last)
        #expect(fallbackPrompt.contains("INFORMATION GATHERED"))
        #expect(fallbackPrompt.contains("NASA named four Artemis III crew members."))
    }

    @Test("native tool turn whose tool only ERRORED degrades to plain RAG, never the loose conclusion")
    func nativeToolErrorDegradesToPlainRAG() async throws {
        // A native tool turn can gather only an error (e.g. web search rate-limited).
        // gemma's loose post-tool conclusion is still untrustworthy filler, so the
        // turn must route through fallBack — which filters the error observation and
        // degrades to plain RAG — rather than surface that filler.
        let (store, embedder) = try await ingestedStore()
        let provider = PrefacingToolProvider(
            preface: "",
            toolName: "web_search",
            toolQuery: "news today",
            fallbackAnswer: "I couldn't reach the web just now — nothing stored covers that either.",
            synthesisText: "Let me have a look for you." // filler that must NOT surface
        )
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            tools: [FixedTool(name: "web_search", response: "Error: web search is temporarily unavailable.")]
        )

        let (_, stream) = try await responder.answerStreaming("what's in the news today?")
        let answer = await collect(stream)

        #expect(answer.contains("I couldn't reach the web just now"))
        #expect(!answer.contains("Let me have a look for you.")) // filler suppressed
        // The error observation was filtered → a plain-RAG prompt (no synthesis
        // over the useless error "evidence"), and the error text never reaches it.
        let fallbackPrompt = try #require(provider.allPrompts.last)
        #expect(!fallbackPrompt.contains("INFORMATION GATHERED"))
        #expect(!fallbackPrompt.contains("temporarily unavailable"))
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

    @Test("with fetch_page available, the rules note web_search auto-reads the top result + point fetch_page elsewhere")
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
        // The rule reflects the deterministic deepen: web_search reads the top
        // result, so fetch_page is for a DIFFERENT one (no redundant re-fetch).
        #expect(prompt.contains("automatically reads the top result"))
        #expect(prompt.contains("DIFFERENT result"))
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

    private func groundingChunk() -> ChunkHit {
        ChunkHit(
            chunkID: UUID(), itemID: UUID(), itemTitle: "Plant Notes", kind: .document,
            heading: "3.2 Seals", content: "The hydraulic seal failed under load."
        )
    }

    @Test("native-path rules carry NO ReAct scaffolding (no CONCLUSION:, no call budget)")
    func nativeRulesDropReActScaffold() {
        let rules = AgentRAGResponder.grounding(
            chunks: [groundingChunk()], toolNames: ["web_search", "search_knowledge"],
            style: .native
        )
        #expect(!rules.contains("CONCLUSION:"))
        #expect(!rules.contains("at most two tool calls"))
        #expect(rules.contains("Pure small talk"))
        // A casually-phrased real-time question must NOT read as no-tool chat
        // (the ⌘R weather refusal).
        #expect(rules.contains("NOT small talk"))
        // Routing + citation guidance survive on both paths.
        #expect(rules.contains("use web_search"))
        #expect(rules.contains("never invent citations"))
    }

    @Test("with lookup_fact available, the rules route uncertain encyclopedic facts to it")
    func rulesRouteFactsToLookup() {
        let rules = AgentRAGResponder.grounding(
            chunks: [groundingChunk()],
            toolNames: ["lookup_fact", "web_search", "search_knowledge"],
            style: .native
        )
        #expect(rules.contains("lookup_fact"))
        #expect(rules.contains("cite its Source"))
        // No lookup_fact tool → no nudge for it (don't advertise a missing tool).
        let noLookup = AgentRAGResponder.grounding(
            chunks: [groundingChunk()], toolNames: ["search_knowledge"], style: .native
        )
        #expect(!noLookup.contains("lookup_fact"))
        // Discriminant is the TOOL, not web being on: web_search present but no
        // lookup_fact → still no nudge.
        let webOnly = AgentRAGResponder.grounding(
            chunks: [groundingChunk()], toolNames: ["web_search"], style: .native
        )
        #expect(!webOnly.contains("lookup_fact"))
    }

    @Test("the rules free the model to answer well-known facts from its own knowledge")
    func rulesFreeKnownFacts() {
        let rules = AgentRAGResponder.grounding(
            chunks: [groundingChunk()],
            toolNames: ["lookup_fact", "web_search", "search_knowledge"],
            style: .native
        )
        // The old leash ("don't answer from memory and risk a confident mistake")
        // made the model defer EVERY fact to lookup_fact and abstain without it.
        #expect(!rules.contains("don't answer from memory"))
        // Freedom: answer what you reliably know; look up only when genuinely unsure.
        #expect(rules.contains("just answer from what you know"))
        #expect(rules.contains("lookup_fact")) // still routed for genuine uncertainty
    }

    @Test("the rules tell the model to answer with uncertainty when a lookup fails")
    func rulesUncertaintyOnToolFailure() {
        let native = AgentRAGResponder.grounding(
            chunks: [groundingChunk()], toolNames: ["web_search"], style: .native
        )
        #expect(native.contains("empty or fails"))
        #expect(native.contains("explicit uncertainty"))
        let react = AgentRAGResponder.grounding(
            chunks: [groundingChunk()], toolNames: ["web_search"], style: .react
        )
        #expect(react.contains("empty or fails"))
        #expect(react.contains("explicit uncertainty"))
    }

    @Test("react-path rules keep the CONCLUSION: scaffold (the floor's format contract)")
    func reactRulesKeepScaffold() {
        let rules = AgentRAGResponder.grounding(
            chunks: [groundingChunk()], toolNames: ["web_search"],
            style: .react
        )
        #expect(rules.contains("CONCLUSION:"))
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

    @Test("the maxIterations provider is consulted fresh each turn (thermal ease applies live)")
    func maxIterationsProviderPerTurn() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = AgentScriptedProvider([
            "CONCLUSION: one.",
            "CONCLUSION: two.",
        ])
        let calls = Mutex(0)
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider,
            toolsProvider: { [] },
            maxIterationsProvider: {
                calls.withLock { $0 += 1 }
                return 3
            }
        )
        _ = try await collect(await responder.answerStreaming("first?").stream)
        _ = try await collect(await responder.answerStreaming("second?").stream)
        // Read once per turn (at agent construction), not per loop iteration —
        // so a CoolHeadState change between turns is picked up, never snapshotted.
        #expect(calls.withLock { $0 } == 2)
    }

    @Test("a lower iteration cap runs strictly fewer agent iterations (thermal lever bites)")
    func lowerCapRunsFewerIterations() async throws {
        /// A provider that NEVER concludes — it keeps asking for the tool — so the
        /// only thing that stops the loop is the iteration cap. The number of model
        /// prompts is then a direct, monotone read on the cap.
        func promptsUnderCap(_ cap: Int) async throws -> Int {
            let (store, embedder) = try await ingestedStore()
            let provider = AgentScriptedProvider(Array(repeating: "ACTION: datetime(now)", count: 8))
            let responder = AgentRAGResponder(
                store: store, embedder: embedder, provider: provider,
                toolsProvider: { [FixedTool(name: "datetime", response: "Tuesday")] },
                maxIterationsProvider: { cap }
            )
            _ = try await collect(await responder.answerStreaming("what day is it?").stream)
            return provider.allPrompts.count
        }
        let tight = try await promptsUnderCap(1)
        let loose = try await promptsUnderCap(3)
        #expect(tight < loose) // the cap genuinely bounds the loop
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
