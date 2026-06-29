"""Unit tests for the pure helpers in check_test_scheme."""

import check_test_scheme as m


def test_scheme_test_targets_extracts_blueprint_identifiers():
    xml = """
    <Scheme>
      <TestAction>
        <Testables>
          <TestableReference skipped="NO">
            <BuildableReference BlueprintIdentifier="M1K3InferenceTests" BuildableName="M1K3InferenceTests"></BuildableReference>
          </TestableReference>
          <TestableReference skipped="NO">
            <BuildableReference BlueprintIdentifier="M1K3VoiceTests" BuildableName="M1K3VoiceTests"></BuildableReference>
          </TestableReference>
        </Testables>
      </TestAction>
    </Scheme>
    """
    assert m.scheme_test_targets(xml) == {"M1K3InferenceTests", "M1K3VoiceTests"}


def test_package_test_targets_extracts_only_testTargets():
    pkg = """
        .target(name: "M1K3Inference", path: "Sources/M1K3Inference"),
        .testTarget(
            name: "M1K3InferenceTests",
            dependencies: ["M1K3Inference"]
        ),
        .target(name: "M1K3VoiceTests-NotReal"),
        .testTarget(name: "M1K3VoiceTests", dependencies: ["M1K3Voice"]),
    """
    # Only the two real .testTarget declarations — NOT the plain .target that
    # happens to contain "Tests" in its name.
    assert m.package_test_targets(pkg) == {"M1K3InferenceTests", "M1K3VoiceTests"}


def test_diff_reports_missing_and_stale():
    pkg = {"A", "B", "C"}
    scheme = {"A", "C", "Z"}
    missing, stale = m.diff_targets(scheme=scheme, package=pkg)
    assert missing == {"B"}   # in the package, absent from the scheme → untested!
    assert stale == {"Z"}     # in the scheme, gone from the package → dangling


def test_diff_empty_when_aligned():
    s = {"A", "B"}
    assert m.diff_targets(scheme=s, package=s) == (set(), set())
