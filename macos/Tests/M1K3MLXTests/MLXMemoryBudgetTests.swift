//
//  MLXMemoryBudgetTests.swift
//  M1K3MLXTests
//
//  Pure policy tests for the process-global MLX memory budget: the RAM→limit
//  mapping that bounds the Metal buffer cache (which otherwise grows to peak
//  and never shrinks — the observed ~16GB-on-an-easy-query bug). The actual
//  MLX.GPU.set calls are verify-by-launch (metallib only resolves in the .app).
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8, Prior: Unknown
//

import Foundation
@testable import M1K3MLX
import Testing

struct MLXMemoryBudgetTests {
    private func gigabytes(_ count: Double) -> UInt64 {
        UInt64(count * 1_073_741_824)
    }

    @Test("cache limit scales with physical memory in three bands")
    func cacheLimitScalesWithPhysicalMemory() {
        // Below 12GB: smallest cache — every byte matters on fleet minimums.
        #expect(MLXMemoryBudget.budget(forPhysicalMemory: gigabytes(8)).cacheLimitBytes == 32 * 1_048_576)
        // 12GB up to (not including) 32GB.
        #expect(MLXMemoryBudget.budget(forPhysicalMemory: gigabytes(12)).cacheLimitBytes == 64 * 1_048_576)
        #expect(MLXMemoryBudget.budget(forPhysicalMemory: gigabytes(16)).cacheLimitBytes == 64 * 1_048_576)
        #expect(MLXMemoryBudget.budget(forPhysicalMemory: gigabytes(24)).cacheLimitBytes == 64 * 1_048_576)
        // 32GB and above.
        #expect(MLXMemoryBudget.budget(forPhysicalMemory: gigabytes(32)).cacheLimitBytes == 128 * 1_048_576)
        #expect(MLXMemoryBudget.budget(forPhysicalMemory: gigabytes(64)).cacheLimitBytes == 128 * 1_048_576)
    }

    @Test("memory limit is three quarters of physical memory")
    func memoryLimitIsThreeQuartersOfPhysical() {
        let sixteen = MLXMemoryBudget.budget(forPhysicalMemory: gigabytes(16))
        #expect(sixteen.memoryLimitBytes == Int(gigabytes(12)))

        let eight = MLXMemoryBudget.budget(forPhysicalMemory: gigabytes(8))
        #expect(eight.memoryLimitBytes == Int(gigabytes(6)))
    }

    @Test("budget never shrinks as physical memory grows")
    func budgetIsMonotonic() {
        let samples = stride(from: 4.0, through: 128.0, by: 4.0)
            .map { MLXMemoryBudget.budget(forPhysicalMemory: gigabytes($0)) }
        for (smaller, larger) in zip(samples, samples.dropFirst()) {
            #expect(larger.cacheLimitBytes >= smaller.cacheLimitBytes)
            #expect(larger.memoryLimitBytes >= smaller.memoryLimitBytes)
        }
    }
}
