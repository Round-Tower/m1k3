//
//  AgentLogTests.swift
//  M1K3AgentTests
//
//  LogPreview keeps diagnostic log lines readable: one line, bounded length,
//  no prompt walls. The logging calls themselves are observability (not
//  behavior) — only the pure formatter is pinned.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Agent
import Testing

struct LogPreviewTests {
    @Test("short text passes through untouched")
    func shortText() {
        #expect(LogPreview.preview("hello world") == "hello world")
    }

    @Test("newlines and runs of whitespace collapse to single spaces")
    func collapsesWhitespace() {
        #expect(LogPreview.preview("line one\n\nline   two\t end") == "line one line two end")
    }

    @Test("long text truncates at the cap with an ellipsis")
    func truncates() {
        let long = String(repeating: "abcde ", count: 60)
        let preview = LogPreview.preview(long)
        #expect(preview.count <= 121) // 120 + ellipsis
        #expect(preview.hasSuffix("…"))
    }

    @Test("custom cap is honoured")
    func customCap() {
        let preview = LogPreview.preview("a very long sentence indeed", max: 6)
        #expect(preview == "a very…")
    }
}
