//
//  MarkdownToHTMLTests.swift
//  M1K3PreviewTests
//
//  The dependency-free markdown → HTML block converter that renders a captured
//  document artifact in the preview WebView.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Preview
import Testing

struct MarkdownToHTMLTests {
    @Test("headings become h1…h6")
    func headings() {
        #expect(MarkdownToHTML.render("# Title") == "<h1>Title</h1>")
        #expect(MarkdownToHTML.render("### Sub") == "<h3>Sub</h3>")
        // Seven hashes is not a heading (max 6) — it stays a paragraph.
        #expect(MarkdownToHTML.render("####### Nope").contains("<p>"))
    }

    @Test("a run of text lines is one paragraph")
    func paragraph() {
        #expect(MarkdownToHTML.render("hello\nthere") == "<p>hello there</p>")
    }

    @Test("blank lines separate paragraphs")
    func paragraphs() {
        #expect(MarkdownToHTML.render("one\n\ntwo") == "<p>one</p>\n<p>two</p>")
    }

    @Test("inline emphasis, code and links render")
    func inline() {
        #expect(MarkdownToHTML.render("a **bold** word") == "<p>a <strong>bold</strong> word</p>")
        #expect(MarkdownToHTML.render("an *italic* word") == "<p>an <em>italic</em> word</p>")
        #expect(MarkdownToHTML.render("call `foo()` now") == "<p>call <code>foo()</code> now</p>")
        #expect(MarkdownToHTML.render("see [docs](https://m1k3.app)")
            == "<p>see <a href=\"https://m1k3.app\">docs</a></p>")
    }

    @Test("unordered and ordered lists")
    func lists() {
        #expect(MarkdownToHTML.render("- a\n- b") == "<ul>\n<li>a</li>\n<li>b</li>\n</ul>")
        #expect(MarkdownToHTML.render("1. a\n2. b") == "<ol>\n<li>a</li>\n<li>b</li>\n</ol>")
    }

    @Test("thematic break becomes hr")
    func thematicBreak() {
        #expect(MarkdownToHTML.render("above\n\n***\n\nbelow").contains("<hr>"))
    }

    @Test("fenced code block is escaped and preserved verbatim")
    func fencedCode() {
        let md = "```\nlet x = a < b && c > d\n```"
        let html = MarkdownToHTML.render(md)
        #expect(html.contains("<pre><code>"))
        #expect(html.contains("a &lt; b &amp;&amp; c &gt; d"))
        #expect(!html.contains("<code>let x = a < b")) // raw < never leaks
    }

    @Test("blockquote renders")
    func blockquote() {
        #expect(MarkdownToHTML.render("> quoted") == "<blockquote>quoted</blockquote>")
    }

    @Test("HTML special characters in prose are escaped (no injection)")
    func escapesProse() {
        #expect(MarkdownToHTML.render("5 < 6 & 7 > 2")
            == "<p>5 &lt; 6 &amp; 7 &gt; 2</p>")
        // A stray tag in model output must not become live markup.
        #expect(MarkdownToHTML.render("<script>alert(1)</script>")
            == "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>")
    }

    @Test("a realistic document renders its structure")
    func document() {
        let md = """
        # Project Blueprint

        ## 1. Core Persona
        - **Primary Directive:** ship it
        - *Persona:* dry, warm
        """
        let html = MarkdownToHTML.render(md)
        #expect(html.contains("<h1>Project Blueprint</h1>"))
        #expect(html.contains("<h2>1. Core Persona</h2>"))
        #expect(html.contains("<li><strong>Primary Directive:</strong> ship it</li>"))
        #expect(html.contains("<li><em>Persona:</em> dry, warm</li>"))
    }
}
