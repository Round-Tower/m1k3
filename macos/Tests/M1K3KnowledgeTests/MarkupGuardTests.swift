//
//  MarkupGuardTests.swift
//  M1K3KnowledgeTests
//
//  The ingest gate against raw-markup blobs — the "Artboard" finding: a 988KB
//  SVG indexed as a document polluted FTS with XML noise. Prose with the odd
//  angle bracket must still pass; whole files of markup must not.
//

import Foundation
@testable import M1K3Knowledge
import Testing

struct MarkupGuardTests {
    @Test("SVG, XML, and HTML payloads are detected by prefix")
    func detectsByPrefix() {
        #expect(MarkupGuard.looksLikeMarkup("<?xml version=\"1.0\"?><svg>…</svg>"))
        #expect(MarkupGuard.looksLikeMarkup("<svg xmlns=\"http://www.w3.org/2000/svg\">"))
        #expect(MarkupGuard.looksLikeMarkup("<!DOCTYPE html><html><body>x</body></html>"))
        #expect(MarkupGuard.looksLikeMarkup("  \n<?xml version=\"1.0\"?>")) // leading whitespace
        #expect(MarkupGuard.looksLikeMarkup("<html lang=\"en\"><head>"))
    }

    @Test("tag-dense bodies are detected even without a known prefix")
    func detectsByDensity() {
        let svgish = String(repeating: "<rect x=\"1\" y=\"2\"/><g transform=\"rotate(90)\">", count: 40)
        #expect(MarkupGuard.looksLikeMarkup(svgish))
    }

    @Test("prose passes, even with occasional angle brackets and code mentions")
    func prosePasses() {
        #expect(!MarkupGuard.looksLikeMarkup("The hydraulic seal failed under load at 3 < 4 bar."))
        #expect(!MarkupGuard.looksLikeMarkup("Use the <think> tag sparingly; models overthink."))
        let paper = String(repeating: "Entropy measures information content per symbol. ", count: 100)
        #expect(!MarkupGuard.looksLikeMarkup(paper))
        #expect(!MarkupGuard.looksLikeMarkup(""))
    }

    @Test("DocumentIngester rejects markup payloads with a clear error")
    func ingesterRejects() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store, embedder: HashingEmbeddingService())
        await #expect(throws: DocumentIngester.IngestError.self) {
            try await ingester.ingest(
                title: "Artboard",
                text: "<?xml version=\"1.0\"?><svg width=\"4096\"><image href=\"data:image/png;base64,AAAA\"/></svg>"
            )
        }
        #expect(try store.allItems().isEmpty)
    }

    @Test("DocumentIngester still accepts prose")
    func ingesterAcceptsProse() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store, embedder: HashingEmbeddingService())
        let result = try await ingester.ingest(title: "Notes", text: "The seal failed under load.")
        #expect(result.chunkCount > 0)
    }
}
