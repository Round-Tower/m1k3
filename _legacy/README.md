# _legacy — dormant pre-Mac-app Python CLI

Archived **2026-06-21**. This directory holds the original Python command-line /
TUI version of M1K3, last meaningfully touched January 2026. It has been
**superseded by the macOS SwiftUI app under `macos/`**, which is the live product.

These files **import each other and the Python modules under `src/`**. They were
moved here as-is — they are **not guaranteed runnable from this directory**. To
revive, run from the **repo root** with the project root on the path, e.g.:

```bash
PYTHONPATH=. python _legacy/m1k3.py --no-voice
PYTHONPATH=. python _legacy/cli.py
```

## What was intentionally NOT moved

- **`mcp_unified_server.py`** — still live; referenced by `.mcp.json`. Stays at the
  repo root.
- **`src/web-avatar/` and `src/avatar-popover/`** — the live web-avatar and Tauri
  popover surfaces remain under `src/`.

These were left in place on purpose; do not assume they are part of the legacy
archive.

<!--
Signed: Kev + claude-opus-4-8 (via subagent), 2026-06-21, Confidence 0.85, Prior: Unknown
Reversible quarantine of the dormant root-level Python CLI into _legacy/ to declutter
the repo root so macos/ reads clearly as the product. git mv preserved history for the
13 tracked files; voice_input_processor.py was untracked and moved with plain mv.
-->
