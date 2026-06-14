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
//      # narrow to a brain or two: M1K3_SELFTEST_CHATEVAL_BRAINS=mini,lil
//
//  Output: the cross-brain matrix (passed/total ⌀latency per task-kind) plus
//  per-fixture detail, written line-by-line to M1K3_SELFTEST_OUT so an
//  interrupted run keeps what it measured. The matrix is the evidence the
//  EscalationLadder policy cites — the AFM-vs-floor gap, in numbers.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.82 (the runner is
//  verify-by-launch — its logic cores are the unit-tested M1K3Eval scorer and
//  the proven RAGResponder/ToolCallingProvider seams; the wiring itself can
//  only be confirmed on-device). Prior: Unknown

import Foundation
import M1K3Chat
import M1K3Eval
import M1K3Inference
import M1K3Knowledge
import M1K3MLX

enum ChatEvalStage {
    static var isRequested: Bool {
        ProcessInfo.processInfo.environment["M1K3_SELFTEST_CHATEVAL"] == "1"
    }

    /// The tools the tool-use fixtures probe for — name + description is all the
    /// chat template needs to offer them; the check is whether the brain picks
    /// the right one.
    private static let toolPalette: [ToolDefinition] = [
        ToolDefinition(
            name: "datetime",
            description: "Get the current date and time on this Mac.",
            parameters: []
        ),
        ToolDefinition(
            name: "search_knowledge",
            description: "Search the user's saved notes, memories and imported documents.",
            parameters: [ToolParameterDefinition(name: "query", description: "What to search for.")]
        ),
        ToolDefinition(
            name: "lookup_fact",
            description: "Look up an encyclopedic fact from a reference source (Wikipedia).",
            parameters: [ToolParameterDefinition(name: "query", description: "The fact to look up.")]
        ),
        ToolDefinition(
            name: "web_search",
            description: "Search the live web for current, up-to-the-minute information.",
            parameters: [ToolParameterDefinition(name: "query", description: "The web query.")]
        ),
    ]

    /// Run the requested brains across every fixture, emit per-fixture detail
    /// live, then the headline matrix.
    static func run(emit: @escaping (String) -> Void) async {
        emit("• chateval: \(ChatEvalFixtures.all.count) fixtures × \(selectedBrains().count) brain(s)…")
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
        guard let raw = ProcessInfo.processInfo.environment["M1K3_SELFTEST_CHATEVAL_BRAINS"] else {
            return BrainTier.allCases
        }
        return raw.split(separator: ",")
            .compactMap { BrainTier(rawValue: String($0).trimmingCharacters(in: .whitespaces)) }
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

        let toolCapable = (provider as? any ToolCallingProvider)?.supportsToolCalls ?? false
        var scores: [ChatEvalScore] = []
        for fixture in ChatEvalFixtures.all {
            // A brain with no native tool dialect doesn't get a tool-use cell at
            // all (— in the matrix) rather than a misleading pass/fail.
            if fixture.kind == .toolUse, !toolCapable {
                emit("  – \(fixture.id): no native tool dialect (not scored)")
                continue
            }
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

    /// Tool-use: one structured turn with the palette offered; record which
    /// tools the brain actually invoked.
    private static func toolObservationScore(
        _ fixture: ChatEvalFixture, provider: any InferenceProvider,
        start: ContinuousClock.Instant, clock: ContinuousClock
    ) async throws -> ChatEvalScore {
        guard let toolProvider = provider as? any ToolCallingProvider else {
            return ChatEvalScore(
                fixtureID: fixture.id, kind: fixture.kind,
                checks: [EvalCheck(name: "tool dialect", outcome: .skip, detail: "no native tools")],
                latencyMS: 0
            )
        }
        let turn = try await toolProvider.continueToolTurn(
            messages: [.system(M1K3Persona.systemPrompt), .user(fixture.prompt)],
            tools: toolPalette
        )
        let observation: EvalObservation
        switch turn {
        case let .toolCalls(calls):
            observation = EvalObservation(
                rawText: "called: \(calls.map(\.name).joined(separator: ","))",
                toolCalls: calls.map(\.name),
                latencyMS: milliseconds(clock.now - start)
            )
        case let .text(text):
            observation = EvalObservation(rawText: text, latencyMS: milliseconds(clock.now - start))
        }
        return ChatEvalScorer.score(fixture: fixture, observation: observation)
    }

    /// Whole milliseconds in a Duration (matches SelfTest's TTFT helper).
    private static func milliseconds(_ duration: Duration) -> Int {
        let parts = duration.components
        return Int(parts.seconds * 1000) + Int(parts.attoseconds / 1_000_000_000_000_000)
    }
}
