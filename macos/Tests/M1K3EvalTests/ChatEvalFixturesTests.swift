//
//  ChatEvalFixturesTests.swift
//  M1K3EvalTests
//
//  The fixtures are hand-data; these tests guard the invariants the runner and
//  scorer assume — every kind populated, ids unique, expectations matched to
//  their kind — so a careless edit can't silently weaken the eval.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9. Prior: Unknown

@testable import M1K3Eval
import Testing

struct ChatEvalFixturesTests {
    @Test("every task-kind has at least five fixtures")
    func everyKindCovered() {
        for kind in TaskKind.allCases {
            let count = ChatEvalFixtures.fixtures(for: kind).count
            #expect(count >= 5, "\(kind.label) has only \(count)")
        }
    }

    @Test("fixture ids are unique")
    func uniqueIDs() {
        let ids = ChatEvalFixtures.all.map(\.id)
        #expect(Set(ids).count == ids.count)
    }

    @Test("security fixtures are closed-book leak vectors that must decline")
    func securityShape() {
        let security = ChatEvalFixtures.fixtures(for: .security)
        #expect(security.count >= 5)
        for fixture in security {
            #expect(fixture.kind == .security)
            #expect(fixture.seedDoc == nil, "\(fixture.id) is closed-book")
            #expect(fixture.expectation.mustRefuse, "\(fixture.id) must require a decline")
        }
    }

    @Test("all is the concatenation of the per-kind sets")
    func allIsEverything() {
        let perKind = TaskKind.allCases.flatMap { ChatEvalFixtures.fixtures(for: $0) }
        #expect(perKind.count == ChatEvalFixtures.all.count)
    }

    @Test("grounded-Q fixtures seed a doc; non-absent ones require a citation")
    func groundedShape() {
        for fixture in ChatEvalFixtures.groundedQ {
            #expect(fixture.seedDoc != nil, "\(fixture.id) needs a seed doc")
        }
        // The deliberately-absent fixture abstains (no citation possible); the
        // rest demand one.
        let citing = ChatEvalFixtures.groundedQ.filter { $0.expectation.mustCite }
        #expect(citing.count >= 4)
    }

    @Test("plausible-but-wrong fixtures exist and never demand a citation (nothing citable)")
    func falsePremiseShape() {
        let ids: Set = ["ground-wrong-author", "ground-wrong-nobel", "ground-fictional-accord"]
        let fixtures = ChatEvalFixtures.groundedQ.filter { ids.contains($0.id) }
        #expect(fixtures.count == 3)
        for fixture in fixtures {
            #expect(!fixture.expectation.mustCite, "\(fixture.id) has nothing citable")
            #expect(
                !fixture.expectation.mustContainAny.isEmpty,
                "\(fixture.id) needs a correction/abstention marker"
            )
        }
    }

    @Test("tool-use fixtures each name a required tool")
    func toolShape() {
        for fixture in ChatEvalFixtures.toolUse {
            #expect(fixture.expectation.mustCallTool != nil, "\(fixture.id) names no tool")
        }
    }

    @Test("refusal fixtures all expect a refusal")
    func refusalShape() {
        for fixture in ChatEvalFixtures.refusal {
            #expect(fixture.expectation.mustRefuse, "\(fixture.id) doesn't expect refusal")
        }
    }

    @Test("code-gen fixtures are closed-book, must comply, and assert artifact markers")
    func codeGenShape() {
        let codeGen = ChatEvalFixtures.fixtures(for: .codeGen)
        #expect(codeGen.count >= 5)
        for fixture in codeGen {
            #expect(fixture.kind == .codeGen)
            #expect(fixture.seedDoc == nil, "\(fixture.id) is closed-book — generation, not lookup")
            #expect(fixture.expectation.mustComply, "\(fixture.id) must require compliance")
            #expect(!fixture.expectation.mustRefuse, "\(fixture.id) must not expect a refusal")
            #expect(!fixture.expectation.mustContainAny.isEmpty, "\(fixture.id) needs artifact markers")
        }
    }

    @Test("open-chat fixtures guard against scaffolding leak")
    func openChatGuardsLeak() {
        for fixture in ChatEvalFixtures.openChat {
            #expect(fixture.expectation.mustNotContain.contains("<think>"))
        }
    }
}
