#!/bin/bash
# ci_post_xcodebuild.sh — Xcode Cloud post-build hook for M1K3 (macOS)
#
# Xcode Cloud distributes to TestFlight NATIVELY (configured in the workflow) —
# no upload script needed here. This just logs the outcome.
set -euo pipefail

echo "=== M1K3 CI: Post-Build ==="
echo "CI_XCODEBUILD_ACTION:    ${CI_XCODEBUILD_ACTION:-unknown}"
echo "CI_XCODEBUILD_EXIT_CODE: ${CI_XCODEBUILD_EXIT_CODE:-unknown}"

if [ "${CI_XCODEBUILD_EXIT_CODE:-1}" = "0" ]; then
  echo "✅ Build/Test succeeded"
else
  echo "❌ Build/Test failed (exit ${CI_XCODEBUILD_EXIT_CODE:-unknown})"
fi

echo "=== Post-Build Complete ==="
