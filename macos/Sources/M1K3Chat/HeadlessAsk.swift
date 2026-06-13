//
//  HeadlessAsk.swift
//  M1K3Chat
//
//  One headless turn through a RAGResponding for callers that aren't the
//  chat UI — today the MCP `ask_m1k3` tool. Mirrors ChatSession.send's
//  post-processing contract (fold → reasoning split → citation validation →
//  polish → merged sources) without touching any transcript: the caller owns
//  conversation state, M1K3's chat history is never written.
//
//  Run this on a DEDICATED responder instance, not the chat UI's —
//  collectedSources() is a draining read and would race a live turn.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (contract
//  test-pinned against delta/cumulative/reasoning/citation fakes).
//  Prior: the MCP test report 2026-06-11 (F5).
//  Review: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.9 — exclude
//  `.memory` hits from the Sources footer (they're "do not cite" ambient
//  context; a promiscuous short-fact embedding was rendering as a citation
//  source for unrelated queries). Validator allow-list unchanged.
//

import Foundation
import M1K3Knowledge

public enum HeadlessAsk {
    public static func answer(_ question: String, using responder: any RAGResponding) async throws -> String {
        let (injected, stream) = try await responder.answerStreaming(
            question, history: [], onActivity: { _ in }
        )
        var raw = ""
        for await chunk in stream {
            raw = ChatSession.fold(raw, chunk)
        }
        let (reasoning, answerText) = ReasoningSplit.split(raw)
        let merged = ChatSession.mergeSources(injected, responder.collectedSources())
        let validation = await CitationValidator.validate(responseText: answerText, against: merged)
        let polished = MessageTextPolish.polish(validation.cleanedText)
        // Floor (test-report F1): the model emitted no usable answer — common on
        // the smaller brains, which reason but never conclude, so ReasoningSplit
        // reduces the turn to empty. Degrade with an honest message instead of
        // throwing; a visiting agent should never see a bare "Error: emptyAnswer".
        guard !polished.isEmpty else { return emptyAnswerMessage(didReason: reasoning != nil) }
        // Memories are ambient context ("use naturally, do not cite" —
        // AgentRAGResponder.memoryBlock), never citation sources. However they
        // cleared the gate (a promiscuous short-fact embedding can match an
        // unrelated query — live: "The user has a Mac." rode an apple-pruning
        // turn), they must not surface in the citation footer. The validator
        // allow-list keeps the full `merged` — memories are inert there.
        let citable = merged.filter { $0.kind != .memory }
        guard !citable.isEmpty else { return polished }
        let lines = sourceLines(citable)
        return polished + "\n\nSources:\n" + lines.joined(separator: "\n")
    }

    /// Honest degradation when the turn yields no answer. `didReason` separates
    /// the two real cases: the model thought but never concluded (the common
    /// small-brain failure), vs it produced nothing at all. Brain-agnostic on
    /// purpose — the caller may already be on the largest brain.
    static func emptyAnswerMessage(didReason: Bool) -> String {
        didReason
            ? "I worked through that but didn't reach a clear answer — try asking it "
            + "as a more direct or specific question."
            : "I wasn't able to produce an answer to that — try rephrasing it more specifically."
    }

    /// Render the Sources block: most-relevant first, no repeats.
    ///
    /// The merged hits are whatever cleared the grounding gate plus anything the
    /// tools collected — top-K retrieval, so the same item can appear several
    /// times and an off-topic-but-above-threshold chunk can ride along
    /// (test-report F4). This is a presentation pass only — it never changes what
    /// the model read, just what the caller is told it read: sort by vector
    /// similarity descending (unscored hits last, stable), then drop duplicate
    /// rendered lines. The deeper per-query relevance tuning is GroundingGate's
    /// own follow-up, not this surface's job.
    static func sourceLines(_ hits: [ChunkHit]) -> [String] {
        let ranked = hits.enumerated().sorted { lhs, rhs in
            let lhsScore = lhs.element.similarity ?? -1
            let rhsScore = rhs.element.similarity ?? -1
            if lhsScore != rhsScore { return lhsScore > rhsScore }
            return lhs.offset < rhs.offset // stable for equal/unscored hits
        }
        var seen = Set<String>()
        var lines: [String] = []
        for (_, hit) in ranked {
            let line: String
            if let heading = hit.heading, !heading.isEmpty {
                line = "— \(hit.itemTitle) §\(heading)"
            } else {
                line = "— \(hit.itemTitle)"
            }
            if seen.insert(line).inserted {
                lines.append(line)
            }
        }
        return lines
    }
}
