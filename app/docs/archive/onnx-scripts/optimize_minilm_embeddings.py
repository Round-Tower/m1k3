#!/usr/bin/env python3
"""
MiniLM Embedding Optimization - PHASE1.5-006

Evaluates three optimization approaches:
1. paraphrase-MiniLM-L3-v2 (smaller model, fp32)
2. all-MiniLM-L6-v2 (current model, INT8 quantization)
3. paraphrase-MiniLM-L3-v2 (smaller model, INT8) - Hybrid

Target: Reduce from 87MB to ~50MB while maintaining >90% retrieval quality
"""

import argparse
import logging
from pathlib import Path
import time

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


def export_minilm_l3(output_dir: str = "models/minilm-l3-onnx"):
    """Export paraphrase-MiniLM-L3-v2 to ONNX"""
    try:
        from optimum.onnxruntime import ORTModelForFeatureExtraction
        from transformers import AutoTokenizer
        
        model_id = "sentence-transformers/paraphrase-MiniLM-L3-v2"
        output_path = Path(output_dir)
        output_path.mkdir(parents=True, exist_ok=True)
        
        logger.info(f"\n{'='*60}")
        logger.info("Option 1: Export paraphrase-MiniLM-L3-v2 (fp32)")
        logger.info(f"{'='*60}")
        logger.info(f"Model: {model_id}")
        logger.info(f"Output: {output_path}")
        
        # Export to ONNX
        logger.info("Exporting to ONNX...")
        model = ORTModelForFeatureExtraction.from_pretrained(
            model_id,
            export=True,
            provider="CPUExecutionProvider"
        )
        
        # Save model
        model.save_pretrained(output_path)
        logger.info("✅ Model exported")
        
        # Save tokenizer
        tokenizer = AutoTokenizer.from_pretrained(model_id)
        tokenizer.save_pretrained(output_path)
        logger.info("✅ Tokenizer saved")
        
        # Report size
        model_file = output_path / "model.onnx"
        if model_file.exists():
            size_mb = model_file.stat().st_size / (1024 * 1024)
            logger.info(f"\n📊 Model size: {size_mb:.1f} MB")
            
            # Compare to target
            current_size = 87.0
            target_size = 50.0
            reduction = current_size - size_mb
            percentage = (reduction / current_size) * 100
            
            logger.info(f"Current (L6): {current_size:.1f} MB")
            logger.info(f"Reduction: {reduction:.1f} MB ({percentage:.1f}%)")
            
            if size_mb <= target_size:
                logger.info(f"✅ Under target by {target_size - size_mb:.1f} MB!")
            else:
                logger.info(f"⚠️  Over target by {size_mb - target_size:.1f} MB")
        
        return True
        
    except Exception as e:
        logger.error(f"❌ Export failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def quantize_minilm_l6(input_dir: str = "models/minilm_onnx", 
                       output_dir: str = "models/minilm-l6-onnx-int8"):
    """Quantize existing all-MiniLM-L6-v2 to INT8"""
    try:
        from onnxruntime.quantization import quantize_dynamic, QuantType
        import shutil
        
        input_path = Path(input_dir)
        output_path = Path(output_dir)
        output_path.mkdir(parents=True, exist_ok=True)
        
        logger.info(f"\n{'='*60}")
        logger.info("Option 2: Quantize all-MiniLM-L6-v2 to INT8")
        logger.info(f"{'='*60}")
        logger.info(f"Input: {input_path}")
        logger.info(f"Output: {output_path}")
        
        # Find model file
        model_file = input_path / "model.onnx"
        if not model_file.exists():
            logger.error(f"❌ Model not found: {model_file}")
            return False
        
        output_model = output_path / "model_quantized.onnx"
        
        logger.info("Quantizing to INT8...")
        quantize_dynamic(
            model_input=str(model_file),
            model_output=str(output_model),
            weight_type=QuantType.QInt8,
            per_channel=True,
        )
        
        # Copy tokenizer files
        for file in ["tokenizer.json", "tokenizer_config.json", "special_tokens_map.json", 
                     "config.json", "vocab.txt"]:
            src = input_path / file
            if src.exists():
                shutil.copy2(src, output_path / file)
        
        logger.info("✅ Quantization complete")
        
        # Report sizes
        input_size = model_file.stat().st_size / (1024 * 1024)
        output_size = output_model.stat().st_size / (1024 * 1024)
        reduction = input_size - output_size
        percentage = (reduction / input_size) * 100
        
        logger.info(f"\n📊 Results:")
        logger.info(f"Original (fp32): {input_size:.1f} MB")
        logger.info(f"Quantized (int8): {output_size:.1f} MB")
        logger.info(f"Reduction: {reduction:.1f} MB ({percentage:.1f}%)")
        
        target_size = 50.0
        if output_size <= target_size:
            logger.info(f"✅ Under target by {target_size - output_size:.1f} MB!")
        else:
            logger.info(f"⚠️  Over target by {output_size - target_size:.1f} MB")
        
        return True
        
    except ImportError as e:
        logger.error(f"❌ Missing onnxruntime: {e}")
        return False
    except Exception as e:
        logger.error(f"❌ Quantization failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def export_and_quantize_l3(output_dir: str = "models/minilm-l3-onnx-int8"):
    """Export paraphrase-MiniLM-L3-v2 and quantize to INT8 (hybrid approach)"""
    try:
        from optimum.onnxruntime import ORTModelForFeatureExtraction
        from transformers import AutoTokenizer
        from onnxruntime.quantization import quantize_dynamic, QuantType
        import shutil
        
        model_id = "sentence-transformers/paraphrase-MiniLM-L3-v2"
        temp_dir = Path("models/minilm-l3-onnx-temp")
        output_path = Path(output_dir)
        output_path.mkdir(parents=True, exist_ok=True)
        temp_dir.mkdir(parents=True, exist_ok=True)
        
        logger.info(f"\n{'='*60}")
        logger.info("Option 3: Hybrid (L3 + INT8)")
        logger.info(f"{'='*60}")
        logger.info(f"Model: {model_id}")
        logger.info(f"Output: {output_path}")
        
        # Step 1: Export to ONNX
        logger.info("\nStep 1: Exporting to ONNX...")
        model = ORTModelForFeatureExtraction.from_pretrained(
            model_id,
            export=True,
            provider="CPUExecutionProvider"
        )
        model.save_pretrained(temp_dir)
        
        tokenizer = AutoTokenizer.from_pretrained(model_id)
        tokenizer.save_pretrained(output_path)
        logger.info("✅ Export complete")
        
        # Step 2: Quantize
        logger.info("\nStep 2: Quantizing to INT8...")
        temp_model = temp_dir / "model.onnx"
        output_model = output_path / "model_quantized.onnx"
        
        quantize_dynamic(
            model_input=str(temp_model),
            model_output=str(output_model),
            weight_type=QuantType.QInt8,
            per_channel=True,
        )
        logger.info("✅ Quantization complete")
        
        # Copy config
        shutil.copy2(temp_dir / "config.json", output_path / "config.json")
        
        # Clean up temp
        shutil.rmtree(temp_dir)
        
        # Report sizes
        output_size = output_model.stat().st_size / (1024 * 1024)
        current_size = 87.0
        reduction = current_size - output_size
        percentage = (reduction / current_size) * 100
        
        logger.info(f"\n📊 Results:")
        logger.info(f"Original (L6 fp32): {current_size:.1f} MB")
        logger.info(f"Optimized (L3 int8): {output_size:.1f} MB")
        logger.info(f"Reduction: {reduction:.1f} MB ({percentage:.1f}%)")
        
        target_size = 50.0
        if output_size <= target_size:
            logger.info(f"✅ Under target by {target_size - output_size:.1f} MB!")
        else:
            logger.info(f"⚠️  Over target by {output_size - target_size:.1f} MB")
        
        return True
        
    except Exception as e:
        logger.error(f"❌ Hybrid optimization failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def test_embedding_quality(model_path: str, model_name: str):
    """Test embedding quality with sample text"""
    try:
        from optimum.onnxruntime import ORTModelForFeatureExtraction
        from transformers import AutoTokenizer
        import numpy as np
        
        logger.info(f"\n🧪 Testing {model_name}...")
        
        # Load model
        model = ORTModelForFeatureExtraction.from_pretrained(model_path)
        tokenizer = AutoTokenizer.from_pretrained(model_path)
        
        # Test queries
        test_texts = [
            "Hello, how are you?",
            "Can you teach me about AI?",
            "Write a poem about technology."
        ]
        
        # Generate embeddings
        start = time.time()
        for text in test_texts:
            inputs = tokenizer(text, return_tensors="pt", padding=True, truncation=True)
            outputs = model(**inputs)
            # Mean pooling
            embeddings = outputs.last_hidden_state.mean(dim=1)
            # L2 normalize
            embeddings = embeddings / np.linalg.norm(embeddings, axis=1, keepdims=True)
        
        duration = (time.time() - start) / len(test_texts) * 1000
        
        logger.info(f"✅ Embedding generation: {duration:.1f}ms per query")
        logger.info(f"✅ Model functional")
        
        return True
        
    except Exception as e:
        logger.error(f"❌ Quality test failed: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Optimize MiniLM embeddings")
    parser.add_argument("--option", type=str, choices=["l3", "l6-int8", "hybrid", "all"],
                       default="all", help="Which optimization to run")
    parser.add_argument("--test", action="store_true", help="Test quality after optimization")
    
    args = parser.parse_args()
    
    logger.info("="*60)
    logger.info("🔧 MiniLM Embedding Optimization - PHASE1.5-006")
    logger.info("="*60)
    logger.info(f"\nCurrent: all-MiniLM-L6-v2 (87 MB)")
    logger.info(f"Target: ~50 MB")
    logger.info(f"Options: L3 (fp32), L6 (int8), L3 (int8)\n")
    
    results = {}
    
    if args.option in ["l3", "all"]:
        results["l3"] = export_minilm_l3()
        if args.test and results["l3"]:
            test_embedding_quality("models/minilm-l3-onnx", "L3 (fp32)")
    
    if args.option in ["l6-int8", "all"]:
        results["l6-int8"] = quantize_minilm_l6()
        if args.test and results["l6-int8"]:
            test_embedding_quality("models/minilm-l6-onnx-int8", "L6 (int8)")
    
    if args.option in ["hybrid", "all"]:
        results["hybrid"] = export_and_quantize_l3()
        if args.test and results["hybrid"]:
            test_embedding_quality("models/minilm-l3-onnx-int8", "L3 (int8)")
    
    # Summary
    logger.info(f"\n{'='*60}")
    logger.info("📊 Optimization Summary")
    logger.info(f"{'='*60}")
    for opt, success in results.items():
        status = "✅" if success else "❌"
        logger.info(f"{status} {opt}")
    
    logger.info("\n📝 Next steps:")
    logger.info("   1. Compare quality metrics")
    logger.info("   2. Choose best size/quality trade-off")
    logger.info("   3. Update app to use optimized model")
    logger.info("="*60 + "\n")


if __name__ == "__main__":
    main()
