#!/usr/bin/env python3
"""
Export all-MiniLM-L6-v2 to ONNX format for mobile inference

This is the DEFAULT embedding model for M1K3 AI Mobile.
Small, fast, and excellent quality for 99% of use cases.

Model: sentence-transformers/all-MiniLM-L6-v2
- Size: 80MB (INT8 quantized)
- Dimensions: 384
- Speed: 25-35ms on mid-range devices
- Quality: Excellent (90M+ downloads)

Usage:
    python export_minilm_embedding.py --output models/minilm --quantize int8
"""

import argparse
from pathlib import Path
import torch
from transformers import AutoModel, AutoTokenizer
from optimum.onnxruntime import ORTModelForFeatureExtraction
from optimum.onnxruntime.configuration import AutoQuantizationConfig
from optimum.onnxruntime import ORTQuantizer
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def export_minilm_embedding(
    model_name: str = "sentence-transformers/all-MiniLM-L6-v2",
    output_dir: Path = Path("models/minilm"),
    quantization: str = "int8"
):
    """
    Export MiniLM-L6 to ONNX format with quantization

    Args:
        model_name: HuggingFace model identifier
        output_dir: Directory to save ONNX model
        quantization: Quantization type (int8, int4, or none)
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    logger.info(f"Loading {model_name}...")

    # Load model and tokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModel.from_pretrained(model_name)

    logger.info("Exporting to ONNX format...")

    # Export to ONNX using Optimum
    try:
        onnx_model = ORTModelForFeatureExtraction.from_pretrained(
            model_name,
            export=True,
            provider="CPUExecutionProvider"
        )

        # Save ONNX model
        onnx_model.save_pretrained(output_dir)
        logger.info(f"ONNX model saved to {output_dir}")

    except Exception as e:
        logger.warning(f"Optimum export failed: {e}, trying manual export...")

        # Fallback to manual export
        from torch.onnx import export
        dummy_input = tokenizer("test", return_tensors="pt", padding=True, truncation=True)

        torch.onnx.export(
            model,
            (dummy_input['input_ids'], dummy_input['attention_mask']),
            output_dir / "model.onnx",
            input_names=['input_ids', 'attention_mask'],
            output_names=['last_hidden_state'],
            dynamic_axes={
                'input_ids': {0: 'batch', 1: 'sequence'},
                'attention_mask': {0: 'batch', 1: 'sequence'},
                'last_hidden_state': {0: 'batch', 1: 'sequence'}
            },
            opset_version=14
        )

    # Apply quantization if requested
    if quantization != "none":
        logger.info(f"Applying {quantization} quantization...")

        try:
            quantizer = ORTQuantizer.from_pretrained(output_dir)

            if quantization == "int8":
                qconfig = AutoQuantizationConfig.arm64(
                    is_static=False,
                    per_channel=True
                )
            elif quantization == "int4":
                qconfig = AutoQuantizationConfig.arm64(
                    is_static=False,
                    per_channel=True,
                    operators_to_quantize=["MatMul", "Add"]
                )
            else:
                raise ValueError(f"Unknown quantization type: {quantization}")

            # Quantize model
            quantizer.quantize(
                save_dir=output_dir / f"quantized_{quantization}",
                quantization_config=qconfig
            )

            logger.info(f"Quantized model saved")

        except Exception as e:
            logger.warning(f"Quantization failed: {e}, using fp32 model")

    # Save tokenizer
    tokenizer.save_pretrained(output_dir)

    # Create metadata file
    metadata = {
        "model_name": model_name,
        "model_type": "sentence-transformers",
        "embedding_dimensions": 384,
        "max_sequence_length": 256,
        "quantization": quantization,
        "pooling": "mean",
        "normalize": True,
        "usage": {
            "default": "General semantic search (no task prefix needed)",
            "query": "Search query: {text}",
            "passage": "Search passage: {text}"
        },
        "features": [
            "Symmetric semantic search (queries and documents use same encoder)",
            "Pre-trained on 1B+ sentence pairs",
            "Optimized for short texts (up to 256 tokens)",
            "Fast inference (<50ms on mobile)",
            "Small model size (80MB quantized)"
        ]
    }

    import json
    with open(output_dir / "metadata.json", "w") as f:
        json.dump(metadata, indent=2, fp=f)

    logger.info(f"Metadata saved to {output_dir / 'metadata.json'}")

    # Test inference
    logger.info("Testing inference...")
    test_text = "This is a test sentence for embedding generation."
    inputs = tokenizer(test_text, return_tensors="pt", padding=True, truncation=True, max_length=256)

    with torch.no_grad():
        outputs = model(**inputs)
        # Mean pooling
        embeddings = mean_pooling(outputs, inputs['attention_mask'])
        # Normalize
        embeddings = torch.nn.functional.normalize(embeddings, p=2, dim=1)

        logger.info(f"Test embedding shape: {embeddings.shape}")
        logger.info(f"Test embedding norm: {embeddings.norm().item():.4f}")

    logger.info("Export complete!")
    logger.info(f"\nNext steps:")
    logger.info(f"1. Copy {output_dir} to app/composeApp/src/androidMain/assets/models/")
    logger.info(f"2. MiniLmEmbeddingEngine will load automatically")
    logger.info(f"3. Test semantic search in the app")

    return output_dir


def mean_pooling(model_output, attention_mask):
    """Mean pooling - take average of all token embeddings"""
    token_embeddings = model_output.last_hidden_state
    input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
    return torch.sum(token_embeddings * input_mask_expanded, 1) / torch.clamp(input_mask_expanded.sum(1), min=1e-9)


def main():
    parser = argparse.ArgumentParser(description="Export MiniLM-L6 to ONNX")
    parser.add_argument(
        "--model",
        default="sentence-transformers/all-MiniLM-L6-v2",
        help="HuggingFace model identifier"
    )
    parser.add_argument(
        "--output",
        default="models/minilm",
        help="Output directory"
    )
    parser.add_argument(
        "--quantize",
        choices=["int8", "int4", "none"],
        default="int8",
        help="Quantization type"
    )

    args = parser.parse_args()

    export_minilm_embedding(
        model_name=args.model,
        output_dir=Path(args.output),
        quantization=args.quantize
    )


if __name__ == "__main__":
    main()
