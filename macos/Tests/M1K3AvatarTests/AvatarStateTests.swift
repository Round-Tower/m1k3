import M1K3Avatar
import Testing

struct AvatarStateTests {
    @Test(".idle factory is neutral+idle")
    func idleFactory() {
        let state = AvatarState.idle
        #expect(state.emotion == .neutral)
        #expect(state.activity == .idle)
    }

    @Test(".error factory is angry+error")
    func errorFactory() {
        let state = AvatarState.error
        #expect(state.emotion == .angry)
        #expect(state.activity == .error)
    }

    @Test("fromActivity(.speaking) → happy+speaking")
    func fromSpeaking() {
        let state = AvatarState.fromActivity(.speaking)
        #expect(state.emotion == .happy)
        #expect(state.activity == .speaking)
    }

    @Test("fromActivity(.error) → angry+error")
    func fromError() {
        let state = AvatarState.fromActivity(.error)
        #expect(state.emotion == .angry)
        #expect(state.activity == .error)
    }

    @Test("fromActivity covers all 6 activities")
    func fromAllActivities() {
        for activity in AvatarActivity.allCases {
            let state = AvatarState.fromActivity(activity)
            #expect(state.activity == activity)
        }
    }

    @Test("Equatable")
    func equatable() {
        #expect(AvatarState.idle == AvatarState.idle)
        #expect(AvatarState.idle != AvatarState.error)
        #expect(
            AvatarState(emotion: .happy, activity: .speaking) ==
                AvatarState(emotion: .happy, activity: .speaking)
        )
    }
}
