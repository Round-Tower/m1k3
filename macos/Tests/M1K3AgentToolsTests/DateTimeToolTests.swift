//
//  DateTimeToolTests.swift
//  M1K3AgentToolsTests
//
//  DateTimeDescriber is pure under injected Date/TimeZone/Locale, so the
//  expected strings are exact. The tool itself just wraps the describer with
//  an injectable clock — local models have no clock without this.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3AgentTools
import Testing

struct DateTimeDescriberTests {
    private let dublin = TimeZone(identifier: "Europe/Dublin")!
    private let irishEnglish = Locale(identifier: "en_IE")

    @Test("describes a known instant with weekday, date, time and zone")
    func describesKnownInstant() {
        // 2026-06-09 14:32:00 UTC == 15:32 IST (Dublin is UTC+1 in June).
        let date = Date(timeIntervalSince1970: 1_781_015_520)
        let described = DateTimeDescriber.describe(date, timeZone: dublin, locale: irishEnglish)
        #expect(described == "Tuesday, 9 June 2026, 15:32 (Europe/Dublin)")
    }

    @Test("respects the injected time zone")
    func respectsTimeZone() throws {
        let date = Date(timeIntervalSince1970: 1_781_015_520)
        let tokyo = try #require(TimeZone(identifier: "Asia/Tokyo"))
        let described = DateTimeDescriber.describe(date, timeZone: tokyo, locale: irishEnglish)
        #expect(described == "Tuesday, 9 June 2026, 23:32 (Asia/Tokyo)")
    }
}

struct DateTimeToolTests {
    @Test("returns the described injected now and ignores the argument")
    func returnsDescribedNow() async throws {
        let fixedNow = Date(timeIntervalSince1970: 1_781_015_520)
        let tool = try DateTimeTool(
            now: { fixedNow },
            timeZone: #require(TimeZone(identifier: "Europe/Dublin")),
            locale: Locale(identifier: "en_IE")
        )
        let result = try await tool.execute(input: ["query": "whatever"])
        #expect(result.output == "Tuesday, 9 June 2026, 15:32 (Europe/Dublin)")
    }

    @Test("declares the agent-facing contract")
    func declaresContract() {
        let tool = DateTimeTool()
        #expect(tool.name == "datetime")
        #expect(!tool.description.isEmpty)
        #expect(tool.parameters.count == 1)
    }
}
