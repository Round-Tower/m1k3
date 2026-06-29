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
        var result = ""
        var cursor = text.startIndex
        for match in text.matches(of: /```(?s:.*?)```/) {
            result += polishProse(String(text[cursor ..< match.range.lowerBound]))
            result += String(text[match.range]) // code fence, verbatim
            cursor = match.range.upperBound
        }
        result += polishProse(String(text[cursor...]))
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// The flattening pass, applied only to non-code prose.
    private static func polishProse(_ text: String) -> String {
        var output = text
        // [label](url): collapse the duplicate-link artifact, keep real labels.
        output = output.replacing(/\[([^\]]+)\]\(([^)\s]+)\)/) { match in
            let label = String(match.1)
            let url = String(match.2)
            return label == url ? url : "\(label) (\(url))"
        }
        // **bold** → bold (single-asterisk emphasis is left alone — too easy
        // to collide with legitimate asterisks).
        output = output.replacing(/\*\*([^*]+)\*\*/) { String($0.1) }
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
