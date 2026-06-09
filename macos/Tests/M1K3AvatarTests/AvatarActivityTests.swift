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
}
