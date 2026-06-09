//
//  VoiceTier.swift
//  M1K3Voice
//
//  How M1K3 sounds when it speaks — the TTS analogue of BrainTier. Built-in is the
//  instant, zero-download Apple voice; M1K3 Voice is the richer neural voice
//  (Kokoro) that downloads once and then runs fully on-device. Drives the
//  onboarding voice-output card and the Settings picker.
//
//  Pure data: SF Symbol names are plain strings, no SwiftUI dependency, so this
//  stays testable in the package.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.85, Prior: Unknown

public enum VoiceTier: String, CaseIterable, Identifiable, Sendable {
    case builtin
    case m1k3Voice

    public var id: String {
        rawValue
    }

    public var displayName: String {
        switch self {
        case .builtin: "Built-in"
        case .m1k3Voice: "M1K3 Voice"
        }
    }

    public var tagline: String {
        switch self {
        case .builtin: "Ready now · no download"
        case .m1k3Voice: "Neural voice · ~354 MB"
        }
    }

    public var detail: String {
        switch self {
        case .builtin:
            "macOS's built-in speech. Works immediately, always on-device — clear, "
                + "if a little robotic."
        case .m1k3Voice:
            "A warm, natural neural voice that runs entirely on your Mac. One "
                + "download, then offline forever. The voice M1K3 was meant to have."
        }
    }

    public var glyph: String {
        switch self {
        case .builtin: "speaker.wave.2"
        case .m1k3Voice: "speaker.wave.3.fill"
        }
    }

    /// One-time download size, or nil when nothing to download (Built-in).
    public var approxDownloadMB: Int? {
        switch self {
        case .builtin: nil
        case .m1k3Voice: 354
        }
    }

    public var requiresDownload: Bool {
        approxDownloadMB != nil
    }
}
