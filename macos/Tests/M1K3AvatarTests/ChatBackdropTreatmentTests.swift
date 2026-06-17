//
//  ChatBackdropTreatmentTests.swift
//  M1K3AvatarTests
//
//  Pins the reactive avatar-background mapping: bloom when idle/speaking/listening,
//  recede while reading a streamed answer or typing, and the Reduce-Motion /
//  Low-Power overrides. The animation feel is verify-by-launch; this is the
//  contract the AvatarChatBackground view renders against.
//

@testable import M1K3Avatar
import Testing

struct ChatBackdropTreatmentTests {
    @Test("idle / speaking / listening bloom when not typing")
    func bloomsWhenIdleOrSpeaking() {
        for activity in [AvatarActivity.idle, .speaking, .listening] {
            #expect(activity.chatBackdropTreatment() == .bloom)
        }
    }

    @Test("thinking and generating recede — the read-the-answer phase")
    func recedesWhileReading() {
        for activity in [AvatarActivity.thinking, .generating] {
            #expect(activity.chatBackdropTreatment() == .receded)
        }
    }

    @Test("typing recedes even when M1K3 is idle")
    func recedesWhileTyping() {
        #expect(AvatarActivity.idle.chatBackdropTreatment(isTyping: true) == .receded)
        #expect(AvatarActivity.speaking.chatBackdropTreatment(isTyping: true) == .receded)
    }

    @Test("recede is dimmer, blurred, scaled back, and still")
    func recededShape() {
        let receded = ChatBackdropTreatment.receded
        #expect(receded.opacity < ChatBackdropTreatment.bloom.opacity)
        #expect(receded.blur > 0)
        #expect(receded.scale < 1.0)
        #expect(receded.animatesMotion == false)
    }

    @Test("Reduce Motion freezes motion but keeps the bloom visibility")
    func reduceMotionFreezesButKeepsVisibility() {
        let treatment = AvatarActivity.idle.chatBackdropTreatment(reduceMotion: true)
        #expect(treatment.animatesMotion == false)
        #expect(treatment.opacity == ChatBackdropTreatment.bloom.opacity) // still fully present
        #expect(treatment.blur == ChatBackdropTreatment.bloom.blur)
    }

    @Test("Low Power forces the dim, static treatment regardless of activity")
    func lowPowerWinsOutright() {
        for activity in AvatarActivity.allCases {
            let treatment = activity.chatBackdropTreatment(lowPower: true)
            #expect(treatment == .still)
            #expect(treatment.animatesMotion == false)
            #expect(treatment.opacity < ChatBackdropTreatment.bloom.opacity)
        }
    }
}
