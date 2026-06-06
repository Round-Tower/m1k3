# Python Test Suite — CI Triage

> Status as of the CI overhaul (2026-06-06). The legacy Python suite under
> `tests/` is **not CI-ready as a whole**: a per-file run is a coin-flip —
> top-level `sys.exit(1)` at import, heavy ML imports (torch/vosk/transformers),
> collection errors, real failures, and multi-second sleeps. Rather than block
> CI on it, we run a **curated green smoke subset** and quarantine the rest for
> rehabilitation.

## How it works
- **`tests/ci_smoke.txt`** — single source of truth: verified green + fast files.
  The `python-smoke` CI job runs exactly these (explicit paths, so the crashy
  files are never imported).
- **`requirements-ci.txt`** — slim deps the smoke set needs (no torch/vosk).
- **Markers** (`tests/conftest.py`): files in the allowlist are auto-marked
  `ci_smoke`, the rest `quarantine`. Locally: `pytest -m quarantine <file>`.

## Smoke subset (green, in CI)
44 files, ~102 tests, ~24s wall. Listed in `tests/ci_smoke.txt`.

## Quarantine backlog (NOT in CI — rehabilitate me)

Each line: `file | pytest-exit-code | last-line`. Exit 2=collection error,
5=no tests collected, 1=test failures. Move a file into `ci_smoke.txt` once
it passes fast under the slim deps (or add what it genuinely needs).

```
tests/cli/test_cli_integration.py|3|no tests ran in 0.06s
tests/cli/test_cli_knowledge.py|5|no tests ran in 0.02s
tests/integration/test_complete_system_integration.py|2|1 error in 0.15s
tests/integration/test_personality_analytics_integration.py|1|3 passed, 4 errors in 16.77s
tests/integration/test_realtime_context_integration.py|2|1 error in 0.14s
tests/personality/test_personality_engine.py|1|7 passed, 3 errors in 0.11s
tests/python/test_adaptive_config_direct.py|2|1 error in 0.16s
tests/python/test_adaptive_system.py|2|1 error in 0.16s
tests/python/test_adaptive_thinking_integration.py|2|1 error in 0.18s
tests/python/test_advanced_expertise.py|2|1 error in 0.15s
tests/python/test_anti_hallucination.py|2|1 error in 0.20s
tests/python/test_comprehensive_vector_memory.py|5|no tests ran in 0.04s
tests/python/test_demo_voice.py|2|1 error in 0.18s
tests/python/test_dynamic_model_monitor_cli.py|2|1 error in 0.17s
tests/python/test_espeak_voice_tuning.py|1|2 passed, 1 error in 10.40s
tests/python/test_gameboy_pixel_engine.py|2|1 error in 0.16s
tests/python/test_hot_loading.py|1|3 passed, 2 warnings, 1 error in 0.18s
tests/python/test_integration_full.py|2|1 error in 0.16s
tests/python/test_intelligent_thinking.py|2|1 error in 0.20s
tests/python/test_listen_method.py|2|1 error in 0.16s
tests/python/test_m1k3_rag_engine.py|1|9 failed, 6 passed in 2.04s
tests/python/test_m1k3_voice_quick.py|5|no tests ran in 0.04s
tests/python/test_macos_stt.py|1|1 failed in 0.17s
tests/python/test_mic_access.py|2|1 error in 0.24s
tests/python/test_mlx_integration.py|1|19 failed in 0.73s
tests/python/test_padding_demo.py|5|no tests ran in 0.12s
tests/python/test_padding_modes.py|1|1 error in 0.12s
tests/python/test_punctuation_padding.py|5|no tests ran in 0.12s
tests/python/test_quick_qwen3.py|2|1 error in 0.19s
tests/python/test_qwen3_integration.py|2|1 error in 0.20s
tests/python/test_qwen3_simple.py|2|1 error in 0.15s
tests/python/test_rag_integration_validation.py|1|8 failed, 5 passed, 4 subtests passed in 0.71s
tests/python/test_rag_quick_validation.py|1|3 failed, 8 passed in 0.41s
tests/python/test_rag_validation.py|2|1 error in 0.28s
tests/python/test_smollm_responses.py|2|1 error in 0.18s
tests/python/test_speech_recognition.py|2|1 error in 0.15s
tests/python/test_streamlined_tts.py|5|no tests ran in 1.06s
tests/python/test_stt_fixes.py|1|2 failed in 0.17s
tests/python/test_task_classification.py|2|1 error in 0.15s
tests/python/test_truncation_fixes.py|2|1 error in 0.16s
tests/python/test_tts_engine_comparison.py|1|1 passed, 2 warnings, 1 error in 6.25s
tests/python/test_voice_fix.py|2|1 error in 0.16s
tests/python/test_voice_smollm2.py|3|no tests ran in 0.05s
tests/python/voice_tests/integration/test_m1k3_live.py|2|1 error in 0.25s
tests/python/voice_tests/profiles/test_m1k3_config.py|2|1 error in 0.24s
tests/python/voice_tests/profiles/test_profile_override.py|2|1 error in 0.17s
tests/python/voice_tests/tts_engines/test_daniel_normal_speed.py|2|1 error in 0.24s
tests/python/voice_tests/tts_engines/test_kokoro_quick.py|2|1 error in 0.25s
tests/python/voice_tests/tts_engines/test_lessac_pipeline.py|2|1 error in 0.24s
tests/python/voice_tests/tts_engines/test_lessac_speeds.py|2|1 error in 0.26s
tests/python/voice_tests/tts_engines/test_m1k3_final_1.4x.py|2|1 error in 0.16s
tests/python/voice_tests/tts_engines/test_m1k3_final_voices.py|2|1 error in 0.24s
tests/python/voice_tests/tts_engines/test_m1k3_fine_tune_speed.py|2|1 error in 0.25s
tests/python/voice_tests/tts_engines/test_m1k3_kokoro_integration.py|2|1 error in 0.27s
tests/test_cli_startup.py|1|1 failed in 0.14s
tests/test_content_specific_effects.py|1|1 failed, 19 passed in 0.21s
tests/test_final_validation.py|2|1 error in 0.15s
tests/test_gemma_2b.py|2|1 error in 0.18s
tests/test_gemma_access.py|5|no tests ran in 0.02s
tests/test_html_code.py|2|1 error in 0.18s
tests/test_html_question.py|2|1 error in 0.18s
tests/test_intelligent_tts_controller.py|1|1 failed, 18 passed in 1.00s
tests/test_long_text_voice.py|2|1 error in 0.14s
tests/test_mcp_server.py|2|1 error in 0.14s
tests/test_minimal_tui.py|2|1 error in 0.14s
tests/test_model_output_parser.py|1|5 failed, 9 passed in 0.11s
tests/test_responses.py|2|1 error in 0.18s
tests/test_scenarios.py|2|1 error in 0.15s
tests/test_tts_live_audio.py|1|8 passed, 1 warning, 2 errors in 0.13s
tests/test_tui_interfaces.py|2|1 error in 0.14s
tests/test_ui_interactive.py|5|no tests ran in 0.04s
tests/test_voice_fix.py|2|1 error in 0.14s
tests/test_voice_narration_integration.py|2|1 error in 0.25s
tests/test_voice_optimization.py|2|1 error in 0.14s
tests/test_websocket.py|2|1 error in 0.14s
tests/websocket/test_websocket_context.py|2|1 error in 0.14s
tests/websocket/test_websocket_manual.py|2|1 error in 0.14s
```

## Cleanup task — rehabilitate the quarantine backlog
**Goal:** shrink the quarantine list, grow `ci_smoke.txt`, until CI meaningfully
covers the Python brain again.

Suggested order (cheapest signal first):
1. **`no tests ran` (exit 5)** — files with zero collected tests. Either dead
   scripts misnamed `test_*` (move/rename out of `tests/`) or tests gated behind
   a skipped import. Quick wins.
2. **Collection errors (exit 2)** — usually a top-level `sys.exit(1)` or a heavy
   import at module scope. Fix: guard the import / move it inside the test, or
   add the dep to a `requirements-ci-extra.txt` if it's light enough.
3. **Real failures (exit 1)** — actual red tests. Triage: fix the code, fix the
   test, or `@pytest.mark.skip(reason=...)` with a tracking note.

Definition of done: quarantine list is empty or every remaining entry has an
explicit skip + reason. Then `pytest -m ci_smoke` ≈ the whole suite.
