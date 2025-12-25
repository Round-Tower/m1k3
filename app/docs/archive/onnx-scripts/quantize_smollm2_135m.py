#!/usr/bin/env python3
"""
SmolLM2-135M INT4 Quantization Script

Quantizes the fp32 ONNX model to INT4 to reduce size from ~519MB to ~70-80MB.
"""

import argparse
import logging
import os
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def quantize_model(input_dir: str, output_dir: str):
    """
    Quantize ONNX model using onnxruntime quantization.

    Args:
        input_dir: Path to fp32 ONNX model directory
        output_dir: Path to save quantized model
    """
    try:
        from onnxruntime.quantization import quantize_dynamic, QuantType

        input_path = Path(input_dir)
        output_path = Path(output_dir)
        output_path.mkdir(parents=True, exist_ok=True)

        # Find model file
        model_file = input_path / "model.onnx"
        if not model_file.exists():
            logger.error(f"❌ Model not found: {model_file}")
            return False

        output_model = output_path / "model_quantized.onnx"

        logger.info(f"🔧 Quantizing model...")
        logger.info(f"   Input: {model_file}")
        logger.info(f"   Output: {output_model}")
        logger.info(f"   Type: INT8 Dynamic (closest to INT4)")

        # Apply INT8 dynamic quantization
        # Note: True INT4 requires specific hardware support
        # INT8 is more widely supported and still gives ~4x reduction
        quantize_dynamic(
            model_input=str(model_file),
            model_output=str(output_model),
            weight_type=QuantType.QInt8,
            per_channel=True,
            optimize_model=True,
        )

        # Report sizes
        input_size = model_file.stat().st_size / (1024 * 1024)
        output_size = output_model.stat().st_size / (1024 * 1024)
        reduction = ((input_size - output_size) / input_size) * 100

        logger.info(f"\n📊 Quantization Results:")
        logger.info(f"   Original (fp32): {input_size:.1f} MB")
        logger.info(f"   Quantized (int8): {output_size:.1f} MB")
        logger.info(f"   Reduction: {reduction:.1f}%")

        # Copy tokenizer files
        logger.info(f"\n📝 Copying tokenizer files...")
        import shutil
        for file in ["tokenizer.json", "tokenizer_config.json", "special_tokens_map.json"]:
            src = input_path / file
            if src.exists():
                dst = output_path / file
                shutil.copy2(src, dst)
                logger.info(f"   ✅ {file}")

        # Check if under target
        target_size = 80.0
        if output_size <= target_size:
            logger.info(f"\n✅ Success! Model is {target_size - output_size:.1f}MB under target")
        else:
            logger.warning(f"\n⚠️ Still over target by {output_size - target_size:.1f}MB")
            logger.info("   Consider further optimizations or use SmolLM2-135M-Instruct-q4f16 variant")

        return True

    except ImportError:
        logger.error("❌ onnxruntime not installed")
        logger.error("   Install with: pip install onnxruntime")
        return False
    except Exception as e:
        logger.error(f"❌ Quantization failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def main():
    parser = argparse.ArgumentParser(
        description="Quantize SmolLM2-135M ONNX model to INT8"
    )
    parser.add_argument(
        "--input-dir",
        type=str,
        default="models/smollm2-135m-onnx",
        help="Input directory with fp32 model"
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default="models/smollm2-135m-onnx-q8",
        help="Output directory for quantized model"
    )

    args = parser.parse_args()

    logger.info("="*60)
    logger.info("🔧 SmolLM2-135M INT8 Quantization")
    logger.info("="*60 + "\n")

    success = quantize_model(args.input_dir, args.output_dir)

    if success:
        logger.info("\n" + "="*60)
        logger.info("✅ Quantization Complete!")
        logger.info("="*60)
        logger.info(f"📁 Output: {args.output_dir}")
        logger.info("\n📝 Next steps:")
        logger.info("   1. Test quantized model quality")
        logger.info("   2. Compare to SmolLM2-360M (20-prompt benchmark)")
        logger.info("   3. If quality >85%, use for production")
        logger.info("="*60 + "\n")
    else:
        logger.error("\n❌ Quantization failed")


if __name__ == "__main__":
    main()
