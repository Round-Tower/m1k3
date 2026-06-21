//
//  CallRecordControls.swift
//  M1K3Calls
//
//  Pure presentation logic for the in-Calls-view record control. The recording
//  button used to live ONLY in the main window toolbar — so opening the Calls
//  drawer (the obvious home for recording) hid the Stop button behind the sheet.
//  This moves the decision into a tested core the Calls view drives directly:
//  what does tapping the button DO, and what does the live timer read.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.9, Prior: Unknown
//

import Foundation

/// What a tap on the record control should trigger, given the current state.
/// The view maps each case onto the AppEnvironment call (and the consent dialog).
public enum CallRecordAction: Sendable, Equatable {
    /// Mid-recording → stop and hand the audio to the pipeline.
    case stop
    /// Not recording and consent is already in hand → start immediately.
    case start
    /// Not recording and not yet consented → show the consent dialog first.
    case requestConsent

    /// Resolve the action from the two facts the view knows. Stop always wins while
    /// recording; otherwise pre-authorisation decides start-now vs ask-first.
    public static func resolve(isRecording: Bool, isPreAuthorised: Bool) -> CallRecordAction {
        if isRecording { return .stop }
        return isPreAuthorised ? .start : .requestConsent
    }
}

/// Formats an elapsed recording duration for the live "Recording…" badge.
public enum RecordingClock {
    /// `m:ss` under an hour, `h:mm:ss` past it. Negative input clamps to zero so a
    /// clock skew can't render "-0:01".
    public static func label(seconds: Int) -> String {
        let total = max(0, seconds)
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let secs = total % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, secs)
        }
        return String(format: "%d:%02d", minutes, secs)
    }
}
