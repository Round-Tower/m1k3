import Foundation
@testable import M1K3Chat
import Testing

/// Pins the paragraph splitter the karaoke view rebases word ranges through:
/// display text per paragraph plus the UTF-16 span each occupies in the
/// original string. An offset error here silently corrupts every word
/// highlight in that paragraph.
struct ParagraphSplitterTests {
    @Test("newlines split paragraphs; ranges index the original string")
    func basicSplit() {
        let text = "First line\nSecond line\nThird"
        let paragraphs = ParagraphSplitter.split(text)
        #expect(paragraphs.map(\.text) == ["First line", "Second line", "Third"])
        #expect(paragraphs.map(\.range) == [0 ..< 10, 11 ..< 22, 23 ..< 28])
        let ns = text as NSString
        for paragraph in paragraphs {
            let slice = ns.substring(with: NSRange(location: paragraph.range.lowerBound, length: paragraph.range.count))
            #expect(slice == paragraph.text)
        }
    }

    @Test("blank lines produce no empty paragraphs")
    func blankLinesSkipped() {
        let paragraphs = ParagraphSplitter.split("a\n\n\nb")
        #expect(paragraphs.map(\.text) == ["a", "b"])
        #expect(paragraphs[1].range == 4 ..< 5)
    }

    @Test("leading and trailing newlines are dropped")
    func edgesTrimmed() {
        let paragraphs = ParagraphSplitter.split("\nhello\n")
        #expect(paragraphs.map(\.text) == ["hello"])
        #expect(paragraphs[0].range == 1 ..< 6)
    }

    @Test("emoji keep UTF-16 offsets honest")
    func emojiOffsets() {
        let text = "fun 🎉\nmore"
        let paragraphs = ParagraphSplitter.split(text)
        #expect(paragraphs.count == 2)
        #expect(paragraphs[0].range == 0 ..< 6) // 🎉 is two UTF-16 units
        #expect(paragraphs[1].range == 7 ..< 11)
        #expect(paragraphs[1].text == "more")
    }

    @Test("no newlines means one paragraph spanning everything")
    func singleParagraph() {
        let text = "just one"
        let paragraphs = ParagraphSplitter.split(text)
        #expect(paragraphs.map(\.text) == [text])
        #expect(paragraphs[0].range == 0 ..< 8)
    }

    @Test("empty and newline-only input yield nothing")
    func emptyInput() {
        #expect(ParagraphSplitter.split("").isEmpty)
        #expect(ParagraphSplitter.split("\n\n").isEmpty)
    }
}
