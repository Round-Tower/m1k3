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
//

import Foundation
import M1K3Knowledge

public enum HeadlessAskError: Error, Sendable, Equatable {
    /// The model produced reasoning or nothing at all — no answer to return.
    case emptyAnswer
}

public enum HeadlessAsk {
    public static func answer(_ question: String, using responder: any RAGResponding) async throws -> String {
        let (injected, stream) = try await responder.answerStreaming(
            question, history: [], onActivity: { _ in }
        )
        var raw = ""
        for await chunk in stream {
            raw = ChatSession.fold(raw, chunk)
        }
        let (_, answerText) = ReasoningSplit.split(raw)
        let merged = ChatSession.mergeSources(injected, responder.collectedSources())
        let validation = await CitationValidator.validate(responseText: answerText, against: merged)
        let polished = MessageTextPolish.polish(validation.cleanedText)
        guard !polished.isEmpty else { throw HeadlessAskError.emptyAnswer }
        guard !merged.isEmpty else { return polished }
        let lines = merged.map { hit in
            if let heading = hit.heading, !heading.isEmpty {
                return "— \(hit.itemTitle) §\(heading)"
            }
            return "— \(hit.itemTitle)"
        }
        return polished + "\n\nSources:\n" + lines.joined(separator: "\n")
    }
}
