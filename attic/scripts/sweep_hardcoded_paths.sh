#!/usr/bin/env bash
#
# sweep_hardcoded_paths.sh — anonymize hardcoded local paths before sharing the repo.
#
# Replaces absolute home paths that leak your username (and don't work on anyone
# else's machine) with portable placeholders, across TRACKED text files only:
#
#   /Users/kevinmurphy/Development/m1k3   ->  $M1K3_ROOT      (repo root)
#   /Users/kevinmurphy                    ->  $HOME           (everything else under home)
#
# Binary files, the .git dir, and generated test-report HTML are skipped.
# (The test reports are better untracked — see scripts/untrack_sensitive.sh.)
#
# DRY RUN by default: shows a diff preview and a per-file count, changes nothing.
#
# Usage:
#   bash scripts/sweep_hardcoded_paths.sh            # dry run
#   bash scripts/sweep_hardcoded_paths.sh --apply    # rewrite files in place
#
# Safety: review `git diff` afterwards. Revert anytime with `git checkout -- <file>`.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

APPLY=0
[[ "${1:-}" == "--apply" ]] && APPLY=1

USER_HOME="/Users/kevinmurphy"
REPO_ABS="$USER_HOME/Development/m1k3"

# Collect tracked files that contain the username path, excluding binaries
# and generated report HTML. (while-read loop, not `mapfile` — macOS ships
# bash 3.2 where `mapfile`/`readarray` don't exist.)
files=()
while IFS= read -r line; do
  [[ -n "$line" ]] && files+=("$line")
done < <(
  git ls-files -z \
    | xargs -0 grep -lI "$USER_HOME" 2>/dev/null \
    | grep -vE 'tests/unified-suite/output/' || true
)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "No tracked text files contain $USER_HOME. Already clean."
  exit 0
fi

total=0
echo "== Files with hardcoded paths =="
for f in "${files[@]}"; do
  c=$(grep -cI "$USER_HOME" "$f" || true)
  total=$((total + c))
  printf "  %4s  %s\n" "$c" "$f"
done
echo "-- $total occurrence(s) across ${#files[@]} file(s) --"

# sed expression: longer/more-specific path first.
SED_PROG="s#${REPO_ABS}#\$M1K3_ROOT#g; s#${USER_HOME}#\$HOME#g"

if [[ "$APPLY" -eq 0 ]]; then
  echo
  echo "Preview (first file, unified diff):"
  f="${files[0]}"
  sed "$SED_PROG" "$f" | diff -u "$f" - || true
  echo
  echo "DRY RUN. Re-run with --apply to rewrite all ${#files[@]} file(s)."
  echo "Mapping:"
  echo "  $REPO_ABS  ->  \$M1K3_ROOT"
  echo "  $USER_HOME            ->  \$HOME"
  exit 0
fi

for f in "${files[@]}"; do
  sed -i.bak "$SED_PROG" "$f" && rm -f "$f.bak"
done
echo
echo "Rewrote ${#files[@]} file(s). Review with:  git diff"
echo "Heads-up: \$M1K3_ROOT is a placeholder — for scripts that need a real value,"
echo "define it (e.g. export M1K3_ROOT=\"\$(pwd)\") or swap to a relative path."
echo "Revert anything you don't like:  git checkout -- <file>"
