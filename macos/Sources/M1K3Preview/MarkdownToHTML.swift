//
//  MarkdownToHTML.swift
//  M1K3Preview
//
//  A small, dependency-free markdown → HTML block converter for rendering a
//  captured document artifact in the preview WebView. Deliberately covers the
//  common document blocks a local model emits — headings, paragraphs, bullet and
//  numbered lists, thematic breaks, blockquotes, fenced code, and inline emphasis /
//  code / links — not the full CommonMark grammar (no tables or nested lists; those
//  are a swift-markdown upgrade if we ever need them).
//
//  Security: ALL text is HTML-escaped before any markup is emitted, so a stray tag
//  in model output can never become live markup. The preview WebView also disables
//  JavaScript and applies a CSP — this escaping is the first line, not the only one.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.85 (block converter TDD'd
//  against the common document shapes; renders in the sandboxed no-JS artifact
//  WebView). Prior: Unknown.

import Foundation

public enum MarkdownToHTML {
    /// Render markdown into an HTML body fragment (no `<html>`/`<head>` wrapper —
    /// `ArtifactFormatter` supplies the document shell + CSP + typography).
    public static func render(_ markdown: String) -> String {
        let lines = markdown.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n")
        var blocks: [String] = []
        var paragraph: [String] = []
        var listType: String?
        var index = 0

        func flushParagraph() {
            guard !paragraph.isEmpty else { return }
            blocks.append("<p>\(renderInline(paragraph.joined(separator: " ")))</p>")
            paragraph.removeAll()
        }
        func closeList() {
            if let type = listType { blocks.append("</\(type)>"); listType = nil }
        }

        while index < lines.count {
            let line = lines[index].trimmingCharacters(in: .whitespaces)

            if line.isEmpty {
                flushParagraph(); closeList(); index += 1; continue
            }

            // Fenced code — verbatim + escaped, no inline processing.
            if line.hasPrefix("```") {
                flushParagraph(); closeList()
                index += 1
                var code: [String] = []
                while index < lines.count,
                      !lines[index].trimmingCharacters(in: .whitespaces).hasPrefix("```")
                {
                    code.append(lines[index]); index += 1
                }
                index += 1 // consume the closing fence
                blocks.append("<pre><code>\(escape(code.joined(separator: "\n")))</code></pre>")
                continue
            }

            if isThematicBreak(line) {
                flushParagraph(); closeList(); blocks.append("<hr>"); index += 1; continue
            }

            if let (level, text) = heading(line) {
                flushParagraph(); closeList()
                blocks.append("<h\(level)>\(renderInline(text))</h\(level)>")
                index += 1; continue
            }

            if line.hasPrefix(">") {
                flushParagraph(); closeList()
                var quoted: [String] = []
                while index < lines.count {
                    let quoteLine = lines[index].trimmingCharacters(in: .whitespaces)
                    guard quoteLine.hasPrefix(">") else { break }
                    quoted.append(String(quoteLine.dropFirst()).trimmingCharacters(in: .whitespaces))
                    index += 1
                }
                blocks.append("<blockquote>\(renderInline(quoted.joined(separator: " ")))</blockquote>")
                continue
            }

            if let item = unorderedItem(line) {
                flushParagraph()
                if listType != "ul" { closeList(); blocks.append("<ul>"); listType = "ul" }
                blocks.append("<li>\(renderInline(item))</li>")
                index += 1; continue
            }

            if let item = orderedItem(line) {
                flushParagraph()
                if listType != "ol" { closeList(); blocks.append("<ol>"); listType = "ol" }
                blocks.append("<li>\(renderInline(item))</li>")
                index += 1; continue
            }

            closeList()
            paragraph.append(line)
            index += 1
        }
        flushParagraph(); closeList()
        return blocks.joined(separator: "\n")
    }

    // MARK: - Inline

    /// Escape first, then emit inline markup so a raw tag can't become live HTML.
    /// Order: links → code → bold → italic (bold before italic so `**` isn't eaten
    /// by the single-asterisk pass). A link's URL and a whole inline-code span are
    /// STASHED behind sentinels before the emphasis passes run, so a `*` inside a
    /// URL or code stays literal (the emphasis regexes would otherwise inject
    /// `<em>` into an `href` or code content and corrupt it) — the label text is
    /// left inline so `[**bold**](url)` still emphasises. Sentinels are restored
    /// last, so none survive into the output.
    private static func renderInline(_ text: String) -> String {
        var output = escape(text)
        var stashed: [String] = []
        func stash(_ html: String) -> String {
            defer { stashed.append(html) }
            return "\u{E000}\(stashed.count)\u{E001}"
        }
        output = output.replacing(/\[([^\]]+)\]\(([^)\s]+)\)/) {
            "<a href=\"\(stash(String($0.2)))\">\($0.1)</a>"
        }
        output = output.replacing(/`([^`]+)`/) { stash("<code>\($0.1)</code>") }
        output = output.replacing(/\*\*([^*]+)\*\*/) { "<strong>\($0.1)</strong>" }
        output = output.replacing(/\*([^*\n]+)\*/) { "<em>\($0.1)</em>" }
        for (index, html) in stashed.enumerated() {
            output = output.replacingOccurrences(of: "\u{E000}\(index)\u{E001}", with: html)
        }
        return output
    }

    /// Escape for BOTH element-content and double-quoted attribute-value contexts
    /// (URLs land in `href="…"`), so `"`/`'` are covered, not just `& < >`. `&`
    /// must be replaced first so the entities we introduce aren't double-escaped.
    private static func escape(_ source: String) -> String {
        source
            // Strip the stash sentinels (U+E000/U+E001, renderInline's delimiters)
            // FIRST so raw model-emitted PUA can't impersonate a stash token and
            // splice in another span's HTML — escape() runs before any stashing.
            .replacingOccurrences(of: "\u{E000}", with: "")
            .replacingOccurrences(of: "\u{E001}", with: "")
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&#39;")
    }

    // MARK: - Block predicates

    private static func heading(_ line: String) -> (Int, String)? {
        guard let match = line.firstMatch(of: /^(#{1,6})\s+(.+)$/) else { return nil }
        return (match.1.count, String(match.2).trimmingCharacters(in: .whitespaces))
    }

    private static func isThematicBreak(_ line: String) -> Bool {
        line.wholeMatch(of: /\*{3,}|-{3,}|_{3,}/) != nil
    }

    private static func unorderedItem(_ line: String) -> String? {
        guard let match = line.firstMatch(of: /^[-*+]\s+(.+)$/) else { return nil }
        return String(match.1)
    }

    private static func orderedItem(_ line: String) -> String? {
        guard let match = line.firstMatch(of: /^\d+\.\s+(.+)$/) else { return nil }
        return String(match.1)
    }
}
