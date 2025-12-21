#!/usr/bin/env python3
"""
SmolLM2-135M Quality Validation

Tests the 135M model with simple prompts to verify quality is acceptable
before committing to it for production.
"""

from pathlib import Path
import logging

logging.basicConfig(level=logging.INFO, format='%(message)s')
logger = logging.getLogger(__name__)


def test_model_quality():
    """Test model with sample prompts"""
    try:
        from optimum.onnxruntime import ORTModelForCausalLM
        from transformers import AutoTokenizer
        
        model_path = "models/smollm2-135m-onnx-q4f16"
        
        logger.info("="*60)
        logger.info("🧪 SmolLM2-135M Quality Test")
        logger.info("="*60)
        logger.info(f"\n📁 Loading model from: {model_path}")
        
        # Load model
        model = ORTModelForCausalLM.from_pretrained(
            model_path,
            subfolder="onnx",
            file_name="model_q4f16.onnx"
        )
        tokenizer = AutoTokenizer.from_pretrained(model_path)
        
        logger.info("✅ Model loaded successfully\n")
        
        # Test prompts (simple, medium, complex)
        test_prompts = [
            "Hello! How are you?",
            "Explain what AI is in simple terms.",
            "Write a short poem about technology.",
        ]
        
        for i, prompt in enumerate(test_prompts, 1):
            logger.info(f"\n{'='*60}")
            logger.info(f"Test {i}/3")
            logger.info(f"{'='*60}")
            logger.info(f"Prompt: {prompt}")
            logger.info(f"\n{'─'*60}")
            
            # Tokenize
            inputs = tokenizer(prompt, return_tensors="pt")
            
            # Generate
            outputs = model.generate(
                **inputs,
                max_new_tokens=100,
                do_sample=True,
                temperature=0.7,
                top_p=0.9,
                pad_token_id=tokenizer.eos_token_id
            )
            
            # Decode
            response = tokenizer.decode(outputs[0], skip_special_tokens=True)
            logger.info(f"Response:\n{response}")
            logger.info(f"{'─'*60}")
        
        logger.info(f"\n{'='*60}")
        logger.info("✅ Quality test complete!")
        logger.info("="*60)
        logger.info("\n📝 Manual Assessment:")
        logger.info("   - Are responses coherent? (Yes/No)")
        logger.info("   - Do they follow instructions? (Yes/No)")
        logger.info("   - Quality vs SmolLM2-360M? (Better/Same/Worse)")
        logger.info("\n💡 If quality is acceptable (>85%), we can use this model!")
        logger.info("="*60 + "\n")
        
        return True
        
    except Exception as e:
        logger.error(f"❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    test_model_quality()
