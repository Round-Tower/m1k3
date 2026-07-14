import M1K3Avatar
import Testing

struct AvatarActivityTests {
    @Test("all 6 cases exist")
    func allCases() {
        #expect(AvatarActivity.allCases.count == 6)
    }

    @Test("isActive is false for idle and error")
    func isActiveIdle() {
        #expect(!AvatarActivity.idle.isActive)
        #expect(!AvatarActivity.error.isActive)
    }

    @Test("isActive is true for engaged states")
    func isActiveEngaged() {
        #expect(AvatarActivity.listening.isActive)
        #expect(AvatarActivity.thinking.isActive)
        #expect(AvatarActivity.generating.isActive)
        #expect(AvatarActivity.speaking.isActive)
    }

    @Test("displayName is non-empty for every case")
    func displayNames() {
        for activity in AvatarActivity.allCases {
            #expect(!activity.displayName.isEmpty)
        }
    }

    @Test("accessibilityLabel names M1K3 and the activity, without the ellipsis VoiceOver would read literally")
    func accessibilityLabelIsSpoken() {
        #expect(AvatarActivity.idle.accessibilityLabel == "M1K3 — Idle")
        #expect(AvatarActivity.thinking.accessibilityLabel == "M1K3 — Thinking")
        #expect(AvatarActivity.speaking.accessibilityLabel == "M1K3 — Speaking")
        #expect(AvatarActivity.listening.accessibilityLabel == "M1K3 — Listening")
        #expect(AvatarActivity.generating.accessibilityLabel == "M1K3 — Generating")
        #expect(AvatarActivity.error.accessibilityLabel == "M1K3 — Error")
        for activity in AvatarActivity.allCases {
            #expect(!activity.accessibilityLabel.contains("…"))
        }
    }
}
