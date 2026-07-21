#!/usr/bin/env python3
"""Regenerate Sources/M1K3MLX/PinnedWeights.swift — the shipped weight manifest.

Why this is a script and not hand-typed hex: re-pinning happens every time a
brain is promoted (both tiers moved inside 26 hours in July 2026), and a
manifest is only worth anything if regenerating it is mechanical enough that
nobody is tempted to skip it.

TRUST MODEL — read before changing anything here.

The digests are computed from the LOCAL, already-downloaded snapshot by
default, NOT from HuggingFace's API. That is deliberate: the local copy is the
one that has actually been run and evaluated, so pinning it pins the bytes we
know behave correctly. HuggingFace's published LFS oid is then used as an
INDEPENDENT SECOND OPINION — if the two agree, two parties confirm the same
bytes, and a compromise would have to have happened before our evals to slip
through. A mismatch is a hard stop, never a warning.

Small files (config.json, chat_template.jinja, tokenizer_config.json) are not
LFS-backed, so HF publishes no content digest for them and only the local hash
is available. They are pinned anyway — the chat template in particular IS the
tool-calling contract for gemma, so a silent change there is a behaviour change
in disguise.

Usage:
    python3 macos/tools/weights/pin_weights.py                # verify + regenerate
    python3 macos/tools/weights/pin_weights.py --check        # verify only, exit 1 on drift
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import sys
import urllib.request

# The repos M1K3 actually ships and downloads for a user-selectable brain.
# Spikes, A/B overrides and retired checkpoints are deliberately NOT pinned —
# see WeightIntegrity.Verdict.unpinned for why that stays permissive.
# Two download bases, because the 2.x layout is preserved byte-for-byte so
# existing caches keep working (see HuggingFaceBridge): LLM weights under
# Caches, embedder weights under Documents. Both flow through the same
# HubApiDownloader.download choke point, so both are enforceable.
CONTAINER = pathlib.Path.home() / "Library/Containers/app.m1k3/Data"
LLM_CACHE = CONTAINER / "Library/Caches/models"
EMBEDDER_CACHE = CONTAINER / "Documents/huggingface/models"

SHIPPED_REPOS = {
    "mlx-community/gemma-4-12B-it-4bit": LLM_CACHE,
    "mlx-community/Qwen3-4B-Instruct-2507-4bit": LLM_CACHE,
    # The retrieval embedder. Smaller, but it is still third-party weights
    # fetched at runtime and fed to MLX — the same exposure, just quieter.
    "mlx-community/Qwen3-Embedding-0.6B-4bit-DWQ": EMBEDDER_CACHE,
}

# Mirrors BrainWeightsFetcher.weightPatterns / upstream modelDownloadPatterns.
# Pin exactly what we fetch: pinning a file we never download is noise, and
# fetching a file we never pinned is a hole.
SUFFIXES = (".safetensors", ".json", ".jinja")

REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = REPO_ROOT / "macos/Sources/M1K3MLX/PinnedWeights.swift"


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(8 * 1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def hf_json(url: str):
    with urllib.request.urlopen(url, timeout=30) as resp:
        return json.load(resp)


def collect(repo: str, cache: pathlib.Path) -> tuple[str, dict[str, dict]]:
    """Local digests for `repo`, cross-checked against HF. Exits on disagreement."""
    directory = cache / repo
    if not directory.is_dir():
        sys.exit(
            f"FATAL {repo}: no local snapshot at {directory}.\n"
            "Pin from bytes that have been run and evaluated — download and "
            "exercise the model first, then re-run this."
        )

    revision = hf_json(f"https://huggingface.co/api/models/{repo}/revision/main")["sha"]

    published: dict[str, str] = {}
    for entry in hf_json(f"https://huggingface.co/api/models/{repo}/tree/main?recursive=1"):
        if entry.get("type") != "file":
            continue
        if oid := (entry.get("lfs") or {}).get("oid"):
            published[entry["path"]] = oid

    files: dict[str, dict] = {}
    confirmed = 0
    for path in sorted(directory.rglob("*")):
        if not path.is_file() or path.name.startswith(".") or ".cache" in path.parts:
            continue
        if not path.name.endswith(SUFFIXES):
            continue
        rel = str(path.relative_to(directory))
        digest = sha256(path)

        if expected := published.get(rel):
            if expected != digest:
                sys.exit(
                    f"FATAL {repo}/{rel}: local sha256 {digest} disagrees with "
                    f"HuggingFace's published LFS oid {expected}.\n"
                    "Two independent sources disagree about these bytes. Do NOT "
                    "pin. Investigate before doing anything else."
                )
            confirmed += 1

        files[rel] = {"size": path.stat().st_size, "sha256": digest}

    print(
        f"  {repo}\n"
        f"    revision {revision}\n"
        f"    {len(files)} files pinned, {confirmed} independently confirmed against HF"
    )
    return revision, files


def swift_literal(pins: dict[str, tuple[str, dict[str, dict]]]) -> str:
    lines = [
        "//",
        "//  PinnedWeights.swift",
        "//  M1K3MLX",
        "//",
        "//  GENERATED — do not hand-edit. Regenerate with:",
        "//      python3 macos/tools/weights/pin_weights.py",
        "//",
        "//  The manifest WeightIntegrity checks downloaded weights against. It",
        "//  lives in our source, under review, on purpose: fetching the expected",
        "//  digest from the same host that serves the file proves nothing, since",
        "//  whoever can swap the file can swap its published hash too.",
        "//",
        "//  Each digest was computed from a local snapshot that had been run and",
        "//  evaluated, then cross-checked against HuggingFace's published LFS oid",
        "//  where one exists. The generator hard-stops on any disagreement.",
        "//",
        "//  Changing a pin is a deliberate act: it means shipping different",
        "//  weights, and it should be reviewed like any other behaviour change.",
        "//",
        "",
        "import Foundation",
        "",
        "/// The shipped weight manifest. Repos absent from this table load",
        "/// unverified by design — see `WeightIntegrity.Verdict.unpinned`.",
        "public enum PinnedWeights {",
        "    public static let all: [String: WeightIntegrity.Pin] = [",
    ]
    for repo, (revision, files) in sorted(pins.items()):
        lines.append(f'        "{repo}": .init(')
        lines.append(f'            revision: "{revision}",')
        lines.append("            files: [")
        for name, meta in sorted(files.items()):
            lines.append(
                f'                "{name}": '
                f'.init(size: {meta["size"]}, sha256: "{meta["sha256"]}"),'
            )
        lines.append("            ]")
        lines.append("        ),")
    lines += [
        "    ]",
        "",
        "    /// The pin for `repoID`, or nil when the repo ships unpinned.",
        "    public static func pin(for repoID: String) -> WeightIntegrity.Pin? {",
        "        all[repoID]",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify and report drift without rewriting the manifest",
    )
    args = parser.parse_args()

    print("Pinning shipped model weights (local bytes, cross-checked against HF):")
    pins = {repo: collect(repo, cache) for repo, cache in SHIPPED_REPOS.items()}
    generated = swift_literal(pins)

    if args.check:
        current = OUT.read_text() if OUT.exists() else ""
        if current != generated:
            sys.exit("DRIFT: PinnedWeights.swift is stale — re-run without --check")
        print("PinnedWeights.swift is up to date.")
        return

    OUT.write_text(generated)
    print(f"\nWrote {OUT.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
