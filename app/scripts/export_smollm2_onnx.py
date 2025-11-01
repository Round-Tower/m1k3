#!/usr/bin/env python3
"""
間 AI - SmolLM2-360M ONNX Export Script

Exports SmolLM2-360M-Instruct to ONNX format for mobile inference.

Features:
- INT8 quantization for efficiency
- Optimized for mobile CPUs
- ~180MB model size (from 360M parameters)
- Perfect for Pixel 6 Pro (Tensor G1 chip)

Usage:
    python export_smollm2_onnx.py --output models/smollm2-360m-int8.onnx
"""

import argparse
import os
from pathlib import Path

try:
    from optimum.onnxruntime import ORTModelForCausalLM
    from optimum.onnxruntime.configuration import AutoQuantizationConfig
    from transformers import AutoTokenizer, AutoConfig
    import torch
    HAS_DEPENDENCIES = True
except ImportError as e:
    print(f"⚠️  Missing dependencies: {e}")
    print("Install with: pip install optimum[onnxruntime] transformers torch")
    HAS_DEPENDENCIES = False


def export_smollm2_to_onnx(output_dir: Path, quantize: bool = True):
    """
    Export SmolLM2-360M-Instruct to ONNX format.

    Args:
        output_dir: Directory to save ONNX model
        quantize: Whether to apply INT8 quantization (recommended)
    """
    if not HAS_DEPENDENCIES:
        raise RuntimeError("Required dependencies not installed")

    model_id = "HuggingFaceTB/SmolLM2-360M-Instruct"

    print("🤖 間 AI - SmolLM2 ONNX Export")
    print("=" * 50)
    print(f"📦 Model: {model_id}")
    print(f"📁 Output: {output_dir}")
    print(f"⚡ Quantization: {'INT8' if quantize else 'FP32'}")
    print()

    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)

    # Step 1: Export to ONNX
    print("Step 1/3: Exporting to ONNX format...")
    model = ORTModelForCausalLM.from_pretrained(
        model_id,
        export=True,
        provider="CPUExecutionProvider"  # Mobile-optimized
    )

    # Step 2: Quantization (INT8)
    if quantize:
        print("Step 2/3: Applying INT8 quantization...")

        # Dynamic quantization (weight-only INT8)
        quantization_config = AutoQuantizationConfig.arm64(
            is_static=False,
            per_channel=True
        )

        quantized_dir = output_dir / "quantized"
        model.quantize(save_dir=quantized_dir, quantization_config=quantization_config)

        print(f"✅ Quantized model saved to: {quantized_dir}")
    else:
        model.save_pretrained(output_dir)
        print(f"✅ FP32 model saved to: {output_dir}")

    # Step 3: Export tokenizer
    print("Step 3/3: Exporting tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(model_id)
    tokenizer.save_pretrained(output_dir)

    # Export model config
    config = AutoConfig.from_pretrained(model_id)
    config.save_pretrained(output_dir)

    print()
    print("🎉 Export complete!")
    print()
    print("📊 Model Statistics:")

    # Calculate file sizes
    if quantize:
        model_path = quantized_dir
    else:
        model_path = output_dir

    total_size = sum(f.stat().st_size for f in model_path.rglob('*') if f.is_file())
    size_mb = total_size / (1024 * 1024)

    print(f"  • Total size: {size_mb:.1f} MB")
    print(f"  • Parameters: 360M")
    print(f"  • Precision: {'INT8' if quantize else 'FP32'}")
    print()
    print("🚀 Next steps:")
    print("  1. Copy ONNX model to app/composeApp/src/androidMain/assets/")
    print("  2. Implement SmolLM2Engine in Kotlin")
    print("  3. Test inference on Pixel 6 Pro")
    print()
    print("💡 Model will run 100% locally on device!")

    return model_path


def main():
    parser = argparse.ArgumentParser(
        description="Export SmolLM2-360M to ONNX for 間 AI"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("models/smollm2-360m-onnx"),
        help="Output directory for ONNX model"
    )
    parser.add_argument(
        "--no-quantize",
        action="store_true",
        help="Skip INT8 quantization (not recommended for mobile)"
    )

    args = parser.parse_args()

    try:
        export_smollm2_to_onnx(
            output_dir=args.output,
            quantize=not args.no_quantize
        )
    except Exception as e:
        print(f"❌ Export failed: {e}")
        raise


if __name__ == "__main__":
    main()
