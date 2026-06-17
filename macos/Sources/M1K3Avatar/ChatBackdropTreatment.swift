//
//  ChatBackdropTreatment.swift
//  M1K3Avatar
//
//  How the full-window avatar BACKGROUND behind the chat should present itself,
//  reactively: it blooms when M1K3 is idle or speaking, and recedes (dims, blurs,
//  scales back, stops moving) while the user is reading a streaming answer or
//  typing — so the conversation always stays legible. This is the PURE decision
//  (unit-tested); the AvatarChatBackground view applies it.
//
//  Same shape as GlyphTreatment: a value type + an AvatarActivity extension, kept
//  dependency-free (plain numbers, no SwiftUI) so it's testable. Two cross-cutting
//  overrides ride on top of the bloom/recede choice:
//   • Reduce Motion freezes the scene's motion (visibility unchanged — it's a
//     motion preference, not a hide).
//   • Low Power forces a dim, static treatment regardless of activity, so a
//     full-bleed RealityKit scene can't cook the battery during MLX inference.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.85 (the mapping is
//  TDD'd; the actual feel — opacities, blur radius — is verify-by-eye at ⌘R).
//  Prior: Unknown.

/// The resolved presentation for the chat's avatar background. Plain numbers so
/// the view maps them to `.opacity` / `.blur` / `.scaleEffect`; `animatesMotion`
/// gates the avatar's idle animation.
public struct ChatBackdropTreatment: Equatable, Sendable {
    /// Layer opacity (0…1).
    public var opacity: Double
    /// Gaussian blur radius in points (0 = sharp).
    public var blur: Double
    /// Scale factor (1.0 = full size).
    public var scale: Double
    /// Whether the avatar's idle animation should run.
    public var animatesMotion: Bool

    public init(opacity: Double, blur: Double, scale: Double, animatesMotion: Bool) {
        self.opacity = opacity
        self.blur = blur
        self.scale = scale
        self.animatesMotion = animatesMotion
    }

    /// Full presence: front-and-centre, sharp, alive.
    public static let bloom = ChatBackdropTreatment(opacity: 1.0, blur: 0, scale: 1.0, animatesMotion: true)
    /// Deferential: dim + blurred + scaled back + still, so streamed text reads cleanly over it.
    public static let receded = ChatBackdropTreatment(opacity: 0.35, blur: 8, scale: 0.97, animatesMotion: false)
    /// Battery-saver: present but dim and frozen, whatever M1K3 is doing.
    public static let still = ChatBackdropTreatment(opacity: 0.45, blur: 4, scale: 1.0, animatesMotion: false)

    /// Resolve the treatment for the current moment.
    /// - reduceMotion freezes motion but keeps the bloom/recede visibility.
    /// - lowPower wins outright (dim + static) so the full-bleed scene stays cheap.
    public static func resolve(
        activity: AvatarActivity,
        isTyping: Bool,
        reduceMotion: Bool,
        lowPower: Bool
    ) -> ChatBackdropTreatment {
        if lowPower { return still }
        // "Reading" = an answer is being produced; the user's attention is on text.
        let reading = activity == .thinking || activity == .generating
        var treatment = (reading || isTyping) ? receded : bloom
        if reduceMotion { treatment.animatesMotion = false }
        return treatment
    }
}

public extension AvatarActivity {
    /// Convenience: the chat-background treatment for this activity.
    func chatBackdropTreatment(
        isTyping: Bool = false,
        reduceMotion: Bool = false,
        lowPower: Bool = false
    ) -> ChatBackdropTreatment {
        ChatBackdropTreatment.resolve(
            activity: self, isTyping: isTyping, reduceMotion: reduceMotion, lowPower: lowPower
        )
    }
}
