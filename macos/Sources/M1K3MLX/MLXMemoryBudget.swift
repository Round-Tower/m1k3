//
//  MLXMemoryBudget.swift
//  M1K3MLX
//
//  Process-global MLX memory policy. MLX's Metal buffer cache grows to peak
//  usage and never shrinks by default (documented upstream: ml-explore/mlx
//  #742, #2668), which is how an easy query against a ~4GB model was holding
//  ~16GB of footprint. The fix is twofold: a small RAM-scaled cacheLimit so
//  freed buffers are returned instead of hoarded, and a memoryLimit as a
//  back-pressure safety net on small-RAM fleet Macs (allocations near the
//  limit wait on scheduled tasks rather than erroring).
//
//  The policy is a pure, tested function of physical RAM; the MLX.Memory
//  mutations are verify-by-launch (metallib only resolves inside the .app).
//  The limits are process-global and shared by the embedder and the LLM —
//  apply once, before any MLX work.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.7, Prior: Unknown
//  Context: cache-limit bands (32/64/128MB) chosen from Apple's LLMEval
//  example (20MB) scaled up for steady-state decode headroom; tune after the
//  before/after footprint measurement. memoryLimit = 75% of physical RAM is
//  deliberately tighter than MLX's default (1.5x the device's recommended
//  working set) because M1K3 shares the machine with the user's real work.
//

import Foundation
import MLX
import os

public struct MLXMemoryBudget: Sendable, Equatable {
    /// Metal buffer-cache cap. Freed buffers beyond this are released to the
    /// OS instead of recycled — bounds the "grows to peak, never shrinks" pool.
    public let cacheLimitBytes: Int
    /// Allocator back-pressure threshold: near it, MLX waits on scheduled
    /// tasks instead of growing the footprint.
    public let memoryLimitBytes: Int

    private static let mebibyte = 1_048_576
    private static let gibibyte: UInt64 = 1_073_741_824

    /// The budget for a machine with the given physical RAM.
    public static func budget(forPhysicalMemory physicalMemoryBytes: UInt64) -> MLXMemoryBudget {
        let physicalGB = Double(physicalMemoryBytes) / Double(gibibyte)
        let cacheMB = switch physicalGB {
        case 32...: 128
        case 12...: 64
        default: 32
        }
        return MLXMemoryBudget(
            cacheLimitBytes: cacheMB * mebibyte,
            memoryLimitBytes: Int(physicalMemoryBytes / 4 * 3)
        )
    }

    private static let log = Logger(subsystem: "dev.murphysig.M1K3", category: "mlx-memory")

    /// One-shot application of this machine's budget to the process-global MLX
    /// memory state. Thread-safe and idempotent (static-let once token); call
    /// it from every MLX entry point that could run first — the earliest wins.
    public static func applyOnce() {
        _ = applied
    }

    private static let applied: Void = {
        let budget = Self.budget(forPhysicalMemory: ProcessInfo.processInfo.physicalMemory)
        MLX.Memory.cacheLimit = budget.cacheLimitBytes
        MLX.Memory.memoryLimit = budget.memoryLimitBytes
        let cacheMB = budget.cacheLimitBytes / mebibyte
        let limitMB = budget.memoryLimitBytes / mebibyte
        log.notice("applied budget: cacheLimit=\(cacheMB)MB memoryLimit=\(limitMB)MB")
    }()

    /// Return all cached Metal buffers to the OS. Call at the end of each
    /// generation so an agent turn's N generations don't compound peaks.
    public static func reclaim(label: String) {
        MLX.Memory.clearCache()
        logSnapshot(label: label)
    }

    /// Log MLX memory (active/cache/peak) plus the process physical
    /// footprint — the same number Activity Monitor's Memory column reports.
    /// Stream with:
    /// `log stream --predicate 'subsystem == "dev.murphysig.M1K3" AND category == "mlx-memory"'`
    public static func logSnapshot(label: String) {
        let snapshot = MLX.Memory.snapshot()
        let activeMB = snapshot.activeMemory / mebibyte
        let cacheMB = snapshot.cacheMemory / mebibyte
        let peakMB = snapshot.peakMemory / mebibyte
        let footprintMB = (physicalFootprintBytes() ?? 0) / UInt64(mebibyte)
        log.notice(
            "\(label, privacy: .public) MB: active=\(activeMB) cache=\(cacheMB) peak=\(peakMB) footprint=\(footprintMB)"
        )
    }

    /// `phys_footprint` from task_vm_info — Activity Monitor's "Memory".
    private static func physicalFootprintBytes() -> UInt64? {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(
            MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<integer_t>.size
        )
        let result = withUnsafeMutablePointer(to: &info) { infoPointer in
            infoPointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { rebound in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), rebound, &count)
            }
        }
        guard result == KERN_SUCCESS else { return nil }
        return info.phys_footprint
    }
}
