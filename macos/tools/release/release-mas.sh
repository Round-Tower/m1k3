#!/bin/bash
# release-mas.sh — build a Mac App Store .pkg for M1K3 (Apple Distribution +
# MAS entitlements + app-store-connect export). The direct .dmg path is
# release-macos.sh; this is the App Store channel.
#
# Differs from the direct build in exactly three ways, all overridden here so
# project.yml (the direct build) stays untouched:
#   • signs with "Apple Distribution" (not Developer ID)
#   • CODE_SIGN_ENTITLEMENTS → M1K3-MAS.entitlements (no audioanalyticsd)
#   • exports method=app-store-connect → a .pkg for App Store Connect upload
#
# Prereqs: a VALID "Apple Distribution: … (76DJH43A4P)" cert in the keychain,
# the Apple ID account configured in Xcode (for -allowProvisioningUpdates to
# register the App ID app.m1k3 + fetch a Mac App Store profile), and eventually
# an App Store Connect app record for the upload step (not needed to BUILD).
#
# bash 3.2-safe.
set -euo pipefail

SCHEME="M1K3"
APP_NAME="M1K3"
TEAM="76DJH43A4P"

HERE="$(cd "$(dirname "$0")" && pwd)"
MACOS_DIR="$(cd "$HERE/../.." && pwd)"
PROJECT="$MACOS_DIR/M1K3.xcodeproj"
MAS_ENTITLEMENTS="$MACOS_DIR/M1K3App/M1K3-MAS.entitlements"
EXPORT_OPTS="$HERE/ExportOptions-MAS.plist"
BUILD="${BUILD_DIR:-/tmp/m1k3-mas}"
ARCHIVE="$BUILD/$APP_NAME.xcarchive"
EXPORT_DIR="$BUILD/export"

VERSION="$(grep -m1 'MARKETING_VERSION:' "$MACOS_DIR/project.yml" | sed -E 's/.*"([^"]+)".*/\1/')"
beautify() { if command -v xcbeautify >/dev/null 2>&1; then xcbeautify; else cat; fi; }

echo "▸ M1K3 Mac App Store build — v$VERSION  (team $TEAM)"
echo

# ── Preflight ────────────────────────────────────────────────────────────────
if ! security find-identity -v 2>/dev/null \
     | grep "Apple Distribution.*$TEAM" | grep -vq "EXPIRED"; then
  echo "✗ No VALID 'Apple Distribution' cert for $TEAM."
  echo "  Xcode → Settings → Accounts → Manage Certificates → ➕ Apple Distribution"
  exit 1
fi
echo "✓ Apple Distribution cert present"
[ -f "$MAS_ENTITLEMENTS" ] || { echo "✗ Missing $MAS_ENTITLEMENTS"; exit 1; }
echo

# ── 1. Archive (automatic signing + MAS entitlements) ────────────────────────
# DON'T override CODE_SIGN_IDENTITY: the project uses automatic signing, which
# manages the identity itself (and a manual override conflicts across every SPM
# dependency). The archive is dev-signed; the app-store-connect EXPORT below
# re-signs with Apple Distribution. Only the entitlements are overridden, so the
# embedded app carries the MAS set (no audioanalyticsd).
echo "▸ [1/4] Archiving (MAS entitlements, automatic signing)…"
rm -rf "$ARCHIVE"
xcodebuild archive \
  -project "$PROJECT" -scheme "$SCHEME" -configuration Release \
  -archivePath "$ARCHIVE" -destination 'generic/platform=macOS' \
  -allowProvisioningUpdates \
  DEVELOPMENT_TEAM="$TEAM" \
  CODE_SIGN_ENTITLEMENTS="$MAS_ENTITLEMENTS" | beautify

# ── 2. Export the .pkg (App Store Connect) ───────────────────────────────────
echo "▸ [2/4] Exporting (app-store-connect)…"
rm -rf "$EXPORT_DIR"
xcodebuild -exportArchive \
  -archivePath "$ARCHIVE" -exportPath "$EXPORT_DIR" \
  -exportOptionsPlist "$EXPORT_OPTS" \
  -allowProvisioningUpdates | beautify

PKG="$(ls "$EXPORT_DIR"/*.pkg 2>/dev/null | head -1)"
[ -n "$PKG" ] || { echo "✗ Export produced no .pkg"; exit 1; }

# ── 3. Verify the package signature ──────────────────────────────────────────
echo "▸ [3/4] Verifying package…"
pkgutil --check-signature "$PKG" 2>&1 | head -6

# ── 4. Confirm the MAS entitlements made it into the embedded app ────────────
echo "▸ [4/4] Confirming MAS entitlements (no audioanalyticsd)…"
APP_IN_ARCHIVE="$ARCHIVE/Products/Applications/$APP_NAME.app"
if codesign -d --entitlements - "$APP_IN_ARCHIVE" 2>/dev/null | grep -q audioanalyticsd; then
  echo "  ✗ audioanalyticsd entitlement present — MAS will REJECT this"
else
  echo "  ✓ no audioanalyticsd entitlement"
fi

echo
echo "✓ MAS package → $PKG  ($(du -h "$PKG" | cut -f1))"
echo "  Upload with: xcrun altool --upload-app -f \"$PKG\" -t macos \\"
echo "    --apple-id <id> --password <app-specific-pw>   (after the App Store Connect app record exists)"
