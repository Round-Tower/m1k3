import M1K3Avatar
import Testing

/// CompanionSpec + ClipMapper: the pure layer that turns an AvatarState into a
/// named animation clip for a 3D companion, across the two model dialects
/// (Quaternius pack vs the Khronos Fox). RealityKit playback is verify-by-launch;
/// this maths carries the tests.
struct CompanionTests {
    // MARK: - Gait resolution (dialect-independent)

    @Test("excited shows the reaction gait regardless of activity")
    func excitedReacts() {
        for activity in AvatarActivity.allCases {
            let state = AvatarState(emotion: .excited, activity: activity)
            #expect(ClipMapper.gait(for: state) == .react)
        }
    }

    @Test("error activity and distressed emotions show the distress gait")
    func distressGait() {
        #expect(ClipMapper.gait(for: .error) == .distress)
        #expect(ClipMapper.gait(for: AvatarState(emotion: .angry, activity: .idle)) == .distress)
        #expect(ClipMapper.gait(for: AvatarState(emotion: .sad, activity: .idle)) == .distress)
    }

    @Test("calm activities map to rest, engaged ones to alert/move")
    func activityGaits() {
        #expect(ClipMapper.gait(for: .idle) == .rest)
        #expect(ClipMapper.gait(for: AvatarState(emotion: .neutral, activity: .listening)) == .alert)
        #expect(ClipMapper.gait(for: AvatarState(emotion: .thinking, activity: .thinking)) == .alert)
        #expect(ClipMapper.gait(for: AvatarState(emotion: .excited, activity: .generating)) == .react) // excited wins
        #expect(ClipMapper.gait(for: AvatarState(emotion: .happy, activity: .generating)) == .move)
        #expect(ClipMapper.gait(for: AvatarState(emotion: .happy, activity: .speaking)) == .move)
    }

    // MARK: - Dialect clip names

    @Test("Quaternius dialect names every gait with a pack clip")
    func quaterniusClipNames() {
        #expect(CompanionDialect.quaternius.clipName(for: .rest) == "Idle_A")
        #expect(CompanionDialect.quaternius.clipName(for: .alert) == "Idle_B")
        #expect(CompanionDialect.quaternius.clipName(for: .move) == "Walk")
        #expect(CompanionDialect.quaternius.clipName(for: .react) == "Jump")
        #expect(CompanionDialect.quaternius.clipName(for: .distress) == "Fear")
    }

    @Test("Fox dialect collapses missing gaits onto its 3 clips")
    func foxClipNames() {
        #expect(CompanionDialect.fox.clipName(for: .rest) == "Survey")
        #expect(CompanionDialect.fox.clipName(for: .alert) == "Survey") // no distinct alert clip
        #expect(CompanionDialect.fox.clipName(for: .move) == "Walk")
        #expect(CompanionDialect.fox.clipName(for: .react) == "Run")
        #expect(CompanionDialect.fox.clipName(for: .distress) == "Run")
    }

    // MARK: - The load-bearing invariant

    @Test("every spec ships every clip its mapper can ever emit (no missing-clip crash)")
    func everyEmittedClipIsBundled() {
        for spec in CompanionSpec.all {
            for gait in CompanionGait.allCases {
                let clip = spec.dialect.clipName(for: gait)
                #expect(spec.clips.contains(clip), "\(spec.id) is missing emitted clip \(clip)")
            }
        }
    }

    @Test("the idle clip a spec advertises is the one it rests on")
    func idleClipIsRestClip() {
        for spec in CompanionSpec.all {
            #expect(spec.idleClip == spec.dialect.clipName(for: .rest))
            #expect(spec.clips.contains(spec.idleClip))
        }
    }

    // MARK: - Spec catalogue

    @Test("Fox is the v1 ship companion, with the fox dialect")
    func foxSpec() {
        let fox = CompanionSpec.fox
        #expect(fox.id == "Fox")
        #expect(fox.dialect == .fox)
        #expect(fox.clips == ["Survey", "Walk", "Run"])
        #expect(fox.scale > 0)
    }

    @Test("clip(for:) resolves through gait → dialect in one hop")
    func endToEndClip() {
        #expect(ClipMapper.clip(for: .idle, dialect: .fox) == "Survey")
        #expect(ClipMapper.clip(for: .error, dialect: .quaternius) == "Fear")
        #expect(ClipMapper.clip(for: AvatarState(emotion: .excited, activity: .speaking), dialect: .quaternius) == "Jump")
    }

    // MARK: - Selection resolution (picker ↔ persisted id)

    @Test("named resolves a bundled id, and treats empty/unknown as the pixel face")
    func namedResolution() {
        #expect(CompanionSpec.named("Fox") == .fox)
        #expect(CompanionSpec.named("Gecko") == .gecko)
        #expect(CompanionSpec.named("") == nil) // the pixel-face sentinel
        #expect(CompanionSpec.named("pixel") == nil)
        #expect(CompanionSpec.named("Wolpertinger") == nil)
    }

    @Test("shipped companions report installed; their whole clip set is bundled")
    func installedReflectsBundle() {
        for spec in [CompanionSpec.fox, .gecko] {
            #expect(CompanionAssets.isInstalled(spec))
            // Every declared clip is actually on disk (so no gait silently dead-ends).
            #expect(CompanionAssets.clipURLs(for: spec).count == spec.clips.count, "\(spec.id) missing clips")
        }
    }

    // MARK: - Crossfade

    @Test("crossfade duration is positive and snappier for reactions")
    func crossfadeDurations() {
        #expect(ClipMapper.crossfadeDuration(to: .rest) > 0)
        #expect(ClipMapper.crossfadeDuration(to: .react) < ClipMapper.crossfadeDuration(to: .rest))
        for gait in CompanionGait.allCases {
            let d = ClipMapper.crossfadeDuration(to: gait)
            #expect(d > 0)
            #expect(d <= 1.0)
        }
    }
}
