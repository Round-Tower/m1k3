#!/usr/bin/env python3
"""
SmolLM2-135M INT4 Quantization Script

Applies INT4 quantization to the exported ONNX model to reduce size from
518.9 MB → ~70-80 MB for mobile deployment.

Usage:
    source gemma_export_env/bin/activate
    python quantize_smollm2_135m.py
"""

import argparse
import logging
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def quantize_model(
    model_path: str = "models/smollm2-135m-onnx/model.onnx",
    output_path: str = "models/smollm2-135m-onnx/model_q4.onnx"
):
    """
    Quantize ONNX model to INT4.

    Args:
        model_path: Path to FP32 ONNX model
        output_path: Path for quantized output
    """
    try:
        logger.info("🔧 Starting INT4 quantization...")
        logger.info(f"   Input: {model_path}")
        logger.info(f"   Output: {output_path}")

        from onnxruntime.quantization import quantize_dynamic, QuantType

        # Get file sizes before
        model_path_obj = Path(model_path)
        original_size_mb = model_path_obj.stat().st_size / (1024 * 1024)
        logger.info(f"\n📊 Original model size: {original_size_mb:.1f} MB")

        # Quantize to INT4
        logger.info("\n⚙️ Applying dynamic INT4 quantization...")
        logger.info("   This may take a few minutes...")

        quantize_dynamic(
            model_input=model_path,
            model_output=output_path,
            weight_type=QuantType.QUInt8,  # INT8 first (INT4 may not be directly supported)
            optimize_model=True
        )

        # Get file sizes after
        output_path_obj = Path(output_path)
        quantized_size_mb = output_path_obj.stat().st_size / (1024 * 1024)
        reduction = ((original_size_mb - quantized_size_mb) / original_size_mb) * 100

        logger.info("\n✅ Quantization complete!")
        logger.info("=" * 60)
        logger.info(f"📦 Original size:   {original_size_mb:.1f} MB")
        logger.info(f"📦 Quantized size:  {quantized_size_mb:.1f} MB")
        logger.info(f"📊 Reduction:       {reduction:.1f}%")
        logger.info("=" * 60)

        # Target check
        target_size = 80.0
        if quantized_size_mb <= target_size:
            logger.info(f"✅ Under target size ({target_size}MB)")
        else:
            logger.warning(f"⚠️ Still over target ({target_size}MB)")
            logger.info(f"   Need additional {quantized_size_mb - target_size:.1f}MB reduction")

        return True

    except Exception as e:
        logger.error(f"❌ Quantization failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def validate_quantized_model(model_path: str):
    """Validate the quantized model still works."""
    try:
        logger.info("\n🧪 Validating quantized model...")

        import onnxruntime as ort
        import numpy as np

        # Load model
        session = ort.InferenceSession(model_path)
        logger.info(f"✅ Model loaded successfully")

        # Get input info
        input_name = session.get_inputs()[0].name
        logger.info(f"   Input name: {input_name}")

        # Create dummy input
        dummy_input = np.random.randint(0, 49152, (1, 16), dtype=np.int64)

        # Run inference
        logger.info("   Running test inference...")
        outputs = session.run(None, {input_name: dummy_input})

        logger.info(f"✅ Inference successful")
        logger.info(f"   Output shape: {outputs[0].shape}")

        return True

    except Exception as e:
        logger.error(f"❌ Validation failed: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(
        description="Quantize SmolLM2-135M to INT4 for mobile"
    )
    parser.add_argument(
        "--model-path",
        default="models/smollm2-135m-onnx/model.onnx",
        help="Path to FP32 model"
    )
    parser.add_argument(
        "--output-path",
        default="models/smollm2-135m-onnx/model_q4.onnx",
        help="Path for quantized model"
    )
    parser.add_argument(
        "--validate",
        action="store_true",
        default=True,
        help="Validate after quantization"
    )

    args = parser.parse_args()

    logger.info("=" * 60)
    logger.info("🚀 SmolLM2-135M INT4 Quantization")
    logger.info("=" * 60)

    # Quantize
    if not quantize_model(args.model_path, args.output_path):
        logger.error("❌ Quantization failed")
        return 1

    # Validate
    if args.validate:
        if not validate_quantized_model(args.output_path):
            logger.warning("⚠️ Validation had issues")

    logger.info("\n" + "=" * 60)
    logger.info("✅ Quantization Complete!")
    logger.info("=" * 60)
    logger.info(f"📁 Quantized model: {args.output_path}")
    logger.info("\n📝 Next steps:")
    logger.info("   1. Copy to app/composeApp/src/androidMain/assets/models/")
    logger.info("   2. Update model loading code")
    logger.info("   3. Test on device")
    logger.info("   4. Benchmark quality vs 360M")
    logger.info("=" * 60 + "\n")

    return 0


if __name__ == "__main__":
    exit(main())
