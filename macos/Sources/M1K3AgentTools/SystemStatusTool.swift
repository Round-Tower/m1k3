//
//  SystemStatusTool.swift
//  M1K3AgentTools
//
//  "How's my battery?" — answered locally, no egress. Pure formatter over a
//  SystemStatusProviding seam; the live provider (IOKit power sources, volume
//  capacity, ProcessInfo uptime) lives in SystemStatusProviding.swift.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Agent
import M1K3Inference

/// Pure snapshot → sentence formatting, every branch deterministic.
enum SystemStatusFormatter {
    static func format(battery: BatterySnapshot?, disk: DiskSnapshot, uptime: TimeInterval) -> String {
        let batteryText = if let battery {
            "Battery: \(battery.percentage)% (\(battery.isCharging ? "charging" : "on battery"))."
        } else {
            "Battery: none (desktop)."
        }
        let diskText = "Disk: \(gigabytes(disk.availableBytes)) GB free of \(gigabytes(disk.totalBytes)) GB."
        return "\(batteryText) \(diskText) Uptime: \(describeUptime(uptime))."
    }

    /// "2 days 3 hours" / "1 hour" / "1 minute" / "under a minute" — the two
    /// largest non-zero units, floored.
    static func describeUptime(_ uptime: TimeInterval) -> String {
        let totalMinutes = Int(uptime) / 60
        guard totalMinutes >= 1 else { return "under a minute" }

        let days = totalMinutes / 1440
        let hours = (totalMinutes % 1440) / 60
        let minutes = totalMinutes % 60

        var parts: [String] = []
        if days > 0 { parts.append(unit(days, "day")) }
        if hours > 0 { parts.append(unit(hours, "hour")) }
        if days == 0, minutes > 0 { parts.append(unit(minutes, "minute")) }
        return parts.prefix(2).joined(separator: " ")
    }

    private static func unit(_ count: Int, _ singular: String) -> String {
        "\(count) \(singular)\(count == 1 ? "" : "s")"
    }

    private static func gigabytes(_ bytes: Int64) -> Int {
        Int((Double(bytes) / 1_000_000_000).rounded())
    }
}

public struct SystemStatusTool: AgentTool {
    public let name = "system_status"
    public let description =
        "Get \(HostPlatform.thisDevice)'s battery, free disk space and uptime. Argument: optional, ignored."
    public let parameters = [
        ToolParameter(name: "query", description: "ignored"),
    ]

    private let provider: any SystemStatusProviding

    public init(provider: any SystemStatusProviding = LiveSystemStatusProvider()) {
        self.provider = provider
    }

    public func execute(input _: [String: String]) async throws -> ToolResult {
        let disk = try provider.diskSnapshot()
        let output = SystemStatusFormatter.format(
            battery: provider.batterySnapshot(),
            disk: disk,
            uptime: provider.uptime()
        )
        return ToolResult(output: output)
    }
}
