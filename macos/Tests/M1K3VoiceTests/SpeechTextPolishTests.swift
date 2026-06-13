import Foundation
import M1K3Voice
import Testing

struct SpeechTextPolishTests {
    // MARK: - Web sources block

    @Test("trailing Web sources block is stripped entirely")
    func stripsWebSourcesBlock() {
        let text = "It is 15 degrees in Cork today.\n\nWeb sources:\n• https://weather.com/cork\n• https://met.ie/forecast"
        #expect(SpeechTextPolish.polish(text) == "It is 15 degrees in Cork today.")
    }

    @Test("Web sources block after a single blank line is stripped")
    func stripsWebSourcesBlockSingleNewline() {
        let text = "Sunny all week.\nWeb sources:\n• https://weather.com"
        #expect(SpeechTextPolish.polish(text) == "Sunny all week.")
    }

    @Test("the phrase Web sources mid-sentence is not treated as a block")
    func keepsWebSourcesProse() {
        let text = "I checked several web sources: none agreed."
        #expect(SpeechTextPolish.polish(text) == "I checked several web sources: none agreed.")
    }

    // MARK: - Citation tokens

    @Test("bracket citations with section marks are stripped")
    func stripsBracketCitations() {
        let text = "Revenue rose 12% [Quarterly Report §Revenue] in the third quarter."
        #expect(SpeechTextPolish.polish(text) == "Revenue rose 12% in the third quarter.")
    }

    @Test("paren citations with section marks are stripped")
    func stripsParenCitations() {
        let text = "Revenue rose (Quarterly Report §Revenue) in the third quarter."
        #expect(SpeechTextPolish.polish(text) == "Revenue rose in the third quarter.")
    }

    @Test("heading-less bracket citations are left alone — no section mark, no strip")
    func keepsPlainBrackets() {
        let text = "See item [1] and the note (see above)."
        #expect(SpeechTextPolish.polish(text) == "See item [1] and the note (see above).")
    }

    // MARK: - URLs

    @Test("inline URL collapses to its host without www")
    func urlBecomesHost() {
        let text = "see https://www.weather.com/today?units=c for details"
        #expect(SpeechTextPolish.polish(text) == "see weather.com for details")
    }

    @Test("URL keeps trailing sentence punctuation")
    func urlKeepsPunctuation() {
        let text = "The forecast is at https://met.ie/forecast."
        #expect(SpeechTextPolish.polish(text) == "The forecast is at met.ie.")
    }

    @Test("multiple URLs each collapse to their host")
    func multipleURLs() {
        let text = "Compare http://a.example.com/x and https://b.example.org/y today"
        #expect(SpeechTextPolish.polish(text) == "Compare a.example.com and b.example.org today")
    }

    // MARK: - Curly punctuation

    @Test("curly apostrophes normalize to ASCII so the G2P dictionary hits contractions")
    func curlyApostrophes() {
        #expect(SpeechTextPolish.polish("don\u{2019}t \u{2018}quote\u{2019} me") == "don't 'quote' me")
    }

    // MARK: - Whitespace

    @Test("stripping leaves no doubled spaces or space before punctuation")
    func whitespaceTidy() {
        let text = "Revenue rose [Report §Q3] , then fell."
        #expect(SpeechTextPolish.polish(text) == "Revenue rose, then fell.")
    }

    @Test("newline pile-ups collapse to a paragraph break and ends are trimmed")
    func newlineCollapse() {
        let text = "  First.\n\n\n\nSecond.  "
        #expect(SpeechTextPolish.polish(text) == "First.\n\nSecond.")
    }

    // MARK: - Contracts

    @Test("polish is idempotent")
    func idempotent() {
        let text = "Rain [Met §Today] via https://www.met.ie/x.\n\nWeb sources:\n• https://met.ie/x"
        let once = SpeechTextPolish.polish(text)
        #expect(SpeechTextPolish.polish(once) == once)
    }

    @Test("text that is only a sources block polishes to empty — caller guards")
    func onlySourcesBlock() {
        let text = "Web sources:\n• https://a.com\n• https://b.com"
        #expect(SpeechTextPolish.polish(text).isEmpty)
    }
}
