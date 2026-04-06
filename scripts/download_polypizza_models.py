#!/usr/bin/env python3
"""
Download Quaternius GLB models from Poly Pizza CDN.

Fetches each model page to extract the CDN UUID, then downloads
the GLB file directly from static.poly.pizza using brotli decompression.
"""

import re
import subprocess
import time
import urllib.request
from pathlib import Path

MODELS_DIR = Path(__file__).parent.parent / "app/composeApp/src/androidMain/assets/models"

# Model IDs to download: {poly_pizza_id: desired_filename}
DINOSAURS = {
    "UYtneO5FpF": "TRex.glb",
    "KeeQrrouRK": "Parasaurolophus.glb",
    "cnlGH2UcDd": "Velociraptor.glb",
    "IGvrUqGrRM": "Triceratops.glb",
    "eFcNbOlpvl": "Stegosaurus.glb",
    "fvo0x8Zk3z": "Apatosaurus.glb",
}

# All 12 from Animated Animal Pack — names resolved from poly.pizza pages
ANIMALS = {
    "26zM1outCr": None,   # Cow
    "qmX6nhnvp7": None,   # Donkey
    "T6Cs7tmMHJ": None,   # Deer
    "bCVFD48i2l": None,   # Alpaca
    "a8PIIYwF7r": None,   # Bull
    "Bc97C66HKi": None,   # Shiba Inu
    "y4wdQpg767": None,   # Stag
    "tQdzbZ1Cmw": None,   # Husky
    "wcWiuEqwzq": None,   # White Horse
    "P1gU3Qkr9r": None,   # Wolf
    "bEdE4rmZy9": None,   # Horse (brown)
    "qvTrSG9pZF": None,   # Horse (white)
}

# Curated fish selection
FISH = {
    "MRjSlwCjHM": "Anglerfish.glb",
    "sZR8AMLMz5": "Shark.glb",
    "7Jh8vsARfN": "Blobfish.glb",
    "UKHtgpxTOk": "Pufferfish.glb",
    "F7bCnF1BFf": "Piranha.glb",
    "qyGtRmhgzl": "Koi.glb",
    "769fHo3eEB": "Clownfish.glb",
    "7hMOlBjln0": "Swordfish.glb",
    "Vg8IlYjdZi": "Betta.glb",
    "JQrBevTzgD": "GoblinShark.glb",
}

UUID_PATTERN = re.compile(r'"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})","PublicID":"([A-Za-z0-9]+)"')
TITLE_PATTERN = re.compile(r'"Title":"([^"]+)"')

def get_model_info(poly_id: str) -> tuple[str, str] | None:
    """Fetch poly.pizza page and extract (uuid, title)."""
    url = f"https://poly.pizza/m/{poly_id}"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode("utf-8", errors="replace")
        # Find UUID + PublicID pair
        m = UUID_PATTERN.search(html)
        if m and m.group(2) == poly_id:
            uuid = m.group(1)
        else:
            # Fallback: find UUID near the model data
            idx = html.find(poly_id)
            if idx == -1:
                return None
            chunk = html[max(0, idx-60):idx+60]
            mu = re.search(r'([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})', chunk)
            if not mu:
                return None
            uuid = mu.group(1)
        # Find title
        mt = TITLE_PATTERN.search(html)
        title = mt.group(1) if mt else poly_id
        return uuid, title
    except Exception as e:
        print(f"  ERROR fetching {poly_id}: {e}")
        return None


def download_glb(uuid: str, dest: Path) -> bool:
    """Download GLB from CDN with brotli decompression via curl."""
    cdn_url = f"https://static.poly.pizza/{uuid}.glb.br"
    result = subprocess.run(
        ["curl", "--compressed", "-s", "-L", "-o", str(dest), cdn_url],
        capture_output=True
    )
    if result.returncode != 0:
        print(f"  curl failed: {result.stderr.decode()}")
        return False
    if dest.stat().st_size < 10_000:
        print(f"  file too small ({dest.stat().st_size} bytes), likely failed")
        dest.unlink(missing_ok=True)
        return False
    return True


def process_pack(pack: dict[str, str | None], pack_name: str):
    """Download all models in a pack."""
    print(f"\n{'='*50}")
    print(f"  {pack_name} ({len(pack)} models)")
    print(f"{'='*50}")
    results = []
    for poly_id, filename in pack.items():
        print(f"\n  [{poly_id}] fetching metadata...")
        info = get_model_info(poly_id)
        if not info:
            print("  SKIP — could not get metadata")
            continue
        uuid, title = info
        safe_title = re.sub(r'[^A-Za-z0-9]', '', title)
        if filename is None:
            filename = f"{safe_title}.glb"
        dest = MODELS_DIR / filename
        if dest.exists():
            print(f"  SKIP — {filename} already exists")
            results.append((title, filename, "skipped"))
            continue
        print(f"  {title!r} → {filename} (uuid: {uuid[:8]}...)")
        print("  downloading...")
        ok = download_glb(uuid, dest)
        if ok:
            size_kb = dest.stat().st_size // 1024
            print(f"  ✓ {filename} ({size_kb} KB)")
            results.append((title, filename, f"{size_kb} KB"))
        else:
            print("  ✗ FAILED")
            results.append((title, filename, "FAILED"))
        time.sleep(0.3)  # polite rate limiting
    return results


if __name__ == "__main__":
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Models directory: {MODELS_DIR}")

    all_results = []
    all_results += process_pack(DINOSAURS, "Dinosaurs")
    all_results += process_pack(ANIMALS, "Animals")
    all_results += process_pack(FISH, "Fish")

    print(f"\n{'='*50}")
    print("SUMMARY")
    print(f"{'='*50}")
    for title, filename, status in all_results:
        print(f"  {status:>10}  {filename:35} ({title})")
    print(f"\nTotal: {len(all_results)} models processed")
    downloaded = sum(1 for _, _, s in all_results if s not in ("skipped", "FAILED"))
    print(f"Downloaded: {downloaded}")
