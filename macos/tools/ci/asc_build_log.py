#!/usr/bin/env python3
"""Pull an Xcode Cloud build run's failure detail from the App Store Connect API.

Diagnose a failed Release build without leaving the terminal — which action
failed, the structured compiler/test issues, and (for small failures) the actual
script logs. Read-only.

    ISSUER_ID=<uuid> python3 asc_build_log.py <ciBuildRun-UUID> [--logs]

Auth: signs a short-lived ES256 JWT from the .p8 key. Defaults match the key at
~/.appstoreconnect/AuthKey_NALJX29Z64.p8 — override with KEY_ID / KEY_PATH.
The build-run UUID is the last path segment of the ASC build URL (the
target_url GitHub's "M1K3 | Release" commit status points at).

The pure helpers (claims/filtering/formatting) are unit-tested in
test_asc_build_log.py; the JWT-sign + HTTP + zip I/O is verify-by-run.

Signed: Kev + claude-opus-4-8, 2026-06-20, Confidence 0.85, Prior: Unknown
"""
from __future__ import annotations

import io
import os
import sys
import time
import zipfile
from typing import Any, Iterable

AUDIENCE = "appstoreconnect-v1"
BASE = "https://api.appstoreconnect.apple.com/v1"
DEFAULT_KEY_ID = "NALJX29Z64"
SMALL_LOG_BYTES = 50_000  # only auto-dump log bundles smaller than this

# --------------------------------------------------------------------------- #
# Pure helpers (unit-tested)
# --------------------------------------------------------------------------- #


def jwt_claims(issuer_id: str, now: int, ttl: int = 600) -> dict[str, Any]:
    """The App Store Connect JWT claim set. ttl must be <= 1200s (ASC rejects more)."""
    return {"iss": issuer_id, "iat": now, "exp": now + ttl, "aud": AUDIENCE}


def is_failed(action: dict[str, Any]) -> bool:
    """True when a ciBuildAction's completionStatus is FAILED."""
    return (action.get("attributes") or {}).get("completionStatus") == "FAILED"


def failed_actions(actions: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return [a for a in actions if is_failed(a)]


def action_summary(action: dict[str, Any]) -> str:
    attrs = action.get("attributes") or {}
    counts = attrs.get("issueCounts") or {}
    return (
        f"{attrs.get('name')} [{attrs.get('actionType')}] → {attrs.get('completionStatus')} "
        f"(errors={counts.get('errors', 0)} warnings={counts.get('warnings', 0)} "
        f"testFailures={counts.get('testFailures', 0)})"
    )


def format_issue(issue: dict[str, Any]) -> str:
    attrs = issue.get("attributes") or {}
    source = attrs.get("fileSource") or {}
    location = f"  @ {source['path']}:{source.get('lineNumber') or ''}" if source.get("path") else ""
    message = (attrs.get("message") or "").strip()
    return f"[{attrs.get('issueType')}] {message}{location}"


def wants_log_dump(artifact: dict[str, Any]) -> bool:
    """A small LOG_BUNDLE worth auto-extracting (the tiny ones hold the error)."""
    attrs = artifact.get("attributes") or {}
    return attrs.get("fileType") == "LOG_BUNDLE" and (attrs.get("fileSize") or 0) <= SMALL_LOG_BYTES


# --------------------------------------------------------------------------- #
# I/O (verify-by-run)
# --------------------------------------------------------------------------- #


def _sign_token(issuer_id: str, key_id: str, key_path: str) -> str:
    import jwt  # local import so the pure helpers need no crypto dep

    with open(key_path) as handle:
        private_key = handle.read()
    return jwt.encode(
        jwt_claims(issuer_id, int(time.time())),
        private_key,
        algorithm="ES256",
        headers={"kid": key_id},
    )


def _get(path: str, token: str) -> dict[str, Any]:
    import requests

    url = path if path.startswith("http") else f"{BASE}{path}"
    resp = requests.get(url, headers={"Authorization": f"Bearer {token}"}, timeout=30)
    if resp.status_code >= 400:
        print(f"  HTTP {resp.status_code} on {url}\n  {resp.text[:400]}", file=sys.stderr)
        resp.raise_for_status()
    return resp.json()


def _dump_log_bundle(url: str) -> None:
    import requests

    archive = zipfile.ZipFile(io.BytesIO(requests.get(url, timeout=60).content))
    for name in archive.namelist():
        print(f"   --- {name} ---")
        print("   " + archive.read(name).decode("utf-8", "replace").replace("\n", "\n   ")[:4000])


def main(argv: list[str]) -> int:
    issuer_id = os.environ.get("ISSUER_ID")
    if not issuer_id:
        print("Set ISSUER_ID=<uuid> (ASC → Users and Access → Integrations).", file=sys.stderr)
        return 2
    args = [a for a in argv[1:] if not a.startswith("--")]
    if not args:
        print(__doc__)
        return 2
    run_id = args[0]
    show_logs = "--logs" in argv

    key_id = os.environ.get("KEY_ID", DEFAULT_KEY_ID)
    key_path = os.environ.get("KEY_PATH", os.path.expanduser(f"~/.appstoreconnect/AuthKey_{key_id}.p8"))
    token = _sign_token(issuer_id, key_id, key_path)

    run = _get(f"/ciBuildRuns/{run_id}", token)["data"]["attributes"]
    print(f"Build #{run.get('number')} — {run.get('executionProgress')} / {run.get('completionStatus')}")

    actions = _get(f"/ciBuildRuns/{run_id}/actions", token).get("data", [])
    print(f"\n{len(actions)} action(s):")
    for action in actions:
        print(f"\n── {action_summary(action)}")
        if not is_failed(action):
            continue
        for issue in _get(f"/ciBuildActions/{action['id']}/issues", token).get("data", [])[:25]:
            print(f"   • {format_issue(issue)}")
        if show_logs:
            for artifact in _get(f"/ciBuildActions/{action['id']}/artifacts", token).get("data", []):
                if wants_log_dump(artifact) and (artifact["attributes"].get("downloadUrl")):
                    print(f"   ===== {artifact['attributes']['fileName']} =====")
                    _dump_log_bundle(artifact["attributes"]["downloadUrl"])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
