#!/usr/bin/env python3
"""
Export Embedding Gemma 300M to ONNX format for mobile inference

Usage:
    python export_gemma_embedding.py --output models/embedding_gemma_300m.onnx --quantize int8

Requirements:
    pip install transformers onnx onnxruntime optimum[exporters]
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


def export_embedding_gemma(
    model_name: str = "google/embeddinggemma-300m",
    output_dir: Path = Path("models/embedding_gemma"),
    quantization: str = "int8",
    matryoshka_dim: int = 512
):
    """
    Export Embedding Gemma to ONNX format with quantization

    Args:
        model_name: HuggingFace model identifier
        output_dir: Directory to save ONNX model
        quantization: Quantization type (int8, int4, or none)
        matryoshka_dim: Output embedding dimension (512, 256, 128, or 768 for full)
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    logger.info(f"Loading {model_name}...")

    # Load model and tokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModel.from_pretrained(model_name, trust_remote_code=True)

    logger.info("Exporting to ONNX format...")

    # Export to ONNX using Optimum
    onnx_model = ORTModelForFeatureExtraction.from_pretrained(
        model_name,
        export=True,
        provider="CPUExecutionProvider"
    )

    # Save ONNX model
    onnx_path = output_dir / "model.onnx"
    onnx_model.save_pretrained(output_dir)

    logger.info(f"ONNX model saved to {output_dir}")

    # Apply quantization if requested
    if quantization != "none":
        logger.info(f"Applying {quantization} quantization...")

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
        quantized_path = output_dir / f"model_quantized_{quantization}.onnx"
        quantizer.quantize(
            save_dir=output_dir / f"quantized_{quantization}",
            quantization_config=qconfig
        )

        logger.info(f"Quantized model saved to {quantized_path}")

    # Save tokenizer
    tokenizer.save_pretrained(output_dir)

    # Create metadata file
    metadata = {
        "model_name": model_name,
        "embedding_dimensions": 768,
        "matryoshka_dimensions": [512, 256, 128],
        "recommended_dimension": matryoshka_dim,
        "max_tokens": 2048,
        "quantization": quantization,
        "task_types": [
            "RETRIEVAL",
            "QUERY",
            "CLASSIFICATION",
            "CLUSTERING",
            "DOCUMENT",
            "CODE"
        ],
        "usage": {
            "retrieval": "query: search_query: [your query]",
            "document": "query: search_document: [your document]",
            "classification": "query: classification: [your text]",
            "clustering": "query: clustering: [your text]"
        }
    }

    import json
    with open(output_dir / "metadata.json", "w") as f:
        json.dump(metadata, indent=2, fp=f)

    logger.info(f"Metadata saved to {output_dir / 'metadata.json'}")

    # Test inference
    logger.info("Testing inference...")
    test_text = "This is a test document for embedding generation."
    inputs = tokenizer(test_text, return_tensors="pt", padding=True, truncation=True)

    with torch.no_grad():
        outputs = model(**inputs)
        # Get embeddings from last hidden state
        embeddings = outputs.last_hidden_state[:, 0, :]  # CLS token

        # Apply Matryoshka truncation if requested
        if matryoshka_dim < 768:
            embeddings = embeddings[:, :matryoshka_dim]

        # Normalize
        embeddings = torch.nn.functional.normalize(embeddings, p=2, dim=1)

        logger.info(f"Test embedding shape: {embeddings.shape}")
        logger.info(f"Test embedding norm: {embeddings.norm().item():.4f}")

    logger.info("Export complete!")
    logger.info(f"\nNext steps:")
    logger.info(f"1. Copy {output_dir} to app/composeApp/src/androidMain/assets/models/")
    logger.info(f"2. Update EmbeddingEngine implementation to use {matryoshka_dim}-dimensional embeddings")
    logger.info(f"3. Update MemoryMetadata schema to support {matryoshka_dim} dimensions")

    return output_dir


def main():
    parser = argparse.ArgumentParser(description="Export Embedding Gemma to ONNX")
    parser.add_argument(
        "--model",
        default="google/embeddinggemma-300m",
        help="HuggingFace model identifier"
    )
    parser.add_argument(
        "--output",
        default="models/embedding_gemma",
        help="Output directory"
    )
    parser.add_argument(
        "--quantize",
        choices=["int8", "int4", "none"],
        default="int8",
        help="Quantization type"
    )
    parser.add_argument(
        "--dim",
        type=int,
        default=512,
        choices=[128, 256, 512, 768],
        help="Matryoshka embedding dimension"
    )

    args = parser.parse_args()

    export_embedding_gemma(
        model_name=args.model,
        output_dir=Path(args.output),
        quantization=args.quantize,
        matryoshka_dim=args.dim
    )


if __name__ == "__main__":
    main()
