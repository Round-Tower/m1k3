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

    public init(
        store: KnowledgeStore,
        embedder: any EmbeddingService,
        provider: any InferenceProvider,
        toolsProvider: @escaping @Sendable () -> [any AgentTool],
        topK: Int = 5,
        maxIterations: Int = 3
    ) {
        self.store = store
        self.embedder = embedder
        self.provider = provider
        self.toolsProvider = toolsProvider
        self.topK = topK
        self.maxIterations = maxIterations
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

    public func answerStreaming(
        _ question: String,
        onActivity: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        onActivity(.retrieving)
        let queryVector = try await embedder.embed(question)
        let chunks = try store.searchHybrid(query: question, queryVector: queryVector, limit: topK)
        // Resolve the tool list once per turn; the routing rules must match
        // what's actually callable (no advertising a disabled web_search).
        let tools = toolsProvider()

        let stream = AsyncStream<String> { continuation in
            Task {
                await runAgentTurn(
                    question: question,
                    chunks: chunks,
                    tools: tools,
                    onActivity: onActivity,
                    continuation: continuation
                )
            }
        }
        return (chunks, stream)
    }

    /// One full agent turn into `continuation`: run the loop (conclusion tail
    /// streams live), then the web-sources tail, with the plain-RAG fallback
    /// on throw or empty.
    private func runAgentTurn(
        question: String,
        chunks: [ChunkHit],
        tools: [any AgentTool],
        onActivity: @escaping @Sendable (ResponderActivity) -> Void,
        continuation: AsyncStream<String>.Continuation
    ) async {
        let grounding = Self.grounding(chunks: chunks, toolNames: Set(tools.map(\.name)))
        Self.logTurnStart(chunks: chunks, tools: tools, grounding: grounding)
        // Fresh agent per turn — its reasoning trace must not bleed across
        // turns, and the tool list reflects current settings.
        let agent = LocalAgent(
            inferenceProvider: provider,
            tools: tools,
            maxIterations: maxIterations,
            concludesOnUnstructuredThought: true
        )
        let emittedLive = Mutex(false)
        do {
            let result = try await agent.run(
                goal: question,
                context: grounding,
                onEvent: { Self.forward($0, to: onActivity) },
                onConclusionToken: { token in
                    emittedLive.withLock { $0 = true }
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

    /// The grounding handed to the agent: the retrieved knowledge (with the
    /// same citation labels the validator expects) + tight behavioral rules
    /// tuned for small models. The tool-routing lines match what's actually
    /// callable — never advertise a disabled web_search (and never imply
    /// search_knowledge can reach the live world; the ⌘R weather bug).
    static func grounding(chunks: [ChunkHit], toolNames: Set<String>) -> String {
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
        let rules = """
        RULES:
        - If the KNOWLEDGE already answers the question, reply IMMEDIATELY \
        starting with "CONCLUSION:" — do not use tools.
        - Cite knowledge sources inline with citation tokens like \
        [Title §heading]; never invent citations.
        - Use at most two tool calls, never repeating one with the same argument.
        \(routing)
        """
        guard !chunks.isEmpty else {
            return "No stored knowledge matched this question.\n\n\(rules)"
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
                let url = String(match.1)
                if seen.insert(url).inserted {
                    ordered.append(url)
                }
            }
        }
        return ordered
    }
}
