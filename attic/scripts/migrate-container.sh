#!/bin/bash
# migrate-container.sh — carry the macOS app's sandbox container across the
# 2026-06-14 bundle-ID rename (dev.murphysig.M1K3 → app.m1k3) so the ~6GB of
# cached brains / WhisperKit / embedder / knowledge.sqlite / distilled memories
# don't get re-downloaded under the new identity.
#
# Safe by default: prints what it WOULD do. Pass --apply to actually copy.
#
# Prereq: launch the renamed app ONCE first so macOS creates the NEW container,
# then quit it, then run this.
#
# Runs on stock macOS /bin/bash 3.2 (no mapfile, no associative arrays).
set -euo pipefail

OLD_ID="dev.murphysig.M1K3"
NEW_ID="app.m1k3"
CONTAINERS="$HOME/Library/Containers"
OLD_DATA="$CONTAINERS/$OLD_ID/Data"
NEW_DATA="$CONTAINERS/$NEW_ID/Data"

APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

echo "M1K3 container migration: $OLD_ID → $NEW_ID"
echo

if [ ! -d "$OLD_DATA" ]; then
  echo "✗ Old container not found: $OLD_DATA"
  echo "  Nothing to migrate (already gone, or never existed). Exiting."
  exit 0
fi

if [ ! -d "$NEW_DATA" ]; then
  echo "✗ New container not found: $NEW_DATA"
  echo "  Launch the renamed app ONCE (so macOS creates it), quit it, then re-run."
  exit 1
fi

OLD_SIZE=$(du -sh "$OLD_DATA" 2>/dev/null | cut -f1)
NEW_SIZE=$(du -sh "$NEW_DATA" 2>/dev/null | cut -f1)
echo "  old: $OLD_DATA  ($OLD_SIZE)"
echo "  new: $NEW_DATA  ($NEW_SIZE)"
echo

# Make sure the app isn't running (an open container can corrupt a mid-flight copy).
if pgrep -x "M1K3" >/dev/null 2>&1; then
  echo "✗ M1K3 is running — quit it first (an open sandbox can corrupt the copy)."
  exit 1
fi

if [ "$APPLY" -eq 0 ]; then
  echo "DRY RUN — would run:"
  echo "  rsync -a \"$OLD_DATA/\" \"$NEW_DATA/\""
  echo
  echo "Re-run with --apply to copy. The old container is left intact (your rollback)."
  exit 0
fi

echo "Copying (rsync -a, additive — old container left intact)…"
rsync -a "$OLD_DATA/" "$NEW_DATA/"
echo "✓ Done. New container now: $(du -sh "$NEW_DATA" 2>/dev/null | cut -f1)"
echo "  Relaunch M1K3 — models/memories should be present, no re-download."
echo "  Once confirmed, you can reclaim space: rm -rf \"$CONTAINERS/$OLD_ID\""
