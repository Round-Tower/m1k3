//
//  FTSQueryTests.swift
//  M1K3KnowledgeTests
//
//  Pins the shared FTS5 MATCH-string policy (strict AND + relaxed OR) both
//  stores build on. Moved from KnowledgeStoreTests when the construction was
//  lifted out of KnowledgeStore into FTSQuery.
//
//  Signed: Kev + claude-fable-5, 2026-07-02, Confidence 0.9,
//  Prior: Kev + claude-opus-4-8 (KnowledgeStoreTests.relaxedQueryConstruction).
//

@testable import M1K3Knowledge
import Testing

struct FTSQueryTests {
    @Test("sanitized double-quotes each token, nil when nothing usable")
    func sanitizedConstruction() {
        #expect(FTSQuery.sanitized("Golden Gate milestone")
            == "\"Golden\" \"Gate\" \"milestone\"")
        #expect(FTSQuery.sanitized("say \"cheese\" now")
            == "\"say\" \"cheese\" \"now\"")
        #expect(FTSQuery.sanitized("   ") == nil)
    }

    @Test("relaxed OR-joins quoted tokens, nil below two tokens")
    func relaxedConstruction() {
        #expect(FTSQuery.relaxed("Golden Gate milestone")
            == "\"Golden\" OR \"Gate\" OR \"milestone\"")
        // A single token relaxes to nothing new — strict already covered it.
        #expect(FTSQuery.relaxed("hydraulic") == nil)
        #expect(FTSQuery.relaxed("   ") == nil)
        // Embedded quotes are stripped, same as the strict sanitiser.
        #expect(FTSQuery.relaxed("say \"cheese\" now")
            == "\"say\" OR \"cheese\" OR \"now\"")
    }
}
