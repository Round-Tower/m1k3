//
//  ArtifactSandboxPolicyTests.swift
//  M1K3PreviewTests
//
//  Pins the two security decisions that keep the artifact preview hermetic:
//  the WKContentRuleList JSON (sub-resource block) and the navigation scheme
//  allowlist. Both render UNTRUSTED model-generated HTML, so a silent typo in
//  either must fail a test, not ship.
//

import Foundation
@testable import M1K3Preview
import Testing

struct ArtifactSandboxPolicyTests {
    // MARK: - Content rule list JSON

    @Test("the content rule list is valid JSON")
    func ruleListIsValidJSON() throws {
        let data = Data(ArtifactSandboxPolicy.contentRuleListJSON.utf8)
        let parsed = try JSONSerialization.jsonObject(with: data)
        let array = try #require(parsed as? [[String: Any]])
        #expect(array.count >= 1)
    }

    @Test("the rule list blocks ALL urls via a catch-all .* trigger")
    func ruleListHasCatchAllBlock() throws {
        let data = Data(ArtifactSandboxPolicy.contentRuleListJSON.utf8)
        let array = try #require(
            try JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        )
        let blockRule = array.first { rule in
            let trigger = rule["trigger"] as? [String: Any]
            let action = rule["action"] as? [String: Any]
            return (trigger?["url-filter"] as? String) == ".*"
                && (action?["type"] as? String) == "block"
        }
        #expect(blockRule != nil, "a typo in the catch-all block silently disables the seal")
    }

    @Test("the rule list re-allows only about: and data: via ignore-previous-rules")
    func ruleListAllowsOnlyInertSchemes() throws {
        let data = Data(ArtifactSandboxPolicy.contentRuleListJSON.utf8)
        let array = try #require(
            try JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        )
        let ignored = array.compactMap { rule -> String? in
            let trigger = rule["trigger"] as? [String: Any]
            let action = rule["action"] as? [String: Any]
            guard (action?["type"] as? String) == "ignore-previous-rules" else { return nil }
            return trigger?["url-filter"] as? String
        }
        #expect(Set(ignored) == ["^about:", "^data:"])
    }

    // MARK: - Navigation scheme allowlist

    @Test("inert/in-page navigation schemes are allowed")
    func allowsInertSchemes() {
        #expect(ArtifactSandboxPolicy.allowsNavigation(scheme: "about"))
        #expect(ArtifactSandboxPolicy.allowsNavigation(scheme: "data"))
        #expect(ArtifactSandboxPolicy.allowsNavigation(scheme: ""))
        // A nil scheme (relative / in-page anchor) is treated as the empty scheme → allowed.
        #expect(ArtifactSandboxPolicy.allowsNavigation(scheme: nil))
    }

    @Test("network and local-file schemes are cancelled")
    func cancelsNetworkSchemes() {
        for scheme in ["http", "https", "file", "ftp", "javascript", "ws", "wss"] {
            #expect(
                !ArtifactSandboxPolicy.allowsNavigation(scheme: scheme),
                "\(scheme) must not be navigable from the hermetic preview"
            )
        }
    }

    // MARK: - Content Security Policy (defense in depth)

    @Test("the CSP seals the egress vectors the rule list misses (connect-src none)")
    func cspBlocksConnectAndDefaultSrc() {
        let csp = ArtifactSandboxPolicy.contentSecurityPolicy
        // connect-src 'none' is what stops WebSocket/fetch/XHR/EventSource — the
        // documented WKContentRuleList blind spot.
        #expect(csp.contains("connect-src 'none'"))
        #expect(csp.contains("default-src 'none'"))
    }

    @Test("the CSP meta tag is a well-formed http-equiv element")
    func cspMetaTagIsWellFormed() {
        let tag = ArtifactSandboxPolicy.cspMetaTag
        #expect(tag.contains("http-equiv=\"Content-Security-Policy\""))
        #expect(tag.contains(ArtifactSandboxPolicy.contentSecurityPolicy))
    }
}
