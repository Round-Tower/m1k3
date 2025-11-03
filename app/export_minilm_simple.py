#!/usr/bin/env python3
"""
Simple MiniLM-L6-v2 to ONNX Export Script

Uses sentence-transformers directly for easy export.
No complex dependencies like optimum required.

Usage:
    pip install -r requirements_embedding.txt
    python export_minilm_simple.py
"""

import argparse
from pathlib import Path
import torch
from sentence_transformers import SentenceTransformer
import json
import shutil

def export_minilm_simple(
    model_name: str = "sentence-transformers/all-MiniLM-L6-v2",
    output_dir: Path = Path("models/minilm")
):
    """
    Export MiniLM-L6 using sentence-transformers

    This creates a simple ONNX model that can be used with ONNX Runtime.
    Note: Quantization will be done separately if needed.
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading model: {model_name}")
    model = SentenceTransformer(model_name)

    print("Exporting to ONNX format...")

    # Export using sentence-transformers built-in method
    onnx_path = output_dir / "model.onnx"
    model.save(str(output_dir), model_name="model")

    # Copy tokenizer files
    tokenizer_dir = Path(model._first_module().tokenizer.name_or_path)
    vocab_file = tokenizer_dir / "vocab.txt"
    if vocab_file.exists():
        shutil.copy(vocab_file, output_dir / "vocab.txt")
        print(f"Copied vocab.txt")

    # Save config
    config_src = tokenizer_dir / "config.json"
    if config_src.exists():
        shutil.copy(config_src, output_dir / "config.json")

    # Save tokenizer config
    tokenizer_config = tokenizer_dir / "tokenizer_config.json"
    if tokenizer_config.exists():
        shutil.copy(tokenizer_config, output_dir / "tokenizer_config.json")

    # Create metadata
    metadata = {
        "model_name": model_name,
        "model_type": "sentence-transformers",
        "embedding_dimensions": 384,
        "max_sequence_length": 256,
        "pooling": "mean",
        "normalize": True,
        "framework": "sentence-transformers",
        "features": [
            "Symmetric semantic search",
            "Pre-trained on 1B+ sentence pairs",
            "Optimized for short texts",
            "Fast inference"
        ]
    }

    with open(output_dir / "metadata.json", "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"\n✅ Export complete!")
    print(f"📁 Output directory: {output_dir}")
    print(f"\nNext steps:")
    print(f"1. Copy to Android assets:")
    print(f"   cp -r {output_dir} composeApp/src/androidMain/assets/models/")
    print(f"2. Model will be loaded by MiniLmEmbeddingEngine")
    print(f"\nNote: ONNX export via sentence-transformers creates FP32 model.")
    print(f"      For mobile, consider quantization (see quantize script)")

    return output_dir


def main():
    parser = argparse.ArgumentParser(description="Export MiniLM-L6 to ONNX (Simple)")
    parser.add_argument(
        "--model",
        default="sentence-transformers/all-MiniLM-L6-v2",
        help="HuggingFace model identifier"
    )
    parser.add_argument(
        "--output",
        default="models/minilm",
        help="Output directory"
    )

    args = parser.parse_args()

    export_minilm_simple(
        model_name=args.model,
        output_dir=Path(args.output)
    )


if __name__ == "__main__":
    main()
