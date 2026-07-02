# M1K3 - Local AI Assistant

@.claude/project-memory.md

> **⚠️ Orientation (2026-07-02):** The **live product** is the Mac-native SwiftUI
> app under **`macos/`** — see `macos/CLAUDE.md`, `macos/PLAN.md` and
> `macos/.claude/project-memory.md`. The entire legacy Python surface (CLI, RAG
> engine, web avatar, PWA, Tauri popover — pre-Mac-app, last meaningful work
> Jan 2026) is archived under **`attic/`**, which preserves the old repo-root
> layout so it still runs as-is (`cd attic` first). The live MCP surface is the
> Mac app's in-app HTTP server (`.mcp.json` points at `127.0.0.1:4242/mcp`);
> the orphaned Python `mcp_unified_server.py` is in `attic/_legacy/`.

Privacy-focused local AI companion for macOS — MLX inference, live voice,
knowledge graph + RAG, and an MCP server.

## Pointers

- **`macos/CLAUDE.md`** — the live product: build, test, architecture. Start here.
- **`macos/README.md`** — human-facing build-from-source.
- **`app/CLAUDE.md`** — 間 AI mobile (Kotlin Multiplatform, slow burn — the next surface).
- **`attic/README.md`** — the archive tour: the original Python CLI, avatar
  experiments, era docs (`attic/docs/`), and how to run any of it.
- **`CONTRIBUTING.md` / `SECURITY.md`** — public-repo contributor surface.

## Test (attic Python, only when touching attic/)

```bash
cd attic
pip install duckdb                    # required by tests/conftest.py
python -m pytest tests/               # full legacy suite
# CI (attic.yml, path-filtered) runs only the smoke subset: tests/ci_smoke.txt
```

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- ⚠️ Graph last built 2026-06-14 and materially stale for the live Mac app (indexes 322 of ~475 current macos/ Swift files, plus gitignored build junk). There is no `graphify` binary on PATH — it runs via the graphify *skill*, not a shell command. Prefer direct reads under `macos/` until the graph is rebuilt.
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
