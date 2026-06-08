//
//  AnimationResolver.swift
//  M1K3Avatar
//
//  Fuzzy keyword matching: given a list of clip names from a loaded USDZ and an
//  AvatarState, returns the best-matching clip name (or nil for procedural-only).
//
//  Ported from Android AnimationIntrospector.kt and the TS web-avatar equivalent.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.85,
//  Prior: Kev + claude-opus-4-8 (AnimationIntrospector.kt, app/composeApp)

public enum AnimationResolver {
    /// Activity keywords take priority over emotion keywords (activity = immediate intent).
    private static let activityKeywords: [AvatarActivity: [String]] = [
        .listening: ["idle_c", "idle", "sit"],
        .thinking: ["idle_a", "idle_c", "sit", "think"],
        .generating: ["walk", "run", "bounce"],
        .speaking: ["bounce", "idle_b", "clicked"],
        .error: ["death", "error", "hit", "fear"],
        .idle: ["idle"],
    ]

    private static let emotionKeywords: [AvatarEmotion: [String]] = [
        .happy: ["bounce", "idle_b", "happy", "excited", "jump"],
        .sad: ["sit", "idle_a", "sad", "death", "walk"],
        .angry: ["attack", "angry", "hit", "spin"],
        .surprised: ["hit", "surprised", "bounce", "clicked"],
        .love: ["eat", "love", "sit", "idle_b"],
        .thinking: ["idle_a", "idle_c", "sit"],
        .sleepy: ["sit", "idle_a", "sleepy", "death"],
        .excited: ["bounce", "jump", "excited", "run", "fly"],
        .neutral: ["idle_c", "idle"],
    ]

    /// Returns the best clip name for `state` from `clipNames`, or `nil` when
    /// nothing matches (caller falls back to procedural motion only).
    public static func resolve(state: AvatarState, clipNames: [String]) -> String? {
        guard !clipNames.isEmpty else { return nil }

        if state.activity != .idle {
            if let match = bestMatch(keywords: activityKeywords[state.activity] ?? [], in: clipNames) {
                return match
            }
        }

        if let match = bestMatch(keywords: emotionKeywords[state.emotion] ?? [], in: clipNames) {
            return match
        }

        return clipNames.first { $0.lowercased().contains("idle") } ?? clipNames.first
    }

    private static func bestMatch(keywords: [String], in clipNames: [String]) -> String? {
        for keyword in keywords {
            if let match = clipNames.first(where: { $0.lowercased().contains(keyword.lowercased()) }) {
                return match
            }
        }
        return nil
    }
}
