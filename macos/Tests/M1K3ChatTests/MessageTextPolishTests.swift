//
//  MessageTextPolishTests.swift
//  M1K3ChatTests
//
//  Models emit markdown; ReadingText renders plain text (it owns the
//  typesetting — dyslexia/bionic modes). Polish flattens the markdown the
//  models actually produce (seen at ⌘R: **bold**, [url](url) duplicate
//  links) and tidies whitespace, WITHOUT touching citation tokens.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Chat
import Testing

struct MessageTextPolishTests {
    @Test("bold and emphasis markers are flattened")
    func flattensBold() {
        #expect(MessageTextPolish.polish("**AccuWeather:** sunny") == "AccuWeather: sunny")
        #expect(MessageTextPolish.polish("the **10-day** forecast") == "the 10-day forecast")
    }

    @Test("single-asterisk italic is flattened, but arithmetic is left alone")
    func flattensItalic() {
        #expect(MessageTextPolish.polish("*(Defines who Claude is)*") == "(Defines who Claude is)")
        #expect(MessageTextPolish.polish("the *quick* brown fox") == "the quick brown fox")
        // The reason single-asterisk was historically left alone: don't eat maths.
        #expect(MessageTextPolish.polish("area = 2 * 3 * 4") == "area = 2 * 3 * 4")
        #expect(MessageTextPolish.polish("a * b + c") == "a * b + c")
    }

    @Test("bold-italic (***) collapses to plain text")
    func flattensBoldItalic() {
        #expect(MessageTextPolish.polish("***Project Blueprint***") == "Project Blueprint")
    }

    @Test("a thematic-break line (*** / --- / ___) is dropped, prose around it kept")
    func dropsThematicBreak() {
        #expect(MessageTextPolish.polish("Intro\n\n***\n\nBody") == "Intro\n\nBody")
        #expect(MessageTextPolish.polish("Intro\n\n---\n\nBody") == "Intro\n\nBody")
    }

    @Test("a link whose label IS the url collapses to the url (the ⌘R artifact)")
    func collapsesDuplicateLink() {
        let text = "[https://weather.com/boston](https://weather.com/boston)"
        #expect(MessageTextPolish.polish(text) == "https://weather.com/boston")
    }

    @Test("a labelled link keeps both label and url")
    func labelledLink() {
        #expect(MessageTextPolish.polish("[AccuWeather](https://accuweather.com/boston)")
            == "AccuWeather (https://accuweather.com/boston)")
    }

    @Test("citation tokens are NOT links and survive untouched")
    func preservesCitations() {
        let text = "Clean the line (ICH-Q7 §5.2 Cleaning) and [Plant Notes §3.2 Seals] says so."
        #expect(MessageTextPolish.polish(text) == text)
    }

    @Test("asterisk bullets become real bullets")
    func bullets() {
        #expect(MessageTextPolish.polish("* first\n*   second") == "• first\n• second")
    }

    @Test("inline code backticks and heading markers are stripped")
    func codeAndHeadings() {
        #expect(MessageTextPolish.polish("run `swift test` now") == "run swift test now")
        #expect(MessageTextPolish.polish("## Forecast\ndetails") == "Forecast\ndetails")
    }

    @Test("newline pile-ups collapse and ends are trimmed (the Web sources gap)")
    func whitespaceTidy() {
        let text = "Check these.\n\n\n\n\n\nWeb sources:\n• https://a\n\n"
        #expect(MessageTextPolish.polish(text) == "Check these.\n\nWeb sources:\n• https://a")
    }

    @Test("plain text passes through unchanged")
    func plainText() {
        let text = "The seal failed under load.\n\nWeb sources:\n• https://a"
        #expect(MessageTextPolish.polish(text) == text)
    }

    @Test("fenced code blocks survive verbatim — heading/comment lines, backticks, bold")
    func preservesFencedCode() {
        let html = "Here you go:\n\n```html\n<!DOCTYPE html>\n<h1>Hi</h1>\n```\n\nThat's it."
        let out = MessageTextPolish.polish(html)
        #expect(out.contains("```html"))
        #expect(out.contains("<!DOCTYPE html>"))
        #expect(out.contains("<h1>Hi</h1>"))
    }

    @Test("a `# comment` line inside a code fence is NOT eaten as a heading")
    func preservesCodeComments() {
        let pySnippet = "```python\n# reverse a string\ndef rev(s):\n    return s[::-1]\n```"
        #expect(MessageTextPolish.polish(pySnippet) == pySnippet)
    }

    @Test("prose around a fence is still flattened; the fence is left alone")
    func polishesProseAroundFence() {
        let text = "**Note:** run this:\n\n```\nls -la\n```\n\nand `done`."
        let out = MessageTextPolish.polish(text)
        #expect(out.contains("Note: run this")) // **bold** flattened in prose
        #expect(out.contains("```\nls -la\n```")) // fence intact (incl. its content)
        #expect(out.contains("and done.")) // inline `code` flattened in prose
    }

    @Test("a fence whose body mentions ``` mid-line does not split early")
    func fenceBodyMentioningBackticksMidLine() {
        // The old non-greedy regex paired the opener with the INLINE ``` and the
        // rest of the code fell through to prose polishing (# lines eaten).
        let text = "```\nuse ``` to open a fence\n# keep this comment\n```"
        #expect(MessageTextPolish.polish(text) == text)
    }

    @Test("an unclosed fence (max-token truncation) is preserved verbatim to the end")
    func unclosedFencePreservedVerbatim() {
        let text = "Here you **go**:\n```swift\n# a comment\nlet x = 1"
        let out = MessageTextPolish.polish(text)
        #expect(out.contains("Here you go:")) // prose before still flattened
        #expect(out.contains("```swift\n# a comment\nlet x = 1")) // code untouched
    }

    @Test("a longer outer fence (````) keeps inner ``` fences whole — run-length pairing")
    func longerOuterFencePreservesInnerFences() {
        let text = "````markdown\n```\ninner code\n```\n````"
        #expect(MessageTextPolish.polish(text) == text)
    }

    @Test("a LINE-LEADING same-line ```code``` span is not an unclosed fence — nothing gets swallowed")
    func lineLeadingSameLineSpanDoesNotSwallow() {
        // CommonMark: a fence opener's info string may not contain backticks,
        // so this line is an inline span, not an opener. Misreading it as an
        // unclosed fence would emit the ENTIRE rest of the message verbatim —
        // defeating the file's whole purpose for that message.
        let text = "```rm -rf /tmp/cache``` clears the cache.\n\nMore **prose** follows."
        let out = MessageTextPolish.polish(text)
        #expect(out.contains("rm -rf /tmp/cache clears the cache."))
        #expect(out.contains("More prose follows."))
    }

    @Test("a same-line inline ```code``` span flattens like inline code — no stray backticks")
    func inlineTripleBacktickSpanFlattens() {
        // Not a fence (fences are line-based); the deliberate tradeoff vs the
        // old anywhere-matching regex, which preserved this verbatim WITH its
        // backticks. Flattening reads right in ReadingText; half-stripped
        // (``code``) would not.
        let text = "Use ```code``` inline."
        #expect(MessageTextPolish.polish(text) == "Use code inline.")
    }

    @Test("a fence indented 4+ spaces (nested under a bullet) is still preserved verbatim")
    func deeplyIndentedFencePreserved() {
        // CommonMark calls a 4-space-indented ``` an indented code block, not a
        // fence — but this file's job is "don't mangle code", so ANY-indent
        // fences are treated as fences (models nest them under list items).
        let text = "* step one:\n    ```swift\n    let s = \"**not bold**\"\n    ```\nand `done`."
        let out = MessageTextPolish.polish(text)
        #expect(out.contains("    ```swift\n    let s = \"**not bold**\"\n    ```"))
        #expect(out.contains("and done.")) // prose after still flattened
    }

    @Test("CRLF line endings pair fences correctly (Windows-origin pastes)")
    func crlfFences() {
        // The trailing \r lands inside the scanner's "line" content; it must
        // still open on the backtick run and close on the backticks-only line
        // (\r counts as whitespace). Pinned by design, not by accident.
        let text = "**Note:**\r\n```\r\n# comment\r\nls\r\n```\r\ndone `now`."
        let out = MessageTextPolish.polish(text)
        #expect(out.contains("Note:")) // prose flattened
        #expect(out.contains("```\r\n# comment\r\nls\r\n```")) // fence intact
        #expect(out.contains("done now.")) // prose after the fence flattened
    }

    @Test("an unclosed <artifact> (truncation) is preserved verbatim to the end")
    func unclosedArtifactPreservedVerbatim() {
        let text = "Intro *here*\n<artifact title=\"Doc\">\n# Heading **kept**"
        let out = MessageTextPolish.polish(text)
        #expect(out.contains("Intro here")) // prose before still flattened
        #expect(out.contains("<artifact title=\"Doc\">\n# Heading **kept**"))
    }
}
