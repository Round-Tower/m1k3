#!/usr/bin/env python3
"""
SmolLM2-360M Reference Test Script

This script provides a Python reference implementation using HuggingFace Transformers
to validate the Android/Kotlin tokenizer and inference implementation.

Compares:
1. Tokenizer output (token IDs) - Kotlin vs Python
2. Inference output (generated text) - Android vs PyTorch
3. Round-trip encoding/decoding - both implementations

Usage:
    python scripts/test_smollm2_reference.py
    python scripts/test_smollm2_reference.py --compare-tokens "Hello world"
    python scripts/test_smollm2_reference.py --generate "Hello, how can I help you?"
"""

import argparse
import sys
from typing import List, Tuple

try:
    from transformers import AutoTokenizer, AutoModelForCausalLM
    import torch
except ImportError:
    print("❌ ERROR: transformers library not installed")
    print("   Install with: pip install transformers torch")
    sys.exit(1)


class SmolLM2Reference:
    """Reference implementation using HuggingFace Transformers"""

    def __init__(self, model_name: str = "HuggingFaceTB/SmolLM2-360M-Instruct"):
        """Initialize tokenizer and model"""
        print(f"📚 Loading SmolLM2-360M from HuggingFace...")
        print(f"   Model: {model_name}")

        try:
            self.tokenizer = AutoTokenizer.from_pretrained(model_name)
            self.model = AutoModelForCausalLM.from_pretrained(
                model_name,
                torch_dtype=torch.float32,
                device_map="cpu"  # Force CPU for compatibility
            )
            print(f"✅ Model loaded successfully")
            print(f"   Vocab size: {self.tokenizer.vocab_size}")
            print(f"   Model params: {sum(p.numel() for p in self.model.parameters()):,}")
            print()

        except Exception as e:
            print(f"❌ ERROR loading model: {e}")
            sys.exit(1)

    def test_tokenizer(self, text: str) -> Tuple[List[int], str]:
        """
        Test tokenizer encoding/decoding

        Args:
            text: Input text to tokenize

        Returns:
            Tuple of (token_ids, decoded_text)
        """
        print("=" * 70)
        print("🧪 TOKENIZER TEST")
        print("=" * 70)
        print(f"Input text: \"{text}\"")
        print()

        # Encode
        token_ids = self.tokenizer.encode(text, add_special_tokens=False)
        print(f"Token IDs ({len(token_ids)}): {token_ids}")
        print()

        # Show tokens
        tokens = [self.tokenizer.decode([tid]) for tid in token_ids]
        print(f"Tokens: {tokens}")
        print()

        # Decode
        decoded = self.tokenizer.decode(token_ids, skip_special_tokens=True)
        print(f"Decoded: \"{decoded}\"")
        print()

        # Verify round-trip
        matches = decoded.strip() == text.strip()
        if matches:
            print("✅ Round-trip successful - perfect match!")
        else:
            print("❌ Round-trip FAILED - mismatch detected!")
            print(f"   Expected: \"{text}\"")
            print(f"   Got:      \"{decoded}\"")

        print("=" * 70)
        print()

        return token_ids, decoded

    def generate(
        self,
        prompt: str,
        max_tokens: int = 256,
        temperature: float = 0.0
    ) -> str:
        """
        Generate text using SmolLM2-360M

        Args:
            prompt: Input prompt
            max_tokens: Maximum tokens to generate
            temperature: Sampling temperature (0.0 = greedy)

        Returns:
            Generated text
        """
        print("=" * 70)
        print("🤖 INFERENCE TEST")
        print("=" * 70)
        print(f"Prompt: \"{prompt}\"")
        print(f"Max tokens: {max_tokens}")
        print(f"Temperature: {temperature} {'(greedy decoding)' if temperature == 0.0 else ''}")
        print()

        # Apply ChatML template
        messages = [
            {"role": "system", "content": "You are M1K3, a helpful AI assistant."},
            {"role": "user", "content": prompt}
        ]

        # Format with chat template
        formatted_prompt = self.tokenizer.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=True
        )

        print(f"Formatted prompt (ChatML):")
        print(formatted_prompt)
        print()

        # Tokenize
        inputs = self.tokenizer(formatted_prompt, return_tensors="pt")
        input_ids = inputs["input_ids"]

        print(f"Input token count: {input_ids.shape[1]}")
        print()

        # Generate
        print("Generating...")
        with torch.no_grad():
            outputs = self.model.generate(
                input_ids,
                max_new_tokens=max_tokens,
                temperature=temperature if temperature > 0 else 1.0,  # Avoid 0.0
                do_sample=temperature > 0,
                pad_token_id=self.tokenizer.eos_token_id,
                eos_token_id=self.tokenizer.eos_token_id
            )

        # Decode (skip prompt)
        generated_ids = outputs[0][input_ids.shape[1]:]
        generated_text = self.tokenizer.decode(generated_ids, skip_special_tokens=True)

        print(f"Generated ({len(generated_ids)} tokens):")
        print("-" * 70)
        print(generated_text)
        print("-" * 70)
        print()

        print("=" * 70)
        print()

        return generated_text

    def compare_tokenizers(self, test_cases: List[str]):
        """
        Test multiple tokenization examples

        Args:
            test_cases: List of test strings
        """
        print("\n" + "=" * 70)
        print("📊 TOKENIZER COMPARISON SUITE")
        print("=" * 70)
        print()

        all_passed = True

        for i, text in enumerate(test_cases, 1):
            print(f"Test {i}/{len(test_cases)}: \"{text}\"")

            token_ids, decoded = self.test_tokenizer(text)

            # Round-trip check
            if decoded.strip() != text.strip():
                all_passed = False

        print("=" * 70)
        if all_passed:
            print("✅ ALL TOKENIZER TESTS PASSED")
        else:
            print("❌ SOME TOKENIZER TESTS FAILED")
        print("=" * 70)
        print()


def main():
    parser = argparse.ArgumentParser(
        description="SmolLM2-360M Reference Test - Validate Android tokenizer/inference"
    )
    parser.add_argument(
        "--compare-tokens",
        type=str,
        help="Test tokenizer with specific text"
    )
    parser.add_argument(
        "--generate",
        type=str,
        help="Generate text from prompt"
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=256,
        help="Maximum tokens to generate (default: 256)"
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.0,
        help="Sampling temperature (default: 0.0 = greedy)"
    )

    args = parser.parse_args()

    # Initialize reference
    ref = SmolLM2Reference()

    # Run tests based on arguments
    if args.compare_tokens:
        # Single tokenizer test
        ref.test_tokenizer(args.compare_tokens)

    elif args.generate:
        # Generate text
        ref.generate(args.generate, args.max_tokens, args.temperature)

    else:
        # Default: Run comprehensive test suite
        print("🧪 Running comprehensive SmolLM2-360M reference tests\n")

        # Tokenizer tests
        test_cases = [
            "Hello world",
            "I am M1K3, your AI assistant.",
            "The quick brown fox jumps over the lazy dog.",
            "Testing spaces and punctuation: hello, how are you?",
            "Special chars: @#$%^&*()",
            "Numbers: 1234567890"
        ]

        ref.compare_tokenizers(test_cases)

        # Inference test
        print("\n" + "=" * 70)
        print("🤖 INFERENCE COMPARISON")
        print("=" * 70)
        print()

        test_prompts = [
            "Hello, how can I help you today?",
            "What is your name?",
            "Tell me about artificial intelligence."
        ]

        for prompt in test_prompts:
            ref.generate(prompt, max_tokens=100, temperature=0.0)

        print("\n✅ Reference tests complete!")
        print("\nNext steps:")
        print("1. Compare token IDs with Android app logcat output")
        print("2. Compare generated text quality (spaces, coherence)")
        print("3. Verify Android matches Python reference implementation")


if __name__ == "__main__":
    main()
