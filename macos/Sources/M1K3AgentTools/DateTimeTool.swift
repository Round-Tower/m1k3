//
//  DateTimeTool.swift
//  M1K3AgentTools
//
//  Local models have no clock — "what's the date?" is unanswerable without
//  this. The describer is pure (deterministic under injected Date/TimeZone/
//  Locale); the tool wraps it with an injectable `now` so tests pin exact
//  strings and the app gets the real clock by default.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.9, Prior: Unknown

import Foundation
import M1K3Agent
import M1K3Inference

/// Pure date → human-sentence formatting, exact under injected inputs.
enum DateTimeDescriber {
    /// "Tuesday, 9 June 2026, 15:32 (Europe/Dublin)"
    static func describe(_ date: Date, timeZone: TimeZone, locale: Locale) -> String {
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = timeZone
        formatter.dateFormat = "EEEE, d MMMM yyyy, HH:mm"
        return "\(formatter.string(from: date)) (\(timeZone.identifier))"
    }
}

public struct DateTimeTool: AgentTool {
    public let name = "datetime"
    public let description =
        "Get the current date and time on \(HostPlatform.thisDevice). Argument: optional, ignored."
    public let parameters = [
        ToolParameter(name: "query", description: "ignored"),
    ]

    private let now: @Sendable () -> Date
    private let timeZone: TimeZone
    private let locale: Locale

    public init(
        now: @escaping @Sendable () -> Date = { Date() },
        timeZone: TimeZone = .current,
        locale: Locale = .current
    ) {
        self.now = now
        self.timeZone = timeZone
        self.locale = locale
    }

    public func execute(input _: [String: String]) async throws -> ToolResult {
        ToolResult(output: DateTimeDescriber.describe(now(), timeZone: timeZone, locale: locale))
    }
}
