//
//  ReviewTargetResolverTests.swift
//  M1K3PreviewTests
//
//  The pure brain of the review panel: turn a raw string (a pasted link, a
//  dropped file:// URL, a typed path) into a routed ReviewTarget. Sandbox reality
//  shapes the contract — a typed bare path to a never-granted file reads as
//  non-existent under the sandbox, so typed dotted tokens resolve to the web and
//  real files arrive as file:// URLs (drop / picker, which grant access).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Preview
import Testing

struct ReviewTargetResolverTests {
    /// A resolver that knows about a fixed set of "existing" paths, so file
    /// resolution is deterministic and off-device.
    private func resolver(existing: Set<String> = [], home: String = "/Users/test") -> (String) -> ReviewTarget {
        { input in
            ReviewTargetResolver.resolve(
                input,
                homePath: home,
                fileExists: { existing.contains($0) }
            )
        }
    }

    // MARK: - Empty / whitespace

    @Test("blank input is empty, not invalid")
    func blankIsEmpty() {
        let resolve = resolver()
        #expect(resolve("") == .empty)
        #expect(resolve("   ") == .empty)
        #expect(resolve("\n\t ") == .empty)
    }

    // MARK: - Web

    @Test("explicit http(s) URLs resolve to web")
    func explicitHTTP() throws {
        let resolve = resolver()
        #expect(try resolve("https://example.com") == .web(#require(URL(string: "https://example.com"))))
        #expect(try resolve("http://example.com/path?q=1") == .web(#require(URL(string: "http://example.com/path?q=1"))))
    }

    @Test("input is trimmed before resolving")
    func trims() throws {
        let resolve = resolver()
        #expect(try resolve("  https://example.com  ") == .web(#require(URL(string: "https://example.com"))))
    }

    @Test("a bare domain is coerced to https")
    func bareDomainCoerced() throws {
        let resolve = resolver()
        #expect(try resolve("example.com") == .web(#require(URL(string: "https://example.com"))))
        #expect(try resolve("sub.example.co.uk/docs") == .web(#require(URL(string: "https://sub.example.co.uk/docs"))))
        #expect(try resolve("m1k3.app") == .web(#require(URL(string: "https://m1k3.app"))))
    }

    // MARK: - Files

    @Test("an existing absolute path resolves to a file URL")
    func absolutePathFile() {
        let resolve = resolver(existing: ["/tmp/report.pdf"])
        #expect(resolve("/tmp/report.pdf") == .file(URL(fileURLWithPath: "/tmp/report.pdf")))
    }

    @Test("a tilde path is expanded against home")
    func tildeExpanded() {
        let resolve = resolver(existing: ["/Users/test/Notes.md"], home: "/Users/test")
        #expect(resolve("~/Notes.md") == .file(URL(fileURLWithPath: "/Users/test/Notes.md")))
    }

    @Test("an existing file:// URL resolves to a file")
    func fileURLScheme() {
        let resolve = resolver(existing: ["/tmp/a.png"])
        #expect(resolve("file:///tmp/a.png") == .file(URL(fileURLWithPath: "/tmp/a.png")))
    }

    @Test("an absolute path that does not exist is invalid")
    func missingPathInvalid() {
        let resolve = resolver(existing: [])
        #expect(resolve("/tmp/gone.pdf") == .invalid("/tmp/gone.pdf"))
    }

    @Test("a file:// URL that does not exist is invalid")
    func missingFileURLInvalid() {
        let resolve = resolver(existing: [])
        #expect(resolve("file:///tmp/gone.pdf") == .invalid("file:///tmp/gone.pdf"))
    }

    // MARK: - Disambiguation (the sandbox-shaped contract)

    @Test("a typed filename with no path prefix is treated as web, not a file")
    func typedDottedTokenIsWeb() throws {
        // README.md typed bare → we cannot open arbitrary sandbox paths anyway,
        // so a dotted token is a domain candidate. Real files arrive as file://.
        let resolve = resolver(existing: [])
        #expect(try resolve("README.md") == .web(#require(URL(string: "https://README.md"))))
    }

    @Test("an existing path wins even when it could read as a domain")
    func explicitPathBeatsDomain() {
        let resolve = resolver(existing: ["/srv/example.com"])
        #expect(resolve("/srv/example.com") == .file(URL(fileURLWithPath: "/srv/example.com")))
    }

    @Test("an existing dot-relative path resolves to a file")
    func relativePathFile() {
        let resolve = resolver(existing: ["./notes.txt", "../parent.md"])
        #expect(resolve("./notes.txt") == .file(URL(fileURLWithPath: "./notes.txt")))
        #expect(resolve("../parent.md") == .file(URL(fileURLWithPath: "../parent.md")))
    }

    @Test("a missing dot-relative path is invalid")
    func relativePathMissing() {
        let resolve = resolver(existing: [])
        #expect(resolve("./gone.txt") == .invalid("./gone.txt"))
    }

    @Test("a bare tilde expands to the home directory")
    func bareTildeIsHome() {
        let resolve = resolver(existing: ["/Users/test"], home: "/Users/test")
        #expect(resolve("~") == .file(URL(fileURLWithPath: "/Users/test")))
    }

    @Test("a label that is all hyphens is rejected, not coerced to web")
    func allHyphenLabelInvalid() {
        // A label can't start or end with a hyphen (RFC 1123), so an all-hyphen
        // label fails the domain shape and routes to .invalid rather than
        // producing a URL that could never resolve.
        let resolve = resolver()
        #expect(resolve("a.---.com") == .invalid("a.---.com"))
    }

    // MARK: - Invalid

    @Test("junk with spaces and no dot is invalid")
    func junkInvalid() {
        let resolve = resolver()
        #expect(resolve("just some text") == .invalid("just some text"))
        #expect(resolve("hello") == .invalid("hello"))
    }

    @Test("a bare host:port with no scheme is invalid (ambiguous, not coerced)")
    func bareHostPortInvalid() {
        // "localhost:3000" reads as scheme "localhost" — unsupported — so it's
        // invalid rather than silently coerced. Type http://localhost:3000 to load it.
        let resolve = resolver()
        #expect(resolve("localhost:3000") == .invalid("localhost:3000"))
    }

    @Test("an explicit http URL with a port resolves to web")
    func explicitHostPortWeb() throws {
        let resolve = resolver()
        #expect(try resolve("http://localhost:3000") == .web(#require(URL(string: "http://localhost:3000"))))
    }

    @Test("an unsupported scheme is invalid")
    func unsupportedScheme() {
        let resolve = resolver()
        #expect(resolve("ftp://example.com/file") == .invalid("ftp://example.com/file"))
        #expect(resolve("javascript:alert(1)") == .invalid("javascript:alert(1)"))
    }

    @Test("data: and blob: injection schemes are invalid, never coerced to web")
    func injectionSchemesInvalid() {
        // The two schemes most used in web injection payloads — pin that they
        // route to .invalid rather than reaching the WebView.
        let resolve = resolver()
        #expect(resolve("data:text/html,<script>alert(1)</script>")
            == .invalid("data:text/html,<script>alert(1)</script>"))
        #expect(resolve("blob:https://example.com/uuid") == .invalid("blob:https://example.com/uuid"))
    }
}
