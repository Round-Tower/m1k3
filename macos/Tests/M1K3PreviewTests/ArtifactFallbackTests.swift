//
//  ArtifactFallbackTests.swift
//  M1K3PreviewTests
//
//  The artifact preview fails CLOSED: if the content sandbox won't compile, the
//  WebView must NOT load model-generated HTML with only a navigation delegate
//  guarding it. It loads this placeholder instead.
//
//  Why this lives in M1K3Preview (a tested module) rather than as a string
//  literal in the app target: the placeholder was a bare white page with grey
//  text, which is INDISTINGUISHABLE from "the renderer is broken". A user
//  reporting "the artifact pane went white" could mean either, and a failure
//  state that can be mistaken for a bug costs days of hunting. These tests pin
//  the invariant that the failure LOOKS like a deliberate failure.
//

@testable import M1K3Preview
import Testing

struct ArtifactFallbackTests {
    @Test("the seal-failure placeholder carries the house sheet, so it can't read as an unstyled blank page")
    func failureCarriesHouseSheet() {
        let html = ArtifactFallback.sealFailureHTML
        #expect(html.contains(ArtifactHouseStyle.marker))
    }

    @Test("the placeholder names the reason — a user can report it without guessing")
    func failureNamesTheReason() {
        let html = ArtifactFallback.sealFailureHTML
        #expect(html.lowercased().contains("sandbox"))
        // The literal string an operator would grep the logs for.
        #expect(html.contains(ArtifactFallback.diagnosticCode))
    }

    @Test("the placeholder is self-contained — no network, no scripts, nothing the seal would have blocked")
    func failureIsSelfContained() {
        let html = ArtifactFallback.sealFailureHTML
        #expect(!html.contains("http://"))
        #expect(!html.contains("https://"))
        #expect(!html.contains("<script"))
        #expect(!html.contains("url("))
    }

    @Test("the placeholder is substantial enough to be visibly a message, not an empty body")
    func failureIsNotBlank() {
        // A body with only a few characters renders as "almost white page" — the
        // exact ambiguity this type exists to remove.
        let body = ArtifactFallback.sealFailureHTML
        #expect(body.count > 200)
    }
}
