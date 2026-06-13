//
//  SeparationEvalFixtures.swift
//  M1K3Knowledge
//
//  Fixtures + pure report for the embedder A/B SEPARATION harness
//  (M1K3_SELFTEST_ABSEP). The whole reason to swap bge-small for a 2026
//  retriever (Qwen3-Embedding) is SEPARATION: how far the off-domain noise
//  ceiling sits below the in-domain signal floor. A wide gap lets GroundingGate's
//  threshold live in a real dead-zone; a narrow one (bge-small's ~0.10) leaks.
//
//  Each pair is (query, document). In-domain docs genuinely answer their query
//  → high cosine (the floor we must keep high). Off-domain docs are unrelated →
//  low cosine (the ceiling we must keep low). The harness embeds both with the
//  OLD and NEW embedder and reports which one separates the classes wider — so
//  the swap is proven, not trusted. Off-domain queries reuse the real on-device
//  leakers from 2026-06-13 (sourdough/apple-pruning/CSS) against the ML corpus.
//
//  Fixtures + formatter are pure (unit-tested); the embedding pass is
//  verify-by-launch.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-13, Confidence 0.85 (hand-curated set
//  on the ML-papers domain; extend as new leakers surface). Prior: Unknown
//

import Foundation

public enum SeparationEvalFixtures {
    public struct Pair: Sendable {
        public let query: String
        public let document: String

        public init(query: String, document: String) {
            self.query = query
            self.document = document
        }
    }

    /// Query + a document chunk that genuinely answers it (ML-papers domain).
    /// These cosines are the SIGNAL floor — a good embedder keeps them high.
    public static let inDomain: [Pair] = [
        .init(
            query: "how does attention work?",
            document: "An attention function maps a query and a set of key-value pairs to an output, "
                + "computed as a weighted sum of the values, where the weight of each value comes from "
                + "a compatibility function of the query with the corresponding key."
        ),
        .init(
            query: "what is the transformer architecture?",
            document: "The Transformer is a model architecture eschewing recurrence and instead relying "
                + "entirely on a self-attention mechanism to draw global dependencies between input and output."
        ),
        .init(
            query: "what is multi-head attention?",
            document: "Multi-head attention lets the model jointly attend to information from different "
                + "representation subspaces at different positions, rather than a single attention head."
        ),
        .init(
            query: "what did McCulloch and Pitts propose about neurons?",
            document: "McCulloch and Pitts gave a logical calculus of the ideas immanent in nervous activity, "
                + "modelling a neuron as a threshold logic unit that fires when its weighted inputs exceed a bound."
        ),
        .init(
            query: "how is information entropy defined?",
            document: "Shannon defined entropy as a measure of the average uncertainty, or information content, "
                + "of a source — the expected number of bits needed to encode its symbols."
        ),
        .init(
            query: "why use positional encoding in a sequence model?",
            document: "Since the model contains no recurrence or convolution, positional encodings are added to "
                + "the input embeddings so the model can make use of the order of the sequence."
        ),
    ]

    /// Query (unrelated to the ML corpus) + an in-corpus document it must NOT
    /// match. These cosines are the NOISE ceiling — a good embedder keeps them
    /// low. The queries are the real leakers observed on-device on 2026-06-13.
    public static let offDomain: [Pair] = [
        .init(
            query: "best sourdough starter ratio?",
            document: "An attention function maps a query and a set of key-value pairs to an output, "
                + "computed as a weighted sum of the values."
        ),
        .init(
            query: "how do I prune an apple tree in winter?",
            document: "The Transformer is a model architecture relying entirely on self-attention to draw "
                + "global dependencies between input and output."
        ),
        .init(
            query: "how do I centre a div in CSS?",
            document: "McCulloch and Pitts modelled a neuron as a threshold logic unit that fires when its "
                + "weighted inputs exceed a bound."
        ),
        .init(
            query: "what's a good weeknight pasta recipe?",
            document: "Shannon defined entropy as a measure of the average uncertainty of a source."
        ),
        .init(
            query: "what time is high tide in Cork today?",
            document: "Multi-head attention lets the model jointly attend to information from different "
                + "representation subspaces at different positions."
        ),
        .init(
            query: "which JavaScript framework should I use for a frontend?",
            document: "Positional encodings are added to the input embeddings so the model can use the order "
                + "of the sequence."
        ),
    ]
}

/// Pure formatter for the A/B separation report. Given each embedder's measured
/// in-domain and off-domain cosine scores, render the distributions, each
/// embedder's separation margin (in-domain floor − off-domain ceiling), and —
/// when exactly two embedders are compared — a head-to-head verdict.
public enum SeparationEvalReport {
    public struct Result: Sendable {
        public let label: String
        public let inDomain: [Float]
        public let offDomain: [Float]

        public init(label: String, inDomain: [Float], offDomain: [Float]) {
            self.label = label
            self.inDomain = inDomain
            self.offDomain = offDomain
        }
    }

    /// Separation margin = lowest in-domain cosine − highest off-domain cosine.
    /// Positive = a clean dead-zone the threshold can sit in; negative = the
    /// noise band swallows real hits. Nil when either class is empty.
    public static func margin(inDomain: [Float], offDomain: [Float]) -> Float? {
        guard let floor = inDomain.min(), let ceiling = offDomain.max() else { return nil }
        return floor - ceiling
    }

    public static func render(_ results: [Result]) -> String {
        var lines: [String] = []
        for r in results {
            lines.append("[\(r.label)]")
            lines.append("  in-domain:  \(summary(r.inDomain))")
            lines.append("  off-domain: \(summary(r.offDomain))")
            if let m = margin(inDomain: r.inDomain, offDomain: r.offDomain) {
                let verdict = m > 0 ? "clean dead-zone" : "OVERLAP — noise band swallows signal"
                lines.append(String(format: "  margin: %.3f (%@)", m, verdict))
            } else {
                lines.append("  margin: n/a (empty class)")
            }
        }

        // Head-to-head only makes sense for a straight two-way comparison.
        if results.count == 2,
           let a = margin(inDomain: results[0].inDomain, offDomain: results[0].offDomain),
           let b = margin(inDomain: results[1].inDomain, offDomain: results[1].offDomain)
        {
            let delta = b - a
            let direction = delta > 0 ? "wider" : (delta < 0 ? "narrower" : "even")
            lines.append(String(
                format: "head-to-head: %@ margin %.3f vs %@ margin %.3f → %@ %@ by %.3f",
                results[0].label, a, results[1].label, b, results[1].label, direction, abs(delta)
            ))
        }
        return lines.joined(separator: "\n")
    }

    static func summary(_ scores: [Float]) -> String {
        guard !scores.isEmpty else { return "no scores" }
        let sorted = scores.sorted()
        let median = trueMedian(sorted)
        return String(
            format: "min %.3f / median %.3f / max %.3f (n=%d)",
            sorted.first ?? 0, median, sorted.last ?? 0, scores.count
        )
    }

    /// True median of a SORTED array — averages the two middle values for an
    /// even count (the upper-median shortcut misreports by up to half the
    /// inter-middle gap, which matters for a threshold-setting statistic).
    static func trueMedian(_ sorted: [Float]) -> Float {
        guard !sorted.isEmpty else { return 0 }
        let mid = sorted.count / 2
        return sorted.count.isMultiple(of: 2) ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid]
    }
}
