//
//  ChatPromptBuilder.swift
//  M1K3Knowledge
//
//  Pure prompt assembly for grounded (RAG) chat: retrieved chunks + the user's
//  message → one prompt. Generalised from the prior knowledge-server project's ChatPromptBuilder (which had a
//  the prior knowledge-server project-specific FLOOR OBSERVATIONS block); M1K3 has a single KNOWLEDGE block.
//
//  Carries the prior knowledge-server project's hard-won prompt lessons forward:
//   - Documents-first: authoritative knowledge leads, then how-to-answer rules.
//   - Citation tokens, NOT markdown links — Apple Foundation Models treats bare
//     [X] as a half-formed link, so we tell it explicitly these are citations.
//   - When the knowledge doesn't cover the question, say so rather than invent.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the prior knowledge-server project the internal knowledge-server core/ChatPromptBuilder.swift (Kev)
//  Review: Kev + claude-opus-4-8, 2026-06-29, Confidence 0.85 — the empty-context
//  branch unconditionally told the model to "say you found nothing in the user's
//  own documents", framing EVERY no-context turn as a failed lookup — so a
//  "write me an HTML page" got a confusing failed-lookup disclaimer. Now it
//  explicitly permits generation (write/create/code/explain are tasks to do, not
//  lookups) and keeps honest abstention only for factual questions.

import Foundation

public enum ChatPromptBuilder {
    /// Build a grounded chat prompt. When `chunks` is empty, returns a
    /// no-context prompt that instructs the model to answer from general
    /// knowledge and be explicit about the lack of sources.
    public static func build(chunks: [ChunkHit], userMessage: String) -> String {
        guard !chunks.isEmpty else {
            return """
            You are M1K3, a private local assistant.

            No stored knowledge matched this question — that's fine.
            A request to write, create, code, or explain something is a task to
            do — just produce it; stored sources aren't needed for that.

            Answer from what you genuinely know. If it's a fact you don't have,
            say so plainly rather than guessing — never invent facts or sources.

            USER: \(userMessage)
            """
        }

        let knowledge = chunks.enumerated().map { index, hit in
            "\(index + 1). \(citationLabel(for: hit))\n\(hit.content)"
        }.joined(separator: "\n\n")

        return """
        You are M1K3, a private local assistant. Answer the user's question using
        the KNOWLEDGE below — the user's own documents, calls, and notes.

        KNOWLEDGE:
        \(knowledge)

        HOW TO ANSWER:
        - Ground your answer in the KNOWLEDGE above. If it doesn't cover the
          question, say so — do not invent facts.
        - Cite sources inline using citation tokens like \(exampleToken(for: chunks[0])).
          These are citation tokens, NOT markdown links — never follow them with
          parentheses or a URL.

        USER: \(userMessage)
        """
    }

    /// The bracketed label for a chunk: `[Title §heading]` or `[Title]`.
    public static func citationLabel(for hit: ChunkHit) -> String {
        if let heading = hit.heading, !heading.isEmpty {
            return "[\(hit.itemTitle) §\(heading)]"
        }
        return "[\(hit.itemTitle)]"
    }

    private static func exampleToken(for hit: ChunkHit) -> String {
        citationLabel(for: hit)
    }
}
