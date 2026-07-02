//
//  DocumentRenderer.swift
//  M1K3Knowledge
//
//  The one "render a stored document as text" policy, shared by the MCP
//  get_document handler and the agent's GetDocumentTool: heading-aware body,
//  an explicit note for chunkless (title-only) items, and windowed paging
//  with a resume-offset footer so a multi-megabyte item can't flood a
//  caller's context (test-report F5, the firehose) — and is never silently
//  truncated either (the agent tool's old bare-ellipsis cut).
//
//  Signed: Kev + claude-fable-5, 2026-07-02, Confidence 0.9 (rendering moved
//  verbatim from KnowledgeMCPTools.getDocument, whose tests pin it; the agent
//  tool now pages instead of truncating). Prior: Kev + claude-opus-4-8
//  (KnowledgeMCPTools.getDocument).
//

import Foundation

/// Renders a stored knowledge item as paged text.
public enum DocumentRenderer {
    /// Default character window per call. Generous enough for a whole short
    /// paper, bounded so one item can't flood a context window in one shot.
    public static let defaultMaxChars = 6000

    public static func render(
        title: String,
        kind: KnowledgeKind,
        chunks: [KnowledgeChunk],
        maxChars: Int = defaultMaxChars,
        offset: Int = 0
    ) -> String {
        let header = "# \(title)  [\(kind.rawValue)]"
        let body: String = chunks.map { chunk -> String in
            let heading: String = chunk.heading.map { "## \($0)\n" } ?? ""
            return "\(heading)\(chunk.content)"
        }.joined(separator: "\n\n")
        guard !body.isEmpty else {
            return "\(header)\n\n(No readable text — this item was indexed by title only, "
                + "with no extractable body content. Nothing to return.)"
        }

        let total = body.count
        let window = max(1, maxChars)
        let start = max(0, min(offset, total))
        let slice = String(body.dropFirst(start).prefix(window))
        let end = start + slice.count
        var out = "\(header)\n\n\(slice)"
        if end < total {
            out += "\n\n[… \(total - end) more characters. "
                + "Call get_document again with offset:\(end) to continue.]"
        } else if start > 0 {
            out += "\n\n[— end of document —]"
        }
        return out
    }
}
