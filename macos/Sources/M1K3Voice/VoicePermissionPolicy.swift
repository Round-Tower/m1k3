//
//  VoicePermissionPolicy.swift
//  M1K3Voice
//
//  The silent-denial fix. A denied mic/speech grant used to just finish the
//  transcript stream empty (AppleSpeechTranscriber.begin guards and bails) —
//  voice mode looked like M1K3 ignoring you, with no path back. This policy
//  decides, from synchronously-readable auth states, whether to show the
//  recovery card and which System Settings pane it opens. Pure so the whole
//  table tests without TCC; the app maps the real
//  `SFSpeechRecognizer.authorizationStatus()` / `AVCaptureDevice
//  .authorizationStatus(for: .audio)` values into `AuthState`.
//
//  Two layers, both here:
//    · pre-flight — before starting a listen; deterministic, no waiting on an
//      empty stream. `.notDetermined` is NOT a recovery case: the system
//      dialog must fire naturally mid-gesture (the contextual ask).
//    · backstop — after a listen produced zero segments; catches mid-session
//      revocation and the notDetermined-then-denied path. A quiet room (zero
//      segments, nothing blocked) is not a denial.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.9. Prior: none (new file).

import Foundation

public enum VoicePermissionPolicy {
    /// Platform-neutral auth state — map SFSpeechRecognizer / AVCaptureDevice
    /// statuses into this at the call site.
    public enum AuthState: Sendable, Equatable {
        case notDetermined
        case authorized
        case denied
        case restricted

        var isBlocked: Bool {
            self == .denied || self == .restricted
        }
    }

    /// Which recovery card to show — and which System Settings pane fixes it.
    public enum Recovery: Sendable, Equatable {
        case microphone
        case speechRecognition

        public var settingsPaneURL: String {
            switch self {
            case .microphone:
                "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone"
            case .speechRecognition:
                "x-apple.systempreferences:com.apple.preference.security?Privacy_SpeechRecognition"
            }
        }
    }

    /// Before starting a listen. Mic outranks speech when both are blocked —
    /// no mic means nothing downstream can work.
    public static func preflightRecovery(speechAuth: AuthState, micAuth: AuthState) -> Recovery? {
        if micAuth.isBlocked { return .microphone }
        if speechAuth.isBlocked { return .speechRecognition }
        return nil
    }

    /// After a listen finished. Only a zero-segment listen WITH a blocked auth
    /// state reads as a denial — this is exactly the today-silent case made loud.
    public static func backstopRecovery(
        speechAuth: AuthState,
        micAuth: AuthState,
        sawSegments: Bool
    ) -> Recovery? {
        guard !sawSegments else { return nil }
        return preflightRecovery(speechAuth: speechAuth, micAuth: micAuth)
    }
}
