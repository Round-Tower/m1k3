#!/usr/bin/env python3
"""One-shot cleanup: strip <think> blocks from call summary chunks in M1K3's knowledge store.

Applies the same logic as ThinkStripper.swift:
  1. If there's a </think>, discard everything before it
  2. Remove any remaining <think>…</think> pairs
  3. Deduplicate consecutive repeated lines (the 6×-repetition bug)
  4. Rebuild the FTS index

Usage:
    python3 tools/cleanup_think_leak.py [--dry-run]
"""

import re
import sqlite3
import sys
from pathlib import Path

DB_PATH = Path.home() / "Library/Containers/app.m1k3/Data/Library/Application Support/M1K3/knowledge.sqlite"


def strip_think(text: str) -> str:
    working = text
    close_idx = working.find("</think>")
    if close_idx != -1:
        working = working[close_idx + len("</think>"):]
    working = re.sub(r"<think>.*?</think>", "", working, flags=re.DOTALL)
    return working.strip()


def dedup_lines(text: str) -> str:
    lines = text.split("\n")
    deduped = []
    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        if deduped and stripped == deduped[-1].strip():
            continue
        deduped.append(line)
    return "\n".join(deduped)


def main():
    dry_run = "--dry-run" in sys.argv

    if not DB_PATH.exists():
        print(f"Database not found: {DB_PATH}")
        sys.exit(1)

    conn = sqlite3.connect(str(DB_PATH))
    cursor = conn.cursor()

    cursor.execute("""
        SELECT c.id, c.item_id, i.title, c.content
        FROM knowledge_chunks c
        JOIN knowledge_items i ON c.item_id = i.id
        WHERE c.content LIKE '%<think>%' OR c.content LIKE '%</think>%'
    """)
    rows = cursor.fetchall()

    if not rows:
        print("No think-contaminated chunks found. Nothing to clean.")
        conn.close()
        return

    print(f"Found {len(rows)} contaminated chunk(s):\n")

    for chunk_id, item_id, title, content in rows:
        cleaned = dedup_lines(strip_think(content))
        print(f"  {title}")
        print(f"    chunk: {chunk_id}")
        print(f"    before: {len(content)} chars")
        print(f"    after:  {len(cleaned)} chars")
        print(f"    preview: {cleaned[:120]}...")
        print()

        if not dry_run:
            cursor.execute(
                "UPDATE knowledge_chunks SET content = ? WHERE id = ?",
                (cleaned, chunk_id),
            )
            cursor.execute(
                """UPDATE knowledge_chunk_fts
                   SET content = ?
                   WHERE id = ?""",
                (cleaned, chunk_id),
            )

    if not dry_run:
        cursor.execute("INSERT INTO knowledge_chunk_fts(knowledge_chunk_fts) VALUES('rebuild')")
        conn.commit()
        print(f"Cleaned {len(rows)} chunk(s) + rebuilt FTS index.")
    else:
        print("[dry-run] No changes written.")

    conn.close()


if __name__ == "__main__":
    main()
