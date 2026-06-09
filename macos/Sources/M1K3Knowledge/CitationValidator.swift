//
//  CitationValidator.swift
//  M1K3Knowledge
//
//  Post-validates `[SOURCE §heading]` / `(SOURCE §heading)` citations in a model
//  response against what's actually indexed. Strips the ones the model invented
//  and reports them separately so an audit trail can record fabrication.
//
//  Generalised from the prior knowledge-server project's CitationValidator: the existence check is *injected*
//  (a predicate) rather than hard-wired to the store, so the parsing/stripping
//  logic is pure and testable with no database. A store-backed predicate wires
//  in at the call site.
//
//  Both delimiters are accepted because Apple Foundation Models renders
//  citations as parentheticals `(ICH-Q7 §5.2 …)` in flowing prose even when the
//  prompt example uses brackets — and `[X]` reads to the model as a half-formed
//  markdown link. Accepting either keeps the audit honest without fighting style.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the prior knowledge-server project the internal knowledge-server core/CitationValidator.swift (Kev)

import Foundation

/// A citation reference parsed from model output: a source token (e.g.
/// "ICH-Q7") and the cited heading.
public struct Citation: Sendable, Equatable, Hashable, Codable {
    public let source: String
    public let heading: String

    public init(source: String, heading: String) {
        self.source = source
        self.heading = heading
    }
}

public enum CitationValidator {
    public struct Result: Sendable, Equatable {
        public let cleanedText: String
        public let validated: [Citation]
        public let stripped: [Citation]

        public init(cleanedText: String, validated: [Citation], stripped: [Citation]) {
            self.cleanedText = cleanedText
            self.validated = validated
            self.stripped = stripped
        }
    }

    /// Parse all citations in `responseText`, validate each via `exists`, strip
    /// the hallucinated ones from the text, and report the breakdown. Each
    /// distinct (source, heading) is checked once even if cited repeatedly.
    public static func validate(
        responseText: String,
        exists: @Sendable (Citation) async -> Bool
    ) async -> Result {
        var validated: [Citation] = []
        var stripped: [Citation] = []
        var cleanedText = responseText
        var processed: Set<Citation> = []

        for hit in citationHits(in: responseText) {
            if processed.contains(hit.citation) { continue }
            processed.insert(hit.citation)

            if await exists(hit.citation) {
                validated.append(hit.citation)
            } else {
                stripped.append(hit.citation)
                cleanedText = cleanedText.replacingOccurrences(of: hit.full, with: "")
            }
        }

        return Result(cleanedText: cleanedText, validated: validated, stripped: stripped)
    }

    /// Validate against the retrieved chunks — the RAG grounding contract: a citation
    /// is kept iff some chunk shares its title (source) and heading. The model may only
    /// cite what it was shown, so a citation to un-retrieved content is stripped even if
    /// that content exists elsewhere in the store.
    public static func validate(
        responseText: String,
        against chunks: [ChunkHit]
    ) async -> Result {
        let result = await validate(responseText: responseText) { citation in
            chunks.contains { chunk in
                chunk.itemTitle == citation.source
                    && (chunk.heading?.trimmingCharacters(in: .whitespaces) ?? "") == citation.heading
            }
        }
        // Stripping a citation leaves a gap ("…not  ." / "see  and"); tidy it so the
        // cleaned answer reads naturally in the UI (this overload feeds the rendered text).
        guard !result.stripped.isEmpty else { return result }
        return Result(
            cleanedText: tidy(result.cleanedText),
            validated: result.validated,
            stripped: result.stripped
        )
    }

    /// Collapse the whitespace a stripped citation leaves behind: runs of spaces → one,
    /// no space before sentence punctuation, trimmed ends. Newlines are preserved.
    static func tidy(_ text: String) -> String {
        var cleaned = text
        while cleaned.contains("  ") {
            cleaned = cleaned.replacingOccurrences(of: "  ", with: " ")
        }
        for mark in [".", ",", ";", ":", "!", "?"] {
            cleaned = cleaned.replacingOccurrences(of: " \(mark)", with: mark)
        }
        return cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Parsing

    struct Hit: Equatable {
        let full: String
        let citation: Citation
    }

    /// Extract every bracket- and paren-delimited citation, in document order
    /// (brackets first, then parens — matching the prior knowledge-server project's collection order).
    static func citationHits(in text: String) -> [Hit] {
        let bracketPattern = #/\[([A-Z][A-Z0-9-]+)\s+§([^\]]+)\]/#
        let parenPattern = #/\(([A-Z][A-Z0-9-]+)\s+§([^\)]+)\)/#

        var hits: [Hit] = []
        for match in text.matches(of: bracketPattern) {
            hits.append(makeHit(full: match.0, source: match.1, heading: match.2))
        }
        for match in text.matches(of: parenPattern) {
            hits.append(makeHit(full: match.0, source: match.1, heading: match.2))
        }
        return hits
    }

    private static func makeHit(
        full: Substring,
        source: Substring,
        heading: Substring
    ) -> Hit {
        Hit(
            full: String(full),
            citation: Citation(
                source: String(source),
                heading: heading.trimmingCharacters(in: .whitespaces)
            )
        )
    }
}
