#!/usr/bin/env python3
"""
M1K3 PWA Model Exporter
Exports and optimizes AI models for WebAssembly deployment
"""

import os
import json
import time
import shutil
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import logging

import torch
import onnx
from transformers import AutoTokenizer, AutoModelForCausalLM
from optimum.onnxruntime import ORTModelForCausalLM
from optimum.onnxruntime.configuration import OptimizationConfig
from optimum.onnxruntime import ORTOptimizer

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class ModelExporter:
    """Handles model export and optimization for web deployment"""
    
    def __init__(self, output_dir: str = "../frontend/models"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # Define model tiers and configurations
        # Using already cached models from main M1K3 system
        self.model_configs = {
            "tiny": {
                "model_name": "distilgpt2",
                "max_memory_gb": 2,
                "description": "Ultra-light model optimized for browser deployment with RAG",
                "size_mb": 339,  # Actual cached size
                "quantization": "int8"  # Will reduce to ~85MB
            },
            "small": {
                "model_name": "microsoft/DialoGPT-small", 
                "max_memory_gb": 4,
                "description": "Conversational model for mid-range browser deployment",
                "size_mb": 336,  # Actual cached size
                "quantization": "int8"
            },
            "medium": {
                "model_name": "TinyLlama/TinyLlama-1.1B-Chat-v1.0",
                "max_memory_gb": 6,
                "description": "Advanced model for high-end devices (desktop only)", 
                "size_mb": 4200,  # Too large for browsers but available
                "quantization": "fp16"
            }
        }
        
    def check_device_capability(self) -> Dict[str, any]:
        """Check system capabilities for model export"""
        import psutil
        
        capabilities = {
            "total_memory_gb": psutil.virtual_memory().total / (1024**3),
            "available_memory_gb": psutil.virtual_memory().available / (1024**3),
            "cpu_cores": psutil.cpu_count(),
            "has_cuda": torch.cuda.is_available(),
            "has_mps": torch.backends.mps.is_available() if hasattr(torch.backends, 'mps') else False
        }
        
        if capabilities["has_cuda"]:
            capabilities["gpu_memory_gb"] = torch.cuda.get_device_properties(0).total_memory / (1024**3)
        
        logger.info(f"System capabilities: {capabilities}")
        return capabilities
    
    def export_model_to_onnx(self, model_tier: str, optimize: bool = True) -> Tuple[str, Dict]:
        """Export a specific model tier to ONNX format"""
        if model_tier not in self.model_configs:
            raise ValueError(f"Unknown model tier: {model_tier}")
        
        config = self.model_configs[model_tier]
        model_name = config["model_name"]
        
        logger.info(f"🚀 Exporting {model_tier} model: {model_name}")
        start_time = time.time()
        
        try:
            # Load tokenizer from HuggingFace (will use cache automatically)
            logger.info("📝 Loading tokenizer...")
            tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)
            if tokenizer.pad_token is None:
                tokenizer.pad_token = tokenizer.eos_token
                
            # Load model from HuggingFace (will use cache automatically)
            logger.info("🧠 Loading model...")
            model = AutoModelForCausalLM.from_pretrained(
                model_name,
                torch_dtype=torch.float16 if config["quantization"] == "fp16" else torch.float32,
                trust_remote_code=True,
                device_map="auto" if torch.cuda.is_available() else None
            )
            
            # Export to ONNX
            output_path = self.output_dir / f"m1k3-{model_tier}"
            logger.info(f"📦 Converting to ONNX format: {output_path}")
            
            # Create ONNX model using Optimum
            ort_model = ORTModelForCausalLM.from_pretrained(
                model_name,
                export=True,
                use_cache=False,  # Disable KV cache for web deployment
                provider="CPUExecutionProvider"  # Ensure CPU compatibility
            )
            
            # Save ONNX model
            ort_model.save_pretrained(output_path)
            tokenizer.save_pretrained(output_path)
            
            # Optimize ONNX model if requested
            if optimize:
                logger.info("⚡ Optimizing ONNX model...")
                self._optimize_onnx_model(output_path)
            
            # Generate model metadata
            model_info = self._generate_model_metadata(model_tier, config, output_path)
            
            export_time = time.time() - start_time
            logger.info(f"✅ Export completed in {export_time:.1f}s")
            
            return str(output_path), model_info
            
        except Exception as e:
            logger.error(f"❌ Export failed for {model_tier}: {str(e)}")
            raise
    
    def _optimize_onnx_model(self, model_path: Path):
        """Optimize ONNX model for web deployment"""
        try:
            # Configure optimization
            optimization_config = OptimizationConfig(
                optimization_level=99,  # Maximum optimization
                optimize_for_gpu=False,  # Web deployment uses CPU/WebGPU
                fp16=False  # Keep FP32 for compatibility
            )
            
            # Initialize optimizer
            optimizer = ORTOptimizer.from_pretrained(model_path)
            
            # Optimize and save
            optimized_path = model_path / "optimized"
            optimizer.optimize(
                save_dir=optimized_path,
                optimization_config=optimization_config
            )
            
            # Replace original with optimized version
            if optimized_path.exists():
                for file in optimized_path.glob("*"):
                    shutil.move(str(file), str(model_path / file.name))
                optimized_path.rmdir()
                
        except Exception as e:
            logger.warning(f"⚠️ Optimization failed: {str(e)}")
    
    def _generate_model_metadata(self, model_tier: str, config: Dict, model_path: Path) -> Dict:
        """Generate metadata for the exported model"""
        
        # Calculate actual model size
        model_size_mb = sum(f.stat().st_size for f in model_path.glob("**/*") if f.is_file()) / (1024 * 1024)
        
        metadata = {
            "name": f"m1k3-{model_tier}",
            "tier": model_tier,
            "source_model": config["model_name"],
            "description": config["description"],
            "size_mb": round(model_size_mb, 1),
            "min_memory_gb": config["max_memory_gb"],
            "quantization": config["quantization"],
            "format": "onnx",
            "supports_webgpu": True,
            "supports_webnn": True,
            "export_timestamp": int(time.time()),
            "files": {
                "model": "model.onnx",
                "tokenizer": "tokenizer.json",
                "config": "config.json"
            }
        }
        
        # Save metadata
        with open(model_path / "model-info.json", "w") as f:
            json.dump(metadata, f, indent=2)
            
        logger.info(f"📊 Model metadata: {model_tier} - {metadata['size_mb']}MB")
        return metadata
    
    def export_all_models(self) -> Dict[str, Dict]:
        """Export all model tiers"""
        logger.info("🚀 Starting batch export of all models...")
        
        # Check system capabilities
        capabilities = self.check_device_capability()
        
        results = {}
        for tier in self.model_configs.keys():
            try:
                model_path, metadata = self.export_model_to_onnx(tier, optimize=True)
                results[tier] = {
                    "status": "success",
                    "path": model_path,
                    "metadata": metadata
                }
                logger.info(f"✅ {tier} model exported successfully")
                
            except Exception as e:
                logger.error(f"❌ Failed to export {tier} model: {str(e)}")
                results[tier] = {
                    "status": "failed",
                    "error": str(e)
                }
        
        # Generate deployment manifest
        self._generate_deployment_manifest(results, capabilities)
        
        return results
    
    def _generate_deployment_manifest(self, results: Dict, capabilities: Dict):
        """Generate deployment manifest for frontend"""
        successful_models = {k: v for k, v in results.items() if v["status"] == "success"}
        
        manifest = {
            "version": "1.0.0",
            "export_timestamp": int(time.time()),
            "system_capabilities": capabilities,
            "models": {
                tier: data["metadata"] 
                for tier, data in successful_models.items()
            },
            "model_selection_rules": {
                "high_end": {
                    "min_memory_gb": 8,
                    "preferred_model": "medium",
                    "fallback": "small"
                },
                "mid_range": {
                    "min_memory_gb": 6,
                    "preferred_model": "small", 
                    "fallback": "tiny"
                },
                "low_end": {
                    "min_memory_gb": 4,
                    "preferred_model": "tiny",
                    "fallback": "tiny"
                }
            }
        }
        
        with open(self.output_dir / "deployment-manifest.json", "w") as f:
            json.dump(manifest, f, indent=2)
            
        logger.info(f"📋 Deployment manifest generated: {len(successful_models)} models available")

def main():
    """Main export function"""
    logger.info("🚀 M1K3 PWA Model Export Pipeline Starting...")
    
    exporter = ModelExporter()
    
    # Export all models
    results = exporter.export_all_models()
    
    # Print summary
    successful = sum(1 for r in results.values() if r["status"] == "success")
    failed = len(results) - successful
    
    logger.info(f"📊 Export Summary: {successful} successful, {failed} failed")
    
    if failed > 0:
        logger.warning("⚠️ Some models failed to export. Check logs above.")
        return 1
    
    logger.info("🎉 All models exported successfully!")
    return 0

if __name__ == "__main__":
    exit(main())