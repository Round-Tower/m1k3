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

import Foundation

public enum WhisperTranscriptText {
    /// Remove `<|…|>` special/timestamp tokens and trim surrounding whitespace.
    public static func stripSpecialTokens(_ raw: String) -> String {
        raw.replacingOccurrences(of: "<\\|[^|]*\\|>", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
