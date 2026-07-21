//
//  PromptMarkerLiveTests.swift
//  M1K3ChatTests
//
//  The anti-drift pin for the prompt-size instrument. `PromptMarker.live` lives
//  in M1K3Eval and duplicates section headers out of M1K3Chat's rendered prompt
//  (M1K3Eval must not depend on M1K3Chat — it's the pure evals enclave). That
//  duplication is only safe if something renders the REAL prompt and checks the
//  markers still bite.
//
//  Without this, a wording pass on groundingBody would silently zero a section
//  in the size report — and a report that quietly stops counting the grounding
//  block is worse than no report, because it reads as "grounding is cheap".
//  Same test-only-dep pattern as the self-query gate's tool-name pin.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-19, Confidence 0.85 (renders the
//  actual AgentRAGResponder.grounding output on both prompt styles; the split
//  is asserted to partition it exactly). Prior: Unknown
//

import Foundation
@testable import M1K3Chat
import M1K3Eval
import M1K3Knowledge
import Testing

struct PromptMarkerLiveTests {
    private func chunk() -> ChunkHit {
        ChunkHit(
            chunkID: UUID(), itemID: UUID(), itemTitle: "Plant Notes", kind: .document,
            heading: "3.2 Seals", content: "The hydraulic seal failed under load."
        )
    }

    private func memory() -> ChunkHit {
        ChunkHit(
            chunkID: UUID(), itemID: UUID(), itemTitle: "Memory", kind: .memory,
            heading: nil, content: "Kev's sister is called Ada."
        )
    }

    private func fullPrompt(style: AgentRAGResponder.PromptStyle) -> String {
        AgentRAGResponder.grounding(
            chunks: [chunk()],
            memories: [memory()],
            toolNames: ["web_search", "search_knowledge"],
            history: [
                ChatTurn(role: .user, text: "hello?"),
                ChatTurn(role: .assistant, text: "Hi there."),
            ],
            style: style
        )
    }

    @Test(
        "every live marker matches the real rendered prompt",
        arguments: [AgentRAGResponder.PromptStyle.react, .native]
    )
    func markersMatchLivePrompt(style: AgentRAGResponder.PromptStyle) {
        let prompt = fullPrompt(style: style)
        for marker in PromptMarker.live {
            // A miss means a wording pass moved the header and the size report
            // would silently stop counting that section.
            #expect(
                prompt.contains(marker.marker),
                Comment(rawValue: "PromptMarker.live \"\(marker.name)\" no longer matches the "
                    + "rendered prompt. Update PromptMarker.live.")
            )
        }
    }

    @Test("the live split partitions the real prompt exactly — no bytes lost")
    func splitPartitionsRealPrompt() {
        let prompt = fullPrompt(style: .native)
        let sections = PromptSectionSplitter.split(prompt, markers: PromptMarker.live)
        #expect(sections.map(\.text).joined() == prompt)
    }

    @Test("the live split names every section of the grounding head")
    func splitNamesAllSections() {
        let prompt = fullPrompt(style: .native)
        let names = Set(PromptSectionSplitter.split(prompt, markers: PromptMarker.live).map(\.name))
        #expect(names == ["history", "knowledge", "memories", "rules"])
    }

    @Test("grounding() opens on the history marker — persona is NOT part of it")
    func groundingCarriesNoPreamble() {
        // Worth pinning because it shapes where the instrument must measure:
        // grounding() is only the head the responder assembles; the persona and
        // tool spec are added further down, at the provider. So a prompt-size
        // measurement that reconstructs from grounding() alone would miss the
        // persona entirely — the size stage intercepts the FULL prompt at the
        // provider seam instead. If this ever fails, grounding() has grown a
        // preamble and the stage's component naming should be revisited.
        let sections = PromptSectionSplitter.split(
            fullPrompt(style: .native), markers: PromptMarker.live
        )
        #expect(sections.first?.name == "history")
    }

    @Test("the knowledge section actually carries the retrieved chunk text")
    func knowledgeSectionCarriesTheChunk() {
        // The whole point of the instrument: this section is the one that is
        // interpolated verbatim and untruncated, so it must be the one whose
        // bytes the report attributes to grounding.
        let prompt = fullPrompt(style: .native)
        let sections = PromptSectionSplitter.split(prompt, markers: PromptMarker.live)
        let knowledge = try? #require(sections.first { $0.name == "knowledge" })
        #expect(knowledge?.text.contains("The hydraulic seal failed under load.") == true)
    }
}
