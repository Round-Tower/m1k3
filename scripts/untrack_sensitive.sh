#!/usr/bin/env bash
#
# untrack_sensitive.sh — stop tracking private runtime data that is now gitignored.
#
# This removes files from git's INDEX only (--cached). Your local files are kept.
# After running this and committing, the files stop traveling with the repo, but
# they REMAIN in git history. To purge them from history entirely, see the note
# at the bottom.
#
# Review every line before you run it. Nothing here is destructive to your disk.
#
# Usage:
#   bash scripts/untrack_sensitive.sh          # dry run (default) — shows what would happen
#   bash scripts/untrack_sensitive.sh --apply  # actually untrack + stage
#
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

APPLY=0
[[ "${1:-}" == "--apply" ]] && APPLY=1

# Paths/globs to stop tracking (must already be in .gitignore).
TARGETS=(
  "databases/m1k3_conversations.duckdb"
  "databases/m1k3_conversations.duckdb.wal"
  ".m1k3_session.json"
  "tests/unified-suite/output"
  "scripts/test_report.json"
  "data/test-results"
)

echo "== Files currently tracked that will be untracked =="
to_remove=()
for t in "${TARGETS[@]}"; do
  while IFS= read -r f; do
    [[ -n "$f" ]] && to_remove+=("$f") && echo "  $f"
  done < <(git ls-files -- "$t")
done

if [[ ${#to_remove[@]} -eq 0 ]]; then
  echo "Nothing tracked matches the targets. You're already clean."
  exit 0
fi

if [[ "$APPLY" -eq 0 ]]; then
  echo
  echo "DRY RUN. Re-run with --apply to untrack the ${#to_remove[@]} file(s) above."
  echo "Your working-tree copies will be kept; only git stops tracking them."
  exit 0
fi

git rm -r --cached --quiet "${to_remove[@]}"
echo
echo "Untracked ${#to_remove[@]} file(s) and staged the removal."
echo "Next:"
echo "  git status                 # review"
echo '  git commit -m "chore: stop tracking private runtime data (conversation DB, sessions, reports)"'
echo
echo "NOTE: these files still exist in PAST commits. If they ever contained data"
echo "you need gone from history (the conversation DB likely does), purge with:"
echo "  pip install git-filter-repo"
echo "  git filter-repo --invert-paths --path databases/m1k3_conversations.duckdb \\"
echo "                  --path databases/m1k3_conversations.duckdb.wal"
echo "Do that on a fresh clone and force-push. Coordinate before rewriting history."
