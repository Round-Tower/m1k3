#!/usr/bin/env python3
"""
M1K3 PWA Embeddings Model Exporter
Exports lightweight embeddings models for browser-based RAG
"""

import os
import json
import logging
from pathlib import Path
from typing import Dict, Optional

import torch
import onnx
from sentence_transformers import SentenceTransformer
from optimum.onnxruntime import ORTModelForFeatureExtraction
from optimum.onnxruntime.configuration import OptimizationConfig
from optimum.onnxruntime import ORTOptimizer

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class EmbeddingsExporter:
    """Exports sentence transformers models to ONNX for browser deployment"""
    
    def __init__(self, output_dir: str = "../frontend/models"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # Define available embeddings models optimized for browser deployment
        self.embeddings_models = {
            "tiny": {
                "model_name": "sentence-transformers/all-MiniLM-L6-v2",
                "description": "Ultra-light embeddings model for browser RAG",
                "embedding_dim": 384,
                "max_seq_length": 256,
                "size_mb": 23,  # Approximate original size
                "quantized_mb": 6   # Expected quantized size
            },
            "small": {
                "model_name": "sentence-transformers/all-MiniLM-L12-v2", 
                "description": "Balanced embeddings model with better quality",
                "embedding_dim": 384,
                "max_seq_length": 256,
                "size_mb": 33,
                "quantized_mb": 8
            }
        }
    
    def export_embeddings_model(self, model_tier: str = "tiny", optimize: bool = True) -> tuple[str, Dict]:
        """Export embeddings model to ONNX format"""
        if model_tier not in self.embeddings_models:
            raise ValueError(f"Unknown embeddings model tier: {model_tier}")
        
        config = self.embeddings_models[model_tier]
        model_name = config["model_name"]
        
        logger.info(f"🚀 Exporting embeddings model: {model_name}")
        
        try:
            # Load sentence transformer model
            logger.info("📝 Loading sentence transformer...")
            st_model = SentenceTransformer(model_name)
            
            # Get the transformer component
            transformer = st_model._modules['0']  # Usually the main transformer
            tokenizer = st_model.tokenizer
            
            # Create output directory
            output_path = self.output_dir / f"embeddings-{model_tier}"
            output_path.mkdir(parents=True, exist_ok=True)
            
            logger.info("🔄 Converting to ONNX...")
            
            # Export to ONNX using Optimum
            ort_model = ORTModelForFeatureExtraction.from_pretrained(
                model_name,
                export=True,
                provider="CPUExecutionProvider"
            )
            
            # Save the ONNX model
            ort_model.save_pretrained(output_path)
            
            # Save tokenizer separately
            tokenizer.save_pretrained(output_path)
            
            # Optimize if requested
            if optimize:
                self._optimize_embeddings_model(output_path)
            
            # Generate metadata
            metadata = self._generate_embeddings_metadata(model_tier, config, output_path)
            
            # Test the exported model
            self._test_embeddings_model(output_path, config)
            
            logger.info(f"✅ Embeddings model exported successfully: {output_path}")
            return str(output_path), metadata
            
        except Exception as e:
            logger.error(f"❌ Failed to export embeddings model: {str(e)}")
            raise
    
    def _optimize_embeddings_model(self, model_path: Path):
        """Optimize the ONNX embeddings model for browser deployment"""
        logger.info("⚡ Optimizing embeddings model...")
        
        try:
            # Load the ONNX model for optimization
            optimizer = ORTOptimizer.from_pretrained(model_path)
            
            # Configure optimization for browser deployment
            optimization_config = OptimizationConfig(
                optimization_level=99,  # Maximum optimization
                optimize_for_gpu=False,  # CPU/WebAssembly only
                fp16=False,  # Keep fp32 for better browser compatibility
                use_external_data_format=False,  # Keep everything in single file
                enable_transformers_specific_optimizations=True
            )
            
            # Apply optimizations
            optimized_path = model_path / "optimized"
            optimizer.optimize(
                save_dir=optimized_path,
                optimization_config=optimization_config
            )
            
            # Replace original with optimized version
            if optimized_path.exists():
                for file in optimized_path.glob("*"):
                    if file.name.endswith('.onnx'):
                        # Replace the main model file
                        target = model_path / "model.onnx"
                        if target.exists():
                            target.unlink()
                        file.rename(target)
                    else:
                        # Keep other files as-is
                        target = model_path / file.name
                        if target.exists():
                            target.unlink()
                        file.rename(target)
                
                # Clean up optimized directory
                if optimized_path.exists():
                    optimized_path.rmdir()
                    
        except Exception as e:
            logger.warning(f"⚠️ Embeddings optimization failed: {str(e)}")
    
    def _generate_embeddings_metadata(self, model_tier: str, config: Dict, model_path: Path) -> Dict:
        """Generate metadata for the exported embeddings model"""
        
        # Calculate actual model size
        model_size_mb = sum(f.stat().st_size for f in model_path.glob("**/*") if f.is_file()) / (1024 * 1024)
        
        metadata = {
            "name": f"m1k3-embeddings-{model_tier}",
            "tier": model_tier,
            "source_model": config["model_name"],
            "description": config["description"],
            "size_mb": round(model_size_mb, 1),
            "embedding_dim": config["embedding_dim"],
            "max_seq_length": config["max_seq_length"],
            "format": "onnx",
            "supports_webassembly": True,
            "export_timestamp": int(time.time()),
            "files": {
                "model": "model.onnx",
                "tokenizer": "tokenizer.json",
                "config": "config.json"
            }
        }
        
        # Save metadata
        with open(model_path / "embeddings-info.json", "w") as f:
            json.dump(metadata, f, indent=2)
            
        logger.info(f"📊 Embeddings metadata: {model_tier} - {metadata['size_mb']}MB")
        return metadata
    
    def _test_embeddings_model(self, model_path: Path, config: Dict):
        """Test the exported embeddings model"""
        logger.info("🧪 Testing exported embeddings model...")
        
        try:
            # Load the exported ONNX model
            ort_model = ORTModelForFeatureExtraction.from_pretrained(
                model_path,
                provider="CPUExecutionProvider"
            )
            
            # Test with sample text
            test_sentences = [
                "Solar panels are renewable energy devices.",
                "Regular exercise improves cardiovascular health.",
                "The water cycle involves evaporation and precipitation."
            ]
            
            # Encode test sentences
            embeddings = ort_model.encode(test_sentences)
            
            # Verify output shape
            expected_dim = config["embedding_dim"]
            if embeddings.shape[1] != expected_dim:
                raise ValueError(f"Expected embedding dim {expected_dim}, got {embeddings.shape[1]}")
            
            logger.info(f"✅ Embeddings test passed: {embeddings.shape}")
            
        except Exception as e:
            logger.error(f"❌ Embeddings test failed: {str(e)}")
            raise
    
    def export_all_embeddings(self) -> Dict[str, Dict]:
        """Export all embeddings model tiers"""
        logger.info("🚀 Starting batch export of embeddings models...")
        
        results = {}
        for tier in self.embeddings_models.keys():
            try:
                model_path, metadata = self.export_embeddings_model(tier, optimize=True)
                results[tier] = {
                    "status": "success",
                    "path": model_path,
                    "metadata": metadata
                }
                logger.info(f"✅ {tier} embeddings model exported successfully")
                
            except Exception as e:
                logger.error(f"❌ Failed to export {tier} embeddings model: {str(e)}")
                results[tier] = {
                    "status": "failed",
                    "error": str(e)
                }
        
        return results

def main():
    """Main function for command line usage"""
    import argparse
    import time
    
    parser = argparse.ArgumentParser(description="Export embeddings models for M1K3 PWA RAG")
    parser.add_argument("--tier", choices=["tiny", "small", "all"], default="tiny",
                       help="Embeddings model tier to export")
    parser.add_argument("--output", default="../frontend/models",
                       help="Output directory for exported models")
    parser.add_argument("--no-optimize", action="store_true",
                       help="Skip ONNX optimization")
    
    args = parser.parse_args()
    
    exporter = EmbeddingsExporter(args.output)
    
    if args.tier == "all":
        results = exporter.export_all_embeddings()
        
        # Print summary
        print("\n" + "="*60)
        print("📊 EMBEDDINGS EXPORT SUMMARY")
        print("="*60)
        
        for tier, result in results.items():
            if result["status"] == "success":
                metadata = result["metadata"]
                print(f"✅ {tier.upper()}: {metadata['size_mb']}MB - {metadata['description']}")
            else:
                print(f"❌ {tier.upper()}: Failed - {result['error']}")
    else:
        try:
            model_path, metadata = exporter.export_embeddings_model(
                args.tier, 
                optimize=not args.no_optimize
            )
            print(f"\n✅ Successfully exported {args.tier} embeddings model to: {model_path}")
            print(f"📊 Size: {metadata['size_mb']}MB")
            print(f"🔍 Embedding dimension: {metadata['embedding_dim']}")
            
        except Exception as e:
            print(f"\n❌ Export failed: {str(e)}")
            return 1
    
    return 0

if __name__ == "__main__":
    exit(main())