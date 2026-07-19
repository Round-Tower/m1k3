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
    /// The bare device noun — compose it with any determiner the sentence
    /// needs ("the \(noun)'ll keep", "My \(noun)'s gone properly warm").
    public static let noun: String = {
        #if os(macOS)
            return "Mac"
        #elseif os(visionOS)
            return "Vision Pro"
        #else
            return "device"
        #endif
    }()

    /// "…running entirely on this Mac." — the subject form.
    public static let thisDevice = "this \(noun)"

    /// "…just me and your Mac…" — the possessive form.
    public static let yourDevice = "your \(noun)"
}
