@testable import M1K3Preview
import Testing

struct ArtifactFormatterTests {
    // MARK: - HTML structure wrapping

    @Test("bare content gets wrapped in a full HTML document")
    func wrapsBarContent() {
        let input = "<h1>Hello</h1>\n<p>World</p>"
        let result = ArtifactFormatter.formatHTML(input)
        #expect(result.contains("<!DOCTYPE html>"))
        #expect(result.contains("<html"))
        #expect(result.contains("<head>"))
        #expect(result.contains("<body>"))
        #expect(result.contains("</body>"))
        #expect(result.contains("</html>"))
        #expect(result.contains("Hello"))
        #expect(result.contains("<h1>"))
    }

    @Test("content that already has doctype is not double-wrapped")
    func doesNotDoubleWrap() {
        let input = "<!DOCTYPE html>\n<html><head></head><body><p>hi</p></body></html>"
        let result = ArtifactFormatter.formatHTML(input)
        let doctypeCount = result.components(separatedBy: "<!DOCTYPE").count - 1
        #expect(doctypeCount == 1)
        let htmlCount = result.components(separatedBy: "<html").count - 1
        #expect(htmlCount == 1)
    }

    @Test("content with html tag but no doctype gets doctype prepended")
    func addsDoctype() {
        let input = "<html><body><p>hi</p></body></html>"
        let result = ArtifactFormatter.formatHTML(input)
        #expect(result.hasPrefix("<!DOCTYPE html>"))
    }

    @Test("content with only a body tag gets full wrapping")
    func wrapsBodyOnly() {
        let input = "<body><p>hi</p></body>"
        let result = ArtifactFormatter.formatHTML(input)
        #expect(result.contains("<!DOCTYPE html>"))
        #expect(result.contains("<html"))
        #expect(result.contains("<head>"))
        #expect(result.contains("hi"))
        #expect(result.contains("<p>"))
    }

    // MARK: - Unclosed tags

    @Test("unclosed div gets closed")
    func closesDiv() {
        let input = "<div><p>hello</p>"
        let result = ArtifactFormatter.closeUnclosedTags(input)
        #expect(result.contains("</div>"))
    }

    @Test("nested unclosed tags are closed in correct order")
    func closesNestedInOrder() throws {
        let input = "<div><section><p>hello</p>"
        let result = ArtifactFormatter.closeUnclosedTags(input)
        let divClose = result.range(of: "</div>")
        let sectionClose = result.range(of: "</section>")
        #expect(divClose != nil)
        #expect(sectionClose != nil)
        #expect(try #require(sectionClose?.lowerBound) < divClose!.lowerBound)
    }

    @Test("void elements are not closed")
    func voidElementsUntouched() {
        let input = "<br><img src=\"x.png\"><hr><input type=\"text\">"
        let result = ArtifactFormatter.closeUnclosedTags(input)
        #expect(!result.contains("</br>"))
        #expect(!result.contains("</img>"))
        #expect(!result.contains("</hr>"))
        #expect(!result.contains("</input>"))
    }

    @Test("self-closing tags are not double-closed")
    func selfClosingUntouched() {
        let input = "<div><br/><img src=\"x.png\" /></div>"
        let result = ArtifactFormatter.closeUnclosedTags(input)
        #expect(!result.contains("</br>"))
        #expect(!result.contains("</img>"))
    }

    @Test("already-closed tags are left alone")
    func alreadyClosedUntouched() {
        let input = "<div><p>hello</p></div>"
        let result = ArtifactFormatter.closeUnclosedTags(input)
        #expect(result == input)
    }

    // MARK: - Indentation

    @Test("flat HTML gets indented")
    func indentsFlat() throws {
        let input = "<div><p>hello</p></div>"
        let result = ArtifactFormatter.normalizeIndentation(input)
        let lines = result.split(separator: "\n", omittingEmptySubsequences: false)
        #expect(lines.count > 1)
        let pLine = lines.first { $0.contains("<p>") }
        #expect(pLine != nil)
        #expect(try #require(pLine?.hasPrefix("  ")))
    }

    @Test("already indented content is re-indented consistently")
    func reindentsMessy() throws {
        let input = """
        <div>
                    <p>hello</p>
          <span>world</span>
        </div>
        """
        let result = ArtifactFormatter.normalizeIndentation(input)
        let lines = result.split(separator: "\n", omittingEmptySubsequences: false)
        let pLine = lines.first { $0.contains("<p>") }
        let spanLine = lines.first { $0.contains("<span>") }
        #expect(pLine != nil && spanLine != nil)
        let pIndent = try #require(pLine?.prefix(while: { $0 == " " }).count)
        let spanIndent = try #require(spanLine?.prefix(while: { $0 == " " }).count)
        #expect(pIndent == spanIndent)
    }

    // MARK: - Full format pipeline

    @Test("format composes structure + close + indent for HTML")
    func fullFormatHTML() {
        let artifact = CodeArtifact(source: "<div><p>hello", language: .html)
        let formatted = ArtifactFormatter.format(artifact)
        #expect(formatted.source.contains("<!DOCTYPE html>"))
        #expect(formatted.source.contains("</div>"))
        #expect(formatted.source.contains("</p>"))
        #expect(formatted.language == .html)
    }

    @Test("format preserves CSS source with basic cleanup")
    func fullFormatCSS() {
        let artifact = CodeArtifact(source: "  body {   color: red;  }  ", language: .css)
        let formatted = ArtifactFormatter.format(artifact)
        #expect(formatted.source.contains("color: red"))
        #expect(!formatted.source.hasPrefix(" "))
    }

    @Test("format preserves JS source with basic cleanup")
    func fullFormatJS() {
        let artifact = CodeArtifact(source: "  function hello() { return 1; }  ", language: .js)
        let formatted = ArtifactFormatter.format(artifact)
        #expect(formatted.source.contains("function hello()"))
        #expect(!formatted.source.hasPrefix(" "))
    }

    // MARK: - Edge cases small models produce

    @Test("handles empty source gracefully")
    func emptySource() {
        let artifact = CodeArtifact(source: "", language: .html)
        let formatted = ArtifactFormatter.format(artifact)
        #expect(formatted.source.contains("<!DOCTYPE html>"))
    }

    @Test("handles source with only whitespace")
    func whitespaceOnly() {
        let artifact = CodeArtifact(source: "   \n\n  ", language: .html)
        let formatted = ArtifactFormatter.format(artifact)
        #expect(formatted.source.contains("<!DOCTYPE html>"))
    }

    @Test("script content with comparison operators — best-effort, must not crash")
    func scriptWithComparisonOperators() {
        let input = "<script>if (x < 5) { return x; }</script>"
        let result = ArtifactFormatter.formatHTML(input)
        _ = result
    }

    @Test("inline style and script tags in HTML are preserved")
    func inlineStyleScript() {
        let input = """
        <style>body { color: red; }</style>
        <script>console.log('hi');</script>
        <p>hello</p>
        """
        let result = ArtifactFormatter.formatHTML(input)
        #expect(result.contains("body { color: red; }"))
        #expect(result.contains("console.log('hi')"))
    }
}
