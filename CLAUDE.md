# M1K3 - Local AI Assistant

@.claude/project-memory.md

> **⚠️ Orientation (2026-07-02):** The **live product** is the Mac-native SwiftUI
> app under **`macos/`** — see `macos/CLAUDE.md`, `macos/PLAN.md` and
> `macos/.claude/project-memory.md`. The Python CLI is **legacy** (pre-Mac-app,
> last meaningful work Jan 2026), archived under **`_legacy/`**. The live MCP
> surface is the Mac app's in-app HTTP server (`.mcp.json` points at
> `127.0.0.1:4242/mcp` as of 2026-06-30); `mcp_unified_server.py` was orphaned by
> that switch and now lives in `_legacy/` too.

Privacy-focused local AI with voice synthesis, RAG, 3D avatars, and CLI interfaces.

## Pointers

- **`macos/CLAUDE.md`** — the live product: build, test, architecture. Start here.
- **`README.md`** — repo map + the legacy Python surface (run commands, AI backends, layout).
- **`_legacy/README.md`** — the archived CLI: what moved, what stayed, how to revive.
- **`SETUP.md`** — legacy Python environment setup.
- **`app/CLAUDE.md`** — 間 AI mobile (Kotlin Multiplatform, slow burn).
- **`docs/`** — `MLX_SETUP.md` (legacy mlx-lm server), `STT_QUICK_START.md`, `plans/`.
- Avatar web demo (legacy): `cd src/web-avatar && npm run dev` →
  `http://localhost:5174/demo-legacy.html`. The Tauri popover (`src/avatar-popover/`)
  is dormant since 2026-02, superseded by the native Mac companion.

## Test (legacy Python)

```bash
pip install duckdb                    # required by tests/conftest.py
python -m pytest tests/               # full legacy suite
# CI runs only the curated smoke subset: tests/ci_smoke.txt (see tests/CI_TRIAGE.md)
```

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- ⚠️ Graph last built 2026-06-14 and materially stale for the live Mac app (indexes 322 of ~475 current macos/ Swift files, plus gitignored build junk). There is no `graphify` binary on PATH — it runs via the graphify *skill*, not a shell command. Prefer direct reads under `macos/` until the graph is rebuilt.
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
