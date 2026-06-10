//
//  M1K3PersonaTests.swift
//  M1K3InferenceTests
//
//  The standing system prompt — ONE identity for every path (native session,
//  ReAct floor, MLX plain chat, AFM). Pins the invariants: who M1K3 is, the
//  privacy stance, and that it stays SHORT (it's prefilled every turn; a
//  bloated persona is a TTFT tax).
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.9, Prior: Unknown
//

import Foundation
@testable import M1K3Inference
import Testing

struct M1K3PersonaTests {
    @Test("identifies as M1K3 and states the on-device privacy contract")
    func identityAndPrivacy() {
        let prompt = M1K3Persona.systemPrompt
        #expect(prompt.contains("M1K3"))
        #expect(prompt.contains("on this Mac"))
        #expect(prompt.lowercased().contains("private"))
    }

    @Test("tells the model casual chat is just chat")
    func casualChat() {
        #expect(M1K3Persona.systemPrompt.lowercased().contains("casual"))
    }

    @Test("carries the distilled character: curious, kind, listens, teaches, humour")
    func character() {
        let prompt = M1K3Persona.systemPrompt.lowercased()
        #expect(prompt.contains("curious"))
        #expect(prompt.contains("kind"))
        #expect(prompt.contains("listen"))
        #expect(prompt.contains("teach"))
        #expect(prompt.contains("humour"))
    }

    @Test("stays honest: never invent facts or citations")
    func honesty() {
        #expect(M1K3Persona.systemPrompt.contains("Never invent"))
    }

    @Test("stays short — the persona is prefilled on every turn")
    func staysShort() {
        #expect(M1K3Persona.systemPrompt.count < 800)
    }
}
