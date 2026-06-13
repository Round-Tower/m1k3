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

    @Test("routes real-time questions to web search instead of refusing")
    func realTimeUsesWebSearch() {
        // The ⌘R weather bug: "Yo Mike what's the weather" read as casual, and
        // the persona's "no searching" + "lives entirely on this Mac" made the
        // model refuse with "I don't have real-time data". The persona must
        // carve out current-world questions and point them at web search.
        let prompt = M1K3Persona.systemPrompt.lowercased()
        #expect(prompt.contains("web search"))
        #expect(prompt.contains("weather") || prompt.contains("news") || prompt.contains("right now"))
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

    @Test("injects the current month + year to keep the model honest about time")
    func currentDate() throws {
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 6
        comps.day = 13
        let june = try #require(Calendar(identifier: .gregorian).date(from: comps))
        #expect(M1K3Persona.currentDateLine(june) == "Today's date is June 2026.")
        // The live prompt carries the real current year so the model can't drift.
        let year = Calendar.current.component(.year, from: Date())
        #expect(M1K3Persona.systemPrompt.contains("\(year)"))
    }

    @Test("stays short — the persona is prefilled on every turn (worst-case month)")
    func staysShort() throws {
        // The prompt now carries an injected date line; September is the longest
        // month name, so pin the budget against the worst case (the live getter
        // uses today's real date, which is never longer).
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 9
        comps.day = 15
        let september = try #require(Calendar(identifier: .gregorian).date(from: comps))
        let worst = M1K3Persona.compose(
            core: M1K3Persona.corePrompt + "\n" + M1K3Persona.currentDateLine(september),
            profile: nil
        )
        #expect(worst.count < 850)
    }

    @Test("voice exemplars are three short beats in M1K3's voice")
    func voiceExemplars() {
        let exemplars = M1K3Persona.voiceExemplars
        #expect(exemplars.components(separatedBy: "USER:").count - 1 == 3)
        #expect(exemplars.components(separatedBy: "M1K3:").count - 1 == 3)
        #expect(exemplars.contains("honey")) // the curious-fact beat
        #expect(exemplars.contains("cod you")) // the honest-abstention beat, in voice
        #expect(exemplars.contains("?")) // ends beats with a question back
    }

    @Test("the exemplar prompt = core + exemplars, within the cached-path budget")
    func exemplarPromptComposition() {
        let full = M1K3Persona.systemPrompt(includeExemplars: true)
        #expect(full.hasPrefix(M1K3Persona.systemPrompt))
        #expect(full.contains("USER:"))
        #expect(full.count < 1500)

        let compact = M1K3Persona.systemPrompt(includeExemplars: false)
        #expect(compact == M1K3Persona.systemPrompt)
    }

    @Test("a user profile composes in as an About-the-user block")
    func profileComposes() {
        let composed = M1K3Persona.compose(core: "CORE", profile: "Kev — dyslexic, prefers brief answers.")
        #expect(composed.hasPrefix("CORE"))
        #expect(composed.contains("About the user: Kev — dyslexic"))

        #expect(M1K3Persona.compose(core: "CORE", profile: nil) == "CORE")
        #expect(M1K3Persona.compose(core: "CORE", profile: "   ") == "CORE")
    }

    @Test("profiles are hard-capped — they ride the system turn every launch")
    func profileCap() {
        let long = String(repeating: "fact ", count: 200)
        let composed = M1K3Persona.compose(core: "CORE", profile: long)
        #expect(composed.count < 500 + M1K3Persona.profileCharacterCap)
        #expect(composed.contains("…"))
    }
}
