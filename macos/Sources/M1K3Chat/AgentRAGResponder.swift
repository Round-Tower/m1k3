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
//  Review: Kev + claude-fable-5, 2026-06-12, Confidence 0.85 — RULES line in
//  both styles routing self-questions (configuration/design/abilities) to the
//  persona instead of document search; the soft fix for meta-question
//  confabulation, taken over a pre-generation router (brittle both ways).
//  Review: Kev + claude-opus-4-8, 2026-06-13, Confidence 0.9 — retrieval now
//  uses store.searchGrounding (two-lane doc+memory budgets, memoryTopK) so the
//  document corpus can't crowd memory recall out of a single top-K. Fixes the
//  live open-chat miss (M1K3 forgot the user's own city). verify-at-⌘R.
//  Review: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.9 — gather-then-
//  synthesise on the native path: once a tool runs, suppress the model's loose
//  in-loop conclusion (gemma free-forms content-free filler and ignores the
//  results) and answer explicitly over the gathered observations via fallBack.
//  ReAct's CONCLUSION: contract is untouched (gated on style == .native).
//  Diagnosed live — the conclusion was filler, not empty; instrument first.
//  Review: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.85 — brain-aware
//  conversation replay: `grounding` now takes a `HistoryWindow.Budget` (per-tier,
//  from `HistoryBudgetPolicy`) so the multi-turn window scales to the brain. The
//  compose-fix that briefly lived here (trust `conclusionWasStreamed`) was
//  SUPERSEDED by the gather-then-synthesise approach above (verified on gemma,
//  the brain that actually fills); the persona-voiced composed answer on Qwen is
//  a tracked follow-up. Only the history-budget threading survives this merge.

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
    /// Memories get their OWN retrieval budget (see `KnowledgeStore.searchGrounding`)
    /// so document volume can't crowd them out of recall.
    private let memoryTopK: Int
    private let maxIterations: Int
    /// Hits the model retrieved itself via search_knowledge — drained after
    /// the stream and merged into the turn's sources (see `collectedSources`).
    private let sourceCollector: ToolSourceCollector?
    /// The user's reasoning preference, read fresh each turn (Settings picker:
    /// Auto / Always think / Fast answers) — same per-turn pattern as tools.
    private let thinkingModeProvider: @Sendable () -> ThinkingMode
    /// The active brain's display name (e.g. "Lil M1K3"), read fresh each turn so
    /// a runtime hot-swap is reflected — same per-turn pattern as `toolsProvider`.
    /// Empty when unknown (tests / simple callers); the context line omits it then.
    private let brainNameProvider: @Sendable () -> String
    /// Per-turn override for the agent-loop iteration cap, read FRESH each turn so
    /// a thermal "ease off" (CoolHeadPolicy) takes effect live without rebuilding
    /// the long-lived responder. `nil` → the fixed `maxIterations` base, so the
    /// default path is byte-identical. Stays an opaque `() -> Int`: the thermal
    /// level enum lives in M1K3LanguageModel, which this module deliberately does
    /// NOT link (the dependency-direction trap) — the enum→Int map is the app's job.
    private let maxIterationsProvider: (@Sendable () -> Int)?
    /// Whether the active brain biases toward fast answers (Mini/Lil), read fresh
    /// each turn like the other providers so a hot-swap takes effect immediately.
    /// Feeds `ThinkingPolicy.shouldThink(fastByDefault:)` — the speed tiers skip the
    /// think phase on plain grounded lookups. Default `false` = the heavy-tier
    /// behaviour, so existing callers/tests are byte-identical.
    private let fastThinkingProvider: @Sendable () -> Bool
    /// The conversation-replay budget, read FRESH each turn (same per-turn pattern
    /// as the other providers) so a brain hot-swap re-sizes the window immediately —
    /// wide on the dense-Qwen tiers, clamped under gemma-4's 8192 rotating window.
    /// Defaults to the conservative shipped window so an unwired caller is unchanged.
    private let historyBudgetProvider: @Sendable () -> HistoryWindow.Budget
    /// Cool Head's heavy-generation defer, read FRESH each turn. `true` (critical
    /// thermal + opt-in ON) makes the turn skip the heavy retrieve+generate and
    /// answer with an honest deferral instead of piling onto a throttling Mac.
    /// `nil` → never defers, so the default path is byte-identical. Opaque
    /// `() -> Bool` for the same dependency-direction reason as `maxIterationsProvider`:
    /// the CoolHeadLevel enum lives in M1K3LanguageModel, which this module doesn't link.
    private let defersHeavyGenerationProvider: (@Sendable () -> Bool)?

    public init(
        store: KnowledgeStore,
        embedder: any EmbeddingService,
        provider: any InferenceProvider,
        toolsProvider: @escaping @Sendable () -> [any AgentTool],
        topK: Int = 5,
        memoryTopK: Int = 5,
        maxIterations: Int = 3,
        sourceCollector: ToolSourceCollector? = nil,
        thinkingModeProvider: @escaping @Sendable () -> ThinkingMode = { .auto },
        brainNameProvider: @escaping @Sendable () -> String = { "" },
        fastThinkingProvider: @escaping @Sendable () -> Bool = { false },
        historyBudgetProvider: @escaping @Sendable () -> HistoryWindow.Budget = { .default },
        maxIterationsProvider: (@Sendable () -> Int)? = nil,
        defersHeavyGenerationProvider: (@Sendable () -> Bool)? = nil
    ) {
        self.store = store
        self.embedder = embedder
        self.provider = provider
        self.toolsProvider = toolsProvider
        self.topK = topK
        self.memoryTopK = memoryTopK
        self.maxIterations = maxIterations
        self.sourceCollector = sourceCollector
        self.thinkingModeProvider = thinkingModeProvider
        self.brainNameProvider = brainNameProvider
        self.maxIterationsProvider = maxIterationsProvider
        self.fastThinkingProvider = fastThinkingProvider
        self.historyBudgetProvider = historyBudgetProvider
        self.defersHeavyGenerationProvider = defersHeavyGenerationProvider
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

    /// The honest line M1K3 gives when Cool Head defers a heavy turn (critical
    /// thermal). Warm, brief, and truthful — never a silent non-answer.
    static let coolHeadDeferralMessage =
        "My Mac's gone properly warm, so I'm easing off the heavy thinking for a "
            + "moment to let it cool down. Give it a minute and ask me again."

    public func answerStreaming(
        _ question: String,
        history: [ChatTurn],
        onActivity: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        // Cool Head at minimal (critical thermal, opt-in ON): skip the whole heavy
        // path — no embed, no retrieval, no decode — and answer honestly instead of
        // piling onto a throttling Mac. The caller (ChatSession) has already recorded
        // the user's turn, so this just fills the assistant bubble; the transcript is
        // intact. Default path (provider nil/false) is byte-identical.
        if defersHeavyGenerationProvider?() == true {
            let message = Self.coolHeadDeferralMessage
            let stream = AsyncStream<String> { continuation in
                continuation.yield(message)
                continuation.finish()
            }
            return (sources: [], stream: stream)
        }

        onActivity(.retrieving)
        // Stale tool hits from an aborted prior turn must not leak in.
        _ = sourceCollector?.drain()
        let queryVector = try await embedder.embed(question)
        // Two-lane retrieval: documents and memories get SEPARATE top-K budgets
        // so the larger document corpus can't crowd short memory facts out of a
        // single ranking (the open-chat recall miss — M1K3 forgot the user's
        // own city when the query leaned even slightly documentary).
        let retrieved = try store.searchGrounding(
            query: question, queryVector: queryVector,
            documentLimit: topK, memoryLimit: memoryTopK
        )
        // Gate on relevance: each lane ALWAYS returns something, even for "what
        // model are you?" — weak hits pollute the prompt and derail small
        // models. Below threshold nothing is injected; the model can still
        // retrieve on its own terms via search_knowledge. Memory hits clear
        // their own (lower) bar and feed a separate uncited prompt block.
        let (chunks, memories) = GroundingGate.partition(retrieved)
        Self.logGateDecision(retrieved: retrieved, kept: chunks + memories)
        // Resolve the tool list once per turn; the routing rules must match
        // what's actually callable (no advertising a disabled web_search).
        let tools = toolsProvider()

        let stream = AsyncStream<String> { continuation in
            let turnTask = Task {
                await runAgentTurn(
                    question: question,
                    chunks: chunks,
                    memories: memories,
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
        // Memory hits ride along as sources so the UI shows their provenance.
        return (chunks + memories, stream)
    }

    /// Grounding for the think-phase decision counts BOTH retrieval lanes: a
    /// turn grounded only by a memory hit (0 doc chunks) is still a grounded
    /// answer and earns CoT on the heavy tiers, same as a document hit.
    static func hasGroundedKnowledge(chunks: [ChunkHit], memories: [ChunkHit]) -> Bool {
        !chunks.isEmpty || !memories.isEmpty
    }

    /// One full agent turn into `continuation`: run the loop (conclusion tail
    /// streams live), then the web-sources tail, with the plain-RAG fallback
    /// on throw or empty.
    private func runAgentTurn(
        question: String,
        chunks: [ChunkHit],
        memories: [ChunkHit],
        history: [ChatTurn],
        tools: [any AgentTool],
        onActivity: @escaping @Sendable (ResponderActivity) -> Void,
        continuation: AsyncStream<String>.Continuation
    ) async {
        // Match the loop LocalAgent will actually run (same capability check),
        // so the native path never sees the ReAct format scaffold.
        let style: Self.PromptStyle =
            (provider as? ToolCallingProvider)?.supportsToolCalls == true ? .native : .react
        // The per-turn truth: today's precise date + which brain is answering.
        // Prepended here (not inside `grounding`, which stays pure/testable) so it
        // rides the variable grounding, never the cached persona prefix.
        let contextLine = PromptContext.line(now: Date(), brainName: brainNameProvider())
        let grounding = contextLine + "\n\n" + Self.grounding(
            chunks: chunks, memories: memories, toolNames: Set(tools.map(\.name)),
            history: history, historyBudget: historyBudgetProvider(), style: style
        )
        Self.logTurnStart(chunks: chunks, tools: tools, grounding: grounding)
        // Fresh agent per turn — its reasoning trace must not bleed across
        // turns, and the tool list reflects current settings. The iteration cap is
        // read fresh too: `nil` keeps the configured base, else the live thermal
        // budget (never snapshotted at build time — the responder is long-lived).
        let iterations = maxIterationsProvider?() ?? maxIterations
        let agent = LocalAgent(
            inferenceProvider: provider,
            tools: tools,
            maxIterations: iterations,
            concludesOnUnstructuredThought: true
        )
        // Per-turn reasoning budget: casual asks skip the think phase
        // entirely (instant answers); grounded/analytic asks keep it.
        let thinkingEnabled = ThinkingPolicy.shouldThink(
            question: question,
            hasGroundedKnowledge: Self.hasGroundedKnowledge(chunks: chunks, memories: memories),
            mode: thinkingModeProvider(),
            fastByDefault: fastThinkingProvider()
        )
        // A direct answer streams live only until the FIRST tool runs. After that
        // the model's free-form in-loop conclusion is unreliable — gemma emits a
        // content-free "let me look" preamble and ignores what the tools actually
        // returned (seen live). So once a tool dispatches we suppress the loose
        // conclusion and, when the loop ends, synthesise explicitly over the
        // gathered observations (gemma's competent grounded path) via `fallBack`.
        let emittedLive = Mutex(false)
        let toolUsed = Mutex(false)
        do {
            let result = try await agent.run(
                goal: question,
                context: grounding,
                thinkingEnabled: thinkingEnabled,
                onEvent: { event in
                    if case .actionStarted = event { toolUsed.withLock { $0 = true } }
                    Self.forward(event, to: onActivity)
                },
                onConclusionToken: { token in
                    // Native small models free-form a content-free "let me look"
                    // preamble AFTER a tool and ignore what it returned, so once a
                    // tool runs we suppress the loose conclusion and synthesise
                    // explicitly below. The ReAct floor's CONCLUSION: contract is
                    // reliable — never suppress it (it carries the real answer).
                    if style == .native, toolUsed.withLock({ $0 }) { return }
                    emittedLive.withLock { $0 = true }
                    continuation.yield(token)
                },
                // Chain-of-thought streams into the same chat stream — the
                // chat-side splitter routes it to the reasoning disclosure.
                // Deliberately does NOT set emittedLive: reasoning alone is
                // not an answer, so the synthesis/fallback still fires.
                onReasoningToken: { token in
                    continuation.yield(token)
                }
            )

            let conclusion = result.conclusion
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let streamed = emittedLive.withLock { $0 }
            // Native tool turns ALWAYS synthesise via fallBack: gemma's loose
            // in-loop conclusion is unreliable filler, so even an error-only or
            // empty gather must route through fallBack (which filters errors and
            // degrades to plain RAG) rather than surface that conclusion. The
            // ReAct path keeps its reliable CONCLUSION:. Both still use the
            // empty-conclusion fallback for the no-tool case.
            let synthesiseOverEvidence = style == .native && !result.toolsUsed.isEmpty
            if synthesiseOverEvidence || (!streamed && conclusion.isEmpty) {
                await fallBack(
                    question: question, chunks: chunks, contextLine: contextLine,
                    result: result, into: continuation
                )
            } else {
                var tail = streamed ? "" : conclusion
                tail += Self.webSourcesBlock(for: result)
                tail += Self.factSourcesBlock(for: result)
                if !tail.isEmpty {
                    continuation.yield(tail)
                }
                Self.logTurnDone(
                    streamed: streamed, tailCount: tail.count, result: result
                )
            }
            continuation.finish()
        } catch is CancellationError {
            Self.log.notice("turn cancelled mid-loop")
            continuation.finish()
        } catch {
            Self.log.error("agent threw — falling back to plain RAG: \(error, privacy: .public)")
            await streamFallback(question: question, chunks: chunks, contextLine: contextLine, into: continuation)
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
        // .notice (not .info): the gate decision is load-bearing for diagnosing a
        // bad retrieval from a captured issue report, where .info/.debug don't persist.
        log.notice("grounding gate: kept \(keptCount)/\(retrievedCount)")
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
        // .notice: retrieval count + grounding size at turn start is a primary
        // breadcrumb — it must survive into a default-level issue-report capture.
        log.notice("""
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
        contextLine: String,
        result: AgentResult,
        into continuation: AsyncStream<String>.Continuation
    ) async {
        let steps = result.reasoningTrace.count
        Self.log.notice("agent returned empty (nothing streamed) — falling back with \(steps) trace step(s)")
        await streamFallback(
            question: question, chunks: chunks, contextLine: contextLine,
            gathered: result.reasoningTrace, into: continuation
        )
        let provenance = Self.webSourcesBlock(for: result) + Self.factSourcesBlock(for: result)
        if !provenance.isEmpty {
            continuation.yield(provenance)
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
        contextLine: String,
        gathered: [ReasoningStep] = [],
        into continuation: AsyncStream<String>.Continuation
    ) async {
        let body = Self.fallbackPrompt(question: question, chunks: chunks, gathered: gathered)
        // Carry the same per-turn context (precise date + active brain) the agent
        // path got, so a "what day is it?" that collapses to the fallback still answers.
        let prompt = contextLine.isEmpty ? body : contextLine + "\n\n" + body
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

        First check the gathered information actually answers the question. If it \
        does NOT establish that the thing asked about exists — it only mentions the \
        words separately, or covers a related-but-different topic — say plainly you \
        couldn't confirm it, and don't present a guess as fact. Do NOT stitch \
        tangential mentions into an affirmative answer.

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
        chunks: [ChunkHit], memories: [ChunkHit] = [], toolNames: Set<String>,
        history: [ChatTurn] = [], historyBudget: HistoryWindow.Budget = .default,
        style: PromptStyle = .react
    ) -> String {
        let body = groundingBody(
            chunks: chunks, memories: memories, toolNames: toolNames, style: style
        )
        guard let replay = HistoryWindow.render(history, budget: historyBudget) else { return body }
        return "\(replay)\n\n\(body)"
    }

    /// The uncited personal-facts block: memories aren't sources to cite,
    /// they're things M1K3 simply knows about the user. Sits between
    /// KNOWLEDGE and RULES; absent when no memory cleared the gate.
    private static func memoryBlock(_ memories: [ChunkHit]) -> String? {
        guard !memories.isEmpty else { return nil }
        let facts = memories.map { "- \($0.content)" }.joined(separator: "\n")
        return "WHAT I KNOW ABOUT YOU (remembered from past conversations — "
            + "use naturally, do not cite):\n\(facts)"
    }

    private static func groundingBody(
        chunks: [ChunkHit], memories: [ChunkHit], toolNames: Set<String>, style: PromptStyle
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
            routing += "\n- web_search returns snippets AND automatically reads the "
                + "top result's page for you. Use fetch_page only to read a "
                + "DIFFERENT result in full, then conclude from the page text."
        }
        if toolNames.contains("lookup_fact") {
            routing += "\n- Stable, well-known facts (who wrote a famous book, a "
                + "capital city, basic science) you can just answer from what you know "
                + "— you're reliable there. Use lookup_fact only when you're genuinely "
                + "unsure, the detail is obscure or easy to mix up, or it could have "
                + "changed over time; then cite its Source."
        }
        let rules = switch style {
        case .react:
            """
            RULES:
            - A request to write, create, code, or compose something is a task to \
            DO, not a lookup — just produce it. No tools, no grounding, no citations, \
            no "found nothing"; those are for factual questions.
            - If the KNOWLEDGE already answers the question, reply IMMEDIATELY \
            starting with "CONCLUSION:" — do not use tools.
            - Cite knowledge sources inline with citation tokens like \
            [Title §heading]; never invent citations.
            - Never present a fact, figure, or date you can't ground or verify \
            as certain; if you're unsure, say so plainly. Honesty beats a confident guess.
            - If a search or lookup comes back empty or fails, answer with explicit \
            uncertainty — name what you couldn't confirm — rather than presenting a guess as fact.
            - Use at most two tool calls, never repeating one with the same argument.
            - Questions about yourself — your configuration, design, or abilities — \
            are answered from your persona; never search stored documents for them.
            \(routing)
            """
        case .native:
            """
            RULES:
            - A request to write, create, code, or compose something is a task to \
            DO, not a lookup — just produce it. No tools, no grounding, no citations, \
            no "found nothing"; those are for factual questions.
            - Pure small talk — greetings, banter — needs no tools or knowledge; just reply. \
            A question about the current world is NOT small talk, even phrased casually.
            - If the KNOWLEDGE above answers the question, answer from it directly.
            - Cite knowledge sources inline with citation tokens like \
            [Title §heading]; never invent citations.
            - Never present a fact, figure, or date you can't ground or verify \
            as certain; if you're unsure, say so plainly. Honesty beats a confident guess.
            - If a search or lookup comes back empty or fails, answer with explicit \
            uncertainty — name what you couldn't confirm — rather than presenting a guess as fact.
            - Never repeat a tool call with the same argument.
            - Questions about yourself — your configuration, design, or abilities — \
            are answered from your persona; never search stored documents for them.
            \(routing)
            """
        }
        let head: String
        if chunks.isEmpty {
            head = toolNames.contains("search_knowledge")
                ? "No stored knowledge was injected — the user's documents are "
                + "NOT in this context. If the question could concern their "
                + "stored documents, calls, or notes, call search_knowledge; "
                + "otherwise answer from what you genuinely know — and if you "
                + "don't, say so plainly rather than guessing."
                : "No stored knowledge matched this question."
        } else {
            let knowledge = chunks.enumerated().map { index, hit in
                "\(index + 1). \(ChatPromptBuilder.citationLabel(for: hit))\n\(hit.content)"
            }.joined(separator: "\n\n")
            head = "KNOWLEDGE (the user's own documents, calls, notes):\n\(knowledge)"
        }
        return [head, memoryBlock(memories), rules]
            .compactMap { $0 }
            .joined(separator: "\n\n")
    }
}
