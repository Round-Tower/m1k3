//
//  CallTranscriptionRestore.swift
//  M1K3Calls
//
//  Whether to reload the batch call-transcription model on launch. Live dictation
//  already restores its WhisperKit upgrade on launch (persisted flag + model on
//  disk); call transcription did NOT — so it reverted to OFF every session and
//  recorded calls parked silently until the user re-enabled it in Settings. This
//  is the same restore decision, made testable: only reload when the user enabled
//  it before AND the (shared) model is already on disk — never trigger a silent
//  re-download.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.9, Prior: Unknown
//

public enum CallTranscriptionRestore {
    /// Reload on launch only if the user enabled call transcription previously and
    /// the model is already downloaded. Both must hold: `wasEnabled` without the
    /// model would force a silent re-download; the model present without intent
    /// shouldn't load a transcriber the user never asked for.
    public static func shouldRestore(wasEnabled: Bool, modelDownloaded: Bool) -> Bool {
        wasEnabled && modelDownloaded
    }
}
