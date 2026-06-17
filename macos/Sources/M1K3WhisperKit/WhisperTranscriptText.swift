//
//  WhisperTranscriptText.swift
//  M1K3WhisperKit
//
//  Cleaning for WhisperKit's raw decoder output, shared by the live and batch
//  transcribers. WhisperKit emits special + timestamp tokens inline
//  (`<|startoftranscript|>`, `<|en|>`, `<|transcribe|>`, `<|0.00|>`) unless told to
//  skip them — and even with skipSpecialTokens the running stream text can briefly
//  carry them. This is the belt-and-braces strip that keeps `<|…|>` out of the UI.
//  The batch path always cleaned its segments here; the LIVE provider didn't, which
//  is why the tags leaked into dictation.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.9, Prior: Unknown
//  (extracted from WhisperKitBatchTranscriber.clean so both transcribers share it).
//  Review: claude-opus-4-8, 2026-06-17 — added `clean`, which also drops WhisperKit's
//  non-speech annotations ([BLANK_AUDIO], [Music], (applause)…). The live dictation
//  path stripped only `<|…|>` tokens, so a silent listen yielded "[BLANK_AUDIO]" as a
//  turn → voice-first sent it to the model ("interesting conversations"). Confidence 0.9.

import Foundation

public enum WhisperTranscriptText {
    /// Remove `<|…|>` special/timestamp tokens and trim surrounding whitespace.
    public static func stripSpecialTokens(_ raw: String) -> String {
        raw.replacingOccurrences(of: "<\\|[^|]*\\|>", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Full clean for text headed downstream — live dictation, voice-first, the UI.
    /// Strips `<|…|>` control tokens AND WhisperKit's non-speech annotations
    /// (`[BLANK_AUDIO]`, `[Music]`, `[ Silence ]`, `(applause)`…) wherever they land,
    /// then collapses the whitespace they leave behind. A clip that was *only* a
    /// non-speech marker comes back empty — which is what lets voice-first treat a
    /// silent listen as silence rather than send "[BLANK_AUDIO]" to the model as a turn.
    ///
    /// Square-bracket markers are stripped wholesale: a person speaking cannot utter
    /// "[…]", so in live STT every `[…]` is a Whisper artifact. Parentheticals are
    /// stripped only for the known non-speech vocabulary, because real speech *can*
    /// carry a parenthetical aside.
    ///
    /// Only call this on raw WhisperKit decoder output — the blanket `[…]` rule
    /// assumes every square-bracket span is a Whisper annotation, which is NOT true
    /// of arbitrary text (LLM output, user-composed messages, citations like `[1]`).
    public static func clean(_ raw: String) -> String {
        var text = stripSpecialTokens(raw)
        text = text.replacingOccurrences(
            of: "\\[[^\\[\\]]*\\]", with: " ", options: .regularExpression
        )
        text = text.replacingOccurrences(
            of: "\\((?:\(nonSpeechVocabulary))\\)", with: " ",
            options: [.regularExpression, .caseInsensitive]
        )
        return text
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Words WhisperKit wraps in parentheses for non-speech events. Kept narrow so
    /// genuine spoken asides ("the answer (maybe) is yes") survive. NOTE: entries are
    /// interpolated verbatim into the outer regex, so they MAY carry regex
    /// metacharacters on purpose (`coughs?` matches cough/coughs). Escape any literal
    /// `.`/`(`/`+`/`*` you add, or the alternation will silently misbehave.
    private static let nonSpeechVocabulary = [
        "blank_audio", "silence", "music", "noise", "applause", "laughter",
        "inaudible", "coughs?", "sighs?", "speaking in foreign language",
        "speaking foreign language", "foreign",
    ].joined(separator: "|")
}
