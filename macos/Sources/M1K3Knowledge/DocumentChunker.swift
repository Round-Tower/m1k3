//
//  DocumentChunker.swift
//  M1K3Knowledge
//
//  Pure, testable chunker for document text. Heading-aware first, sliding-window
//  fallback within oversized regions. ~300-token target (≈1200 chars). Running
//  header/footer + page-number boilerplate is stripped by frequency before
//  sectioning.
//
//  Ported from the prior knowledge-server project's DocumentChunker (named on the prior knowledge-server project's canonical pure-layer
//  list). Heading detection stays tuned for N.N(.N) SOP-style numbering; a
//  future M1K3 tweak may broaden it, but the mechanism is unchanged.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.95,
//  Prior: the prior knowledge-server project the internal knowledge-server core/DocumentChunker.swift (Kev, HIGH confidence)

import Foundation

public struct DocumentChunk: Codable, Sendable, Equatable {
    public let chunkIndex: Int
    public let pageNumber: Int
    public let heading: String?
    public let content: String

    public init(chunkIndex: Int, pageNumber: Int, heading: String?, content: String) {
        self.chunkIndex = chunkIndex
        self.pageNumber = pageNumber
        self.heading = heading
        self.content = content
    }
}

public enum DocumentChunker {
    // ~300 tokens ≈ 1200 chars (4 chars/token rule of thumb).
    public static let targetChars = 1200
    public static let overlapChars = 200
    public static let maxChunks = 1000

    /// Chunk a sequence of extracted pages into searchable, embedding-friendly
    /// chunks. Heading-aware first; sliding-window fallback for oversized sections.
    public static func chunk(pages: [DocumentPage]) -> [DocumentChunk] {
        let deboilered = filterBoilerplate(pages: pages)
        let sections = buildSections(from: deboilered)
        var chunks: [DocumentChunk] = []
        var nextIndex = 0
        for section in sections {
            let body = section.bodyLines.joined(separator: " ")
            for content in splitIntoWindows(body) {
                guard chunks.count < maxChunks else { return chunks }
                chunks.append(DocumentChunk(
                    chunkIndex: nextIndex,
                    pageNumber: section.startPage,
                    heading: section.heading,
                    content: content
                ))
                nextIndex += 1
            }
        }
        return chunks
    }

    // MARK: - Running header / footer dedup

    /// Fraction of total pages at which a repeated line is treated as boilerplate.
    static let boilerplateFractionThreshold = 0.4

    /// Filter running headers/footers/page-number lines that repeat across most
    /// pages. Detection is by frequency (no Y-coordinate available): a line on
    /// ≥ `max(3, ceil(0.4 × pageCount))` pages is repeating chrome.
    ///
    /// Heading-shaped boilerplate keeps its FIRST occurrence (the real section
    /// opener); later repeats are dropped so they don't open phantom sections.
    static func filterBoilerplate(pages: [DocumentPage]) -> [DocumentPage] {
        guard pages.count >= 3 else { return pages }
        let threshold = max(
            3,
            Int((Double(pages.count) * boilerplateFractionThreshold).rounded(.up))
        )

        var pageCount: [String: Int] = [:]
        for page in pages {
            var seenOnThisPage: Set<String> = []
            for rawLine in page.text.components(separatedBy: .newlines) {
                let line = rawLine.trimmingCharacters(in: .whitespaces)
                guard !line.isEmpty, !seenOnThisPage.contains(line) else { continue }
                seenOnThisPage.insert(line)
                pageCount[line, default: 0] += 1
            }
        }

        let boilerplate = Set(pageCount.filter { $0.value >= threshold }.map(\.key))
        guard !boilerplate.isEmpty else { return pages }

        var firstHeadingClaimed: Set<String> = []
        var filtered: [DocumentPage] = []
        filtered.reserveCapacity(pages.count)
        for page in pages {
            let kept = page.text.components(separatedBy: .newlines).compactMap { rawLine -> String? in
                let trimmed = rawLine.trimmingCharacters(in: .whitespaces)
                if trimmed.isEmpty { return rawLine }
                guard boilerplate.contains(trimmed) else { return rawLine }
                if parseHeading(trimmed) != nil, !firstHeadingClaimed.contains(trimmed) {
                    firstHeadingClaimed.insert(trimmed)
                    return rawLine
                }
                return nil
            }
            filtered.append(DocumentPage(
                pageNumber: page.pageNumber,
                text: kept.joined(separator: "\n")
            ))
        }
        return filtered
    }

    // MARK: - Heading detection

    /// Matches lines like `3.2 Pressure Differential` / `4.1.2 Cleaning`. Anchored
    /// to the whole trimmed line; each level after the first is a SINGLE digit
    /// (tuned for N.N(.N) SOP numbering — rejects paragraph numbers like `2.11`).
    ///
    /// The title must be LETTER-LED: a real heading reads "3 Cleaning Procedure",
    /// whereas a numbered body/math/list line reads "3 * 10 = 30 miles" — leading
    /// with an operator or digit. Without this, such lines parsed as headings and
    /// leaked "§3 * 10 = 30 miles…" into the rendered source line.
    static func parseHeading(_ trimmedLine: String) -> String? {
        let pattern = #/\s*(\d+(?:\.\d){0,3})\s+(\p{L}.{2,79})/#
        guard let match = try? pattern.wholeMatch(in: trimmedLine) else { return nil }
        return "\(match.1) \(match.2)"
    }

    // MARK: - Sectioning

    struct Section {
        var heading: String?
        var startPage: Int
        var bodyLines: [String]
    }

    static func buildSections(from pages: [DocumentPage]) -> [Section] {
        var sections: [Section] = []
        var current: Section?

        for page in pages {
            for rawLine in page.text.components(separatedBy: .newlines) {
                let line = rawLine.trimmingCharacters(in: .whitespaces)
                guard !line.isEmpty else { continue }
                if isPageNumberLine(line) { continue }

                if let heading = parseHeading(line) {
                    if let open = current, !isEmptySection(open) {
                        sections.append(open)
                    }
                    current = Section(heading: heading, startPage: page.pageNumber, bodyLines: [])
                } else if current != nil {
                    current?.bodyLines.append(line)
                } else {
                    current = Section(heading: nil, startPage: page.pageNumber, bodyLines: [line])
                }
            }
        }
        if let open = current, !isEmptySection(open) {
            sections.append(open)
        }
        return sections
    }

    static func isEmptySection(_ section: Section) -> Bool {
        section.heading == nil && section.bodyLines.isEmpty
    }

    /// True for lines that are exclusively a page-number signal: bare digits,
    /// "Page N", "N of M", or "- N -".
    static func isPageNumberLine(_ trimmedLine: String) -> Bool {
        let collapsed = trimmedLine
            .replacingOccurrences(of: "Page", with: "", options: .caseInsensitive)
            .replacingOccurrences(of: "of", with: "", options: .caseInsensitive)
            .replacingOccurrences(of: "-", with: " ")
            .trimmingCharacters(in: .whitespaces)
        guard !collapsed.isEmpty else { return false }
        let tokens = collapsed.split(separator: " ")
        guard !tokens.isEmpty else { return false }
        return tokens.allSatisfy { token in
            !token.isEmpty && token.allSatisfy(\.isNumber)
        }
    }

    // MARK: - Sliding window

    /// Split a section body into chunks. Single chunk if it fits `targetChars`;
    /// otherwise slide with `overlapChars` overlap, snapped to word boundaries so
    /// no chunk cuts a word in half.
    static func splitIntoWindows(_ text: String) -> [String] {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        if trimmed.count <= targetChars {
            return [trimmed]
        }

        let chars = Array(trimmed)
        var chunks: [String] = []
        var startOffset = 0

        while startOffset < chars.count {
            let remaining = chars.count - startOffset
            if remaining <= targetChars {
                let tail = String(chars[startOffset...]).trimmingCharacters(in: .whitespacesAndNewlines)
                if !tail.isEmpty { chunks.append(tail) }
                break
            }

            var endOffset = startOffset + targetChars
            while endOffset > startOffset, !chars[endOffset - 1].isWhitespace {
                endOffset -= 1
            }
            if endOffset == startOffset {
                endOffset = startOffset + targetChars
            }

            let chunk = String(chars[startOffset ..< endOffset])
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !chunk.isEmpty { chunks.append(chunk) }

            var newStart = max(startOffset + 1, endOffset - overlapChars)
            while newStart > startOffset + 1, !chars[newStart - 1].isWhitespace {
                newStart -= 1
            }
            while newStart < chars.count, chars[newStart].isWhitespace {
                newStart += 1
            }
            if newStart <= startOffset { newStart = endOffset }
            startOffset = newStart
        }
        return chunks
    }
}
