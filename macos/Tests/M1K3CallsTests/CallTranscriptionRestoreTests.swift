//
//  CallTranscriptionRestoreTests.swift
//  M1K3CallsTests
//
//  Pins the launch-restore decision for call transcription: reload the model only
//  when the user enabled it before AND it's already on disk — so a recorded call
//  transcribes every session (Kev's "the setting turns itself off" bug) without
//  ever forcing a silent re-download.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.9, Prior: Unknown
//

@testable import M1K3Calls
import Testing

struct CallTranscriptionRestoreTests {
    @Test("restores only when enabled before AND the model is downloaded")
    func restoresWhenEnabledAndDownloaded() {
        #expect(CallTranscriptionRestore.shouldRestore(wasEnabled: true, modelDownloaded: true))
    }

    @Test("enabled but no model on disk → don't (would force a silent re-download)")
    func skipsWhenModelMissing() {
        #expect(!CallTranscriptionRestore.shouldRestore(wasEnabled: true, modelDownloaded: false))
    }

    @Test("model present but never enabled → don't load an unwanted transcriber")
    func skipsWhenNotEnabled() {
        #expect(!CallTranscriptionRestore.shouldRestore(wasEnabled: false, modelDownloaded: true))
    }

    @Test("neither → don't")
    func skipsWhenNeither() {
        #expect(!CallTranscriptionRestore.shouldRestore(wasEnabled: false, modelDownloaded: false))
    }
}
