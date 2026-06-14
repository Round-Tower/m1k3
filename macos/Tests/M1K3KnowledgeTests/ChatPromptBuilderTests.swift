//
//  ChatPromptBuilderTests.swift
//  M1K3KnowledgeTests
//
//  Contract tests for the pure RAG prompt builder: knowledge block, citation
//  framing, the empty-context path, and the citation label format.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct ChatPromptBuilderTests {
    private func hit(_ title: String, _ heading: String?, _ content: String) -> ChunkHit {
        ChunkHit(
            chunkID: UUID(), itemID: UUID(), itemTitle: title,
            kind: .document, heading: heading, content: content
        )
    }

    @Test("includes the knowledge, the user message, and citation framing")
    func grounded() {
        let prompt = ChatPromptBuilder.build(
            chunks: [hit("Plant Notes", "3.2 Seals", "The hydraulic seal failed.")],
            userMessage: "What failed?"
        )
        #expect(prompt.contains("KNOWLEDGE:"))
        #expect(prompt.contains("The hydraulic seal failed."))
        #expect(prompt.contains("[Plant Notes §3.2 Seals]"))
        #expect(prompt.contains("USER: What failed?"))
        // The Apple-FM lesson must be present.
        #expect(prompt.contains("NOT markdown links"))
    }

    @Test("empty knowledge produces a no-context prompt that admits the gap")
    func emptyContext() {
        let prompt = ChatPromptBuilder.build(chunks: [], userMessage: "Anything on seals?")
        #expect(prompt.contains("No stored knowledge matched"))
        #expect(prompt.contains("USER: Anything on seals?"))
        #expect(!prompt.contains("KNOWLEDGE:"))
    }

    @Test("empty-knowledge fallback biases to honest abstention, not a confab licence")
    func emptyContextDiscouragesGuessing() {
        // This prompt feeds the agent-loop fallback and the plain RAGResponder.
        // The old "Answer from general knowledge if you can" read as licence to
        // invent on a weak model — align it with the anti-confab stance the
        // grounding body carries everywhere else.
        let prompt = ChatPromptBuilder.build(chunks: [], userMessage: "q")
        #expect(!prompt.contains("Answer from general knowledge if you can"))
        // The prompt wraps across lines, so match within a line.
        #expect(prompt.contains("Answer from what you"))
        #expect(prompt.contains("say so plainly rather than"))
    }

    @Test("citation label includes heading when present, omits when absent")
    func citationLabel() {
        #expect(ChatPromptBuilder.citationLabel(for: hit("Doc", "1.1 A", "x")) == "[Doc §1.1 A]")
        #expect(ChatPromptBuilder.citationLabel(for: hit("Doc", nil, "x")) == "[Doc]")
        #expect(ChatPromptBuilder.citationLabel(for: hit("Doc", "", "x")) == "[Doc]")
    }

    @Test("numbers multiple chunks in order")
    func numbered() {
        let prompt = ChatPromptBuilder.build(
            chunks: [hit("A", nil, "first"), hit("B", nil, "second")],
            userMessage: "q"
        )
        #expect(prompt.contains("1. [A]"))
        #expect(prompt.contains("2. [B]"))
    }
}
