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
}
