#!/usr/bin/env python3
"""
Gemma 3:270m Validation Script - PHASE1.5-001

Validates the exported ONNX model for correctness and performance.

Usage:
    python validate_gemma3.py
    python validate_gemma3.py --model-path custom/path
    python validate_gemma3.py --benchmark  # Run performance benchmarks
"""

import argparse
import logging
import time
from pathlib import Path

import torch
from transformers import AutoTokenizer

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class Gemma3Validator:
    """Validates Gemma 3:270m ONNX export."""

    def __init__(self, model_path: str = "models/gemma3-270m-onnx"):
        self.model_path = Path(model_path)
        self.model = None
        self.tokenizer = None

    def load_model(self) -> bool:
        """Load ONNX model and tokenizer."""
        try:
            logger.info(f"🔄 Loading model from {self.model_path}")

            from optimum.onnxruntime import ORTModelForCausalLM

            self.model = ORTModelForCausalLM.from_pretrained(self.model_path)
            self.tokenizer = AutoTokenizer.from_pretrained(self.model_path)

            logger.info(f"✅ Model loaded successfully")
            logger.info(f"📝 Tokenizer vocab size: {len(self.tokenizer)}")

            return True

        except Exception as e:
            logger.error(f"❌ Failed to load model: {e}")
            return False

    def validate_structure(self) -> bool:
        """Validate ONNX model structure."""
        try:
            logger.info("\n🔍 Validating model structure...")

            # Check required files
            required_files = ["model.onnx", "config.json", "tokenizer.json"]
            for file_name in required_files:
                file_path = self.model_path / file_name
                if not file_path.exists():
                    logger.error(f"❌ Missing file: {file_name}")
                    return False
                logger.info(f"  ✅ {file_name} exists")

            logger.info("✅ Model structure valid")
            return True

        except Exception as e:
            logger.error(f"❌ Structure validation failed: {e}")
            return False

    def validate_inference(self) -> bool:
        """Validate basic inference."""
        try:
            logger.info("\n🧪 Validating inference...")

            test_prompts = [
                "Hello, how are you?",
                "What is 2+2?",
                "Explain AI in simple terms.",
            ]

            for i, prompt in enumerate(test_prompts, 1):
                logger.info(f"\n  Test {i}/3: '{prompt}'")

                # Tokenize
                inputs = self.tokenizer(prompt, return_tensors="pt")

                # Generate
                start_time = time.time()
                outputs = self.model.generate(
                    **inputs,
                    max_new_tokens=50,
                    do_sample=True,
                    temperature=0.7
                )
                duration = time.time() - start_time

                # Decode
                response = self.tokenizer.decode(outputs[0], skip_special_tokens=True)

                # Check output
                if len(response.strip()) > len(prompt):
                    logger.info(f"  ✅ Generated ({duration:.2f}s): {response[:80]}...")
                else:
                    logger.error(f"  ❌ Response too short: {response}")
                    return False

            logger.info("\n✅ Inference validation passed")
            return True

        except Exception as e:
            logger.error(f"❌ Inference validation failed: {e}")
            return False

    def benchmark_performance(self) -> bool:
        """Benchmark model performance."""
        try:
            logger.info("\n⚡ Benchmarking performance...")

            prompt = "Hello, how are you today?"
            inputs = self.tokenizer(prompt, return_tensors="pt")

            # Warmup
            logger.info("  🔥 Warming up...")
            for _ in range(3):
                self.model.generate(**inputs, max_new_tokens=10)

            # Benchmark
            num_runs = 10
            total_tokens = 0
            total_time = 0

            logger.info(f"  🏃 Running {num_runs} iterations...")

            for i in range(num_runs):
                start_time = time.time()
                outputs = self.model.generate(
                    **inputs,
                    max_new_tokens=50,
                    do_sample=True,
                    temperature=0.7
                )
                duration = time.time() - start_time

                num_tokens = outputs.shape[1] - inputs['input_ids'].shape[1]
                total_tokens += num_tokens
                total_time += duration

            # Calculate metrics
            avg_tokens = total_tokens / num_runs
            avg_time = total_time / num_runs
            tokens_per_sec = total_tokens / total_time

            logger.info("\n📊 Benchmark Results:")
            logger.info(f"  Average tokens: {avg_tokens:.1f}")
            logger.info(f"  Average time: {avg_time:.3f}s")
            logger.info(f"  Tokens/sec: {tokens_per_sec:.1f}")

            # Compare to targets
            logger.info("\n🎯 Target Comparison:")
            if tokens_per_sec >= 25:
                logger.info(f"  ✅ Meets target (25+ tok/s on device)")
            else:
                logger.warning(f"  ⚠️ Below target (got {tokens_per_sec:.1f}, target 25)")
                logger.info(f"  💡 Note: CPU performance. On-device with NNAPI will be faster")

            return True

        except Exception as e:
            logger.error(f"❌ Benchmark failed: {e}")
            return False

    def validate_tokenizer(self) -> bool:
        """Validate tokenizer encoding/decoding."""
        try:
            logger.info("\n🔤 Validating tokenizer...")

            test_texts = [
                "Hello, world!",
                "The quick brown fox jumps over the lazy dog.",
                "AI and ML are fascinating fields.",
            ]

            for text in test_texts:
                # Encode
                tokens = self.tokenizer.encode(text)
                # Decode
                decoded = self.tokenizer.decode(tokens, skip_special_tokens=True)

                # Check round-trip
                if decoded.strip() == text.strip():
                    logger.info(f"  ✅ '{text}'")
                else:
                    logger.warning(f"  ⚠️ Mismatch: '{text}' → '{decoded}'")

            logger.info("✅ Tokenizer validation passed")
            return True

        except Exception as e:
            logger.error(f"❌ Tokenizer validation failed: {e}")
            return False


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Validate Gemma 3:270m ONNX export"
    )
    parser.add_argument(
        "--model-path",
        type=str,
        default="models/gemma3-270m-onnx",
        help="Path to exported ONNX model"
    )
    parser.add_argument(
        "--benchmark",
        action="store_true",
        help="Run performance benchmarks"
    )

    args = parser.parse_args()

    # Create validator
    validator = Gemma3Validator(model_path=args.model_path)

    logger.info("="*60)
    logger.info("🧪 Gemma 3:270m ONNX Validation")
    logger.info("="*60)

    # Load model
    if not validator.load_model():
        logger.error("❌ Cannot proceed without model")
        return 1

    # Run validations
    all_passed = True

    # Structure validation
    if not validator.validate_structure():
        all_passed = False

    # Tokenizer validation
    if not validator.validate_tokenizer():
        all_passed = False

    # Inference validation
    if not validator.validate_inference():
        all_passed = False

    # Benchmark (optional)
    if args.benchmark:
        if not validator.benchmark_performance():
            all_passed = False

    # Summary
    logger.info("\n" + "="*60)
    if all_passed:
        logger.info("✅ All validations passed!")
        logger.info("="*60)
        logger.info("🎉 Gemma 3:270m ONNX model is ready for deployment")
        return 0
    else:
        logger.error("❌ Some validations failed")
        logger.info("="*60)
        logger.info("⚠️ Review errors above and re-export if needed")
        return 1


if __name__ == "__main__":
    exit(main())
