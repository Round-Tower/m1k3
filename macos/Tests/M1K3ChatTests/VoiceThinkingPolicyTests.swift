//
//  VoiceThinkingPolicyTests.swift
//  M1K3ChatTests
//
//  Voice mode owns its own brain switch: latency IS the UX in a spoken loop,
//  so the in-mode toggle REPLACES the global Reasoning setting while active —
//  off (default) → fast, on → auto (heuristics still skip small talk).
//  Outside voice mode the toggle is inert and Settings governs.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.9, Prior: Unknown
//

@testable import M1K3Chat
import Testing

struct VoiceThinkingPolicyTests {
    @Test("outside voice mode the stored setting passes through untouched")
    func storedGovernsOutsideVoiceMode() {
        for stored in ThinkingMode.allCases {
            #expect(VoiceThinkingPolicy.effectiveMode(
                stored: stored, voiceModeActive: false, voiceThinkingEnabled: false
            ) == stored)
            // The voice toggle is inert outside voice mode.
            #expect(VoiceThinkingPolicy.effectiveMode(
                stored: stored, voiceModeActive: false, voiceThinkingEnabled: true
            ) == stored)
        }
    }

    @Test("voice mode with thinking off forces fast — even over an explicit Always")
    func voiceModeDefaultIsFast() {
        for stored in ThinkingMode.allCases {
            #expect(VoiceThinkingPolicy.effectiveMode(
                stored: stored, voiceModeActive: true, voiceThinkingEnabled: false
            ) == .fast)
        }
    }

    @Test("voice mode with thinking on yields auto — heuristics decide per turn")
    func voiceModeThinkingOnIsAuto() {
        for stored in ThinkingMode.allCases {
            #expect(VoiceThinkingPolicy.effectiveMode(
                stored: stored, voiceModeActive: true, voiceThinkingEnabled: true
            ) == .auto)
        }
    }
}
