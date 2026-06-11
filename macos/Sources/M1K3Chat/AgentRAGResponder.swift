//
//  AgentRAGResponder.swift
//  M1K3Chat
//
//  The always-on tool-calling responder: every chat message runs the LocalAgent
//  ReAct loop with injected tools (web search, datetime, system status, …).
//
//  Retrieval still happens FIRST, exactly like RAGResponder — the chunks come
//  back as typed sources so ChatSession's citation validation is unchanged, and
//  a model that opens with CONCLUSION: costs exactly one generation, same as
//  plain RAG. The conclusion's tail streams live via the agent's
//  ConclusionStreamSplitter; tool use surfaces through onActivity; and if the
//  agent throws or comes back empty, we fall back to the plain grounded prompt
//  rather than failing the turn (AFM guardrails, format collapse — never a
//  dead bubble).
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.8, Prior: Unknown
//  Context: tools are injected by the app layer (M1K3Chat does not link
//  M1K3AgentTools); web_search is only in the list when the user's setting
//  allows it.

import Foundation
import M1K3Agent
import M1K3Inference
import M1K3Knowledge
import os
import Synchronization

public struct AgentRAGResponder: RAGResponding, Sendable {
    private static let log = Logger(subsystem: M1K3Log.subsystem, category: "responder")
    private let store: KnowledgeStore
    private let embedder: any EmbeddingService
    private let provider: any InferenceProvider
    /// Evaluated fresh each turn so a settings change (e.g. the web-search
    /// privacy toggle) applies immediately — a disabled tool is never even
    /// visible to the model.
    private let toolsProvider: @Sendable () -> [any AgentTool]
    private let topK: Int
    private let maxIterations: Int
    /// Hits the model retrieved itself via search_knowledge — drained after
    /// the stream and merged into the turn's sources (see `collectedSources`).
    private let sourceCollector: ToolSourceCollector?
    /// The user's reasoning preference, read fresh each turn (Settings picker:
    /// Auto / Always think / Fast answers) — same per-turn pattern as tools.
    private let thinkingModeProvider: @Sendable () -> ThinkingMode

    public init(
        store: KnowledgeStore,
        embedder: any EmbeddingService,
        provider: any InferenceProvider,
        toolsProvider: @escaping @Sendable () -> [any AgentTool],
        topK: Int = 5,
        maxIterations: Int = 3,
        sourceCollector: ToolSourceCollector? = nil,
        thinkingModeProvider: @escaping @Sendable () -> ThinkingMode = { .auto }
    ) {
        self.store = store
        self.embedder = embedder
        self.provider = provider
        self.toolsProvider = toolsProvider
        self.topK = topK
        self.maxIterations = maxIterations
        self.sourceCollector = sourceCollector
        self.thinkingModeProvider = thinkingModeProvider
    }

    /// Fixed tool list — convenience for tests and simple callers.
    public init(
        store: KnowledgeStore,
        embedder: any EmbeddingService,
        provider: any InferenceProvider,
        tools: [any AgentTool],
        topK: Int = 5,
        maxIterations: Int = 3
    ) {
        self.init(
            store: store, embedder: embedder, provider: provider,
            toolsProvider: { tools }, topK: topK, maxIterations: maxIterations
        )
    }

    /// History-free entry points (tests, simple callers): same turn, no replay.
    /// Both forward UP to the real (history) implementation — the protocol's
    /// one-way defaults would forward DOWN to the plain variant and bypass it.
    public func answerStreaming(
        _ question: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        try await answerStreaming(question, history: [], onActivity: { _ in })
    }

    public func answerStreaming(
        _ question: String,
        onActivity: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        try await answerStreaming(question, history: [], onActivity: onActivity)
    }

    public func answerStreaming(
        _ question: String,
        history: [ChatTurn],
        onActivity: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        onActivity(.retrieving)
        // Stale tool hits from an aborted prior turn must not leak in.
        _ = sourceCollector?.drain()
        let queryVector = try await embedder.embed(question)
        let retrieved = try store.searchHybrid(query: question, queryVector: queryVector, limit: topK)
        // Gate on relevance: top-K ALWAYS returns something, even for "what
        // model are you?" — weak hits pollute the prompt and derail small
        // models. Below threshold nothing is injected; the model can still
        // retrieve on its own terms via search_knowledge.
        let chunks = GroundingGate.filter(retrieved)
        Self.logGateDecision(retrieved: retrieved, kept: chunks)
        // Resolve the tool list once per turn; the routing rules must match
        // what's actually callable (no advertising a disabled web_search).
        let tools = toolsProvider()

        let stream = AsyncStream<String> { continuation in
            let turnTask = Task {
                await runAgentTurn(
                    question: question,
                    chunks: chunks,
                    history: history,
                    tools: tools,
                    onActivity: onActivity,
                    continuation: continuation
                )
            }
            // The Task above is NOT a child of the stream's consumer, so the
            // consumer cancelling (user sends a new message mid-loop, voice
            // mode bails) would otherwise leave the agent loop running to the
            // iteration cap. Terminating the stream cancels the turn.
            continuation.onTermination = { _ in turnTask.cancel() }
        }
        return (chunks, stream)
    }

    /// One full agent turn into `continuation`: run the loop (conclusion tail
    /// streams live), then the web-sources tail, with the plain-RAG fallback
    /// on throw or empty.
    private func runAgentTurn(
        question: String,
        chunks: [ChunkHit],
        history: [ChatTurn],
        tools: [any AgentTool],
        onActivity: @escaping @Sendable (ResponderActivity) -> Void,
        continuation: AsyncStream<String>.Continuation
    ) async {
        // Match the loop LocalAgent will actually run (same capability check),
        // so the native path never sees the ReAct format scaffold.
        let style: Self.PromptStyle =
            (provider as? ToolCallingProvider)?.supportsToolCalls == true ? .native : .react
        let grounding = Self.grounding(
            chunks: chunks, toolNames: Set(tools.map(\.name)), history: history, style: style
        )
        Self.logTurnStart(chunks: chunks, tools: tools, grounding: grounding)
        // Fresh agent per turn — its reasoning trace must not bleed across
        // turns, and the tool list reflects current settings.
        let agent = LocalAgent(
            inferenceProvider: provider,
            tools: tools,
            maxIterations: maxIterations,
            concludesOnUnstructuredThought: true
        )
        // Per-turn reasoning budget: casual asks skip the think phase
        // entirely (instant answers); grounded/analytic asks keep it.
        let thinkingEnabled = ThinkingPolicy.shouldThink(
            question: question,
            hasGroundedKnowledge: !chunks.isEmpty,
            mode: thinkingModeProvider()
        )
        let emittedLive = Mutex(false)
        do {
            let result = try await agent.run(
                goal: question,
                context: grounding,
                thinkingEnabled: thinkingEnabled,
                onEvent: { Self.forward($0, to: onActivity) },
                onConclusionToken: { token in
                    emittedLive.withLock { $0 = true }
                    continuation.yield(token)
                },
                // Chain-of-thought streams into the same chat stream — the
                // chat-side splitter routes it to the reasoning disclosure.
                // Deliberately does NOT set emittedLive: reasoning alone is
                // not an answer, so the empty-conclusion fallback still fires.
                onReasoningToken: { token in
                    continuation.yield(token)
                }
            )

            let conclusion = result.conclusion
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let streamed = emittedLive.withLock { $0 }
            if !streamed, conclusion.isEmpty {
                await fallBack(question: question, chunks: chunks, result: result, into: continuation)
            } else {
                var tail = streamed ? "" : conclusion
                tail += Self.webSourcesBlock(for: result)
                if !tail.isEmpty {
                    continuation.yield(tail)
                }
                Self.logTurnDone(streamed: streamed, tailCount: tail.count, result: result)
            }
            continuation.finish()
        } catch is CancellationError {
            Self.log.notice("turn cancelled mid-loop")
            continuation.finish()
        } catch {
            Self.log.error("agent threw — falling back to plain RAG: \(error, privacy: .public)")
            await streamFallback(question: question, chunks: chunks, into: continuation)
            continuation.finish()
        }
    }

    /// Sources the model gathered itself via search_knowledge this turn —
    /// merged into the message's sources (and the citation allow-list) by
    /// ChatSession once the stream completes.
    public func collectedSources() -> [ChunkHit] {
        sourceCollector?.drain() ?? []
    }

    /// One line per retrieved hit with both relevance signals, so the gate
    /// thresholds can be tuned on real queries from the unified log.
    private static func logGateDecision(retrieved: [ChunkHit], kept: [ChunkHit]) {
        let keptIDs = Set(kept.map(\.chunkID))
        for hit in retrieved {
            let similarity = hit.similarity.map { String(format: "%.3f", $0) } ?? "–"
            let fused = hit.rrfScore.map { String(format: "%.4f", $0) } ?? "–"
            let verdict = keptIDs.contains(hit.chunkID) ? "kept" : "gated"
            let title = LogPreview.preview(hit.itemTitle, max: 60)
            log.debug(
                "gate \(verdict, privacy: .public): sim=\(similarity, privacy: .public) rrf=\(fused, privacy: .public) [\(title, privacy: .public)]"
            )
        }
        let retrievedCount = retrieved.count
        let keptCount = kept.count
        log.info("grounding gate: kept \(keptCount)/\(retrievedCount)")
    }

    /// Map agent-loop events to UI activity.
    private static func forward(
        _ event: AgentLoopEvent,
        to onActivity: @Sendable (ResponderActivity) -> Void
    ) {
        switch event {
        case let .thinking(iteration):
            onActivity(.thinking(iteration: iteration))
        case let .actionStarted(tool, argument):
            onActivity(.usingTool(name: tool, argument: argument))
        }
    }

    // MARK: - Diagnostics (unified logging)

    private static func logTurnStart(chunks: [ChunkHit], tools: [any AgentTool], grounding: String) {
        let toolNames = tools.map(\.name).sorted().joined(separator: ", ")
        log.info("""
        turn start: \(chunks.count) chunk(s) retrieved, tools=[\(toolNames, privacy: .public)], \
        grounding=\(grounding.count) chars
        """)
    }

    private static func logTurnDone(streamed: Bool, tailCount: Int, result: AgentResult) {
        let toolsUsed = result.toolsUsed.sorted().joined(separator: ", ")
        log.info("""
        turn done: streamedLive=\(streamed), tail=\(tailCount) chars, \
        toolsUsed=[\(toolsUsed, privacy: .public)]
        """)
    }

    /// The loop produced nothing usable as an answer — but its tools may have
    /// gathered real information. Synthesise from that (plain RAG only when
    /// there's truly nothing), keeping the deterministic web-sources block.
    private func fallBack(
        question: String,
        chunks: [ChunkHit],
        result: AgentResult,
        into continuation: AsyncStream<String>.Continuation
    ) async {
        let steps = result.reasoningTrace.count
        Self.log.notice("agent returned empty (nothing streamed) — falling back with \(steps) trace step(s)")
        await streamFallback(
            question: question, chunks: chunks,
            gathered: result.reasoningTrace, into: continuation
        )
        let webBlock = Self.webSourcesBlock(for: result)
        if !webBlock.isEmpty {
            continuation.yield(webBlock)
        }
    }

    /// Grounded generation — the safety net when the agent loop fails. Uses
    /// the tool observations the loop already gathered when there are any
    /// (don't throw away a good web search because the model fumbled the
    /// CONCLUSION format), else the plain RAG prompt. Only ever runs when
    /// nothing has been yielded, so the raw provider chunks (cumulative or
    /// delta) fold correctly downstream.
    private func streamFallback(
        question: String,
        chunks: [ChunkHit],
        gathered: [ReasoningStep] = [],
        into continuation: AsyncStream<String>.Continuation
    ) async {
        let prompt = Self.fallbackPrompt(question: question, chunks: chunks, gathered: gathered)
        for await chunk in provider.generateStreaming(prompt: prompt) {
            continuation.yield(chunk)
        }
    }

    /// Pure fallback-prompt assembly: informative tool observations win;
    /// otherwise the standard grounded prompt.
    static func fallbackPrompt(
        question: String, chunks: [ChunkHit], gathered: [ReasoningStep]
    ) -> String {
        let observations = gathered.compactMap { step -> String? in
            guard let action = step.action,
                  let observation = step.observation,
                  !observation.hasPrefix("Error"),
                  !observation.hasPrefix("You already ran")
            else { return nil }
            return "\(action):\n\(observation)"
        }
        guard !observations.isEmpty else {
            return ChatPromptBuilder.build(chunks: chunks, userMessage: question)
        }
        return """
        You are M1K3, a private local assistant. Answer the user's question \
        using the INFORMATION GATHERED below. Be direct and plain — do not \
        mention tools or actions.

        INFORMATION GATHERED:
        \(observations.joined(separator: "\n\n"))

        USER: \(question)
        """
    }

    /// Which loop the prompt is feeding. The ReAct floor NEEDS its format
    /// scaffold (CONCLUSION:/call budget); the native loop speaks structured
    /// calls, and feeding it the ReAct scaffold makes reasoning models burn
    /// their think budget obeying a format they never emit (seen live on
    /// Qwen3.5-9B agonising over "CONCLUSION:" for a casual greeting).
    enum PromptStyle {
        case react
        case native
    }

    /// The grounding handed to the agent: the retrieved knowledge (with the
    /// same citation labels the validator expects) + tight behavioral rules
    /// tuned for small models. The tool-routing lines match what's actually
    /// callable — never advertise a disabled web_search (and never imply
    /// search_knowledge can reach the live world; the ⌘R weather bug).
    static func grounding(
        chunks: [ChunkHit], toolNames: Set<String>, history: [ChatTurn] = [],
        style: PromptStyle = .react
    ) -> String {
        let body = groundingBody(chunks: chunks, toolNames: toolNames, style: style)
        guard let replay = HistoryWindow.render(history) else { return body }
        return "\(replay)\n\n\(body)"
    }

    private static func groundingBody(
        chunks: [ChunkHit], toolNames: Set<String>, style: PromptStyle
    ) -> String {
        let hasWebSearch = toolNames.contains("web_search")
        var routing = hasWebSearch
            ? "- For current or external information — weather, news, prices, "
            + "anything happening now — use web_search. search_knowledge only "
            + "finds documents already stored on this Mac."
            : "- search_knowledge only finds documents already stored on this "
            + "Mac. You have no web access — if the stored knowledge can't "
            + "answer, say so plainly; do not guess."
        if hasWebSearch, toolNames.contains("fetch_page") {
            routing += "\n- web_search returns links and snippets. For details "
                + "(like an actual forecast), run fetch_page on the most "
                + "relevant result URL, then conclude from the page text."
        }
        let rules = switch style {
        case .react:
            """
            RULES:
            - If the KNOWLEDGE already answers the question, reply IMMEDIATELY \
            starting with "CONCLUSION:" — do not use tools.
            - Cite knowledge sources inline with citation tokens like \
            [Title §heading]; never invent citations.
            - Use at most two tool calls, never repeating one with the same argument.
            \(routing)
            """
        case .native:
            """
            RULES:
            - Pure small talk — greetings, banter — needs no tools or knowledge; just reply. \
            A question about the current world is NOT small talk, even phrased casually.
            - If the KNOWLEDGE above answers the question, answer from it directly.
            - Cite knowledge sources inline with citation tokens like \
            [Title §heading]; never invent citations.
            - Never repeat a tool call with the same argument.
            \(routing)
            """
        }
        guard !chunks.isEmpty else {
            let hint = toolNames.contains("search_knowledge")
                ? "No stored knowledge was injected — the user's documents are "
                + "NOT in this context. If the question could concern their "
                + "stored documents, calls, or notes, call search_knowledge; "
                + "otherwise answer directly."
                : "No stored knowledge matched this question."
            return "\(hint)\n\n\(rules)"
        }
        let knowledge = chunks.enumerated().map { index, hit in
            "\(index + 1). \(ChatPromptBuilder.citationLabel(for: hit))\n\(hit.content)"
        }.joined(separator: "\n\n")
        return "KNOWLEDGE (the user's own documents, calls, notes):\n\(knowledge)\n\n\(rules)"
    }

    /// Deterministic provenance for web answers — extracted from the trace,
    /// not the model, so it can't be hallucinated.
    private static func webSourcesBlock(for result: AgentResult) -> String {
        guard result.toolsUsed.contains("web_search") else { return "" }
        let urls = WebSourceExtractor.urls(from: result.reasoningTrace)
        guard !urls.isEmpty else { return "" }
        let lines = urls.prefix(3).map { "• \($0)" }.joined(separator: "\n")
        return "\n\nWeb sources:\n\(lines)"
    }
}

/// Pulls the result URLs out of web_search observations ("Title — https://…").
enum WebSourceExtractor {
    static func urls(from trace: [ReasoningStep]) -> [String] {
        var seen = Set<String>()
        var ordered: [String] = []
        for step in trace where step.action?.hasPrefix("web_search(") == true {
            guard let observation = step.observation else { continue }
            for match in observation.matches(of: /— (https?:\/\/\S+)/) {
                // \S+ greedily eats sentence punctuation after the URL
                // ("… — https://example.com/page." captures the dot) — trim
                // trailing punctuation that is never meaningful at a URL end.
                let url = String(match.1).trimmedOfTrailingPunctuation()
                if seen.insert(url).inserted {
                    ordered.append(url)
                }
            }
        }
        return ordered
    }
}

private extension String {
    func trimmedOfTrailingPunctuation() -> String {
        var trimmed = Substring(self)
        while let last = trimmed.last, ".,;:)]»'\"".contains(last) {
            trimmed = trimmed.dropLast()
        }
        return String(trimmed)
    }
}
