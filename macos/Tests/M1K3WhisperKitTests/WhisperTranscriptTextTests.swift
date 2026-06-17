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

    // MARK: - clean (downstream: dictation + voice-first)

    @Test("a lone [BLANK_AUDIO] marker cleans to empty")
    func blankAudioOnly() {
        // The bug: a silent live listen yielded "[BLANK_AUDIO]" as a turn.
        #expect(WhisperTranscriptText.clean("[BLANK_AUDIO]") == "")
    }

    @Test("non-speech markers are case-insensitive and tolerate inner spaces")
    func markerVariants() {
        #expect(WhisperTranscriptText.clean("[blank_audio]") == "")
        #expect(WhisperTranscriptText.clean("[ Silence ]") == "")
        #expect(WhisperTranscriptText.clean("[Music]") == "")
        #expect(WhisperTranscriptText.clean("[NOISE]") == "")
    }

    @Test("a marker embedded in speech is removed but the words survive")
    func markerEmbedded() {
        #expect(WhisperTranscriptText.clean("[BLANK_AUDIO] so what time is it") == "so what time is it")
        #expect(WhisperTranscriptText.clean("hello [BLANK_AUDIO] world") == "hello world")
    }

    @Test("known parenthetical non-speech annotations are stripped")
    func parentheticalNonSpeech() {
        #expect(WhisperTranscriptText.clean("(applause)") == "")
        #expect(WhisperTranscriptText.clean("(speaking in foreign language)") == "")
        // `coughs?` in the vocabulary makes the trailing `s` optional.
        #expect(WhisperTranscriptText.clean("(cough)") == "")
        #expect(WhisperTranscriptText.clean("(coughs)") == "")
    }

    @Test("back-to-back markers collapse to empty")
    func consecutiveMarkers() {
        // A silent clip can emit several markers in a row — all must vanish.
        #expect(WhisperTranscriptText.clean("[BLANK_AUDIO] [Music]") == "")
    }

    @Test("a known parenthetical embedded in speech is removed but the words survive")
    func parentheticalEmbedded() {
        // Symmetry with markerEmbedded: `(applause)` mid-sentence vanishes, words stay.
        // The strip leaves a double space ("thanks  everyone") which clean() collapses.
        #expect(WhisperTranscriptText.clean("thanks (applause) everyone") == "thanks everyone")
    }

    @Test("real parenthetical speech is preserved")
    func parentheticalSpeechKept() {
        // Only the known non-speech vocabulary is stripped; genuine asides stay.
        #expect(WhisperTranscriptText.clean("the answer (maybe) is yes") == "the answer (maybe) is yes")
    }

    @Test("clean also strips control tokens, then collapses whitespace")
    func cleanStripsTokensToo() {
        let raw = "<|startoftranscript|><|transcribe|> [BLANK_AUDIO] hello   there"
        #expect(WhisperTranscriptText.clean(raw) == "hello there")
    }

    @Test("clean leaves ordinary speech untouched")
    func cleanLeavesPlainText() {
        #expect(WhisperTranscriptText.clean("just words") == "just words")
    }
}
