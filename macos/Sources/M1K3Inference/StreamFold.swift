//
//  StreamFold.swift
//  M1K3Inference
//
//  The one home for the InferenceProvider streaming contract's normalisation
//  rule. `generateStreaming` yields cumulative-or-delta chunks (backend-
//  defined): Apple Foundation Models sends cumulative snapshots, MLX sends
//  plain deltas. The rule that reconciles them — a chunk that extends the
//  accumulated text is a snapshot (replace / diff it), anything else is a
//  delta (append) — was hand-implemented three times across M1K3Chat and
//  M1K3Agent; it lives here once, next to the seam that defines the contract.
//
//  Signed: Kev + claude-fable-5, 2026-07-02, Confidence 0.9 (pure, two-line
//  policy, test-pinned; behavior byte-identical to the three inlined copies
//  it replaces — the empty-current case folds through both branches to the
//  same result). Prior: Kev + claude-opus-4-8 (ChatSession.fold).
//

import Foundation

/// Normalisation for the dual provider stream contract (snapshot vs delta).
public enum StreamFold {
    /// Fold a chunk into the accumulated text: snapshot replaces, delta appends.
    public static func fold(current: String, chunk: String) -> String {
        chunk.hasPrefix(current) ? chunk : current + chunk
    }

    /// The new text a chunk contributes: a snapshot's tail past `current`, or
    /// the whole chunk when it's already a delta.
    public static func delta(current: String, chunk: String) -> String {
        chunk.hasPrefix(current) ? String(chunk.dropFirst(current.count)) : chunk
    }
}
