#!/usr/bin/env python3
"""
MiniLM Embedding Optimization Script - PHASE1.5-006

Exports optimized sentence embedding models for mobile deployment. Evaluates
3 options to reduce size from current 90 MB to target 25-60 MB.

Options:
1. paraphrase-MiniLM-L3-v2 (fp32) - ~50 MB, smaller model
2. all-MiniLM-L6-v2 (int8) - ~45 MB, quantized current model
3. paraphrase-MiniLM-L3-v2 (int8) - ~25 MB, best size (aggressive)

Usage:
    source gemma_export_env/bin/activate
    python export_minilm_optimized.py --option 1  # L3-fp32
    python export_minilm_optimized.py --option 2  # L6-int8
    python export_minilm_optimized.py --option 3  # L3-int8 (recommended)
"""

import argparse
import logging
import os
import sys
from pathlib import Path

import torch
from transformers import AutoTokenizer, AutoModel

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class MiniLMOptimizer:
    """
    Optimizes MiniLM embedding models for mobile deployment.
    """

    OPTIONS = {
        1: {
            "name": "paraphrase-MiniLM-L3-v2 (fp32)",
            "model_id": "sentence-transformers/paraphrase-MiniLM-L3-v2",
            "quantize": False,
            "expected_size": 50,
            "notes": "Smaller model, same 384-dim, safest option"
        },
        2: {
            "name": "all-MiniLM-L6-v2 (int8)",
            "model_id": "sentence-transformers/all-MiniLM-L6-v2",
            "quantize": True,
            "expected_size": 45,
            "notes": "Quantized current model, minimal quality loss"
        },
        3: {
            "name": "paraphrase-MiniLM-L3-v2 (int8)",
            "model_id": "sentence-transformers/paraphrase-MiniLM-L3-v2",
            "quantize": True,
            "expected_size": 25,
            "notes": "Best size, moderate quality trade-off (RECOMMENDED)"
        }
    }

    def __init__(self, option: int, output_dir: str = "models/minilm-optimized"):
        if option not in self.OPTIONS:
            raise ValueError(f"Invalid option {option}. Choose 1, 2, or 3")

        self.option = self.OPTIONS[option]
        self.output_dir = Path(output_dir) / f"option{option}"
        self.output_dir.mkdir(parents=True, exist_ok=True)

        logger.info(f"📦 MiniLM Optimizer initialized")
        logger.info(f"   Option: {self.option['name']}")
        logger.info(f"   Expected size: ~{self.option['expected_size']} MB")
        logger.info(f"📁 Output directory: {self.output_dir}")

    def check_dependencies(self) -> bool:
        """Check if required packages are installed."""
        try:
            import optimum
            from optimum.onnxruntime import ORTModelForFeatureExtraction
            logger.info("✅ optimum installed")
            return True
        except ImportError:
            logger.error("❌ Missing dependencies. Install with:")
            logger.error("   pip install optimum[onnxruntime] sentence-transformers")
            return False

    def export_to_onnx(self) -> bool:
        """
        Export MiniLM to ONNX format with optional INT8 quantization.

        Returns:
            bool: True if export successful
        """
        try:
            model_id = self.option['model_id']
            logger.info(f"🔄 Loading model: {model_id}")

            from optimum.onnxruntime import ORTModelForFeatureExtraction

            # Load tokenizer
            tokenizer = AutoTokenizer.from_pretrained(model_id)
            logger.info(f"📝 Tokenizer loaded: {len(tokenizer)} tokens")

            # Export to ONNX
            logger.info("🔧 Exporting to ONNX format...")

            ort_model = ORTModelForFeatureExtraction.from_pretrained(
                model_id,
                export=True,
                provider="CPUExecutionProvider"
            )

            logger.info(f"✅ ONNX model exported")

            # Apply INT8 quantization if requested
            if self.option['quantize']:
                logger.info("⚙️ Applying INT8 quantization...")
                try:
                    from optimum.onnxruntime import ORTQuantizer
                    from optimum.onnxruntime.configuration import AutoQuantizationConfig

                    # Create quantization config
                    qconfig = AutoQuantizationConfig.avx512_vnni(
                        is_static=False,
                        per_channel=True
                    )

                    # Quantize model
                    quantizer = ORTQuantizer.from_pretrained(ort_model)
                    quantizer.quantize(
                        save_dir=self.output_dir,
                        quantization_config=qconfig
                    )
                    logger.info("✅ INT8 quantization applied")

                except Exception as e:
                    logger.warning(f"⚠️ Quantization failed: {e}")
                    logger.info("   Saving FP32 model instead")
                    ort_model.save_pretrained(self.output_dir)
            else:
                # Save FP32 model
                ort_model.save_pretrained(self.output_dir)

            # Save tokenizer
            tokenizer.save_pretrained(self.output_dir)
            logger.info(f"✅ Tokenizer saved")

            # Report sizes
            self._report_sizes()

            return True

        except Exception as e:
            logger.error(f"❌ Export failed: {e}")
            import traceback
            traceback.print_exc()
            return False

    def _report_sizes(self):
        """Report model sizes and comparison."""
        try:
            total_size = 0
            logger.info("\n📊 Export Summary:")
            logger.info("=" * 60)

            for file_path in self.output_dir.rglob("*"):
                if file_path.is_file():
                    size_mb = file_path.stat().st_size / (1024 * 1024)
                    total_size += size_mb
                    if size_mb > 0.5:  # Show files > 0.5MB
                        logger.info(f"  {file_path.name}: {size_mb:.1f} MB")

            logger.info("=" * 60)
            logger.info(f"📦 Total size: {total_size:.1f} MB")

            # Compare to current MiniLM-L6 (90 MB)
            current_size = 90.0
            percentage = (total_size / current_size) * 100
            reduction = current_size - total_size
            logger.info(f"📊 vs Current MiniLM-L6 (90MB):")
            logger.info(f"   Size: {percentage:.1f}% ({reduction:.1f} MB smaller)")

            # Expected size
            expected = self.option['expected_size']
            if abs(total_size - expected) < 10:
                logger.info(f"✅ Close to expected size ({expected}MB)")
            else:
                logger.warning(f"⚠️ Different from expected ({expected}MB)")

        except Exception as e:
            logger.warning(f"⚠️ Could not calculate sizes: {e}")

    def validate_embeddings(self) -> bool:
        """
        Validate exported model by generating embeddings.

        Returns:
            bool: True if validation passes
        """
        try:
            logger.info("\n🧪 Validating embeddings...")

            from optimum.onnxruntime import ORTModelForFeatureExtraction
            from transformers import AutoTokenizer
            import numpy as np

            # Load model
            model = ORTModelForFeatureExtraction.from_pretrained(self.output_dir)
            tokenizer = AutoTokenizer.from_pretrained(self.output_dir)

            # Test sentences
            sentences = [
                "What is artificial intelligence?",
                "Machine learning is a subset of AI",
                "The weather is nice today"
            ]

            logger.info("📝 Generating embeddings for test sentences:")
            embeddings = []

            for sentence in sentences:
                logger.info(f"\n  Sentence: '{sentence}'")

                # Tokenize
                inputs = tokenizer(
                    sentence,
                    padding=True,
                    truncation=True,
                    return_tensors="pt"
                )

                # Generate embedding
                outputs = model(**inputs)

                # Mean pooling
                attention_mask = inputs['attention_mask']
                token_embeddings = outputs[0]
                input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
                embedding = torch.sum(token_embeddings * input_mask_expanded, 1) / torch.clamp(input_mask_expanded.sum(1), min=1e-9)

                embeddings.append(embedding[0].detach().numpy())
                logger.info(f"  Embedding shape: {embedding.shape}")
                logger.info(f"  Embedding norm: {np.linalg.norm(embedding[0].detach().numpy()):.4f}")

            # Test semantic similarity
            logger.info("\n🔍 Testing semantic similarity:")
            sim_12 = np.dot(embeddings[0], embeddings[1]) / (np.linalg.norm(embeddings[0]) * np.linalg.norm(embeddings[1]))
            sim_13 = np.dot(embeddings[0], embeddings[2]) / (np.linalg.norm(embeddings[0]) * np.linalg.norm(embeddings[2]))

            logger.info(f"  Similarity (AI / ML): {sim_12:.4f} (should be high)")
            logger.info(f"  Similarity (AI / Weather): {sim_13:.4f} (should be low)")

            if sim_12 > 0.6 and sim_13 < 0.4:
                logger.info("✅ Validation passed: Embeddings semantically meaningful")
                return True
            else:
                logger.warning("⚠️ Validation warning: Unexpected similarity scores")
                return False

        except Exception as e:
            logger.error(f"❌ Validation failed: {e}")
            import traceback
            traceback.print_exc()
            return False


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Export optimized MiniLM embeddings for mobile"
    )
    parser.add_argument(
        "--option",
        type=int,
        choices=[1, 2, 3],
        default=3,
        help="Optimization option: 1=L3-fp32, 2=L6-int8, 3=L3-int8 (default: 3)"
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default="models/minilm-optimized",
        help="Output directory base"
    )
    parser.add_argument(
        "--validate",
        action="store_true",
        default=True,
        help="Run validation tests (default: True)"
    )

    args = parser.parse_args()

    # Create optimizer
    optimizer = MiniLMOptimizer(option=args.option, output_dir=args.output_dir)

    # Check dependencies
    if not optimizer.check_dependencies():
        logger.error("❌ Please install required dependencies")
        sys.exit(1)

    # Export
    logger.info("\n" + "="*60)
    logger.info(f"🚀 Starting MiniLM Optimization (Option {args.option})")
    logger.info("="*60 + "\n")

    if not optimizer.export_to_onnx():
        logger.error("❌ Export failed")
        sys.exit(1)

    # Validate
    if args.validate:
        logger.info("\n" + "="*60)
        logger.info("🧪 Validation Step")
        logger.info("="*60 + "\n")
        if not optimizer.validate_embeddings():
            logger.warning("⚠️ Validation had warnings")

    # Final summary
    logger.info("\n" + "="*60)
    logger.info("✅ MiniLM Optimization Complete!")
    logger.info("="*60)
    logger.info(f"📁 Output: {optimizer.output_dir}")
    logger.info("\n📝 Next steps:")
    logger.info("   1. Test semantic retrieval quality with this model")
    logger.info("   2. Compare precision@3 vs current MiniLM-L6")
    logger.info("   3. If precision >90%, use for production")
    logger.info("   4. Copy to app/composeApp/src/androidMain/assets/models/")
    logger.info("="*60 + "\n")


if __name__ == "__main__":
    main()
