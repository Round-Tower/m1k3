//
//  AvatarController.swift
//  M1K3Avatar
//
//  Observable state owner for the avatar companion panel. Driven by AppEnvironment
//  at every meaningful transition (dictation → thinking → generating → speaking).
//  Pure: no RealityKit, no UI dep — safe to `swift test`.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.9, Prior: Unknown

import Observation

@MainActor
@Observable
public final class AvatarController {
    public private(set) var state: AvatarState = .idle

    public init() {}

    public func setActivity(_ activity: AvatarActivity) {
        state = AvatarState.fromActivity(activity)
    }

    public func setEmotion(_ emotion: AvatarEmotion) {
        state = AvatarState(emotion: emotion, activity: state.activity)
    }

    public func resetToIdle() {
        state = .idle
    }
}
