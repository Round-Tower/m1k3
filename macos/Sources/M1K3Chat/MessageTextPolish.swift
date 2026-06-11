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

import Foundation

public enum MessageTextPolish {
    public static func polish(_ text: String) -> String {
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
        return output.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
