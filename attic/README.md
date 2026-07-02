# The Attic

Every companion worth knowing has a past. This is M1K3's.

Before the Mac app there was a Python CLI that spoke through a synthesized
voice in August 2025. Before the RealityKit companion there was a THREE.js
fox in a browser tab. Before the in-app MCP server there was
`mcp_unified_server.py`. Everything the native app does well, something up
here did first — slower, stranger, and out loud.

Nothing in the attic is maintained, but nothing is dead either: the CLI still
boots, the avatar demo still dances, and the smoke tests still run in CI when
this folder changes. It's kept runnable and signed because a project built on
[provenance](https://murphysig.dev) should keep its own.

## The timeline

| When | What | Where up here |
|---|---|---|
| Aug 2025 | **The birth**: Python CLI with voice synthesis — M1K3 speaks | [`_legacy/m1k3.py`](./_legacy/m1k3.py) |
| Aug 2025 | THREE.js avatar + web dashboard, emotion state | [`src/web-avatar/`](./src/web-avatar) · [`design/`](./design) (the original screenshots) |
| Sep–Dec 2025 | RAG engine, vector memory, multi-engine STT/TTS (Kokoro, Piper, Vosk) | [`src/rag/`](./src/rag) · [`src/engines/`](./src/engines) |
| late 2025 | PWA experiment; Tauri desktop popover (⌘⇧M) | [`pwa-deployment/`](./pwa-deployment) · [`src/avatar-popover/`](./src/avatar-popover) |
| Jan 2026 | The Python MCP server — M1K3 as a tool for other agents | [`_legacy/mcp_unified_server.py`](./_legacy/mcp_unified_server.py) |
| Feb 2026 | Last meaningful CLI work; the Mac-native rebuild begins | → [`/macos`](../macos) |
| Jul 2026 | Open-sourced, Apache-2.0 — Mac first, [KMP mobile](../app) next | you are here |

The spec-kit feature specs from the Python era are in [`specs/`](./specs) —
the ideas, as written before the code.

## It still runs

```bash
cd attic
pip install -r requirements.txt

# Download local models (AI + embeddings + voice)
python src/models/loaders/download_models.py

# The original surfaces
python _legacy/m1k3.py --no-voice    # CLI, no audio
python _legacy/m1k3.py --tui --rag   # TUI with retrieval
python _legacy/cli.py                # CLI with the avatar dashboard
```

**Apple Silicon:** the CLI auto-detects an MLX-LM server on port 8080 and
prefers it (`mlx_lm.server --model mlx-community/SmolLM2-135M-Instruct
--port 8080` — full setup in [`docs/MLX_SETUP.md`](./docs/MLX_SETUP.md)).

Backends fall through in priority order so it always answers, even with
nothing installed: MLX-LM → Ollama → HuggingFace Transformers → ctransformers
→ a dependency-free mock.

The avatar web demo: `cd src/web-avatar && npm install && npm run dev` →
<http://localhost:5174/demo-legacy.html>. The Tauri popover:
`cd src/avatar-popover && cargo tauri dev`.

> **Note:** JS/Rust lockfiles were removed when this moved to the attic (a
> museum shouldn't pin known-vulnerable dependency trees) — installs resolve
> fresh, so expect minor drift in the web demos.

## Map

```
attic/
├── _legacy/          # The original CLI + Python MCP server (see _legacy/README.md)
├── src/              # engines (ai/tts/stt) · rag/ · database/ · web-avatar/ · avatar-popover/
├── tests/            # pytest suite — CI runs the curated subset in tests/ci_smoke.txt
├── specs/            # Spec-kit feature specs — the ideas, pre-code
├── design/           # Aug 2025 screenshots — the first face M1K3 ever had
├── docs/             # MLX/STT/Kokoro setup, plans, prompts, PRDs of the era
├── knowledge/        # The original knowledge-base corpus (JSON)
└── SETUP.md          # Python environment setup, as it was
```

## Testing

```bash
cd attic
pip install -r requirements-ci.txt      # slim CI deps
python -m pytest tests/                 # full suite needs the heavy ML stack
```

CI runs the vetted-green subset (`tests/ci_smoke.txt`) via
`.github/workflows/attic.yml`, path-filtered so it only spends minutes when
the attic itself changes. Triage history: [`tests/CI_TRIAGE.md`](./tests/CI_TRIAGE.md).

---

*The present is for sowing, the harvest comes after. The attic is where the
seeds came from.*
