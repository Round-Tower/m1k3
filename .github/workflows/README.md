# M1K3 CI/CD

Lean pipeline that tests **what M1K3 actually ships**. Overhauled 2026-06-06:
the old Node/Playwright "unified test suite" (unified-tests, quick-tests,
release-testing, security-scanning) tested a web dashboard, was perpetually red,
and never covered the Swift Mac MVP — the active surface. It was removed.

## Workflows

| File | Purpose | Triggers |
|------|---------|----------|
| **`ci.yml`** | Tests. `swift-mac` runs `swift test` on the macOS MVP; `python-smoke` runs the curated green Python subset. | push to `master`/`develop`, all PRs |
| **`security.yml`** | TruffleHog (verified secrets) + Bandit (Python SAST, high/high gate). | push to `master`/`develop`, all PRs |
| **`claude.yml`** | `@claude` assistant on issues/PRs. | `@claude` mentions |
| **`claude-code-review.yml`** | Auto Claude review of **Android/Kotlin** changes. | PRs touching `app/**/*.kt` |
| **`claude-code-review-mac.yml`** | Auto Claude review of **Swift/Mac** changes. | PRs touching `macos/**` |
| **`repo-visualizer.yml`** | Updates `diagram.svg`. | push to `main` |

## Notes & gotchas

- **`swift-mac` needs a runner with Xcode 26+** (the package targets
  `.macOS(.v26)` / swift-tools 6.2). `latest-stable` picks the newest Xcode on
  the image; pin a `macos-26` runner if the hosted image lags. MLX/Metal +
  WhisperKit integration tests are env-gated **off** (they only run from a
  bundled `.app`, never the CLI — see `macos/` project memory).
- **`python-smoke` runs a curated subset, not the whole suite.** The legacy
  Python tests are a swamp; see **`tests/CI_TRIAGE.md`** for the green list
  (`tests/ci_smoke.txt`), the quarantine backlog, and the rehabilitation task.
  Slim deps live in `requirements-ci.txt`.
- **Dropped CodeQL** (needed repo "Code Security" enabled + noisy here) and
  **npm-audit** (no real Node app — root `package.json` is a stub).
- The root `package.json` shields.io badges point at the removed
  `unified-tests.yml` / `quick-tests.yml` and will read "unknown" — update or
  drop them next time the README is touched.
