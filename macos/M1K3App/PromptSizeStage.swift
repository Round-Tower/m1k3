//
//  PromptSizeStage.swift
//  M1K3App
//
//  THE PROMPT-SIZE INSTRUMENT (M1K3_SELFTEST_PROMPTSIZE=1).
//
//  Every context guarantee in the app currently rests on an estimate nobody has
//  checked against a tokenizer. HistoryBudgetPolicy assumes 3.5 chars/token and
//  its own header names real token counting as a deferred [SPIKE];
//  DocumentChunker implies 4; AppEnvironment+ChatHistory reserves the
//  non-history prompt at 3000 tokens with grounding itemised at "~1100" — while
//  AgentRAGResponder.groundingBody interpolates retrieved chunks VERBATIM and
//  untruncated (5 doc hits × DocumentChunker.targetChars 1200, plus a second
//  memory lane). On Big the failure mode is silent: gemma-4-12B's
//  RotatingKVCache(8192) rotates the persona/grounding head out during prefill
//  and answers off-persona with no error. This stage produces the numbers that
//  turn that from an argument into a measurement.
//
//  HOW IT MEASURES — interception, not reconstruction. A RecordingProvider
//  wraps the real provider and captures the prompt string the agent actually
//  sends, then the run is scored offline. Re-assembling a prompt from
//  AgentRAGResponder's parts would be a second implementation of prompt
//  assembly, and the moment it drifted the instrument would confidently measure
//  something the model never saw. (It would also miss the persona outright —
//  grounding() opens on the history marker; the persona is added further down.
//  Pinned in PromptMarkerLiveTests.)
//
//  The component breakdown comes from PromptSectionSplitter over
//  PromptMarker.live; real token counts come from the model's own tokenizer via
//  MLXGemmaProvider.tokenCount. A tier with no exposed tokenizer (AFM/mini)
//  reports bytes with nil tokens — never a silent zero.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-19, Confidence 0.75 (the pure halves
//  — PromptSizeReport, PromptSectionSplitter, the marker pins — are TDD'd
//  red-first and green; this app-target glue follows ChatEvalStage's shape and
//  has no test bundle by design, so it is verify-by-launch like every other
//  SelfTest arm. The numbers it produces are the point and they are UNMEASURED
//  until Kev runs it). Prior: Unknown
//

import Foundation
import M1K3Chat
import M1K3Eval
import M1K3Inference
import M1K3Knowledge
import M1K3MLX

/// Wraps a provider and records every prompt handed to it, so the stage can
/// measure exactly what the model saw.
private actor PromptRecorder {
    private(set) var prompts: [String] = []

    func record(_ prompt: String) {
        prompts.append(prompt)
    }
}

private struct RecordingProvider: InferenceProvider {
    let wrapped: any InferenceProvider
    let recorder: PromptRecorder

    var name: String {
        wrapped.name
    }

    var isAvailable: Bool {
        wrapped.isAvailable
    }

    func generate(prompt: String) async throws -> String {
        await recorder.record(prompt)
        return try await wrapped.generate(prompt: prompt)
    }

    func generateStreaming(prompt: String) -> AsyncStream<String> {
        let recorder = self.recorder
        let wrapped = self.wrapped
        return AsyncStream { continuation in
            Task {
                await recorder.record(prompt)
                for await piece in wrapped.generateStreaming(prompt: prompt) {
                    continuation.yield(piece)
                }
                continuation.finish()
            }
        }
    }
}

enum PromptSizeStage {
    static var isRequested: Bool {
        SelfTestEnv.value("M1K3_SELFTEST_PROMPTSIZE") == "1"
    }

    /// Fixture kinds worth sizing. grounded-Q is the one that matters — it's
    /// the only kind that fills the KNOWLEDGE block — but open-chat gives the
    /// floor (persona + tools + rules with no retrieval) so the grounding cost
    /// can be read as a delta.
    private static let kinds: [TaskKind] = [.openChat, .groundedQ]

    /// Documents seeded before the sized turns, so retrieval actually fires.
    /// Chunks are deliberately built at DocumentChunker.targetChars (1200) —
    /// the WORST CASE the live path can inject, which is the number the
    /// ~1100-token reserve has to survive.
    private static func seedWorstCaseCorpus(into store: KnowledgeStore) async throws {
        let filler = String(repeating: "The hydraulic seal failed under load. ", count: 32)
        for index in 1 ... 6 {
            let ingester = DocumentIngester(store: store, embedder: HashingEmbeddingService())
            _ = try await ingester.ingest(
                title: "Plant Notes \(index)",
                text: "3.\(index) Seals\n\(filler.prefix(DocumentChunker.targetChars))"
            )
        }
    }

    static func run(emit: @escaping (String) -> Void) async {
        emit("• promptsize: assembling real prompts and counting real tokens…")

        let tier = BrainTier.big
        guard case let .mlx(modelID) = tier.backing else {
            emit("  – \(tier.rawValue): not an MLX tier, no tokenizer — skipped")
            return
        }
        let provider = MLXGemmaProvider(modelID: modelID)
        let recorder = PromptRecorder()

        var measurements: [PromptSizeMeasurement] = []
        for kind in kinds {
            guard let fixture = ChatEvalFixtures.fixtures(for: kind).first else { continue }
            do {
                let store = try KnowledgeStore()
                if kind == .groundedQ {
                    try await seedWorstCaseCorpus(into: store)
                }
                let responder = AgentRAGResponder(
                    store: store,
                    embedder: HashingEmbeddingService(),
                    provider: RecordingProvider(wrapped: provider, recorder: recorder),
                    tools: [],
                    maxIterations: 1
                )
                let (_, stream) = try await responder.answerStreaming(fixture.prompt)
                for await _ in stream {}

                guard let prompt = await recorder.prompts.last else {
                    emit("  – \(kind.label): provider was never called — skipped")
                    continue
                }
                measurements.append(
                    await measure(prompt: prompt, label: kind.label, tier: tier, provider: provider)
                )
            } catch {
                emit("  – \(kind.label): failed (\(error))")
            }
        }

        emit("")
        emit(PromptSizeReport.table(measurements))
        emit("")
        emit(verdict(for: measurements))
    }

    /// Split the intercepted prompt, count each section with the model's own
    /// tokenizer, and attribute the chat-template wrapper as its own component
    /// (templated whole − sum of raw sections) so the parts re-sum honestly.
    private static func measure(
        prompt: String, label: String, tier: BrainTier, provider: MLXGemmaProvider
    ) async -> PromptSizeMeasurement {
        var components: [PromptComponentSize] = []
        var sectionTokenTotal = 0
        var everySectionCounted = true

        for section in PromptSectionSplitter.split(prompt, markers: PromptMarker.live) {
            let tokens = await provider.tokenCount(section.text)
            if let tokens {
                sectionTokenTotal += tokens
            } else {
                everySectionCounted = false
            }
            components.append(
                PromptComponentSize(
                    name: section.name, bytes: section.text.utf8.count, tokens: tokens
                )
            )
        }

        // The wrapper's cost, measured rather than assumed.
        if everySectionCounted, let templated = await provider.templatedTokenCount(prompt) {
            components.append(
                PromptComponentSize(
                    name: "template", bytes: 0, tokens: max(0, templated - sectionTokenTotal)
                )
            )
        }

        return PromptSizeMeasurement(
            label: "\(label) [\(tier.rawValue)]",
            components: components,
            reserveTokens: AppEnvironment.historyReserveTokens,
            windowTokens: tier.approximateContextTokens
        )
    }

    /// The headline: does the assembled prompt fit what the app reserved, and
    /// what is the real chars/token ratio the estimates should be tuned to.
    private static func verdict(for measurements: [PromptSizeMeasurement]) -> String {
        var lines = ["=== PROMPT SIZE VERDICT ==="]
        let overruns = measurements.filter { $0.exceedsReserve == true }
        if overruns.isEmpty {
            lines.append(
                "• every measured prompt fits AppEnvironment.historyReserveTokens "
                    + "(\(AppEnvironment.historyReserveTokens)tok)."
            )
        } else {
            for measurement in overruns {
                lines.append(
                    "• ⚠️ \(measurement.label) OVER reserve by "
                        + "\(measurement.reserveOverrunTokens ?? 0)tok — the replay budget is "
                        + "computed from a reserve the prompt does not honour, so history is "
                        + "over-granted and gemma's 8192 window can rotate the persona head out "
                        + "silently."
                )
            }
        }
        let ratios = measurements.compactMap(\.measuredCharsPerToken)
        if !ratios.isEmpty {
            let mean = ratios.reduce(0, +) / Double(ratios.count)
            lines.append(
                "• measured chars/token ⌀\(String(format: "%.2f", mean)) "
                    + "(HistoryBudgetPolicy assumes 3.50, DocumentChunker implies 4.00) — "
                    + "the [SPIKE] figure."
            )
        }
        return lines.joined(separator: "\n")
    }
}
