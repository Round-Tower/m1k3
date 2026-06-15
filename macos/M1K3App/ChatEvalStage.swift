//
//  ChatEvalStage.swift
//  M1K3App
//
//  The model-running half of the evals enclave (Phase 14). The PURE scoring
//  lives in M1K3Eval (fixtures, ChatEvalScorer, ChatEvalReport — all unit-
//  tested off-device); this stage runs each fixture against each real brain
//  from INSIDE the .app bundle (MLX needs the bundle's metallib, AFM needs the
//  entitlements — bare `swift test` can't), captures what the brain actually
//  produced, and feeds it to the pure scorer. Same harness as MEMEVAL/ABSEP.
//
//      M1K3_SELFTEST=1 M1K3_SELFTEST_CHATEVAL=1 M1K3.app/Contents/MacOS/M1K3
//      # narrow: M1K3_SELFTEST_CHATEVAL_BRAINS=mini,lil  M1K3_SELFTEST_CHATEVAL_KINDS=tool-use
//
//  Output: the cross-brain matrix (passed/total ⌀latency per task-kind) plus
//  per-fixture detail, written line-by-line to M1K3_SELFTEST_OUT so an
//  interrupted run keeps what it measured. The matrix is the evidence the
//  EscalationLadder policy cites — the AFM-vs-floor gap, in numbers.
//
//  Tool-use is scored through the REAL agent path each brain uses in
//  production: LocalAgent gives AFM (mini) the prompt-ReAct floor and MLX
//  brains their native dialect, and AgentResult.toolsUsed reads the same either
//  way — so mini's tool-calling shows up, it isn't skipped.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.82 (the runner is
//  verify-by-launch — its logic cores are the unit-tested M1K3Eval scorer and
//  the proven RAGResponder/LocalAgent seams; the wiring itself can only be
//  confirmed on-device). Prior: Unknown

import Foundation
import M1K3Agent
import M1K3Chat
import M1K3Eval
import M1K3Inference
import M1K3Knowledge
import M1K3MLX

enum ChatEvalStage {
    static var isRequested: Bool {
        SelfTestEnv.value("M1K3_SELFTEST_CHATEVAL") == "1"
    }

    /// Stand-in tools the tool-use fixtures probe for. The eval measures whether
    /// the brain SELECTS the right tool — not what the tool returns — so execute
    /// is a no-op that hands back a plausible canned observation (enough for the
    /// agent to conclude and stop). Descriptions are faithful so selection is
    /// realistic: search_knowledge = personal store, lookup_fact = encyclopedic,
    /// web_search = live web.
    private struct StubTool: AgentTool {
        let name: String
        let description: String
        let parameters: [ToolParameter]
        let cannedOutput: String

        init(name: String, description: String, cannedOutput: String) {
            self.name = name
            self.description = description
            parameters = [ToolParameter(name: "query", description: "the input")]
            self.cannedOutput = cannedOutput
        }

        func execute(input _: [String: String]) async throws -> ToolResult {
            ToolResult(output: cannedOutput)
        }
    }

    private static let toolPalette: [any AgentTool] = [
        StubTool(
            name: "datetime",
            description: "Get the current date and time on this Mac.",
            cannedOutput: "It is 12:00 on Saturday 14 June 2026."
        ),
        StubTool(
            name: "search_knowledge",
            description: "Search the user's OWN saved notes, memories and imported documents.",
            cannedOutput: "Your notes say you chose GRDB for persistence."
        ),
        StubTool(
            name: "lookup_fact",
            description: "Look up an encyclopedic fact from a reference source (Wikipedia).",
            cannedOutput: "Cork was founded in the 6th century."
        ),
        StubTool(
            name: "web_search",
            description: "Search the LIVE web for current, up-to-the-minute news and information.",
            cannedOutput: "Top result: Apple unveils new Apple Silicon today."
        ),
    ]

    /// Run the requested brains across the requested fixtures, emit per-fixture
    /// detail live, then the headline matrix.
    static func run(emit: @escaping (String) -> Void) async {
        let kinds = selectedKinds()
        let fixtureCount = ChatEvalFixtures.all.filter { kinds?.contains($0.kind) ?? true }.count
        emit("• chateval: \(fixtureCount) fixture(s) × \(selectedBrains().count) brain(s)"
            + (kinds.map { " [kinds: \($0.map(\.label).sorted().joined(separator: ","))]" } ?? "") + "…")
        var runs: [ChatEvalReport.BrainRun] = []
        for tier in selectedBrains() {
            emit("• chateval brain \(tier.rawValue) (\(tier.displayName))…")
            guard let scores = await evalBrain(tier, emit: emit) else {
                emit("  – \(tier.rawValue): unavailable (skipped)")
                continue
            }
            runs.append(ChatEvalReport.BrainRun(brainID: tier.rawValue, scores: scores))
        }
        emit("")
        emit(ChatEvalReport.matrix(runs))
    }

    /// Brains to run: M1K3_SELFTEST_CHATEVAL_BRAINS=mini,lil narrows it (a full
    /// four-brain run loads ~16GB of weights); default is the whole catalogue.
    private static func selectedBrains() -> [BrainTier] {
        guard let raw = SelfTestEnv.value("M1K3_SELFTEST_CHATEVAL_BRAINS") else {
            return BrainTier.allCases
        }
        return raw.split(separator: ",")
            .compactMap { BrainTier(rawValue: String($0).trimmingCharacters(in: .whitespaces)) }
    }

    /// Optional task-kind filter (M1K3_SELFTEST_CHATEVAL_KINDS=tool-use,reasoning)
    /// — nil means every kind. Lets a focused tool-calling run skip the slow
    /// open-chat/reasoning turns.
    private static func selectedKinds() -> Set<TaskKind>? {
        guard let raw = SelfTestEnv.value("M1K3_SELFTEST_CHATEVAL_KINDS") else {
            return nil
        }
        let kinds = raw.split(separator: ",")
            .compactMap { TaskKind(rawValue: String($0).trimmingCharacters(in: .whitespaces)) }
        return kinds.isEmpty ? nil : Set(kinds)
    }

    private static func evalBrain(
        _ tier: BrainTier, emit: @escaping (String) -> Void
    ) async -> [ChatEvalScore]? {
        let provider: any InferenceProvider
        switch tier.backing {
        case .appleFoundationModels:
            let afm = AppleFoundationModelsProvider()
            guard afm.isAvailable else { return nil }
            provider = afm
        case let .mlx(modelID):
            // 2048 like the per-model eval: a reasoning brain can spend hundreds
            // of tokens inside <think> before a one-word answer.
            provider = MLXGemmaProvider(modelID: modelID, maxTokens: 2048)
        }

        let kinds = selectedKinds()
        var scores: [ChatEvalScore] = []
        for fixture in ChatEvalFixtures.all where kinds?.contains(fixture.kind) ?? true {
            let score = await runFixture(fixture, provider: provider)
            emit(score.rendered)
            scores.append(score)
        }
        return scores
    }

    private static func runFixture(
        _ fixture: ChatEvalFixture, provider: any InferenceProvider
    ) async -> ChatEvalScore {
        let clock = ContinuousClock()
        let start = clock.now
        do {
            switch fixture.kind {
            case .groundedQ:
                let observation = try await groundedObservation(fixture, provider: provider, start: start, clock: clock)
                return ChatEvalScorer.score(fixture: fixture, observation: observation)
            case .toolUse:
                return try await toolObservationScore(fixture, provider: provider, start: start, clock: clock)
            case .openChat, .reasoning, .refusal:
                let raw = try await provider.generate(prompt: fixture.prompt)
                let ms = milliseconds(clock.now - start)
                return ChatEvalScorer.score(
                    fixture: fixture, observation: EvalObservation(rawText: raw, latencyMS: ms)
                )
            }
        } catch {
            return ChatEvalScore(
                fixtureID: fixture.id, kind: fixture.kind,
                checks: [EvalCheck(
                    name: "ran", outcome: .fail,
                    detail: String(describing: error).prefix(70).description
                )],
                latencyMS: milliseconds(clock.now - start)
            )
        }
    }

    /// Grounded-Q: seed the doc into a throwaway store, answer through the real
    /// RAGResponder (which retrieves, then strips hallucinated citations), and
    /// read the validated-citation count straight off the response.
    private static func groundedObservation(
        _ fixture: ChatEvalFixture, provider: any InferenceProvider,
        start: ContinuousClock.Instant, clock: ContinuousClock
    ) async throws -> EvalObservation {
        let store = try KnowledgeStore() // in-memory
        let embedder = MLXEmbeddingService()
        let ingester = DocumentIngester(store: store, embedder: embedder)
        if let doc = fixture.seedDoc {
            _ = try await ingester.ingest(title: "Notes", text: doc)
        }
        let responder = RAGResponder(store: store, embedder: embedder, provider: provider)
        let response = try await responder.answer(fixture.prompt)
        return EvalObservation(
            rawText: response.answer,
            validCitationCount: response.citations.count,
            latencyMS: milliseconds(clock.now - start)
        )
    }

    /// Tool-use: run the REAL agent loop. LocalAgent routes AFM through the
    /// prompt-ReAct floor and MLX through its native dialect; AgentResult
    /// .toolsUsed reads the same either way, so mini's tool-calling is measured,
    /// not skipped. maxIterations 3 = pick a tool, observe, conclude.
    private static func toolObservationScore(
        _ fixture: ChatEvalFixture, provider: any InferenceProvider,
        start: ContinuousClock.Instant, clock: ContinuousClock
    ) async throws -> ChatEvalScore {
        let agent = LocalAgent(inferenceProvider: provider, tools: toolPalette, maxIterations: 3)
        let result = try await agent.run(goal: fixture.prompt)
        let toolsUsed = result.toolsUsed
        let rawText = result.conclusion.isEmpty
            ? "tools used: \(toolsUsed.joined(separator: ","))"
            : result.conclusion
        let observation = EvalObservation(
            rawText: rawText,
            toolCalls: toolsUsed,
            latencyMS: milliseconds(clock.now - start)
        )
        return ChatEvalScorer.score(fixture: fixture, observation: observation)
    }

    /// Whole milliseconds in a Duration (matches SelfTest's TTFT helper).
    private static func milliseconds(_ duration: Duration) -> Int {
        let parts = duration.components
        return Int(parts.seconds * 1000) + Int(parts.attoseconds / 1_000_000_000_000_000)
    }
}
