//
//  DocumentIngesterTests.swift
//  M1K3KnowledgeTests
//
//  Phase 4 ingest, end-to-end: pages/text/PDF → chunk → embed → store →
//  searchable. Plus the PDFTextExtractor round-trip (generated PDF) and the
//  sourceRef dedupe path.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import CoreGraphics
import CoreText
import Foundation
@testable import M1K3Knowledge
import Testing

// MARK: - In-memory PDF generator (test helper)

/// Render text pages into a minimal PDF so PDFTextExtractor has something real
/// to parse — no fixture file on disk.
private func makePDF(pages: [String]) -> Data {
    let data = NSMutableData()
    guard let consumer = CGDataConsumer(data: data as CFMutableData) else { return Data() }
    var mediaBox = CGRect(x: 0, y: 0, width: 612, height: 792)
    guard let ctx = CGContext(consumer: consumer, mediaBox: &mediaBox, nil) else { return Data() }
    let font = CTFontCreateWithName("Helvetica" as CFString, 12, nil)
    for text in pages {
        ctx.beginPDFPage(nil)
        let attributed = NSAttributedString(string: text, attributes: [.font: font])
        let framesetter = CTFramesetterCreateWithAttributedString(attributed)
        let path = CGPath(rect: CGRect(x: 36, y: 36, width: 540, height: 720), transform: nil)
        let frame = CTFramesetterCreateFrame(framesetter, CFRange(location: 0, length: 0), path, nil)
        CTFrameDraw(frame, ctx)
        ctx.endPDFPage()
    }
    ctx.closePDF()
    return data as Data
}

// MARK: - Recording embedder (test helper)

/// Wraps the deterministic hashing embedder but records every text it is asked
/// to embed — so a test can assert WHAT was embedded, not just that it was.
private final class RecordingEmbedder: EmbeddingService, @unchecked Sendable {
    private let inner = HashingEmbeddingService()
    private let lock = NSLock()
    private(set) var embedded: [String] = []

    var fingerprint: String {
        inner.fingerprint
    }

    var dimension: Int {
        inner.dimension
    }

    func isAvailable() async -> Bool {
        await inner.isAvailable()
    }

    func embed(_ text: String) async throws -> [Float] {
        record([text])
        return try await inner.embed(text)
    }

    func embedBatch(_ texts: [String]) async throws -> [[Float]] {
        record(texts)
        return try await inner.embedBatch(texts)
    }

    private func record(_ texts: [String]) {
        lock.withLock { embedded.append(contentsOf: texts) }
    }
}

// MARK: - B5 layer 3: chunks embed WITH their title

struct TitlePrefixedEmbeddingTests {
    @Test("ingest embeds the chunk title-prefixed when the title adds context")
    func ingestEmbedsTitlePrefixed() async throws {
        let store = try KnowledgeStore()
        let embedder = RecordingEmbedder()
        let ingester = DocumentIngester(store: store, embedder: embedder)
        _ = try await ingester.ingest(
            title: "Golden Gate derisk",
            text: "The full-graph beta build passed at every rung."
        )
        #expect(embedder.embedded.contains(
            "Golden Gate derisk\nThe full-graph beta build passed at every rung."
        ))
    }

    @Test("a memory fact whose title IS the text embeds bare — no doubled fact")
    func factEmbedsBare() async throws {
        let store = try KnowledgeStore()
        let embedder = RecordingEmbedder()
        let ingester = DocumentIngester(store: store, embedder: embedder)
        let fact = "Kev lives in Cork."
        _ = try await ingester.ingest(title: fact, text: fact, kind: .memory)
        #expect(embedder.embedded == [fact])
    }

    @Test("reindexEmbeddings re-embeds with the SAME title-prefixed composition")
    func reindexUsesComposition() async throws {
        let store = try KnowledgeStore()
        let ingestEmbedder = RecordingEmbedder()
        let ingester = DocumentIngester(store: store, embedder: ingestEmbedder)
        _ = try await ingester.ingest(
            title: "Golden Gate derisk",
            text: "The full-graph beta build passed at every rung."
        )
        let reindexEmbedder = RecordingEmbedder()
        _ = try await store.reindexEmbeddings(using: reindexEmbedder)
        #expect(reindexEmbedder.embedded == ingestEmbedder.embedded)
    }
}

// MARK: - PDF extraction

struct PDFTextExtractorTests {
    @Test("round-trips text and page count from generated PDF bytes")
    func roundTrip() throws {
        let data = makePDF(pages: ["Hydraulic seal failed under load.", "Operators wear gloves."])
        let pages = try PDFTextExtractor.extract(data: data)
        #expect(pages.count == 2)
        #expect(pages[0].pageNumber == 1)
        #expect(pages[0].text.contains("Hydraulic seal"))
        #expect(pages[1].text.contains("gloves"))
    }

    @Test("non-PDF data throws cannotOpen")
    func badData() {
        #expect(throws: PDFTextExtractor.ExtractError.cannotOpen) {
            try PDFTextExtractor.extract(data: Data("not a pdf".utf8))
        }
    }
}

// MARK: - Ingestion

struct DocumentIngesterTests {
    @Test("ingests text and makes it FTS-searchable")
    func ingestText() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store)
        let result = try await ingester.ingest(title: "Note", text: "The hydraulic seal failed under load.")
        #expect(result.chunkCount >= 1)
        #expect(result.wasDeduped == false)
        #expect(try store.itemCount() == 1)
        #expect(try store.searchFTS(query: "hydraulic").isEmpty == false)
    }

    @Test("with an embedder, ingested content is hybrid-searchable")
    func ingestWithEmbedder() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let ingester = DocumentIngester(store: store, embedder: embedder)
        _ = try await ingester.ingest(
            title: "Plant",
            pages: [DocumentPage(pageNumber: 1, text: "1.1 Seals\nThe hydraulic seal failed under load.")]
        )
        let q = try await embedder.embed("hydraulic seal")
        let hits = try store.searchHybrid(query: "hydraulic seal", queryVector: q)
        #expect(hits.first?.content.contains("hydraulic") == true)
    }

    @Test("re-ingesting the same sourceRef dedupes (no duplicate item)")
    func dedupe() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store)
        let first = try await ingester.ingest(title: "Doc", text: "content here", sourceRef: "sha:123")
        let second = try await ingester.ingest(title: "Doc", text: "content here", sourceRef: "sha:123")
        #expect(second.wasDeduped == true)
        #expect(second.itemID == first.itemID)
        #expect(try store.itemCount() == 1)
    }

    @Test("END TO END: ingest a generated PDF, then find it by search")
    func pdfEndToEnd() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store, embedder: HashingEmbeddingService())
        let pdf = makePDF(pages: ["1.1 Cleaning\nClean the conveyor seal before each shift."])
        let result = try await ingester.ingestPDF(title: "SOP", data: pdf, sourceRef: "sha:pdf1")
        #expect(result.chunkCount >= 1)
        let item = try #require(try store.item(id: result.itemID))
        #expect(item.kind == .document)
        #expect(try store.searchFTS(query: "conveyor").isEmpty == false)
    }

    // MARK: - Memory kind (provenance, not size)

    @Test("ingest with kind/source stamps the item — the memory write path")
    func ingestMemoryKind() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store)
        let result = try await ingester.ingest(
            title: "Kev's sister",
            text: "Kev's sister is called Aoife.",
            kind: .memory,
            source: .user
        )
        let item = try #require(try store.item(id: result.itemID))
        #expect(item.kind == .memory)
        #expect(item.source == .user)
        // The residency loop: remembered facts stay searchable.
        #expect(try store.searchFTS(query: "Aoife").isEmpty == false)
    }

    @Test("default ingest is unchanged — .document, source nil (legacy pin)")
    func defaultKindUnchanged() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store)
        let result = try await ingester.ingest(title: "Note", text: "Plain document text.")
        let item = try #require(try store.item(id: result.itemID))
        #expect(item.kind == .document)
        #expect(item.source == nil)
    }

    @Test("a short memory fact lands as exactly one chunk")
    func shortMemoryIsOneChunk() async throws {
        let store = try KnowledgeStore()
        let ingester = DocumentIngester(store: store)
        let result = try await ingester.ingest(
            title: "Units", text: "Prefers metric units.", kind: .memory, source: .distilled
        )
        #expect(result.chunkCount == 1)
    }
}
