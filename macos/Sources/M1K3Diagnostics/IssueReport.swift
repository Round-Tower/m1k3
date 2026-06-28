//
//  IssueReport.swift
//  M1K3Diagnostics
//
//  The diagnostic bundle for a GitHub issue, plus pure formatters for the
//  markdown body and the prefilled new-issue URL. No network, no token: the app
//  copies the full body to the clipboard and opens the prefilled GitHub page for
//  the user to review and submit (secret-free filing).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-28, Confidence 0.85 (pure formatting,
//  TDD-pinned incl. the URL length cap). Prior: Unknown.
//

import Foundation

/// A redacted diagnostic bundle. `logs` MUST already be passed through the
/// redactor + honeypot scan before construction — this type does no scrubbing.
public struct IssueReport: Sendable, Equatable {
    public var title: String
    public var whatHappened: String
    public var appVersion: String
    public var build: String
    public var osVersion: String
    public var device: String
    public var memoryGB: Int
    public var activeBrain: String
    public var logs: String

    public init(
        title: String,
        whatHappened: String = "",
        appVersion: String,
        build: String,
        osVersion: String,
        device: String,
        memoryGB: Int,
        activeBrain: String,
        logs: String
    ) {
        self.title = title
        self.whatHappened = whatHappened
        self.appVersion = appVersion
        self.build = build
        self.osVersion = osVersion
        self.device = device
        self.memoryGB = memoryGB
        self.activeBrain = activeBrain
        self.logs = logs
    }
}

public enum IssueReportFormatter {
    /// The markdown issue body. `whatHappened` leads when present; environment is
    /// always included; logs go in a fenced block so GitHub renders them verbatim.
    public static func markdownBody(_ report: IssueReport) -> String {
        var sections: [String] = []

        let what = report.whatHappened.trimmingCharacters(in: .whitespacesAndNewlines)
        if !what.isEmpty {
            sections.append("### What happened\n\(what)")
        }

        sections.append("""
        ### Environment
        - App: \(report.appVersion) (\(report.build))
        - macOS: \(report.osVersion)
        - Device: \(report.device)
        - Memory: \(report.memoryGB) GB
        - Active brain: \(report.activeBrain)
        """)

        let logs = report.logs.trimmingCharacters(in: .whitespacesAndNewlines)
        sections.append("### Recent logs (redacted)\n```\n\(logs.isEmpty ? "(none captured)" : logs)\n```")

        return sections.joined(separator: "\n\n")
    }
}

public enum GitHubIssueURL {
    public struct Built: Sendable, Equatable {
        public let url: URL
        /// True when the body exceeded the URL cap and was trimmed — the caller
        /// should put the full body on the clipboard and tell the user to paste it.
        public let truncated: Bool
    }

    /// Build a prefilled `…/issues/new` URL. GitHub (and browsers) cap URL length,
    /// so an over-long body is trimmed in the URL and flagged truncated; the full
    /// text is meant to ride the clipboard instead.
    public static func newIssue(
        repo: String,
        title: String,
        body: String,
        maxBodyChars: Int = 6000
    ) -> Built? {
        var body = body
        var truncated = false
        if body.count > maxBodyChars {
            body = String(body.prefix(maxBodyChars))
                + "\n\n_… truncated — the full report is on your clipboard. Paste it here._"
            truncated = true
        }

        var components = URLComponents(string: "https://github.com/\(repo)/issues/new")
        components?.queryItems = [
            URLQueryItem(name: "title", value: title),
            URLQueryItem(name: "body", value: body),
        ]
        guard let url = components?.url else { return nil }
        return Built(url: url, truncated: truncated)
    }
}
