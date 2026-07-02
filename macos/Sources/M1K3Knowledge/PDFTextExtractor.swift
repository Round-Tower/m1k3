//
//  PDFTextExtractor.swift
//  M1K3Knowledge
//
//  Thin PDFKit adapter: a PDF (data or file) → ordered [DocumentPage] for the
//  chunker. PDFKit ships with macOS, so this adds no third-party dependency.
//
//  Mirrors the prior knowledge-server's PDFTextExtractor. Kept deliberately minimal — the testable
//  logic (boilerplate strip, sectioning, windowing) lives in DocumentChunker;
//  this is the I/O boundary, verified by a generated-PDF round-trip.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: internal knowledge-server project, PDFTextExtractor.swift (Kev)

import Foundation
import PDFKit

public enum PDFTextExtractor {
    public enum ExtractError: Error, Sendable {
        case cannotOpen
        case empty
    }

    /// Extract per-page text from PDF bytes. Page numbers are 1-based.
    public static func extract(data: Data) throws -> [DocumentPage] {
        guard let document = PDFDocument(data: data) else { throw ExtractError.cannotOpen }
        return try pages(from: document)
    }

    /// Extract per-page text from a PDF file URL.
    public static func extract(url: URL) throws -> [DocumentPage] {
        guard let document = PDFDocument(url: url) else { throw ExtractError.cannotOpen }
        return try pages(from: document)
    }

    private static func pages(from document: PDFDocument) throws -> [DocumentPage] {
        guard document.pageCount > 0 else { throw ExtractError.empty }
        var out: [DocumentPage] = []
        out.reserveCapacity(document.pageCount)
        for index in 0 ..< document.pageCount {
            guard let page = document.page(at: index) else { continue }
            out.append(DocumentPage(pageNumber: index + 1, text: page.string ?? ""))
        }
        return out
    }
}
