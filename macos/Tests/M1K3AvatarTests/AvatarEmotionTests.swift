import M1K3Avatar
import Testing

struct AvatarEmotionTests {
    @Test("all 9 cases have distinct raw values")
    func allCases() {
        let rawValues = AvatarEmotion.allCases.map(\.rawValue)
        #expect(rawValues.count == 9)
        #expect(Set(rawValues).count == 9)
    }

    @Test("from(_:) maps known strings")
    func fromKnownStrings() {
        #expect(AvatarEmotion.from("happy") == .happy)
        #expect(AvatarEmotion.from("HAPPY") == .happy)
        #expect(AvatarEmotion.from("thinking") == .thinking)
    }

    @Test("from(_:) unknown string → neutral")
    func fromUnknown() {
        #expect(AvatarEmotion.from("banana") == .neutral)
        #expect(AvatarEmotion.from("") == .neutral)
    }

    // accentColor moved to the M1K3App SwiftUI extension (AvatarEmotion+SwiftUI.swift)
    // to keep this package UI-free; its switch is exhaustive at compile time.
}
