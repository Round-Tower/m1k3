#!/usr/bin/env python3
"""
間 AI - Simple Model File Downloader

Downloads just the essential SmolLM2-360M files needed for mobile demo.
Skips complex ONNX conversion due to dependency conflicts.

Usage:
    python simple_model_download.py
"""

import os
from pathlib import Path

try:
    from huggingface_hub import hf_hub_download
    HAS_HF = True
except ImportError:
    print("⚠️  huggingface_hub not installed")
    print("Install with: pip install huggingface_hub")
    HAS_HF = False


def download_model_files():
    """Download essential model files from HuggingFace"""

    if not HAS_HF:
        raise RuntimeError("huggingface_hub not installed")

    model_id = "HuggingFaceTB/SmolLM2-360M-Instruct"
    output_dir = Path("models/smollm2-360m")
    output_dir.mkdir(parents=True, exist_ok=True)

    print("🤖 間 AI - SmolLM2 Model Downloader")
    print("=" * 50)
    print(f"📦 Model: {model_id}")
    print(f"📁 Output: {output_dir}")
    print()

    # Essential files for mobile demo
    files_to_download = [
        "config.json",
        "tokenizer.json",
        "tokenizer_config.json",
        "vocab.json",
        "merges.txt",
        "special_tokens_map.json",
    ]

    print("📥 Downloading essential model files...")
    print()

    downloaded = []
    for filename in files_to_download:
        try:
            print(f"  ⬇️  {filename}...")
            local_path = hf_hub_download(
                repo_id=model_id,
                filename=filename,
                local_dir=output_dir,
                local_dir_use_symlinks=False
            )
            file_size = Path(local_path).stat().st_size / 1024
            print(f"     ✅ {file_size:.1f} KB")
            downloaded.append(filename)
        except Exception as e:
            print(f"     ⚠️  Failed: {e}")

    print()
    print(f"✅ Downloaded {len(downloaded)}/{len(files_to_download)} files")
    print()
    print("📊 Files saved to:")
    for file in output_dir.rglob("*"):
        if file.is_file() and not file.name.endswith(('.lock', '.metadata')):
            size_kb = file.stat().st_size / 1024
            print(f"  • {file.name} ({size_kb:.1f} KB)")

    print()
    print("🎉 Download complete!")
    print()
    print("📝 Note: For full ONNX inference, you would need:")
    print("   1. PyTorch 2.3+")
    print("   2. ONNX Runtime with quantization support")
    print("   3. Model conversion (which has dependency conflicts)")
    print()
    print("💡 Current demo uses mock inference with these tokenizer files.")
    print("   This demonstrates the UI and architecture perfectly!")
    print()
    print("🚀 Next: Copy vocab.json and merges.txt to app assets if needed")

    return output_dir


def main():
    try:
        download_model_files()
    except Exception as e:
        print(f"❌ Download failed: {e}")
        raise


if __name__ == "__main__":
    main()
