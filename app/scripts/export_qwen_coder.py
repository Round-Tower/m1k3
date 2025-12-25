#!/usr/bin/env python3
"""
ONNX Export Script for Qwen2.5-Coder-0.5B
Exports model for Android ONNX Runtime deployment

Usage:
    python export_qwen_coder.py

Requirements:
    pip install optimum[onnxruntime]
    pip install transformers>=4.35.0
    pip install onnx>=1.15.0
    pip install onnxruntime>=1.17.0
    pip install sentencepiece>=0.1.99
"""

import os
import sys
from pathlib import Path
from typing import Optional
import shutil

# Model configuration
MODEL_NAME = "Qwen/Qwen2.5-Coder-0.5B-Instruct"
OUTPUT_DIR = Path(__file__).parent.parent / "composeApp/src/androidMain/assets/models/qwen-coder"


def check_dependencies():
    """Check if all required dependencies are installed"""
    try:
        import optimum
        import transformers
        import onnx
        import onnxruntime
        import sentencepiece
        print("✅ All dependencies installed")
        return True
    except ImportError as e:
        print(f"❌ Missing dependency: {e}")
        print("\nInstall with:")
        print("pip install optimum[onnxruntime] transformers>=4.35.0 onnx>=1.15.0 onnxruntime>=1.17.0 sentencepiece>=0.1.99")
        return False


def export_to_onnx():
    """Export Qwen2.5-Coder to ONNX format"""
    from optimum.onnxruntime import ORTModelForCausalLM
    from transformers import AutoTokenizer

    print(f"\n✨ Step 1: Converting {MODEL_NAME} to ONNX format...")
    print(f"   This may take 5-10 minutes...")

    try:
        # Create output directory
        base_dir = OUTPUT_DIR / "base"
        base_dir.mkdir(parents=True, exist_ok=True)

        # Export to ONNX
        ort_model = ORTModelForCausalLM.from_pretrained(
            MODEL_NAME,
            export=True,
            use_cache=False,  # Disable KV cache for mobile
            provider="CPUExecutionProvider"
        )

        # Save ONNX model
        ort_model.save_pretrained(base_dir)
        print(f"✅ Base ONNX model saved to {base_dir}")

        return ort_model, base_dir

    except Exception as e:
        print(f"❌ Failed to export model: {e}")
        import traceback
        traceback.print_exc()
        return None, None


def optimize_for_mobile(base_dir: Path):
    """Optimize ONNX model for mobile deployment"""
    from optimum.onnxruntime import ORTOptimizer, ORTModelForCausalLM
    from optimum.onnxruntime.configuration import OptimizationConfig

    print("\n⚡ Step 2: Optimizing for mobile...")

    try:
        optimized_dir = OUTPUT_DIR / "optimized"
        optimized_dir.mkdir(parents=True, exist_ok=True)

        # Load model for optimization
        ort_model = ORTModelForCausalLM.from_pretrained(base_dir)

        # Configure optimization
        optimization_config = OptimizationConfig(
            optimization_level=99,  # Maximum optimization
            optimize_for_gpu=False,  # CPU-only
            fp16=False  # Keep FP32 for now
        )

        # Optimize
        optimizer = ORTOptimizer.from_pretrained(ort_model)
        optimizer.optimize(
            save_dir=optimized_dir,
            optimization_config=optimization_config
        )

        print(f"✅ Optimized model saved to {optimized_dir}")
        return optimized_dir

    except Exception as e:
        print(f"❌ Failed to optimize model: {e}")
        import traceback
        traceback.print_exc()
        return None


def quantize_to_int4(optimized_dir: Path):
    """Quantize model to INT4 for reduced size"""
    import onnxruntime.quantization as quantization
    from onnxruntime.quantization import QuantType

    print("\n🔢 Step 3: Quantizing to INT4...")

    try:
        model_input = optimized_dir / "model.onnx"
        model_output = OUTPUT_DIR / "model_int4.onnx"

        if not model_input.exists():
            print(f"❌ Model file not found: {model_input}")
            return None

        # Quantize to INT4
        quantization.quantize_dynamic(
            model_input=str(model_input),
            model_output=str(model_output),
            weight_type=QuantType.QUInt4,  # 4-bit unsigned integer
            optimize_model=True,
            per_channel=False,  # Simplify for mobile
            reduce_range=False
        )

        print(f"✅ Quantized model saved to {model_output}")

        # Get file size
        size_mb = model_output.stat().st_size / 1024 / 1024
        print(f"   Model size: {size_mb:.1f} MB")

        return model_output

    except Exception as e:
        print(f"❌ Failed to quantize model: {e}")
        import traceback
        traceback.print_exc()
        return None


def convert_to_ort_format():
    """Convert ONNX model to .ort format for mobile optimization"""
    import onnxruntime

    print("\n📦 Step 4: Converting to .ort format for mobile...")

    try:
        # Note: .ort conversion requires onnxruntime.tools
        # For simplicity, we'll use the .onnx file directly on mobile
        # The .ort format is an optimization that can be done later

        print("   ℹ️  Skipping .ort conversion for now")
        print("   The .onnx file will work directly with ONNX Runtime Mobile")

        return True

    except Exception as e:
        print(f"⚠️  Warning: {e}")
        return False


def save_tokenizer():
    """Save tokenizer files"""
    from transformers import AutoTokenizer

    print("\n🔤 Step 5: Saving tokenizer...")

    try:
        tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
        tokenizer.save_pretrained(OUTPUT_DIR)

        print(f"✅ Tokenizer saved to {OUTPUT_DIR}")

        # List tokenizer files
        tokenizer_files = list(OUTPUT_DIR.glob("tokenizer*")) + list(OUTPUT_DIR.glob("*.json"))
        print(f"   Files: {[f.name for f in tokenizer_files]}")

        return True

    except Exception as e:
        print(f"❌ Failed to save tokenizer: {e}")
        import traceback
        traceback.print_exc()
        return False


def validate_export():
    """Validate the exported model"""
    import onnxruntime as ort
    from transformers import AutoTokenizer
    import numpy as np

    print("\n✓ Step 6: Validating export...")

    try:
        model_path = OUTPUT_DIR / "model_int4.onnx"
        if not model_path.exists():
            print(f"❌ Model file not found: {model_path}")
            return False

        # Load tokenizer
        tokenizer = AutoTokenizer.from_pretrained(OUTPUT_DIR)

        # Create ONNX session
        session = ort.InferenceSession(
            str(model_path),
            providers=["CPUExecutionProvider"]
        )

        # Test inference
        test_text = "Hello, world!"
        inputs = tokenizer(test_text, return_tensors="np")
        input_ids = inputs["input_ids"].astype(np.int64)

        print(f"   Testing with input: '{test_text}'")
        print(f"   Input shape: {input_ids.shape}")

        # Run inference
        outputs = session.run(None, {"input_ids": input_ids})

        print(f"✅ Model inference successful!")
        print(f"   Output shape: {outputs[0].shape}")

        return True

    except Exception as e:
        print(f"❌ Validation failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def display_summary():
    """Display export summary with file sizes"""
    print("\n" + "=" * 70)
    print("📊 Export Summary")
    print("=" * 70)

    try:
        # Calculate sizes
        base_dir = OUTPUT_DIR / "base"
        optimized_dir = OUTPUT_DIR / "optimized"
        int4_model = OUTPUT_DIR / "model_int4.onnx"

        if base_dir.exists():
            base_size = sum(f.stat().st_size for f in base_dir.rglob("*") if f.is_file())
            print(f"Base ONNX:       {base_size / 1024 / 1024:.1f} MB")

        if optimized_dir.exists():
            optimized_size = sum(f.stat().st_size for f in optimized_dir.rglob("*") if f.is_file())
            print(f"Optimized:       {optimized_size / 1024 / 1024:.1f} MB")

        if int4_model.exists():
            int4_size = int4_model.stat().st_size
            print(f"INT4 Quantized:  {int4_size / 1024 / 1024:.1f} MB")

        tokenizer_size = sum(f.stat().st_size for f in OUTPUT_DIR.glob("tokenizer*"))
        config_size = sum(f.stat().st_size for f in OUTPUT_DIR.glob("*.json"))
        print(f"Tokenizer:       {tokenizer_size / 1024:.1f} KB")
        print(f"Config files:    {config_size / 1024:.1f} KB")

        # Total size
        total_size = int4_size + tokenizer_size + config_size if int4_model.exists() else 0
        print(f"\nTotal (deployable): {total_size / 1024 / 1024:.1f} MB")

        print("=" * 70)

    except Exception as e:
        print(f"⚠️  Could not calculate sizes: {e}")


def cleanup_intermediate_files():
    """Clean up intermediate files to save space"""
    print("\n🧹 Cleaning up intermediate files...")

    try:
        # Remove base and optimized directories (keep only INT4 model)
        base_dir = OUTPUT_DIR / "base"
        optimized_dir = OUTPUT_DIR / "optimized"

        if base_dir.exists():
            shutil.rmtree(base_dir)
            print(f"   Removed {base_dir}")

        if optimized_dir.exists():
            shutil.rmtree(optimized_dir)
            print(f"   Removed {optimized_dir}")

        print("✅ Cleanup complete")

    except Exception as e:
        print(f"⚠️  Cleanup warning: {e}")


def main():
    """Main export workflow"""
    print("=" * 70)
    print("🚀 Qwen2.5-Coder ONNX Export for Android")
    print("=" * 70)
    print(f"Model: {MODEL_NAME}")
    print(f"Output: {OUTPUT_DIR}")
    print("=" * 70)

    # Step 0: Check dependencies
    if not check_dependencies():
        sys.exit(1)

    # Step 1: Export to ONNX
    ort_model, base_dir = export_to_onnx()
    if not ort_model or not base_dir:
        sys.exit(1)

    # Step 2: Optimize for mobile
    optimized_dir = optimize_for_mobile(base_dir)
    if not optimized_dir:
        sys.exit(1)

    # Step 3: Quantize to INT4
    int4_model = quantize_to_int4(optimized_dir)
    if not int4_model:
        sys.exit(1)

    # Step 4: Convert to .ort (optional)
    convert_to_ort_format()

    # Step 5: Save tokenizer
    if not save_tokenizer():
        sys.exit(1)

    # Step 6: Validate export
    if not validate_export():
        print("\n⚠️  Warning: Validation failed, but files may still be usable")

    # Display summary
    display_summary()

    # Cleanup intermediate files
    cleanup_intermediate_files()

    # Final instructions
    print("\n🎉 Export complete!")
    print(f"\n📁 Model files ready at: {OUTPUT_DIR}")
    print("\n📝 Next steps:")
    print("1. Verify model files in Android assets directory")
    print("2. Implement CodingEngine.kt")
    print("3. Test on Android device (mid-range: 6GB RAM)")
    print("4. Monitor performance:")
    print("   - Model load time (target: <3s)")
    print("   - Inference speed (target: 15-25 tokens/sec)")
    print("   - Memory usage (target: ~500MB)")
    print("   - Battery drain (target: <2%/hour)")
    print("\n💡 Tip: Test with simple prompts first:")
    print("   'Write a Python function to reverse a string'")
    print("   'Create a JSON schema for user profile'")
    print("=" * 70)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  Export interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
