//
//  ToolEmotionMapper.swift
//  M1K3Avatar
//
//  Maps a tool ID + success flag → the emotion M1K3 should show after the tool runs.
//  Ported from Android ToolEmotionMap.kt (app/composeApp/.../avatar/ToolEmotionMap.kt).
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.85,
//  Prior: Kev + claude-opus-4-8 (ToolEmotionMap.kt, app/composeApp)

public enum ToolEmotionMapper {
    private static let toolEmotions: [String: AvatarEmotion] = [
        // Knowledge tools
        "search_knowledge": .thinking,
        "list_documents": .thinking,
        "get_document": .thinking,
        // Web / network
        "web_search": .thinking,
        // Utility
        "get_current_time": .neutral,
        "get_battery_level": .neutral,
        // Timed actions — pleasing when they succeed
        "set_timer": .excited,
        "set_alarm": .excited,
        // Health / personal
        "get_health": .love,
    ]

    /// Resolve the emotion for a completed tool call.
    /// - Returns: `.sad` on failure (always); resolved or `.happy` on success.
    public static func emotionFor(toolID: String, success: Bool) -> AvatarEmotion {
        guard success else { return .sad }
        return toolEmotions[toolID] ?? .happy
    }
}
