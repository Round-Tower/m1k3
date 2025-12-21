#!/usr/bin/env python3
"""
Direct PyTorch to ONNX conversion for MiniLM-L6-v2
No optimum/diffusers dependencies needed!

Usage:
    pip install torch transformers sentence-transformers onnx
    python convert_minilm_pytorch_to_onnx.py
"""

import torch
from sentence_transformers import SentenceTransformer
from pathlib import Path
import json

def convert_to_onnx():
    """Convert sentence-transformers model to ONNX"""

    model_name = "sentence-transformers/all-MiniLM-L6-v2"
    output_dir = Path("models/minilm_onnx")
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading {model_name}...")
    model = SentenceTransformer(model_name)

    # Get the transformer model (first module)
    transformer = model._first_module()
    base_model = transformer.auto_model

    print("Converting to ONNX...")

    # Create dummy input
    dummy_text = "This is a sample sentence."
    encoded = transformer.tokenize([dummy_text])

    input_ids = encoded['input_ids']
    attention_mask = encoded['attention_mask']

    # Export to ONNX
    onnx_path = output_dir / "model.onnx"

    torch.onnx.export(
        base_model,
        (input_ids, attention_mask),
        str(onnx_path),
        input_names=['input_ids', 'attention_mask'],
        output_names=['last_hidden_state', 'pooler_output'],
        dynamic_axes={
            'input_ids': {0: 'batch', 1: 'sequence'},
            'attention_mask': {0: 'batch', 1: 'sequence'},
            'last_hidden_state': {0: 'batch', 1: 'sequence'},
            'pooler_output': {0: 'batch'}
        },
        opset_version=14,
        do_constant_folding=True
    )

    print(f"✅ ONNX model saved to {onnx_path}")

    # Copy tokenizer files from model cache
    import shutil
    model_path = Path(transformer.tokenizer.name_or_path)

    files_to_copy = [
        'vocab.txt',
        'tokenizer.json',
        'tokenizer_config.json',
        'config.json',
        'special_tokens_map.json'
    ]

    for filename in files_to_copy:
        src = model_path / filename
        if src.exists():
            shutil.copy(src, output_dir / filename)
            print(f"✅ Copied {filename}")

    # Create metadata
    metadata = {
        "model_name": model_name,
        "model_type": "sentence-transformers",
        "format": "onnx",
        "embedding_dimensions": 384,
        "max_sequence_length": 256,
        "pooling": "mean",
        "normalize": True,
        "opset_version": 14,
        "input_names": ["input_ids", "attention_mask"],
        "output_names": ["last_hidden_state", "pooler_output"]
    }

    with open(output_dir / "metadata.json", "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"✅ Metadata saved")

    # Test the ONNX model
    import onnxruntime as ort
    import numpy as np

    print("\nTesting ONNX model...")
    session = ort.InferenceSession(str(onnx_path))

    # Run inference
    outputs = session.run(
        None,
        {
            'input_ids': input_ids.numpy(),
            'attention_mask': attention_mask.numpy()
        }
    )

    print(f"✅ ONNX inference successful!")
    print(f"   Output shape: {outputs[0].shape}")
    print(f"   Expected: (batch=1, sequence=?, hidden=384)")

    print(f"\n✅ Conversion complete!")
    print(f"\nNext steps:")
    print(f"1. Copy to Android assets:")
    print(f"   cp -r {output_dir}/* composeApp/src/androidMain/assets/models/minilm/")
    print(f"2. Model is ready for ONNX Runtime Android!")

    return output_dir

if __name__ == "__main__":
    try:
        convert_to_onnx()
    except Exception as e:
        print(f"\n❌ Error: {e}")
        print(f"\nTrying alternative approach...")

        # Fallback: Just use the existing PyTorch model
        print("\nAlternative: Use sentence-transformers model directly")
        print("The model files in models/minilm/ can be loaded with:")
        print("  - sentence-transformers library (Python)")
        print("  - ONNX conversion at runtime (more complex)")
        print("  - Or keep using placeholder embeddings for now")
