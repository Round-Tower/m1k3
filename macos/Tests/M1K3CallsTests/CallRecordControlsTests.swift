//
//  CallRecordControlsTests.swift
//  M1K3CallsTests
//
//  Pins the in-Calls-view record control logic: the start-vs-consent branch (the
//  subtle bit — a first-time tap must ask consent, a pre-authorised tap must not)
//  and the live elapsed-time formatting.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.9, Prior: Unknown
//

@testable import M1K3Calls
import Testing

struct CallRecordActionTests {
    @Test("recording → stop, regardless of pre-authorisation")
    func stopWhileRecording() {
        #expect(CallRecordAction.resolve(isRecording: true, isPreAuthorised: true) == .stop)
        #expect(CallRecordAction.resolve(isRecording: true, isPreAuthorised: false) == .stop)
    }

    @Test("idle + pre-authorised → start immediately (no second consent prompt)")
    func startWhenPreAuthorised() {
        #expect(CallRecordAction.resolve(isRecording: false, isPreAuthorised: true) == .start)
    }

    @Test("idle + not yet consented → ask first")
    func requestConsentFirstTime() {
        #expect(CallRecordAction.resolve(isRecording: false, isPreAuthorised: false) == .requestConsent)
    }
}

struct RecordingClockTests {
    @Test("under a minute shows m:ss with a zero minute")
    func underAMinute() {
        #expect(RecordingClock.label(seconds: 0) == "0:00")
        #expect(RecordingClock.label(seconds: 5) == "0:05")
        #expect(RecordingClock.label(seconds: 59) == "0:59")
    }

    @Test("minutes roll over correctly")
    func minutes() {
        #expect(RecordingClock.label(seconds: 65) == "1:05")
        #expect(RecordingClock.label(seconds: 600) == "10:00")
    }

    @Test("the last second before an hour stays m:ss")
    func justUnderAnHour() {
        #expect(RecordingClock.label(seconds: 3599) == "59:59")
    }

    @Test("past an hour switches to h:mm:ss")
    func hours() {
        #expect(RecordingClock.label(seconds: 3600) == "1:00:00")
        #expect(RecordingClock.label(seconds: 3661) == "1:01:01")
    }

    @Test("negative input clamps to zero")
    func clampsNegative() {
        #expect(RecordingClock.label(seconds: -3) == "0:00")
    }
}
