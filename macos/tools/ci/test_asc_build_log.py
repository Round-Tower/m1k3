"""Unit tests for the pure helpers in asc_build_log.

The JWT-sign / HTTP / zip paths are verify-by-run (they need the real .p8 + the
ASC API); everything that shapes a decision off the API JSON is tested here.

Signed: Kev + claude-opus-4-8, 2026-06-20, Confidence 0.85, Prior: Unknown
"""
from __future__ import annotations

import asc_build_log as m


class TestJWTClaims:
    def test_carries_issuer_audience_and_expiry_window(self):
        claims = m.jwt_claims("issuer-uuid", now=1000, ttl=600)
        assert claims == {
            "iss": "issuer-uuid",
            "iat": 1000,
            "exp": 1600,
            "aud": "appstoreconnect-v1",
        }

    def test_default_ttl_is_within_asc_limit(self):
        claims = m.jwt_claims("x", now=0)
        assert claims["exp"] - claims["iat"] <= 1200


def _action(status: str, name: str = "Test - macOS", action_type: str = "TEST", **counts):
    return {
        "id": "abc",
        "attributes": {
            "name": name,
            "actionType": action_type,
            "completionStatus": status,
            "issueCounts": counts or {},
        },
    }


class TestFailureFiltering:
    def test_is_failed_true_only_for_failed_status(self):
        assert m.is_failed(_action("FAILED")) is True
        assert m.is_failed(_action("SUCCEEDED")) is False
        assert m.is_failed({"attributes": {}}) is False  # missing status

    def test_failed_actions_keeps_only_failures(self):
        actions = [_action("SUCCEEDED"), _action("FAILED"), _action("SUCCEEDED")]
        assert m.failed_actions(actions) == [actions[1]]


class TestSummaries:
    def test_action_summary_includes_status_and_counts(self):
        summary = m.action_summary(_action("FAILED", errors=2, warnings=1, testFailures=0))
        assert "Test - macOS" in summary
        assert "FAILED" in summary
        assert "errors=2" in summary and "warnings=1" in summary

    def test_action_summary_defaults_missing_counts_to_zero(self):
        assert "errors=0" in m.action_summary(_action("SUCCEEDED"))


class TestFormatIssue:
    def test_issue_with_file_source_shows_location(self):
        issue = {
            "attributes": {
                "issueType": "ERROR",
                "message": "no such module 'FoundationModels'\n",
                "fileSource": {"path": "/repo/x.swift", "lineNumber": 12},
            }
        }
        out = m.format_issue(issue)
        assert out == "[ERROR] no such module 'FoundationModels'  @ /repo/x.swift:12"

    def test_issue_without_source_has_no_location_suffix(self):
        issue = {"attributes": {"issueType": "WARNING", "message": "heads up"}}
        assert m.format_issue(issue) == "[WARNING] heads up"

    def test_path_with_null_line_number_has_no_trailing_none(self):
        issue = {
            "attributes": {
                "issueType": "ERROR",
                "message": "script failed",
                "fileSource": {"path": "/repo/ci_pre.sh", "lineNumber": None},
            }
        }
        assert m.format_issue(issue) == "[ERROR] script failed  @ /repo/ci_pre.sh:"


class TestWantsLogDump:
    def test_small_log_bundle_is_dumped(self):
        assert m.wants_log_dump({"attributes": {"fileType": "LOG_BUNDLE", "fileSize": 623}}) is True

    def test_large_log_bundle_is_skipped(self):
        assert m.wants_log_dump({"attributes": {"fileType": "LOG_BUNDLE", "fileSize": 999_999}}) is False

    def test_non_log_artifact_is_skipped(self):
        assert m.wants_log_dump({"attributes": {"fileType": "TEST_PRODUCTS", "fileSize": 10}}) is False
