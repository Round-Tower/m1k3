//
//  ConversationTitlerTests.swift
//  M1K3ChatTests
//
//  TitlePrompt and TitleSanitizer are pure; ProviderConversationTitler is a
//  thin adapter pinned with a fake provider. Small local models return messy
//  strings — quotes, "Title:" prefixes, trailing periods, whole paragraphs —
//  so the sanitizer carries the real behaviour.
//

import M1K3Chat
import M1K3Inference
import Testing

struct TitleSanitizerTests {
    @Test("trims whitespace, strips wrapping quotes and a Title: prefix")
    func basicCleanup() {
        #expect(TitleSanitizer.sanitize("  \"Conveyor seal failure\"  ") == "Conveyor seal failure")
        #expect(TitleSanitizer.sanitize("Title: Weather in Cork") == "Weather in Cork")
        #expect(TitleSanitizer.sanitize("'Plant maintenance'") == "Plant maintenance")
        #expect(TitleSanitizer.sanitize("`Code review notes`") == "Code review notes")
    }

    @Test("strips trailing sentence punctuation and collapses internal whitespace")
    func punctuationAndWhitespace() {
        #expect(TitleSanitizer.sanitize("Weather in Cork.") == "Weather in Cork")
        #expect(TitleSanitizer.sanitize("Big   news\ttoday!") == "Big news today")
    }

    @Test("takes only the first non-empty line of a rambling answer")
    func firstLine() {
        #expect(TitleSanitizer.sanitize("\n\nSeal failure\nHere is why I chose this title…") == "Seal failure")
    }

    @Test("caps at 60 characters on a word boundary")
    func capAtWordBoundary() throws {
        let long = "An extremely detailed conversation about the hydraulic conveyor seal replacement schedule"
        let title = TitleSanitizer.sanitize(long)
        #expect(title != nil)
        #expect(try #require(title?.count) <= 60)
        #expect(try !#require(title?.hasSuffix(" ")))
        // Cut lands between words, not mid-word.
        #expect(try long.hasPrefix(#require(title)))
    }

    @Test("garbage input returns nil")
    func garbage() {
        #expect(TitleSanitizer.sanitize("") == nil)
        #expect(TitleSanitizer.sanitize("   \n  ") == nil)
        #expect(TitleSanitizer.sanitize("\"\"") == nil)
        #expect(TitleSanitizer.sanitize("...") == nil)
    }
}

struct TitlePromptTests {
    @Test("the prompt carries both texts and the word-count instruction")
    func promptShape() {
        let prompt = TitlePrompt.build(user: "what's the weather in Cork?", assistant: "Cloudy, 15°C.")
        #expect(prompt.contains("what's the weather in Cork?"))
        #expect(prompt.contains("Cloudy, 15°C."))
        #expect(prompt.contains("3-6 word"))
    }

    @Test("long turns are truncated to keep the titling prompt cheap")
    func truncation() {
        let longUser = String(repeating: "a", count: 1000)
        let prompt = TitlePrompt.build(user: longUser, assistant: "ok")
        #expect(!prompt.contains(String(repeating: "a", count: 401)))
    }
}

private struct CannedProvider: InferenceProvider {
    let canned: String
    var name: String {
        "canned"
    }

    var isAvailable: Bool {
        true
    }

    func generate(prompt _: String) async throws -> String {
        canned
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { $0.finish() }
    }
}

struct ProviderConversationTitlerTests {
    @Test("the adapter returns the provider's output for the built prompt")
    func adapter() async throws {
        let titler = ProviderConversationTitler(provider: CannedProvider(canned: "\"Cork weather\""))
        let raw = try await titler.title(forUser: "weather?", assistant: "15°C")
        #expect(raw == "\"Cork weather\"")
    }
}
