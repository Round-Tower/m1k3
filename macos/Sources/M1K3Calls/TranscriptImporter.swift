//
//  TranscriptImporter.swift
//  M1K3Calls
//
//  Parse a plain-text transcript ("Speaker: line") into CallTranscriptSegments —
//  the headless, no-mic way to get a real call into M1K3 (drop a transcript, get a
//  summarised, indexed, persisted call). Heuristic speaker detection: a short
//  label before the first colon, with the colon followed by whitespace, counts as
//  a speaker — so "the ratio is 3:1" and "https://x" don't get mis-parsed.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project PlainTextTranscriptParser (Kev, concept).

import Foundation

public enum TranscriptImporter {
    /// Parse transcript text into ordered segments. Timestamps are line-ordinal
    /// (no real audio timing on import) — enough to preserve order for display +
    /// indexing.
    public static func parse(_ text: String) -> [CallTranscriptSegment] {
        var segments: [CallTranscriptSegment] = []
        var ordinal = 0.0
        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            let (speaker, content) = parseLine(line)
            if content.isEmpty { continue }
            segments.append(CallTranscriptSegment(text: content, startTime: ordinal, speaker: speaker))
            ordinal += 1
        }
        return segments
    }

    /// Split a line into (speaker?, text). A speaker prefix is a short, label-like
    /// token before a colon that's followed by whitespace (or end of line).
    static func parseLine(_ line: String) -> (speaker: String?, text: String) {
        guard let colon = line.firstIndex(of: ":") else { return (nil, line) }
        let label = String(line[..<colon]).trimmingCharacters(in: .whitespaces)
        let afterColon = line.index(after: colon)
        let rest = afterColon < line.endIndex ? String(line[afterColon...]) : ""

        let labelLooksRight = (1 ... 30).contains(label.count)
            && (label.first?.isLetter ?? false)
            && label.allSatisfy { $0.isLetter || $0.isNumber || $0 == " " || "._'-".contains($0) }
        let delimiterLooksRight = rest.isEmpty || (rest.first?.isWhitespace ?? false)

        if labelLooksRight, delimiterLooksRight {
            return (label, rest.trimmingCharacters(in: .whitespaces))
        }
        return (nil, line)
    }
}
