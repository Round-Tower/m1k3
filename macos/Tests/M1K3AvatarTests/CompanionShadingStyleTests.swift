//
//  CompanionShadingStyleTests.swift
//  M1K3AvatarTests
//
//  Pins the style→shader-function map (the names must match Phosphor.metal's
//  [[visible]] entry points), the off→baked contract, and which style needs the
//  original texture (cel). The on-screen look is verify-at-⌘R; the routing is not.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-18, Confidence 0.95 (pure map). Prior: Unknown.
//

@testable import M1K3Avatar
import Testing

struct CompanionShadingStyleTests {
    @Test("off keeps the baked materials — no shader, no texture rebuild")
    func offUsesNoShader() {
        #expect(CompanionShadingStyle.off.shaderFunctionName == nil)
        #expect(CompanionShadingStyle.off.usesShader == false)
        #expect(CompanionShadingStyle.off.usesOriginalTexture == false)
    }

    @Test("each glow style maps to its metallib function name")
    func glowStyleFunctions() {
        #expect(CompanionShadingStyle.phosphor.shaderFunctionName == "phosphorSurface")
        #expect(CompanionShadingStyle.cel.shaderFunctionName == "celSurface")
    }

    @Test("cel adapts the original texture; phosphor does not")
    func textureUse() {
        #expect(CompanionShadingStyle.cel.usesOriginalTexture)
        #expect(CompanionShadingStyle.phosphor.usesOriginalTexture == false)
    }

    @Test("every glow style reports usesShader true")
    func glowStylesUseShader() {
        for style in CompanionShadingStyle.allCases where style != .off {
            #expect(style.usesShader, "\(style.rawValue) should use a shader")
        }
    }

    @Test("rawValue round-trips (the @AppStorage persistence contract)")
    func rawValueRoundTrips() {
        for style in CompanionShadingStyle.allCases {
            #expect(CompanionShadingStyle(rawValue: style.rawValue) == style)
        }
    }

    @Test("every style has a non-empty display name")
    func displayNames() {
        for style in CompanionShadingStyle.allCases {
            #expect(!style.displayName.isEmpty)
        }
    }
}
