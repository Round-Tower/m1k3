//
//  ParagraphSplitter.swift
//  M1K3Chat
//
//  Paragraph spans of a string: display text per paragraph plus the UTF-16
//  range each occupies in the original. The karaoke reading view renders one
//  Text per paragraph and rebases global word ranges through these spans, so
//  the offsets here must agree exactly with NSString/UTF-16 arithmetic —
//  pinned by tests (an error here silently kills the word highlight).
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (pure, test-pinned;
//  extracted from the view layer on tdd-enforcer review). Prior: Unknown.
//

import Foundation

public enum ParagraphSplitter {
    public struct Paragraph: Equatable, Sendable {
        public let text: String
        /// UTF-16 offsets of `text` within the original string.
        public let range: Range<Int>

        public init(text: String, range: Range<Int>) {
            self.text = text
            self.range = range
        }
    }

    public static func split(_ text: String) -> [Paragraph] {
        var paragraphs: [Paragraph] = []
        var offset = 0
        var startOffset: Int?
        var current = ""
        for character in text {
            if character == "\n" {
                if let start = startOffset, !current.isEmpty {
                    paragraphs.append(Paragraph(text: current, range: start ..< offset))
                }
                startOffset = nil
                current = ""
            } else {
                if startOffset == nil { startOffset = offset }
                current.append(character)
            }
            offset += character.utf16.count
        }
        if let start = startOffset, !current.isEmpty {
            paragraphs.append(Paragraph(text: current, range: start ..< offset))
        }
        return paragraphs
    }
}
