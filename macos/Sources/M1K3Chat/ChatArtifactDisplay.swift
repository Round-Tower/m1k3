//
//  ChatArtifactDisplay.swift
//  M1K3Chat
//
//  When the model marks a document with <artifact …>…</artifact>, the document is
//  captured and rendered in the review panel — not the transcript. This replaces the
//  raw tag block in the chat bubble with a one-line breadcrumb so the answer stays
//  readable and the document has a single home (the panel).
//
//  Ordering matters: artifact DETECTION reads the raw text before this runs, so the
//  tags must still be present at detection time. This is a display-only rewrite,
//  applied to the finished assistant message after the panel has been opened.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.85, Prior: Unknown

import Foundation

public enum ChatArtifactDisplay {
    /// Replace every <artifact …>…</artifact> block with a "📄 Title — opened in the
    /// panel" breadcrumb. No tags → returned unchanged (trimmed). The title comes from
    /// a `title="…"` attribute when present, else a generic label.
    public static func stripArtifactTags(_ text: String) -> String {
        let block = /<artifact([^>]*)>(?s:.*?)<\/artifact>/.ignoresCase()
        guard text.firstMatch(of: block) != nil else { return text }

        var result = text.replacing(block) { match in
            let title = String(match.1)
                .firstMatch(of: /title\s*=\s*"([^"]*)"/.ignoresCase())
                .map { String($0.1).trimmingCharacters(in: .whitespaces) }
            let name = (title?.isEmpty == false ? title! : "Document")
            return "📄 \(name) — opened in the panel"
        }
        // Tidy the gaps the removal leaves.
        result = result.replacing(/\n{3,}/) { _ in "\n\n" }
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
