//
//  HostPlatformTests.swift
//  M1K3InferenceTests
//
//  Pins the platform-honest device nouns the prompt surface interpolates.
//  THE LOAD-BEARING PIN: on macOS both nouns must be byte-identical to the
//  literals they replaced ("this Mac" / "your Mac") — gemma is prompt-fragile
//  and the Mac persona is A/B-frozen; this fix must change ZERO Mac prompt
//  bytes. The iOS/visionOS arms are compile-time (#if os) and can't execute
//  under this suite — their honesty is pinned by inspection + the mobile
//  compile; the macOS byte-stability is what CI enforces.
//
//  Signed: Kev + claude-fable-5, 2026-07-18, Confidence 0.85 (macOS arms
//  pinned exactly; non-Mac arms compile-checked by the mobile-build job,
//  not executable here). Prior: none (new file).
//

@testable import M1K3Inference
import Testing

struct HostPlatformTests {
    @Test("macOS keeps the exact frozen prompt nouns — 'this Mac' / 'your Mac'")
    func macOSNounsAreByteIdentical() {
        #if os(macOS)
            #expect(HostPlatform.thisDevice == "this Mac")
            #expect(HostPlatform.yourDevice == "your Mac")
        #endif
    }

    @Test("the nouns are lowercase noun phrases — safe mid-sentence")
    func nounsCompseMidSentence() {
        #expect(HostPlatform.thisDevice.hasPrefix("this "))
        #expect(HostPlatform.yourDevice.hasPrefix("your "))
        #expect(!HostPlatform.thisDevice.contains("\n"))
        #expect(!HostPlatform.yourDevice.contains("\n"))
    }
}
