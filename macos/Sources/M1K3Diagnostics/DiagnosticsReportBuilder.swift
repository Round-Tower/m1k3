//
//  DiagnosticsReportBuilder.swift
//  M1K3Diagnostics
//
//  The pure "Report an issue" assembly pipeline, extracted from
//  M1K3App/IssueReporter (109-12 review nit): the app target gathers OS inputs
//  (OSLogStore, sysctl, Bundle, ProcessInfo) and hands them here; this module
//  owns the order-sensitive scrub — PII redaction FIRST, then the injected
//  extra scrub (the app passes CanaryGuard.scan, which lives in M1K3Chat; the
//  seam keeps this module dependency-free) — and the markdown assembly.
//
//  Contract: `report.logs` arrives RAW; everything else in `report` is already
//  display-safe (versions, device model, brain name — none carry user text).
//  `whatHappened` is the user's own typed words into their own issue, so it is
//  deliberately not machine-scrubbed.
//
//  Signed: Kev + claude-fable-5, 2026-07-11, Confidence 0.9 (pipeline order and
//  scrub seam unit-pinned; behavior byte-identical to the pre-extraction glue).
//  Prior: Kev + claude-opus-4-8 (M1K3App/IssueReporter.swift).
//

import Foundation

public enum DiagnosticsReportBuilder {
    /// Redact `report.logs` (paths/emails/name via `DiagnosticRedactor`), apply
    /// the injected extra scrub to the REDACTED text, and format the markdown
    /// body. The default scrub is a pass-through.
    public static func build(
        report: IssueReport,
        homeDirectory: String,
        userName: String?,
        scrubExtra: (String) -> String = { $0 }
    ) -> String {
        let redactor = DiagnosticRedactor(homeDirectory: homeDirectory, userName: userName)
        var scrubbed = report
        scrubbed.logs = scrubExtra(redactor.redact(report.logs))
        return IssueReportFormatter.markdownBody(scrubbed)
    }
}
