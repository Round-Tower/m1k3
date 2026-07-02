//
//  DocumentPage.swift
//  M1K3Knowledge
//
//  Extracted-text page — the input unit to DocumentChunker. A PDF extractor
//  (PDFKit, added with the document-ingest pipeline) produces these; the
//  chunker turns them into embedding-friendly chunks. Plain value type, no I/O.
//
//  Ported from the prior knowledge-server's DocumentPage.
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.95,
//  Prior: internal knowledge-server project, DocumentPage.swift (Kev)

import Foundation

public struct DocumentPage: Codable, Sendable, Equatable {
    public let pageNumber: Int
    public let text: String

    public init(pageNumber: Int, text: String) {
        self.pageNumber = pageNumber
        self.text = text
    }
}
