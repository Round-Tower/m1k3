# M1K3 — macOS distribution

Three channels, one signed build foundation. Sequenced by what's unblocked.

## 1. Direct `.dmg` (Developer ID) — **ready now**

The invite-now / Homebrew-cask path. Works with the current entitlements, no
feature loss.

```bash
# one-time, per machine:
xcrun notarytool store-credentials "M1K3-notary" \
  --apple-id "you@round-tower.ie" --team-id 76DJH43A4P \
  --password "<app-specific-password from appleid.apple.com>"

# then, to cut a release:
macos/tools/release/release-macos.sh              # signed + notarized + stapled DMG
macos/tools/release/release-macos.sh --skip-notarize   # local test build (no Gatekeeper pass elsewhere)
```

Output: `/tmp/m1k3-release/M1K3-<version>.dmg` (drag-to-Applications layout).
Signing identity: **Developer ID Application: Round Tower Software Studios Ltd
(76DJH43A4P)** — already installed.

### Homebrew cask (after the first hosted DMG)
A cask formula points at the hosted `.dmg` URL + its sha256. Add once m1k3.app
serves the DMG; `brew install --cask m1k3` then tracks releases. (Formula TODO —
needs the public download URL first.)

## 2. Mac App Store — **one blocker to clear first**

MAS requires sandbox (✅ already on) and rejects two entitlements the app
currently ships:

```
com.apple.security.temporary-exception.mach-lookup.global-name → com.apple.audioanalyticsd
com.apple.security.exception.mach-lookup.global-name           → com.apple.audioanalyticsd
```

These exist **only** for the AVSpeechSynthesizer system-voice path. The app also
ships **Kokoro** (pure on-device ONNX, no mach service). So the MAS build must:
- gate out the AVSpeechSynthesizer/system-voice option (Kokoro becomes the voice), and
- drop those two entitlements from a MAS-specific entitlements file.

Then: an **Apple Distribution** cert (not yet in the keychain — only Developer ID
is), a separate `ExportOptions.plist` with `method=app-store-connect`, an App
Store Connect app record + listing (ASO: title/subtitle/keywords — NOT the bundle
ID), and review. Everything else (network client+server for the loopback MCP, mic,
screen-recording-via-TCC for calls) is MAS-compatible.

## 3. iOS — **later**

The KMP app (`app/`) already targets iOS-future. Separate effort: an iOS
`EmbeddingEngine` impl, the three-brain story on iOS (FM + MLX + Ma), App Store
Connect for iPhone/iPad. Tracked in `app/PLAN_IOS.md`.

---
*Bundle/app ID is `app.m1k3` across platforms (reverse-DNS of m1k3.app). It's
invisible to users and immutable post-launch — ASO lives in the store listing,
never the ID.*
