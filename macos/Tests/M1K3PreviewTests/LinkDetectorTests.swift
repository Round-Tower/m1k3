//
//  LinkDetectorTests.swift
//  M1K3PreviewTests
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Preview
import Testing

struct LinkDetectorTests {
    @Test("no text, no links")
    func empty() {
        #expect(LinkDetector.detect(in: "") == [])
        #expect(LinkDetector.detect(in: "just some words, nothing clickable") == [])
    }

    @Test("a single https link is found")
    func single() throws {
        let urls = LinkDetector.detect(in: "Have a look at https://example.com for details.")
        #expect(try urls == [#require(URL(string: "https://example.com"))])
    }

    @Test("links are returned in first-seen order")
    func ordered() throws {
        let urls = LinkDetector.detect(in: "First https://a.com then https://b.com last.")
        #expect(try urls == [#require(URL(string: "https://a.com")), #require(URL(string: "https://b.com"))])
    }

    @Test("a repeated link is de-duplicated")
    func deduped() throws {
        let urls = LinkDetector.detect(in: "https://a.com … https://b.com … https://a.com again")
        #expect(try urls == [#require(URL(string: "https://a.com")), #require(URL(string: "https://b.com"))])
    }

    @Test("a markdown link's URL is extracted")
    func markdown() throws {
        let urls = LinkDetector.detect(in: "See [the docs](https://example.com/docs).")
        #expect(try urls == [#require(URL(string: "https://example.com/docs"))])
    }

    @Test("non-web schemes are ignored")
    func ignoresNonWeb() {
        // mailto: is a link to NSDataDetector but not something the panel reviews.
        let urls = LinkDetector.detect(in: "Mail me at mailto:kev@example.com or file:///tmp/x")
        #expect(urls == [])
    }
}

struct ReviewTargetGrantedTests {
    @Test("a file URL routes straight to .file (caller holds the grant)")
    func fileTrusted() {
        let url = URL(fileURLWithPath: "/tmp/report.pdf")
        #expect(ReviewTarget.forGranted(url) == .file(url))
    }

    @Test("a web URL is validated through the resolver")
    func webValidated() throws {
        let url = try #require(URL(string: "https://example.com/page"))
        #expect(ReviewTarget.forGranted(url) == .web(url))
    }
}
