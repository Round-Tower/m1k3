//
//  ModelEval.swift
//  M1K3Inference
//
//  Per-model behavioral evals. Every model family lands with its own
//  surprises — Gemma-3n's chat template silently DROPPED injected tools,
//  Qwen3.5 pre-opens <think> and emits only the close, dialects differ per
//  family — so promotion into the brain lineup needs the same checklist run
//  against the real model, on-device. This file is the PURE core (verdicts,
//  report, assertion helpers — unit-tested); the checks that touch a live
//  model run from the headless self-test (M1K3_SELFTEST_EVAL=1), because MLX
//  only runs inside the .app bundle.
//
//  Adding a new model costs nothing here: the runner walks BrainTier's MLX
//  ids by default (a new tier is automatically evaluated) or any ad-hoc list
//  via M1K3_SELFTEST_EVAL_MODELS.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation

public enum ModelEvalOutcome: String, Sendable, Equatable {
    case pass
    case fail
    case skip

    var mark: String {
        switch self {
        case .pass: "✓"
        case .fail: "✗"
        case .skip: "–"
        }
    }
}

public struct ModelEvalRecord: Sendable, Equatable {
    public let check: String
    public let outcome: ModelEvalOutcome
    public let detail: String

    public init(check: String, outcome: ModelEvalOutcome, detail: String) {
        self.check = check
        self.outcome = outcome
        self.detail = detail
    }
}

public struct ModelEvalReport: Sendable, Equatable {
    public let modelID: String
    public let records: [ModelEvalRecord]

    public init(modelID: String, records: [ModelEvalRecord]) {
        self.modelID = modelID
        self.records = records
    }

    /// Failures fail the model; skips are honest "not applicable", not passes.
    public var passed: Bool {
        records.allSatisfy { $0.outcome != .fail }
    }

    public var rendered: String {
        let lines = records.map { record -> String in
            let suffix = record.detail.isEmpty ? "" : " — \(record.detail)"
            return "  \(record.outcome.mark) \(record.check)\(suffix)"
        }
        let verdict = passed ? "PASS" : "FAIL"
        return "eval \(modelID): \(verdict)\n" + lines.joined(separator: "\n")
    }

    /// Strip chain-of-thought for answer assertions — handles the matched
    /// pair, Qwen3.5's lone close, and plain text alike.
    public static func strippingThink(_ text: String) -> String {
        var working = text
        if let close = working.range(of: "</think>") {
            working = String(working[close.upperBound...])
        }
        working = working.replacingOccurrences(
            of: "<think>.*?</think>", with: "", options: .regularExpression
        )
        return working.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
