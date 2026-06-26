#!/usr/bin/env python3
"""Fail loudly if the M1K3-Tests scheme drifts from the package's test targets.

The native Xcode Cloud Test action runs the hand-listed test targets in the
shared `M1K3-Tests` scheme (xcodegen / the app project can't reference package
test targets, so the scheme is the source of truth for what gets tested). The
risk: someone adds a `.testTarget` to Package.swift and forgets the scheme — the
new tests then silently never run, a "hollow green" gate. This guard compares the
two sets and exits non-zero on ANY divergence, so the omission fails CI instead
of hiding.

    python3 check_test_scheme.py [PACKAGE_SWIFT] [SCHEME_XCSCHEME]

Defaults resolve both files relative to this script's repo. Read-only.

The pure helpers (parsing + diff) are unit-tested in test_check_test_scheme.py;
the file I/O + exit wiring is verify-by-run.

Signed: Kev + claude-opus-4-8, 2026-06-26, Confidence 0.85, Prior: Unknown
"""
from __future__ import annotations

import os
import re
import sys

# --------------------------------------------------------------------------- #
# Pure helpers (unit-tested)
# --------------------------------------------------------------------------- #


def scheme_test_targets(scheme_xml: str) -> set[str]:
    """Every BlueprintIdentifier referenced in a scheme's testables."""
    return set(re.findall(r'BlueprintIdentifier\s*=\s*"([^"]+)"', scheme_xml))


def package_test_targets(package_swift: str) -> set[str]:
    """Names of every `.testTarget(...)` declared in a Package.swift."""
    out: set[str] = set()
    for call in re.finditer(r"\.testTarget\(", package_swift):
        window = package_swift[call.end() : call.end() + 300]
        name = re.search(r'name:\s*"([^"]+)"', window)
        if name:
            out.add(name.group(1))
    return out


def diff_targets(scheme: set[str], package: set[str]) -> tuple[set[str], set[str]]:
    """(missing_from_scheme, stale_in_scheme) — both empty means aligned."""
    return (package - scheme, scheme - package)


# --------------------------------------------------------------------------- #
# I/O (verify-by-run)
# --------------------------------------------------------------------------- #


def main(argv: list[str]) -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    macos = os.path.dirname(os.path.dirname(here))
    pkg_path = argv[1] if len(argv) > 1 else os.path.join(macos, "Package.swift")
    scheme_path = (
        argv[2]
        if len(argv) > 2
        else os.path.join(
            macos, "M1K3.xcworkspace", "xcshareddata", "xcschemes", "M1K3-Tests.xcscheme"
        )
    )

    package = package_test_targets(open(pkg_path).read())
    scheme = scheme_test_targets(open(scheme_path).read())
    missing, stale = diff_targets(scheme=scheme, package=package)

    if not missing and not stale:
        print(f"✓ M1K3-Tests scheme covers all {len(package)} package test targets.")
        return 0

    if missing:
        print("❌ test targets in Package.swift but MISSING from M1K3-Tests.xcscheme")
        print("   (these would silently never run in the native Test action):")
        for t in sorted(missing):
            print(f"     - {t}")
    if stale:
        print("⚠️  test targets in M1K3-Tests.xcscheme but GONE from Package.swift:")
        for t in sorted(stale):
            print(f"     - {t}")
    print("\nFix: update M1K3.xcworkspace/.../M1K3-Tests.xcscheme to match Package.swift.")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
