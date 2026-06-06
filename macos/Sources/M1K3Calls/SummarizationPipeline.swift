//
//  SummarizationPipeline.swift
//  M1K3Calls
//
//  Two-stage call summary over the existing InferenceProvider seam (NO new model):
//  Tier-1 quick gist (a cheap provider, AFM) + Tier-2 deep analysis (the strong
//  provider — Gemma 4 AS A TEXT MODEL, the challenger-blessed safe win; the full
//  transcript text has no 30s audio cap). The two stages are ERROR-ISOLATED: a
//  failure or unavailability in one never blocks the other, mirroring the prior call-pipeline's
//  pipeline so a flaky deep pass still leaves a usable quick summary.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project SummarizationPipeline (Kev) — neutral output, no agent.

import Foundation
import M1K3Inference

public struct SummarizationPipeline: Sendable {
    public struct Output: Sendable, Equatable {
        public let quick: QuickSummary?
        public let full: CallSummary?
    }

    private let quickProvider: any InferenceProvider
    private let deepProvider: any InferenceProvider
    private let parser: CallSummaryParser

    public init(
        quickProvider: any InferenceProvider,
        deepProvider: any InferenceProvider,
        parser: CallSummaryParser = CallSummaryParser()
    ) {
        self.quickProvider = quickProvider
        self.deepProvider = deepProvider
        self.parser = parser
    }

    /// Run both tiers independently; each catches its own failure.
    public func summarize(transcript: String) async -> Output {
        async let quick = runQuick(transcript)
        async let full = runDeep(transcript)
        return Output(quick: await quick, full: await full)
    }

    private func runQuick(_ transcript: String) async -> QuickSummary? {
        guard quickProvider.isAvailable,
              let text = try? await quickProvider.generate(prompt: Self.quickPrompt(transcript))
        else { return nil }
        let overview = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return overview.isEmpty ? nil : QuickSummary(overview: overview)
    }

    private func runDeep(_ transcript: String) async -> CallSummary? {
        guard deepProvider.isAvailable,
              let text = try? await deepProvider.generate(prompt: Self.deepPrompt(transcript))
        else { return nil }
        let summary = parser.parse(text)
        return summary.overview.isEmpty && summary.keyPoints.isEmpty && summary.actionItems.isEmpty
            ? nil : summary
    }

    static func quickPrompt(_ transcript: String) -> String {
        """
        Summarise this call transcript in one or two sentences. Be factual and concise.

        TRANSCRIPT:
        \(transcript)
        """
    }

    static func deepPrompt(_ transcript: String) -> String {
        """
        Analyse this call transcript. Respond using exactly these headers:
        Overview: <one paragraph>
        Key points:
        - <point>
        Action items:
        - <action>

        TRANSCRIPT:
        \(transcript)
        """
    }
}
