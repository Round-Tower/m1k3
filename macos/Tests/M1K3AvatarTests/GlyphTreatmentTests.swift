//
//  GlyphTreatmentTests.swift
//  M1K3AvatarTests
//
//  Pins the menu-bar glyph's status mapping: each activity → its dot, and the
//  recording override winning over activity. The animation is verify-by-launch;
//  this is the contract the label view renders against.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.85, Prior: Unknown

@testable import M1K3Avatar
import Testing

struct GlyphTreatmentTests {
    @Test("idle is the calm bare glyph")
    func idleCalm() {
        #expect(AvatarActivity.idle.glyphTreatment() == .calm)
        #expect(AvatarActivity.idle.glyphTreatment().dot == .none)
    }

    @Test("thinking and generating pulse an accent dot")
    func busyPulses() {
        for activity in [AvatarActivity.thinking, .generating] {
            let treatment = activity.glyphTreatment()
            #expect(treatment.dot == .pulsing)
            #expect(treatment.dotColorName == "accent")
            #expect(treatment.pulses)
        }
    }

    @Test("speaking shows a soft glow, not a pulsing dot")
    func speakingGlows() {
        let treatment = AvatarActivity.speaking.glyphTreatment()
        #expect(treatment.dot == .glow)
        #expect(!treatment.pulses)
    }

    @Test("recording wins over any activity with a red dot")
    func recordingOverrides() {
        for activity in AvatarActivity.allCases {
            let treatment = activity.glyphTreatment(isRecording: true)
            #expect(treatment.dot == .recording, "\(activity) while recording")
            #expect(treatment.dotColorName == "red")
        }
    }

    @Test("error surfaces a red dot when not recording")
    func errorIsRed() {
        let treatment = AvatarActivity.error.glyphTreatment()
        #expect(treatment.dotColorName == "red")
    }

    @Test("every activity maps to a defined treatment (total)")
    func totalMapping() {
        for activity in AvatarActivity.allCases {
            _ = activity.glyphTreatment()
            _ = activity.glyphTreatment(isRecording: true)
        }
    }
}
