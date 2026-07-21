//
//  PromptSectionSplitterTests.swift
//  M1K3EvalTests
//
//  Red-first for the breakdown half of the prompt-size instrument. Knowing a
//  prompt costs 5000 tokens doesn't tell you WHICH ingest path spent them —
//  the split is the answer to "where do the tokens go".
//
//  Signed: Kev + claude-opus-4-8, 2026-07-19, Confidence 0.8 (pure string
//  slicing; the markers it keys on are asserted against the live prompt in a
//  pin test so a reworded header fails loudly instead of silently reporting a
//  missing section). Prior: Unknown
//

@testable import M1K3Eval
import Testing

struct PromptSectionSplitterTests {
    private let markers = [
        PromptMarker(name: "history", marker: "CONVERSATION SO FAR"),
        PromptMarker(name: "knowledge", marker: "KNOWLEDGE ("),
        PromptMarker(name: "memories", marker: "WHAT I KNOW ABOUT YOU"),
        PromptMarker(name: "rules", marker: "RULES"),
    ]

    @Test("splits an assembled prompt into sections in the order they appear")
    func splitsInAppearanceOrder() {
        let prompt = """
        You are M1K3.
        CONVERSATION SO FAR (context):
        user: hi
        KNOWLEDGE (the user's own documents):
        1. [doc] a fact
        RULES
        - be brief
        """
        let sections = PromptSectionSplitter.split(prompt, markers: markers)
        #expect(sections.map(\.name) == ["preamble", "history", "knowledge", "rules"])
        #expect(sections[0].text.contains("You are M1K3."))
        #expect(sections[1].text.contains("user: hi"))
        #expect(sections[2].text.contains("a fact"))
        #expect(sections[3].text.contains("be brief"))
    }

    @Test("a marker that isn't present yields NO section — not an empty one")
    func absentMarkerIsOmitted() {
        // An empty section would report 0 tokens, reading as "measured and
        // free". Absent must stay absent so the report shows nothing at all.
        let prompt = "You are M1K3.\nRULES\n- be brief"
        let sections = PromptSectionSplitter.split(prompt, markers: markers)
        #expect(sections.map(\.name) == ["preamble", "rules"])
        #expect(!sections.contains { $0.name == "knowledge" })
    }

    @Test("sections partition the prompt exactly — no byte lost, none double-counted")
    func partitionsExactly() {
        // The invariant that makes the totals trustworthy: concatenating the
        // sections must reproduce the prompt byte-for-byte.
        let prompt = """
        preamble text
        CONVERSATION SO FAR (context):
        replay
        KNOWLEDGE (docs):
        chunk
        RULES
        rule
        """
        let sections = PromptSectionSplitter.split(prompt, markers: markers)
        #expect(sections.map(\.text).joined() == prompt)
    }

    @Test("no preamble section when the prompt opens on a marker")
    func noEmptyPreamble() {
        let prompt = "RULES\n- be brief"
        let sections = PromptSectionSplitter.split(prompt, markers: markers)
        #expect(sections.map(\.name) == ["rules"])
    }

    @Test("markers declared in render order split a prompt that follows it")
    func renderOrderSplits() {
        let prompt = """
        KNOWLEDGE (docs):
        chunk
        WHAT I KNOW ABOUT YOU (remembered):
        a fact
        """
        let sections = PromptSectionSplitter.split(prompt, markers: markers)
        #expect(sections.map(\.name) == ["knowledge", "memories"])
    }

    @Test("a chunk quoting a later header does NOT steal that section")
    func chunkTextCannotStealAMarker() {
        // THE case sequential scanning exists for. KNOWLEDGE carries the user's
        // own document text verbatim, so a chunk about "the RULES of chess"
        // must not open the RULES section early and hand the rest of the
        // grounding bytes to the wrong component — that would under-report the
        // exact block this instrument watches.
        let prompt = """
        KNOWLEDGE (docs):
        the RULES of chess
        RULES
        - be brief
        """
        let sections = PromptSectionSplitter.split(prompt, markers: markers)
        #expect(sections.map(\.name) == ["knowledge", "rules"])
        #expect(sections[0].text.contains("the RULES of chess"))
        #expect(sections[1].text.contains("be brief"))
    }

    @Test("a marker appearing BEFORE its declared predecessor is skipped — the ordering contract")
    func outOfOrderMarkerIsSkipped() {
        // The documented cost of sequential scanning: markers are consumed in
        // declaration order, so one appearing earlier than its predecessor is
        // invisible. Pinned so the contract is a decision, not a surprise —
        // PromptMarker.live must stay in render order.
        let prompt = """
        RULES
        - be brief
        KNOWLEDGE (docs):
        chunk
        """
        let sections = PromptSectionSplitter.split(prompt, markers: markers)
        #expect(sections.map(\.name) == ["preamble", "knowledge"])
        #expect(sections[1].text.contains("chunk"))
    }

    @Test("an empty prompt yields no sections")
    func emptyPrompt() {
        #expect(PromptSectionSplitter.split("", markers: markers).isEmpty)
    }

    @Test("no markers means the whole prompt is preamble")
    func noMarkers() {
        let sections = PromptSectionSplitter.split("just text", markers: [])
        #expect(sections.map(\.name) == ["preamble"])
        #expect(sections[0].text == "just text")
    }
}
