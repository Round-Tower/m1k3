@testable import M1K3Preview
import Testing

struct CodeBlockDetectorTests {
    @Test("detects a fenced HTML code block")
    func fencedHTML() {
        let text = """
        Here is a page:

        ```html
        <h1>Hello</h1>
        <p>World</p>
        ```

        Hope that helps!
        """
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.count == 1)
        #expect(artifacts.first?.language == .html)
        #expect(artifacts.first?.source.contains("<h1>Hello</h1>") == true)
    }

    @Test("detects a fenced CSS code block")
    func fencedCSS() {
        let text = """
        ```css
        body { color: red; }
        ```
        """
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.count == 1)
        #expect(artifacts.first?.language == .css)
    }

    @Test("detects a fenced JS code block")
    func fencedJS() {
        let text = """
        ```js
        function hello() { return 1; }
        ```
        """
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.count == 1)
        #expect(artifacts.first?.language == .js)
    }

    @Test("javascript tag also detected as JS")
    func fencedJavaScript() {
        let text = """
        ```javascript
        const x = 42;
        ```
        """
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.count == 1)
        #expect(artifacts.first?.language == .js)
    }

    @Test("unfenced HTML is detected by content")
    func unfencedHTML() {
        let text = """
        <!DOCTYPE html>
        <html>
        <head><title>Test</title></head>
        <body><p>Hello</p></body>
        </html>
        """
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.count == 1)
        #expect(artifacts.first?.language == .html)
    }

    @Test("multiple code blocks produce multiple artifacts")
    func multipleBlocks() {
        let text = """
        ```html
        <p>one</p>
        ```

        And also:

        ```css
        body { margin: 0; }
        ```
        """
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.count == 2)
    }

    @Test("plain text without code returns no artifacts")
    func noCode() {
        let text = "Just a normal answer about coffee shops and their history."
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.isEmpty)
    }

    @Test("empty text returns no artifacts")
    func emptyText() {
        #expect(CodeBlockDetector.detect(in: "").isEmpty)
    }

    @Test("a fenced block with no language tag uses content detection")
    func untaggedFence() {
        let text = """
        ```
        <div><p>hello</p></div>
        ```
        """
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.count == 1)
        #expect(artifacts.first?.language == .html)
    }

    @Test("a fenced block with unknown language tag is skipped")
    func unknownLanguage() {
        let text = """
        ```python
        print("hello")
        ```
        """
        let artifacts = CodeBlockDetector.detect(in: text)
        #expect(artifacts.isEmpty)
    }
}
