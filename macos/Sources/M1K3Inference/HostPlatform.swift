//
//  HostPlatform.swift
//  M1K3Inference
//
//  Platform-honest device nouns for the prompt surface. Mini introduced itself
//  with "I, M1K3, am running directly on this Mac" ON AN IPHONE (caught
//  on-simulator, 2026-07-18) — every persona/tool string that names the machine
//  now interpolates these instead of hardcoding "Mac".
//
//  ⚠️ THE CONSTRAINT: on macOS these MUST stay byte-identical to the literals
//  they replaced ("this Mac" / "your Mac") — gemma is prompt-fragile and the
//  Mac persona is A/B-frozen, so this fix ships ZERO Mac prompt-byte changes
//  (pinned by HostPlatformTests). Compile-time #if — no runtime branching, no
//  UIKit dependency (why iOS says "device", not iPhone/iPad-by-idiom).
//
//  Signed: Kev + claude-fable-5, 2026-07-18, Confidence 0.85 (macOS arms
//  test-pinned byte-identical; mobile arms compile-proven, honesty-by-
//  inspection; live mobile persona feel is verify-on-device). Prior: none.
//

public enum HostPlatform {
    /// "…running entirely on this Mac." — the subject form.
    public static let thisDevice: String = {
        #if os(macOS)
            return "this Mac"
        #elseif os(visionOS)
            return "this Vision Pro"
        #else
            return "this device"
        #endif
    }()

    /// "…just me and your Mac…" — the possessive form.
    public static let yourDevice: String = {
        #if os(macOS)
            return "your Mac"
        #elseif os(visionOS)
            return "your Vision Pro"
        #else
            return "your device"
        #endif
    }()
}
