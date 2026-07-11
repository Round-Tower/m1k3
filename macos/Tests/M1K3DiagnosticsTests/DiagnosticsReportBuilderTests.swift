//
//  DiagnosticsReportBuilderTests.swift
//  M1K3DiagnosticsTests
//
//  The pure report-assembly pipeline (109-12 review extraction): raw logs →
//  DiagnosticRedactor → injected extra scrub (the app passes CanaryGuard, which
//  lives in M1K3Chat — this module stays dependency-free) → markdown body.
//  Previously this orchestration sat untestable in M1K3App/IssueReporter.
//

import Foundation
@testable import M1K3Diagnostics
import Testing

struct DiagnosticsReportBuilderTests {
    private func rawReport(logs: String) -> IssueReport {
        IssueReport(
            title: "M1K3: issue report",
            whatHappened: "It crashed",
            appVersion: "1.2.0",
            build: "42",
            osVersion: "macOS 26.0",
            device: "Mac15,3",
            memoryGB: 32,
            activeBrain: "Lil",
            logs: logs
        )
    }

    @Test("assembles the markdown body with environment and fenced logs")
    func assemblesMarkdown() {
        let md = DiagnosticsReportBuilder.build(
            report: rawReport(logs: "line1\nline2"),
            homeDirectory: "/Users/kevinmurphy",
            userName: nil
        )
        #expect(md.contains("### What happened\nIt crashed"))
        #expect(md.contains("- App: 1.2.0 (42)"))
        #expect(md.contains("```\nline1\nline2\n```"))
    }

    @Test("raw logs pass through the PII redactor — home paths and emails never survive")
    func redactsRawLogs() {
        let md = DiagnosticsReportBuilder.build(
            report: rawReport(logs: "read /Users/kevinmurphy/Documents/secret.txt as kevin@round-tower.ie"),
            homeDirectory: "/Users/kevinmurphy",
            userName: "Kevin Murphy"
        )
        #expect(!md.contains("/Users/kevinmurphy"))
        #expect(!md.contains("kevin@round-tower.ie"))
        #expect(md.contains("~/Documents/secret.txt"))
        #expect(md.contains("[email]"))
    }

    @Test("the injected scrub runs AFTER redaction and its output is what ships")
    func extraScrubOrderAndEffect() {
        nonisolated(unsafe) var sawInput: String?
        let md = DiagnosticsReportBuilder.build(
            report: rawReport(logs: "canary-token in /Users/kevinmurphy/x"),
            homeDirectory: "/Users/kevinmurphy",
            userName: nil,
            scrubExtra: { text in
                sawInput = text
                return text.replacingOccurrences(of: "canary-token", with: "[REDACTED]")
            }
        )
        // Order pin: the scrub sees ALREADY-redacted text (paths collapsed) —
        // a honeypot scan over raw text would be a different, weaker contract.
        #expect(sawInput?.contains("~/x") == true)
        #expect(sawInput?.contains("/Users/kevinmurphy") == false)
        // And its output is what lands in the body.
        #expect(md.contains("[REDACTED]"))
        #expect(!md.contains("canary-token"))
    }

    @Test("without an injected scrub the redacted logs ship unchanged (pass-through default)")
    func defaultScrubIsPassThrough() {
        let md = DiagnosticsReportBuilder.build(
            report: rawReport(logs: "plain diagnostic line"),
            homeDirectory: "/Users/kevinmurphy",
            userName: nil
        )
        #expect(md.contains("plain diagnostic line"))
    }
}
