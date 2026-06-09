//
//  AvatarState.swift
//  M1K3Avatar
//
//  Composite snapshot of what M1K3 looks like right now.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.9, Prior: Unknown

public struct AvatarState: Equatable, Sendable {
    public var emotion: AvatarEmotion
    public var activity: AvatarActivity

    public init(emotion: AvatarEmotion = .neutral, activity: AvatarActivity = .idle) {
        self.emotion = emotion
        self.activity = activity
    }

    public static let idle = AvatarState()
    public static let error = AvatarState(emotion: .angry, activity: .error)

    /// Derive a natural state from the current activity (emotion follows the task).
    public static func fromActivity(_ activity: AvatarActivity) -> AvatarState {
        let emotion: AvatarEmotion
        switch activity {
        case .idle: emotion = .neutral
        case .listening: emotion = .thinking
        case .thinking: emotion = .thinking
        case .generating: emotion = .excited
        case .speaking: emotion = .happy
        case .error: emotion = .angry
        }
        return AvatarState(emotion: emotion, activity: activity)
    }
}
