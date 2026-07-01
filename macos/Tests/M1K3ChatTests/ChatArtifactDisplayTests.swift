//
//  ChatArtifactDisplayTests.swift
//  M1K3ChatTests
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.85, Prior: Unknown

@testable import M1K3Chat
import Testing

struct ChatArtifactDisplayTests {
    @Test("an <artifact> block becomes a titled breadcrumb")
    func breadcrumb() {
        let text = """
        Here's the plan:

        <artifact title="Project Blueprint">
        # Project Blueprint
        - ship it
        </artifact>
        """
        let stripped = ChatArtifactDisplay.stripArtifactTags(text)
        #expect(stripped == "Here's the plan:\n\n📄 Project Blueprint — opened in the panel")
        #expect(!stripped.contains("<artifact"))
        #expect(!stripped.contains("# Project Blueprint"))
    }

    @Test("a titleless artifact gets a generic label")
    func untitled() {
        #expect(ChatArtifactDisplay.stripArtifactTags("<artifact>\n# Doc\n</artifact>")
            == "📄 Document — opened in the panel")
    }

    @Test("text with no artifact tags is unchanged")
    func passthrough() {
        #expect(ChatArtifactDisplay.stripArtifactTags("just a normal answer") == "just a normal answer")
    }

    @Test("polish leaves <artifact> markdown verbatim for the panel to render")
    func polishProtectsArtifact() {
        let text = "Intro\n\n<artifact title=\"Doc\">\n# Heading\n**bold** and *italic*\n</artifact>"
        let polished = MessageTextPolish.polish(text)
        // The markdown inside the artifact must survive untouched (panel renders it).
        #expect(polished.contains("# Heading"))
        #expect(polished.contains("**bold**"))
        #expect(polished.contains("*italic*"))
    }
}
