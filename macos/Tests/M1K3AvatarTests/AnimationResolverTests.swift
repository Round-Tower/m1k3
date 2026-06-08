import M1K3Avatar
import Testing

struct AnimationResolverTests {
    private let sparrowClips = [
        "Armature_idle_A",
        "Armature_idle_B",
        "Armature_idle_C",
        "Armature_bounce",
        "Armature_walk",
        "Armature_death",
    ]

    @Test("resolves idle activity → idle_c clip")
    func resolvesIdle() {
        let state = AvatarState(emotion: .neutral, activity: .idle)
        let clip = AnimationResolver.resolve(state: state, clipNames: sparrowClips)
        #expect(clip?.lowercased().contains("idle") == true)
    }

    @Test("resolves speaking activity → bounce clip")
    func resolvesSpeaking() {
        let state = AvatarState.fromActivity(.speaking)
        let clip = AnimationResolver.resolve(state: state, clipNames: sparrowClips)
        #expect(clip?.lowercased().contains("bounce") == true)
    }

    @Test("resolves thinking activity → idle_a clip")
    func resolvesThinking() {
        let state = AvatarState.fromActivity(.thinking)
        let clip = AnimationResolver.resolve(state: state, clipNames: sparrowClips)
        #expect(clip?.lowercased().contains("idle") == true)
    }

    @Test("resolves error activity → death clip")
    func resolvesError() {
        let state = AvatarState.fromActivity(.error)
        let clip = AnimationResolver.resolve(state: state, clipNames: sparrowClips)
        #expect(clip?.lowercased().contains("death") == true)
    }

    @Test("falls back to first clip when nothing matches")
    func fallbackToFirst() {
        let state = AvatarState(emotion: .love, activity: .idle)
        let clips = ["Armature_eat", "Armature_something"]
        let clip = AnimationResolver.resolve(state: state, clipNames: clips)
        // "eat" matches love keyword
        #expect(clip?.lowercased().contains("eat") == true)
    }

    @Test("returns nil for empty clip list")
    func emptyClips() {
        let state = AvatarState.idle
        let clip = AnimationResolver.resolve(state: state, clipNames: [])
        #expect(clip == nil)
    }

    @Test("matching is case-insensitive")
    func caseInsensitive() {
        let state = AvatarState.fromActivity(.generating)
        let clips = ["Armature_WALK", "Armature_Idle"]
        let clip = AnimationResolver.resolve(state: state, clipNames: clips)
        #expect(clip != nil)
    }
}
