//
//  EmbeddingTextTests.swift
//  M1K3KnowledgeTests
//
//  Pins the B5-layer-3 composition: chunks are embedded WITH their document
//  title so title-carried context ("Golden Gate" in the title, generic body)
//  reaches the vector — the measured 0.286 retrieval miss. The composition is
//  versioned into the store fingerprint so changing it re-embeds the corpus
//  exactly like an embedder swap.
//
//  Signed: Kev + claude-fable-5, 2026-07-08, Confidence 0.85 (composition
//  rules pinned; the retrieval-quality delta is measured on-device via
//  ABSEP/MEMEVAL, not asserted here). Prior: Unknown
//

@testable import M1K3Knowledge
import Testing

struct EmbeddingTextTests {
    @Test("a distinct title is prefixed onto the chunk text")
    func titlePrefixed() {
        let text = EmbeddingText.forChunk(
            title: "Golden Gate derisk", content: "The full-graph beta build passed at every rung."
        )
        #expect(text == "Golden Gate derisk\nThe full-graph beta build passed at every rung.")
    }

    @Test("content that IS the title embeds bare — memory facts are their own titles")
    func factTitleNotDoubled() {
        let fact = "Kev lives in Cork."
        #expect(EmbeddingText.forChunk(title: fact, content: fact) == fact)
    }

    @Test("a word-boundary-truncated title (…) is recognised as the content's own head")
    func ellipsisTitleNotDoubled() {
        let fact = "Kev decided RRF over learned fusion on 06-11 because rank fusion is deterministic."
        let title = "Kev decided RRF over learned fusion on 06-11 because…"
        #expect(EmbeddingText.forChunk(title: title, content: fact) == fact)
    }

    @Test("a title that is a mere word-prefix of the content still prefixes — no boundary, no match")
    func wordPrefixIsNotTheTitle() {
        // "Cork" vs "Corker Ltd…": shared letters, different word — the title
        // adds context and must be prefixed (the review-caught boundary gap).
        let text = EmbeddingText.forChunk(
            title: "Cork", content: "Corker Ltd was founded in 1998."
        )
        #expect(text == "Cork\nCorker Ltd was founded in 1998.")
    }

    @Test("a boundary right after the title head still counts as leading")
    func punctuationBoundaryCounts() {
        let content = "Kev lives in Cork, near the coast."
        #expect(EmbeddingText.forChunk(title: "Kev lives in Cork", content: content) == content)
    }

    @Test("title matching is case-insensitive")
    func caseInsensitiveHead() {
        let content = "the seal failed under load."
        #expect(EmbeddingText.forChunk(title: "The seal failed", content: content) == content)
    }

    @Test("an empty or whitespace title embeds the bare content")
    func emptyTitleBare() {
        #expect(EmbeddingText.forChunk(title: "", content: "body") == "body")
        #expect(EmbeddingText.forChunk(title: "  ", content: "body") == "body")
    }

    @Test("the store fingerprint is the embedder's, salted with the composition version")
    func fingerprintSalted() {
        let salted = EmbeddingText.storeFingerprint(embedder: "qwen3-embed-512+mlx-1.2")
        #expect(salted == "qwen3-embed-512+mlx-1.2+\(EmbeddingText.compositionVersion)")
        #expect(EmbeddingText.compositionVersion.isEmpty == false)
    }
}
