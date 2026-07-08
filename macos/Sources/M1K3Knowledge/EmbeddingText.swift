//
//  EmbeddingText.swift
//  M1K3Knowledge
//
//  B5 layer 3 — what a chunk is embedded AS. A chunk's discriminating context
//  often lives in the document TITLE ("Golden Gate derisk") while the body is
//  generic; embedding bare content left that context invisible to the vector
//  (the milestone memory measured 0.286 against its own query — under every
//  floor). Chunks now embed title-prefixed, at ingest AND reindex, through
//  this one composition point.
//
//  Two rules keep it honest:
//  - Content that already LEADS with its title (memory facts are their own
//    titles, including the word-boundary "…" truncation) embeds bare — no
//    doubled text shifting the fact lane.
//  - The composition is versioned into the store fingerprint, so changing it
//    triggers the same one-time corpus re-embed an embedder swap does; mixed
//    composition in one store would compare incompatible vectors forever.
//
//  Queries embed as typed (asymmetric on purpose): the title is document-side
//  context, not something a user would type twice.
//
//  Signed: Kev + claude-fable-5, 2026-07-08, Confidence 0.85 (composition
//  pinned by tests; the retrieval delta + ABSEP/MEMEVAL threshold re-check is
//  the named on-device verify — layer 3's doctrine is measured, not guessed).
//  Prior: Unknown
//

import Foundation

public enum EmbeddingText {
    /// Versioned composition identity, salted into the store's embedder
    /// fingerprint. Bump when `forChunk` changes shape.
    public static let compositionVersion = "title-v1"

    /// The text a chunk is embedded as: title-prefixed unless the content
    /// already leads with the title (case-insensitive; a trailing "…" from
    /// word-boundary truncation is ignored) or the title is empty.
    public static func forChunk(title: String, content: String) -> String {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else { return content }
        var head = trimmedTitle
        if head.hasSuffix("…") { head = String(head.dropLast()) }
        head = head.trimmingCharacters(in: .whitespaces)
        if !head.isEmpty, content.lowercased().hasPrefix(head.lowercased()) {
            return content
        }
        return "\(trimmedTitle)\n\(content)"
    }

    /// The fingerprint recorded with (and compared against) the store's
    /// vectors: the embedder's own identity plus the composition version —
    /// either changing means the stored vectors are no longer comparable.
    public static func storeFingerprint(embedder fingerprint: String) -> String {
        "\(fingerprint)+\(compositionVersion)"
    }
}
