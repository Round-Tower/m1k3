#!/usr/bin/env python3
"""
M1K3 Model Pre-packing Utility
Ensures all local models are properly prepared for fast startup
"""

import os
import json
import time
from pathlib import Path
from typing import Dict, List, Tuple, Optional

# Enable HuggingFace tokenizers parallelism for better performance
os.environ['TOKENIZERS_PARALLELISM'] = 'true'

try:
    from local_model_manager import LocalModelManager
    from transformers import AutoTokenizer, AutoModelForCausalLM
    import torch
    DEPS_AVAILABLE = True
except ImportError as e:
    print(f"❌ Dependencies not available: {e}")
    DEPS_AVAILABLE = False

class ModelPrePacker:
    """Pre-pack and verify models for optimal M1K3 startup"""
    
    def __init__(self):
        self.models_dir = Path("models")
        self.models_dir.mkdir(exist_ok=True)
        self.model_manager = None
        self.verification_results = {}
        
        if DEPS_AVAILABLE:
            try:
                self.model_manager = LocalModelManager()
            except Exception as e:
                print(f"⚠️  LocalModelManager failed: {e}")
    
    def verify_model_integrity(self, model_name: str, model_spec) -> Dict[str, any]:
        """Verify that a model can actually be loaded"""
        print(f"🔍 Verifying {model_name}...")
        
        result = {
            "model_name": model_name,
            "model_type": model_spec.model_type,
            "size_mb": model_spec.size_mb,
            "can_load_tokenizer": False,
            "can_load_model": False,
            "load_time": None,
            "error": None,
            "recommendation": "unknown"
        }
        
        if model_spec.model_type == "hf_transformers":
            try:
                start_time = time.time()
                
                # Test tokenizer loading
                tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=True)
                result["can_load_tokenizer"] = True
                
                # Test model loading (just structure, not full weights)
                model = AutoModelForCausalLM.from_pretrained(
                    model_name, 
                    local_files_only=True,
                    torch_dtype=torch.float32,
                    device_map="cpu",
                    low_cpu_mem_usage=True
                )
                result["can_load_model"] = True
                result["load_time"] = round(time.time() - start_time, 2)
                
                # Quick inference test
                test_input = tokenizer("Hello", return_tensors="pt")
                with torch.no_grad():
                    output = model.generate(
                        test_input["input_ids"],
                        max_new_tokens=5,
                        do_sample=False,
                        pad_token_id=tokenizer.eos_token_id
                    )
                
                result["recommendation"] = "✅ READY"
                print(f"   ✅ {model_name}: Ready ({result['load_time']}s)")
                
            except Exception as e:
                result["error"] = str(e)
                if "does not appear to have files" in str(e):
                    result["recommendation"] = "❌ INCOMPLETE DOWNLOAD"
                elif "SentencePiece" in str(e) or "tiktoken" in str(e):
                    result["recommendation"] = "❌ TOKENIZER ISSUES" 
                else:
                    result["recommendation"] = "❌ LOAD FAILED"
                print(f"   ❌ {model_name}: {result['recommendation']}")
        
        elif model_spec.model_type == "gguf":
            # GGUF files just need to exist and be non-empty
            model_path = Path(model_spec.path)
            if model_path.exists() and model_path.stat().st_size > 1024:
                result["can_load_model"] = True
                result["recommendation"] = "✅ GGUF READY"
                print(f"   ✅ {model_name}: GGUF ready")
            else:
                result["recommendation"] = "❌ FILE MISSING"
                print(f"   ❌ {model_name}: File missing or empty")
        
        return result
    
    def analyze_all_models(self) -> Dict[str, Dict]:
        """Analyze all available models and their readiness"""
        print("🔍 M1K3 Model Pre-packing Analysis")
        print("=" * 50)
        
        if not self.model_manager:
            print("❌ LocalModelManager not available")
            return {}
        
        results = {}
        available_models = self.model_manager.available_models
        
        print(f"📦 Found {len(available_models)} models to verify...")
        print()
        
        for model_name, model_spec in available_models.items():
            result = self.verify_model_integrity(model_name, model_spec)
            results[model_name] = result
        
        return results
    
    def create_startup_config(self, results: Dict[str, Dict]) -> str:
        """Create optimized startup configuration"""
        
        # Find the best working HF model
        working_hf_models = []
        working_gguf_models = []
        
        for model_name, result in results.items():
            if result["recommendation"] == "✅ READY":
                working_hf_models.append((model_name, result))
            elif result["recommendation"] == "✅ GGUF READY":
                working_gguf_models.append((model_name, result))
        
        # Sort by size for priority
        working_hf_models.sort(key=lambda x: x[1]["size_mb"])
        working_gguf_models.sort(key=lambda x: x[1]["size_mb"])
        
        config = {
            "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
            "total_models": len(results),
            "working_hf_models": [m[0] for m in working_hf_models],
            "working_gguf_models": [m[0] for m in working_gguf_models],
            "recommended_primary": working_hf_models[0][0] if working_hf_models else None,
            "recommended_fallback": working_hf_models[1][0] if len(working_hf_models) > 1 else None,
            "recommended_gguf": working_gguf_models[0][0] if working_gguf_models else None,
            "verification_results": results
        }
        
        config_path = self.models_dir / "startup_config.json"
        with open(config_path, 'w') as f:
            json.dump(config, f, indent=2)
        
        print(f"\\n📄 Startup configuration saved to: {config_path}")
        return str(config_path)
    
    def display_recommendations(self, results: Dict[str, Dict]):
        """Display model recommendations and status"""
        print("\\n" + "=" * 50)
        print("🎯 M1K3 MODEL RECOMMENDATIONS")
        print("=" * 50)
        
        # Working models
        working_models = [(name, res) for name, res in results.items() 
                         if res["recommendation"].startswith("✅")]
        
        if working_models:
            print("\\n🚀 READY FOR USE:")
            for name, result in sorted(working_models, key=lambda x: x[1]["size_mb"]):
                size_mb = result["size_mb"]
                load_time = result.get("load_time", "N/A")
                print(f"   ✅ {name}")
                print(f"      📦 Size: {size_mb:.1f}MB | Load time: {load_time}s")
        
        # Problematic models
        problem_models = [(name, res) for name, res in results.items() 
                         if res["recommendation"].startswith("❌")]
        
        if problem_models:
            print("\\n⚠️  NEEDS ATTENTION:")
            for name, result in problem_models:
                print(f"   ❌ {name}: {result['recommendation']}")
                if result["error"]:
                    print(f"      🔍 Error: {result['error'][:100]}...")
        
        # Recommendations
        working_hf = [r for r in working_models if r[1]["model_type"] == "hf_transformers"]
        
        if working_hf:
            best_model = min(working_hf, key=lambda x: x[1]["size_mb"])
            print(f"\\n🎯 RECOMMENDED PRIMARY MODEL:")
            print(f"   🥇 {best_model[0]} ({best_model[1]['size_mb']:.1f}MB)")
        
        print(f"\\n💡 Pre-packing analysis complete!")

def main():
    """Main pre-packing interface"""
    if not DEPS_AVAILABLE:
        print("❌ Missing dependencies. Install with:")
        print("   pip install transformers torch")
        return 1
    
    packer = ModelPrePacker()
    
    # Analyze all models
    results = packer.analyze_all_models()
    
    if not results:
        print("❌ No models found to analyze")
        return 1
    
    # Create startup config
    config_path = packer.create_startup_config(results)
    
    # Display recommendations
    packer.display_recommendations(results)
    
    return 0

if __name__ == "__main__":
    exit(main())