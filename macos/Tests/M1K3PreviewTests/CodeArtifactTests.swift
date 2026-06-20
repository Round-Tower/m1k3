import Foundation
@testable import M1K3Preview
import Testing

struct CodeArtifactTests {
    // MARK: - Language detection

    @Test("language from common file extensions")
    func languageFromExtension() {
        #expect(CodeArtifact.Language(fileExtension: "html") == .html)
        #expect(CodeArtifact.Language(fileExtension: "htm") == .html)
        #expect(CodeArtifact.Language(fileExtension: "css") == .css)
        #expect(CodeArtifact.Language(fileExtension: "js") == .js)
        #expect(CodeArtifact.Language(fileExtension: "HTML") == .html)
    }

    @Test("unknown extension returns nil")
    func unknownExtension() {
        #expect(CodeArtifact.Language(fileExtension: "swift") == nil)
        #expect(CodeArtifact.Language(fileExtension: "") == nil)
    }

    @Test("language inferred from source content")
    func languageFromContent() {
        #expect(CodeArtifact.Language.detect(from: "<html><body>hi</body></html>") == .html)
        #expect(CodeArtifact.Language.detect(from: "<!DOCTYPE html>") == .html)
        #expect(CodeArtifact.Language.detect(from: "<div>hello</div>") == .html)
        #expect(CodeArtifact.Language.detect(from: "body { color: red; }") == .css)
        #expect(CodeArtifact.Language.detect(from: ".container { display: flex; }") == .css)
        #expect(CodeArtifact.Language.detect(from: "function hello() { return 1; }") == .js)
        #expect(CodeArtifact.Language.detect(from: "const x = 42;") == .js)
    }

    // MARK: - Filename

    @Test("default filename uses language extension")
    func defaultFilename() {
        let artifact = CodeArtifact(source: "<p>hi</p>", language: .html)
        #expect(artifact.filename.hasSuffix(".html"))

        let css = CodeArtifact(source: "body {}", language: .css)
        #expect(css.filename.hasSuffix(".css"))

        let js = CodeArtifact(source: "alert(1)", language: .js)
        #expect(js.filename.hasSuffix(".js"))
    }

    @Test("custom title is reflected in filename")
    func customTitleFilename() {
        let artifact = CodeArtifact(source: "<p>hi</p>", language: .html, title: "My Page")
        #expect(artifact.filename == "My Page.html")
    }

    @Test("untitled artifact gets a default filename")
    func untitledFilename() {
        let artifact = CodeArtifact(source: "<p>hi</p>", language: .html)
        #expect(artifact.filename == "untitled.html")
    }

    // MARK: - Export

    @Test("exportData returns source encoded as UTF-8")
    func exportData() {
        let source = "<p>hello</p>"
        let artifact = CodeArtifact(source: source, language: .html)
        #expect(artifact.exportData == source.data(using: .utf8))
    }
}
