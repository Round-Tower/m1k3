//
//  VoicePermissionPolicyTests.swift
//  M1K3VoiceTests
//
//  The silent-denial fix, as a pure table. Today a denied mic/speech grant just
//  finishes the transcript stream empty — voice mode looks like M1K3 ignoring
//  you. The policy decides, from synchronously-readable auth states, whether to
//  show the recovery card and WHICH System Settings pane to open. No TCC needed
//  in tests — that's the point of the seam.
//
//  Rules with teeth:
//    · .notDetermined is NOT a recovery case — the system dialog must fire
//      naturally mid-gesture (contextual ask), never our card pre-empting it
//    · mic outranks speech when both are blocked (no mic = nothing works)
//    · the backstop only fires when a listen produced ZERO segments AND an
//      auth state is actually blocked — a quiet room is not a denial.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.9. Prior: none (new file).

@testable import M1K3Voice
import Testing

struct VoicePermissionPolicyTests {
    typealias Auth = VoicePermissionPolicy.AuthState

    // MARK: - Pre-flight (before starting a listen)

    @Test("all authorized → no recovery, start the listen")
    func authorizedIsClear() {
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .authorized, micAuth: .authorized) == nil)
    }

    @Test("notDetermined → nil: let the system ask mid-gesture, never pre-empt")
    func notDeterminedDefersToSystem() {
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .notDetermined, micAuth: .notDetermined) == nil)
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .authorized, micAuth: .notDetermined) == nil)
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .notDetermined, micAuth: .authorized) == nil)
    }

    @Test("mic denied or restricted → microphone recovery")
    func micBlocked() {
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .authorized, micAuth: .denied) == .microphone)
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .authorized, micAuth: .restricted) == .microphone)
    }

    @Test("speech denied with mic fine → speech-recognition recovery")
    func speechBlocked() {
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .denied, micAuth: .authorized) == .speechRecognition)
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .restricted, micAuth: .authorized) == .speechRecognition)
    }

    @Test("both blocked → microphone wins (no mic = nothing works)")
    func micOutranksSpeech() {
        #expect(VoicePermissionPolicy.preflightRecovery(speechAuth: .denied, micAuth: .denied) == .microphone)
    }

    // MARK: - Backstop (a listen finished; covers mid-session revocation and

    // the notDetermined-then-denied path the pre-flight can't see)

    @Test("segments arrived → never a recovery, whatever the states read")
    func segmentsMeanWorking() {
        #expect(VoicePermissionPolicy.backstopRecovery(speechAuth: .denied, micAuth: .denied, sawSegments: true) == nil)
    }

    @Test("zero segments + blocked auth → recovery (the today-silent case made loud)")
    func silentDenialCaught() {
        #expect(VoicePermissionPolicy.backstopRecovery(speechAuth: .denied, micAuth: .authorized, sawSegments: false) == .speechRecognition)
        #expect(VoicePermissionPolicy.backstopRecovery(speechAuth: .authorized, micAuth: .denied, sawSegments: false) == .microphone)
    }

    @Test("zero segments but nothing blocked → nil: a quiet room is not a denial")
    func quietRoomIsNotDenial() {
        #expect(VoicePermissionPolicy.backstopRecovery(speechAuth: .authorized, micAuth: .authorized, sawSegments: false) == nil)
        #expect(VoicePermissionPolicy.backstopRecovery(speechAuth: .notDetermined, micAuth: .notDetermined, sawSegments: false) == nil)
    }

    // MARK: - Settings pane deep links

    @Test("each recovery names its exact System Settings pane")
    func settingsPanes() {
        #expect(VoicePermissionPolicy.Recovery.microphone.settingsPaneURL
            == "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone")
        #expect(VoicePermissionPolicy.Recovery.speechRecognition.settingsPaneURL
            == "x-apple.systempreferences:com.apple.preference.security?Privacy_SpeechRecognition")
    }
}
