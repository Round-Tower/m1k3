import M1K3Chat
import Testing

struct BionicTextFormatterTests {
    @Test("bold-prefix schedule by word length")
    func schedule() {
        #expect(BionicTextFormatter.boldPrefixCount(for: "a") == 1)
        #expect(BionicTextFormatter.boldPrefixCount(for: "cat") == 1)
        #expect(BionicTextFormatter.boldPrefixCount(for: "word") == 2)
        #expect(BionicTextFormatter.boldPrefixCount(for: "bionic") == 2)
        #expect(BionicTextFormatter.boldPrefixCount(for: "reading") == 3) // ceil(7*0.4)=3
        #expect(BionicTextFormatter.boldPrefixCount(for: "exceptional") == 5) // ceil(11*0.4)=5
        #expect(BionicTextFormatter.boldPrefixCount(for: "") == 0)
    }

    @Test("a single word bolds its leading letters")
    func singleWord() {
        let runs = BionicTextFormatter.runs("hello")
        #expect(runs.count == 1)
        #expect(runs[0].bold == "he")
        #expect(runs[0].rest == "llo")
    }

    @Test("whitespace tokens pass through with empty bold")
    func whitespacePreserved() {
        let runs = BionicTextFormatter.runs("hi  there")
        // word "hi" → ("h","i"); "  " whitespace → ("","  "); "there" → ("th","ere")
        #expect(runs.count == 3)
        #expect(runs[0].bold == "h")
        #expect(runs[0].rest == "i")
        #expect(runs[1].bold.isEmpty)
        #expect(runs[1].rest == "  ")
        #expect(runs[2].bold == "th")
        #expect(runs[2].rest == "ere")
    }

    @Test("trailing punctuation stays in the rest")
    func punctuation() {
        let runs = BionicTextFormatter.runs("world!")
        #expect(runs.count == 1)
        #expect(runs[0].bold == "wo") // leading letters "world" → 2
        #expect(runs[0].rest == "rld!")
    }

    @Test("reconstructing bold+rest reproduces the original text")
    func lossless() {
        for text in ["Hello, M1K3 — how are you?", "  spaced  out  ", "single", "", "\nnewlines\nhere"] {
            let rebuilt = BionicTextFormatter.runs(text)
                .map { String($0.bold) + String($0.rest) }
                .joined()
            #expect(rebuilt == text)
        }
    }

    @Test("empty input yields no runs")
    func empty() {
        #expect(BionicTextFormatter.runs("").isEmpty)
    }

    @Test("deterministic")
    func deterministic() {
        let text = "The quick brown fox."
        #expect(
            BionicTextFormatter.runs(text).map { "\($0.bold)|\($0.rest)" }
                == BionicTextFormatter.runs(text).map { "\($0.bold)|\($0.rest)" }
        )
    }
}
