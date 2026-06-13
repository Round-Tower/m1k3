//
//  WhisperTranscriptTextTests.swift
//  M1K3WhisperKitTests
//
//  Pins the special-token stripping shared by the live + batch transcribers — the
//  thing that keeps WhisperKit's `<|…|>` tags out of the UI (the live dictation
//  path was yielding them raw).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.9, Prior: Unknown

@testable import M1K3WhisperKit
import Testing

struct WhisperTranscriptTextTests {
    @Test("strips the leading decoder control tokens")
    func stripsControlTokens() {
        let raw = "<|startoftranscript|><|en|><|transcribe|> Hello world"
        #expect(WhisperTranscriptText.stripSpecialTokens(raw) == "Hello world")
    }

    @Test("strips timestamp tokens anywhere in the text")
    func stripsTimestamps() {
        #expect(WhisperTranscriptText.stripSpecialTokens("<|0.00|> tick <|1.20|> tock") == "tick  tock")
    }

    @Test("leaves clean speech untouched")
    func leavesPlainText() {
        #expect(WhisperTranscriptText.stripSpecialTokens("just words") == "just words")
    }

    @Test("a token-only string collapses to empty")
    func tokenOnly() {
        #expect(WhisperTranscriptText.stripSpecialTokens("<|startoftranscript|><|endoftext|>") == "")
    }
}
