//
//  PhosphorTreatmentTests.swift
//  M1K3AvatarTests
//
//  The companion phosphor skin reacts to M1K3's state. Pins the per-activity
//  mapping and the SIMD4 packing the shader reads — the visible glow is
//  verify-at-⌘R, but the decision is not.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.95 (pure mapping +
//  packing). Prior: Unknown.
//

@testable import M1K3Avatar
import simd
import Testing

struct PhosphorTreatmentTests {
    @Test("customValue packs colour into xyz and fresnel power into w")
    func packing() {
        let treatment = PhosphorTreatment(red: 0.1, green: 0.2, blue: 0.3, fresnelPower: 2.5)
        #expect(treatment.customValue == SIMD4<Float>(0.1, 0.2, 0.3, 2.5))
    }

    @Test("resting + error states wear the calm green default")
    func calmStates() {
        #expect(AvatarActivity.idle.phosphorTreatment == .calm)
        #expect(AvatarActivity.error.phosphorTreatment == .calm)
    }

    @Test("speaking lights up brighter and tighter than rest")
    func speakingBrighter() {
        let speaking = AvatarActivity.speaking.phosphorTreatment
        #expect(speaking == .speaking)
        // brighter (higher green+blue sum) and a tighter rim (lower power) than calm
        #expect(speaking.green + speaking.blue > PhosphorTreatment.calm.green + PhosphorTreatment.calm.blue)
        #expect(speaking.fresnelPower < PhosphorTreatment.calm.fresnelPower)
    }

    @Test("thinking/generating cool toward blue")
    func thinkingCooler() {
        #expect(AvatarActivity.thinking.phosphorTreatment == .thinking)
        #expect(AvatarActivity.generating.phosphorTreatment == .thinking)
        // blue dominates the tint while it works
        #expect(PhosphorTreatment.thinking.blue > PhosphorTreatment.thinking.green)
    }

    @Test("listening is its own attentive treatment")
    func listeningDistinct() {
        #expect(AvatarActivity.listening.phosphorTreatment == .listening)
    }

    @Test("every activity maps to a treatment (total, no crash)")
    func totalMapping() {
        for activity in AvatarActivity.allCases {
            _ = activity.phosphorTreatment
        }
    }
}
