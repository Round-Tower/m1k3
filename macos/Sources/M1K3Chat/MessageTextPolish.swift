//
//  MessageTextPolish.swift
//  M1K3Chat
//
//  Models emit markdown; ReadingText renders plain text (it owns typesetting —
//  dyslexia/bionic modes work on words, not markup). This flattens the markdown
//  models actually produce into readable plain text and tidies whitespace.
//  Citation tokens ([Title §heading], no following parenthesis) are not links
//  and pass through untouched.
//
//  Runs once on the FINAL message text (ChatSession rewrites it at completion
//  after citation validation), so streaming shows raw tokens for a moment and
//  then settles clean.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//  Review: Kev + claude-opus-4-8, 2026-06-29, Confidence 0.85 — fenced code
//  blocks are now preserved VERBATIM. Flattening ran over generated code too,
//  eating `#`-comment / heading lines and stripping backticks and **bold** —
//  mangled code reads as "won't generate code properly". Only the prose between
//  fences is flattened for ReadingText now. (Limitation: UNfenced code can't be
//  told from prose, so it's still flattened — models reliably fence code blocks,
//  and the app surfaces the first fence in a code webview regardless.)

import Foundation

public enum MessageTextPolish {
    /// Flatten markdown prose for plain-text rendering while leaving fenced code
    /// blocks (```…```) untouched. Splits on fences, polishes only the prose
    /// segments, then trims the joined result once.
    public static func polish(_ text: String) -> String {
        // Regions left byte-for-byte: fenced code blocks AND <artifact>…</artifact>
        // document blocks — the markdown inside an artifact must survive verbatim so
        // the review panel can render it (flattening would strip its structure first).
        var ranges: [Range<String.Index>] = []
        for match in text.matches(of: /```(?s:.*?)```/) {
            ranges.append(match.range)
        }
        for match in text.matches(of: /<artifact(?:[^>]*)>(?s:.*?)<\/artifact>/.ignoresCase()) {
            ranges.append(match.range)
        }
        ranges.sort { $0.lowerBound < $1.lowerBound }

        var result = ""
        var cursor = text.startIndex
        for range in ranges {
            // Skip a region nested in one already emitted (e.g. a fence inside an
            // <artifact> — the artifact span already covers it).
            guard range.lowerBound >= cursor else { continue }
            result += polishProse(String(text[cursor ..< range.lowerBound]))
            result += String(text[range]) // verbatim
            cursor = range.upperBound
        }
        result += polishProse(String(text[cursor...]))
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// The flattening pass, applied only to non-code prose.
    private static func polishProse(_ text: String) -> String {
        var output = text
        // Thematic breaks (*** / --- / ___ alone on a line) are document structure,
        // not prose — drop the line before the emphasis passes run (a bare *** would
        // otherwise be mis-read as an unterminated italic). The newline-collapse
        // below tidies the gap it leaves.
        output = output.replacing(/^[ \t]*[-*_]{3,}[ \t]*$/.anchorsMatchLineEndings()) { _ in "" }
        // [label](url): collapse the duplicate-link artifact, keep real labels.
        output = output.replacing(/\[([^\]]+)\]\(([^)\s]+)\)/) { match in
            let label = String(match.1)
            let url = String(match.2)
            return label == url ? url : "\(label) (\(url))"
        }
        // **bold** → bold. Runs first so ***bold-italic*** lands as *bold-italic*,
        // which the italic pass below then finishes.
        output = output.replacing(/\*\*([^*]+)\*\*/) { String($0.1) }
        // *italic* → italic. Only a properly-paired *word* where the content touches
        // both asterisks — so arithmetic ("2 * 3") and the "* " bullet marker (space
        // after the star) are left untouched. Group 1 is the preserved leading
        // boundary (start-of-line / space / opening bracket); the trailing boundary
        // is a zero-width lookahead so it isn't consumed.
        output = output.replacing(
            /(^|[\s(\[])\*(\S(?:[^*\n]*\S)?|\S)\*(?=$|[\s).,;:!?\]])/.anchorsMatchLineEndings()
        ) { "\($0.1)\($0.2)" }
        // `code` → code
        output = output.replacing(/`([^`\n]+)`/) { String($0.1) }
        // Line-leading "* " bullets → real bullets.
        output = output.replacing(/^\s{0,3}\*\s+/.anchorsMatchLineEndings()) { _ in "• " }
        // Heading markers vanish, the heading text stays.
        output = output.replacing(/^#{1,6}\s+/.anchorsMatchLineEndings()) { _ in "" }
        // Newline pile-ups (the "Web sources" gap) collapse to one blank line.
        output = output.replacing(/\n{3,}/) { _ in "\n\n" }
        return output // the whole result is trimmed once in polish()
    }
}
