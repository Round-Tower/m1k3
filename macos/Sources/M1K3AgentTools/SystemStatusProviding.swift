//
//  SystemStatusProviding.swift
//  M1K3AgentTools
//
//  The OS seam behind SystemStatusTool. Snapshots are pure values; the live
//  provider is the only code touching IOKit/FileManager/ProcessInfo, kept
//  thin and covered by a smoke test (values aren't deterministic).
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.8, Prior: Unknown

import Foundation

// IOKit is macOS-only. The battery lane is the ONLY IOKit user; guarding the
// import (rather than the whole file) keeps the disk/uptime lanes — and every
// pure snapshot type — portable to iOS/visionOS for the shared shell. macOS
// compiles the exact same code as before (byte-identical behaviour).
#if canImport(IOKit)
    import IOKit.ps
#endif

public struct BatterySnapshot: Sendable, Equatable {
    public let percentage: Int
    public let isCharging: Bool

    public init(percentage: Int, isCharging: Bool) {
        self.percentage = percentage
        self.isCharging = isCharging
    }
}

public struct DiskSnapshot: Sendable, Equatable {
    public let availableBytes: Int64
    public let totalBytes: Int64

    public init(availableBytes: Int64, totalBytes: Int64) {
        self.availableBytes = availableBytes
        self.totalBytes = totalBytes
    }
}

public protocol SystemStatusProviding: Sendable {
    /// nil on a Mac with no battery (desktop).
    func batterySnapshot() -> BatterySnapshot?
    func diskSnapshot() throws -> DiskSnapshot
    func uptime() -> TimeInterval
}

public struct LiveSystemStatusProvider: SystemStatusProviding {
    public init() {}

    public func batterySnapshot() -> BatterySnapshot? {
        #if canImport(IOKit)
            guard let blob = IOPSCopyPowerSourcesInfo()?.takeRetainedValue(),
                  let sources = IOPSCopyPowerSourcesList(blob)?.takeRetainedValue() as? [CFTypeRef]
            else { return nil }

            for source in sources {
                guard let info = IOPSGetPowerSourceDescription(blob, source)?
                    .takeUnretainedValue() as? [String: Any],
                    let capacity = info[kIOPSCurrentCapacityKey] as? Int,
                    let max = info[kIOPSMaxCapacityKey] as? Int, max > 0
                else { continue }
                let charging = (info[kIOPSIsChargingKey] as? Bool) ?? false
                return BatterySnapshot(percentage: capacity * 100 / max, isCharging: charging)
            }
            return nil
        #else
            // iOS/visionOS: no IOKit power-sources API. Battery is optional (nil on a
            // desktop Mac too), so the tool degrades cleanly. A UIDevice-based lane is
            // a Phase-B follow — it needs MainActor hops this nonisolated seam avoids.
            return nil
        #endif
    }

    public func diskSnapshot() throws -> DiskSnapshot {
        let home = URL(fileURLWithPath: NSHomeDirectory())
        let values = try home.resourceValues(forKeys: [
            .volumeAvailableCapacityForImportantUsageKey,
            .volumeTotalCapacityKey,
        ])
        return DiskSnapshot(
            availableBytes: values.volumeAvailableCapacityForImportantUsage ?? 0,
            totalBytes: Int64(values.volumeTotalCapacity ?? 0)
        )
    }

    public func uptime() -> TimeInterval {
        ProcessInfo.processInfo.systemUptime
    }
}
