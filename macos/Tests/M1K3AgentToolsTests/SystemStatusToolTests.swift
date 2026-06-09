//
//  SystemStatusToolTests.swift
//  M1K3AgentToolsTests
//
//  The formatter is pure (every branch pinned: charging/draining/no battery,
//  GB rounding, uptime pluralisation); the tool is tested against a fake
//  provider. LiveSystemStatusProvider gets a non-crash smoke test only —
//  IOKit/volume values aren't deterministic on CI.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3AgentTools
import Testing

struct SystemStatusFormatterTests {
    private let disk = DiskSnapshot(
        availableBytes: 412_000_000_000,
        totalBytes: 994_000_000_000
    )

    @Test("formats charging battery, disk and multi-day uptime")
    func formatsChargingBattery() {
        let text = SystemStatusFormatter.format(
            battery: BatterySnapshot(percentage: 87, isCharging: true),
            disk: disk,
            uptime: 2 * 86400 + 3 * 3600
        )
        #expect(text == "Battery: 87% (charging). Disk: 412 GB free of 994 GB. Uptime: 2 days 3 hours.")
    }

    @Test("formats draining battery")
    func formatsDrainingBattery() {
        let text = SystemStatusFormatter.format(
            battery: BatterySnapshot(percentage: 41, isCharging: false),
            disk: disk,
            uptime: 3600
        )
        #expect(text == "Battery: 41% (on battery). Disk: 412 GB free of 994 GB. Uptime: 1 hour.")
    }

    @Test("desktop Mac has no battery")
    func formatsMissingBattery() {
        let text = SystemStatusFormatter.format(battery: nil, disk: disk, uptime: 90)
        #expect(text == "Battery: none (desktop). Disk: 412 GB free of 994 GB. Uptime: 1 minute.")
    }

    @Test("uptime singular/plural and sub-minute floor")
    func uptimeGrammar() {
        #expect(SystemStatusFormatter.describeUptime(30) == "under a minute")
        #expect(SystemStatusFormatter.describeUptime(60) == "1 minute")
        #expect(SystemStatusFormatter.describeUptime(2 * 86400) == "2 days")
        #expect(SystemStatusFormatter.describeUptime(86400 + 3600 + 120) == "1 day 1 hour")
    }
}

struct SystemStatusToolTests {
    private struct FakeProvider: SystemStatusProviding {
        func batterySnapshot() -> BatterySnapshot? {
            BatterySnapshot(percentage: 64, isCharging: false)
        }

        func diskSnapshot() throws -> DiskSnapshot {
            DiskSnapshot(availableBytes: 100_000_000_000, totalBytes: 500_000_000_000)
        }

        func uptime() -> TimeInterval {
            7200
        }
    }

    @Test("reports the provider's snapshot as one observation")
    func reportsSnapshot() async throws {
        let tool = SystemStatusTool(provider: FakeProvider())
        let result = try await tool.execute(input: [:])
        #expect(result.output == "Battery: 64% (on battery). Disk: 100 GB free of 500 GB. Uptime: 2 hours.")
    }

    @Test("declares the agent-facing contract")
    func declaresContract() {
        let tool = SystemStatusTool()
        #expect(tool.name == "system_status")
        #expect(!tool.description.isEmpty)
    }

    @Test("live provider smoke: uptime positive, disk readable")
    func liveProviderSmoke() throws {
        let live = LiveSystemStatusProvider()
        #expect(live.uptime() > 0)
        let liveDisk = try live.diskSnapshot()
        #expect(liveDisk.totalBytes > 0)
        #expect(liveDisk.availableBytes > 0)
        #expect(liveDisk.availableBytes <= liveDisk.totalBytes)
        _ = live.batterySnapshot() // may be nil on a desktop — just must not crash
    }
}
