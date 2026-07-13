//
//  AvatarActivity.swift
//  M1K3Avatar
//
//  What M1K3 is currently doing — drives animation tier selection and procedural motion.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.9, Prior: Unknown

public enum AvatarActivity: String, CaseIterable, Sendable {
    case idle
    case listening
    case thinking
    case generating
    case speaking
    case error

    /// True when M1K3 is actively engaged — drives faster procedural animation.
    public var isActive: Bool {
        switch self {
        case .idle, .error: false
        default: true
        }
    }

    public var displayName: String {
        switch self {
        case .idle: "Idle"
        case .listening: "Listening…"
        case .thinking: "Thinking…"
        case .generating: "Generating…"
        case .speaking: "Speaking…"
        case .error: "Error"
        }
    }

    /// A VoiceOver-friendly phrase naming M1K3 and what it's doing — same cases as
    /// `displayName`, but without the trailing ellipsis (VoiceOver reads "…" as
    /// literal dots, not "in progress"). Composed once here so every surface that
    /// speaks the avatar's state (the hero panel, voice mode) says the same thing.
    public var accessibilityLabel: String {
        let state: String
        switch self {
        case .idle: state = "Idle"
        case .listening: state = "Listening"
        case .thinking: state = "Thinking"
        case .generating: state = "Generating"
        case .speaking: state = "Speaking"
        case .error: state = "Error"
        }
        return "M1K3 — \(state)"
    }
}
