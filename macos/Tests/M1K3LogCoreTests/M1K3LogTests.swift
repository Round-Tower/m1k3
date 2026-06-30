//
//  M1K3LogTests.swift
//  M1K3LogCoreTests
//
//  Pure unit tests for the logging catalogue. The subsystem-uniformity guard
//  (the real drift-prevention) lives in SubsystemGuardTests.swift.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.9. Prior: Unknown.
//

import Foundation
@testable import M1K3LogCore
import Testing

struct M1K3LogTests {
    @Test("subsystem is the one canonical value the diagnostics path filters on")
    func subsystemIsCanonical() {
        #expect(M1K3Log.subsystem == "app.m1k3")
    }

    @Test("every category raw value is unique — no silent de-correlation")
    func categoryRawValuesAreUnique() {
        let raws = M1K3Log.Category.allCases.map(\.rawValue)
        #expect(Set(raws).count == raws.count, "duplicate category raw values: \(raws)")
    }

    @Test("category raw values are lowercase kebab/word tokens (Console-friendly)")
    func categoryRawValuesAreWellFormed() {
        let allowed = /^[a-z0-9-]+$/
        for category in M1K3Log.Category.allCases {
            #expect(
                (try? allowed.firstMatch(in: category.rawValue)) != nil,
                "category \(category) has a non-conforming raw value '\(category.rawValue)'"
            )
        }
    }

    @Test("the agentLoop convenience maps to the agent-loop category")
    func agentLoopCategory() {
        // The Logger API exposes no readable subsystem/category, so we assert the
        // contract via the catalogue rather than the constructed instance.
        #expect(M1K3Log.Category.agentLoop.rawValue == "agent-loop")
    }

    @Test("LogPreview collapses whitespace and caps length")
    func previewCollapsesAndCaps() {
        #expect(LogPreview.preview("") == "")
        #expect(LogPreview.preview("a\n\n  b\t c") == "a b c")
        let long = String(repeating: "x", count: 500)
        let capped = LogPreview.preview(long, max: 20)
        #expect(capped.count == 21) // 20 chars + the ellipsis
        #expect(capped.hasSuffix("…"))
    }
}
