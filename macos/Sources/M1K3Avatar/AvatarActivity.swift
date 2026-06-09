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
}
