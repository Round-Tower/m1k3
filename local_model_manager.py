#!/usr/bin/env python3
"""
M1K3 Local Model Manager
Manages cached HuggingFace models and prevents unnecessary downloads
"""

import os
import psutil
import platform
from pathlib import Path
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
import subprocess
import json

# Enable HuggingFace tokenizers parallelism for better performance
os.environ['TOKENIZERS_PARALLELISM'] = 'true'

@dataclass
class ModelSpec:
    """Model specification with performance characteristics"""
    name: str
    path: str
    size_mb: float
    ram_required_mb: int
    description: str
    speed_estimate: str
    model_type: str  # "hf_transformers", "gguf", "local"
    
@dataclass 
class DeviceCapability:
    """Device capability analysis"""
    total_ram_gb: float
    available_ram_gb: float
    cpu_model: str
    cpu_cores: int
    platform: str
    gpu_info: str
    recommended_models: List[str]

class LocalModelManager:
    """Enhanced model manager that prioritizes local cached models"""
    
    def __init__(self):
        self.models_dir = Path("models")
        self.models_dir.mkdir(exist_ok=True)
        self.hf_cache_dir = Path.home() / '.cache' / 'huggingface' / 'hub'
        
        # Available local models (no downloads needed)
        self.available_models = {}
        self._discover_local_models()
        
    def _discover_local_models(self):
        """Discover all available local models"""
        self.available_models = {}
        
        # 1. Check HuggingFace cache for complete models
        hf_models = self._discover_hf_cached_models()
        self.available_models.update(hf_models)
        
        # 2. Check local GGUF models
        gguf_models = self._discover_gguf_models()
        self.available_models.update(gguf_models)
        
        # 3. Check symlinked models in models directory
        symlink_models = self._discover_symlinked_models()
        self.available_models.update(symlink_models)
        
    def _discover_hf_cached_models(self) -> Dict[str, ModelSpec]:
        """Discover cached HuggingFace models"""
        models = {}
        
        if not self.hf_cache_dir.exists():
            return models
        
        for model_folder in self.hf_cache_dir.glob("models--*"):
            model_name = model_folder.name.replace("models--", "").replace("--", "/")
            
            # Check if model is complete
            has_config = any(model_folder.rglob("config.json"))
            has_model = any(model_folder.rglob("*.safetensors")) or any(model_folder.rglob("pytorch_model.bin"))
            has_tokenizer = any(model_folder.rglob("tokenizer.json"))
            
            if has_config and has_model and has_tokenizer:
                # Get model size
                total_size = sum(f.stat().st_size for f in model_folder.rglob('*') if f.is_file())
                size_mb = total_size / (1024 * 1024)
                
                # Estimate RAM requirements (model size + overhead)
                ram_required = int(size_mb * 1.5)  # 50% overhead for inference
                
                # Determine model characteristics
                description, speed_estimate = self._analyze_model_characteristics(model_name, size_mb)
                
                models[model_name] = ModelSpec(
                    name=model_name,
                    path=str(model_folder),
                    size_mb=round(size_mb, 1),
                    ram_required_mb=ram_required,
                    description=description,
                    speed_estimate=speed_estimate,
                    model_type="hf_transformers"
                )
        
        return models
    
    def _discover_gguf_models(self) -> Dict[str, ModelSpec]:
        """Discover local GGUF models"""
        models = {}
        
        for gguf_file in self.models_dir.glob("*.gguf"):
            if gguf_file.stat().st_size > 1000:  # Not a dummy file
                size_mb = gguf_file.stat().st_size / (1024 * 1024)
                
                # Estimate RAM requirements for GGUF
                ram_required = int(size_mb * 1.2)  # 20% overhead for GGUF
                
                models[gguf_file.stem] = ModelSpec(
                    name=gguf_file.stem,
                    path=str(gguf_file),
                    size_mb=round(size_mb, 1),
                    ram_required_mb=ram_required,
                    description=f"GGUF quantized model ({gguf_file.name})",
                    speed_estimate="~10-25 tokens/sec",
                    model_type="gguf"
                )
        
        return models
    
    def _discover_symlinked_models(self) -> Dict[str, ModelSpec]:
        """Discover symlinked models in models directory"""
        models = {}
        
        for item in self.models_dir.iterdir():
            if item.is_symlink() and item.is_dir():
                # This is a symlinked model directory
                target = item.resolve()
                if target.exists():
                    # Get original model name from symlink name
                    original_name = item.name.replace("_", "/").replace("google/", "google/").replace("microsoft/", "microsoft/")
                    
                    # Check if it's in our HF cache models
                    if original_name in self.available_models:
                        # Already discovered, just note it's symlinked
                        self.available_models[original_name].description += " (symlinked)"
        
        return models
    
    def _analyze_model_characteristics(self, model_name: str, size_mb: float) -> Tuple[str, str]:
        """Analyze model characteristics based on name and size"""
        name_lower = model_name.lower()
        
        if "gemma" in name_lower:
            if "2b" in name_lower:
                return "Google Gemma 2B - Enhanced reasoning capabilities", "~5-15 tokens/sec"
            else:
                return "Google Gemma - Instruction-tuned model", "~8-20 tokens/sec"
        
        elif "phi-3" in name_lower or "phi3" in name_lower:
            return "Microsoft Phi-3 - Excellent reasoning in compact size", "~10-25 tokens/sec"
        
        elif "tinyllama" in name_lower:
            return "TinyLlama - Fast, reliable chat model", "~15-30 tokens/sec"
        
        elif "qwen3-0.6b" in name_lower:
            return "Qwen3-0.6B - Latest reasoning model with thinking mode", "~15-35 tokens/sec"
        elif "qwen2.5" in name_lower:
            return "Qwen2.5 - Enhanced instruction model", "~12-28 tokens/sec"
        elif "qwen" in name_lower:
            return "Qwen - High-quality instruction model", "~10-25 tokens/sec"
        
        elif "dialogpt" in name_lower:
            return "DialoGPT - Conversational AI model", "~20-40 tokens/sec"
        
        elif "distilgpt2" in name_lower:
            return "DistilGPT2 - Lightweight language model", "~25-50 tokens/sec"
        
        else:
            # Estimate based on size
            if size_mb < 500:
                return "Small language model", "~20-40 tokens/sec"
            elif size_mb < 2000:
                return "Medium language model", "~10-25 tokens/sec"
            else:
                return "Large language model", "~5-15 tokens/sec"
    
    def analyze_device(self) -> DeviceCapability:
        """Analyze device capabilities for model recommendations"""
        print("🔍 Analyzing device capabilities...")
        
        # Memory analysis
        memory = psutil.virtual_memory()
        total_ram_gb = memory.total / (1024**3)
        available_ram_gb = memory.available / (1024**3)
        
        # CPU analysis
        cpu_count = psutil.cpu_count(logical=True)
        cpu_model = "Unknown"
        
        # Try to get CPU model on different platforms
        try:
            if platform.system() == "Darwin":  # macOS
                result = subprocess.run(['sysctl', '-n', 'machdep.cpu.brand_string'], 
                                      capture_output=True, text=True)
                if result.returncode == 0:
                    cpu_model = result.stdout.strip()
            elif platform.system() == "Linux":
                with open('/proc/cpuinfo', 'r') as f:
                    for line in f:
                        if line.startswith('model name'):
                            cpu_model = line.split(':')[1].strip()
                            break
        except:
            pass
            
        # GPU detection (basic)
        gpu_info = "Unknown"
        try:
            if platform.system() == "Darwin":
                # Check for Apple Silicon GPU
                if "Apple" in cpu_model:
                    gpu_info = "Apple GPU (Integrated)"
        except:
            pass
            
        # Recommend models based on available resources
        recommended = []
        available_models = list(self.available_models.keys())
        
        # Prioritize by RAM requirements and model quality - avoid Gemma due to tokenizer issues
        model_priorities = []
        for name, spec in self.available_models.items():
            ram_req_gb = spec.ram_required_mb / 1024
            if ram_req_gb <= available_ram_gb:
                # Score based on quality and capabilities (prioritize most powerful models)
                if "qwen3-0.6b" in name.lower() or "qwen/qwen3-0.6b" in name.lower():
                    priority = 99   # Highest priority - latest reasoning model, compact and fast
                elif "gemma-2-2b-it" in name.lower():
                    priority = 100  # Very high priority - powerful but larger model
                elif "gemma" in name.lower():
                    priority = 95   # High priority - enhanced capabilities
                elif "qwen2.5-1.5b-instruct" in name.lower():
                    priority = 98   # Excellent reasoning model - small and fast
                elif "tinyllama" in name.lower():
                    priority = 90   # Reliable fallback - good compatibility
                elif "dialogpt" in name.lower():
                    priority = 85   # Good conversational model
                elif "phi-3" in name.lower():
                    priority = 80   # Good quality when compatible
                elif "qwen" in name.lower():
                    priority = 85   # Generally good Qwen models
                else:
                    priority = 50   # Default priority
                
                model_priorities.append((priority, name))
        
        # Sort by priority and take top recommendations
        model_priorities.sort(reverse=True)
        recommended = [name for _, name in model_priorities[:3]]
        
        return DeviceCapability(
            total_ram_gb=total_ram_gb,
            available_ram_gb=available_ram_gb,
            cpu_model=cpu_model,
            cpu_cores=cpu_count,
            platform=platform.system(),
            gpu_info=gpu_info,
            recommended_models=recommended
        )
    
    def display_device_analysis(self, device: DeviceCapability):
        """Display device analysis and model recommendations"""
        print("=" * 80)
        print("🖥️  DEVICE ANALYSIS & LOCAL MODEL INVENTORY")
        print("=" * 80)
        print(f"💻 System: {device.platform}")
        print(f"🧠 CPU: {device.cpu_model}")
        print(f"⚡ Cores: {device.cpu_cores}")
        print(f"🎮 GPU: {device.gpu_info}")
        print(f"💾 Total RAM: {device.total_ram_gb:.1f} GB")
        print(f"🆓 Available RAM: {device.available_ram_gb:.1f} GB")
        
        print(f"\n📦 AVAILABLE LOCAL MODELS ({len(self.available_models)}):")
        
        if not self.available_models:
            print("  ❌ No local models found")
            print("  💡 Run 'python package_models.py' to create symlinks")
            return
        
        # Group by model type
        hf_models = [(name, spec) for name, spec in self.available_models.items() if spec.model_type == "hf_transformers"]
        gguf_models = [(name, spec) for name, spec in self.available_models.items() if spec.model_type == "gguf"]
        
        if hf_models:
            print("\n  🤗 HuggingFace Transformers Models:")
            for name, spec in sorted(hf_models, key=lambda x: x[1].size_mb):
                ram_req_gb = spec.ram_required_mb / 1024
                status = "✅ RECOMMENDED" if name in device.recommended_models else "✅ Available"
                if ram_req_gb > device.available_ram_gb:
                    status = "⚠️  High RAM usage"
                
                print(f"    {status}")
                print(f"      📦 {name}")
                print(f"      💾 Size: {spec.size_mb:.1f}MB | RAM: {ram_req_gb:.1f}GB")
                print(f"      📝 {spec.description}")
                print(f"      ⚡ Speed: {spec.speed_estimate}")
                print()
        
        if gguf_models:
            print("  🔧 GGUF Quantized Models:")
            for name, spec in sorted(gguf_models, key=lambda x: x[1].size_mb):
                ram_req_gb = spec.ram_required_mb / 1024
                status = "✅ Available"
                if ram_req_gb > device.available_ram_gb:
                    status = "⚠️  High RAM usage"
                
                print(f"    {status} {name}: {spec.size_mb:.1f}MB | RAM: {ram_req_gb:.1f}GB")
                print(f"      📝 {spec.description}")
        
        if device.recommended_models:
            print(f"\n🎯 TOP RECOMMENDATIONS:")
            for i, model_name in enumerate(device.recommended_models[:3], 1):
                if model_name in self.available_models:
                    spec = self.available_models[model_name]
                    print(f"  {i}. {model_name} ({spec.size_mb:.1f}MB)")
                    print(f"     {spec.description}")
        
        print(f"\n💡 ALL MODELS ARE LOCAL - NO DOWNLOADS NEEDED!")
    
    def get_best_model(self) -> Optional[str]:
        """Get the best available local model"""
        device = self.analyze_device()
        
        if device.recommended_models:
            return device.recommended_models[0]
        
        # Fallback to smallest model
        if self.available_models:
            smallest = min(self.available_models.items(), key=lambda x: x[1].size_mb)
            return smallest[0]
        
        return None
    
    def get_model_path(self, model_name: str) -> Optional[str]:
        """Get the path to a specific model"""
        if model_name in self.available_models:
            return self.available_models[model_name].path
        return None
    
    def create_model_shortcuts(self) -> Dict[str, bool]:
        """Create shortcuts to access models easily"""
        results = {}
        
        for model_name, spec in self.available_models.items():
            if spec.model_type == "hf_transformers":
                # Create symlink in models directory
                safe_name = model_name.replace("/", "_").replace("-", "_")
                symlink_path = self.models_dir / safe_name
                
                try:
                    if symlink_path.exists():
                        symlink_path.unlink()
                    
                    symlink_path.symlink_to(spec.path)
                    results[model_name] = True
                    print(f"🔗 Created shortcut: {safe_name}")
                    
                except Exception as e:
                    results[model_name] = False
                    print(f"❌ Failed to create shortcut for {model_name}: {e}")
        
        return results
    
    def export_model_config(self) -> str:
        """Export model configuration for AI inference"""
        config = {
            "available_models": [],
            "recommended_order": [],
            "device_info": {}
        }
        
        device = self.analyze_device()
        config["device_info"] = {
            "ram_gb": device.available_ram_gb,
            "cpu_cores": device.cpu_cores,
            "platform": device.platform
        }
        
        for name, spec in self.available_models.items():
            config["available_models"].append({
                "name": name,
                "path": spec.path,
                "type": spec.model_type,
                "size_mb": spec.size_mb,
                "ram_required_mb": spec.ram_required_mb
            })
        
        config["recommended_order"] = device.recommended_models
        
        config_path = self.models_dir / "model_config.json"
        with open(config_path, 'w') as f:
            json.dump(config, f, indent=2)
        
        print(f"📄 Model configuration exported to: {config_path}")
        return str(config_path)

def main():
    """Main interface for local model management"""
    manager = LocalModelManager()
    
    print("🚀 M1K3 Local Model Manager")
    print("=" * 40)
    
    # Analyze device and show available models
    device = manager.analyze_device()
    manager.display_device_analysis(device)
    
    # Show recommended actions
    print(f"\n🎯 QUICK ACTIONS:")
    print("1. Create model shortcuts: manager.create_model_shortcuts()")
    print("2. Get best model: manager.get_best_model()")
    print("3. Export config: manager.export_model_config()")
    
    if not manager.available_models:
        print(f"\n💡 TO GET MODELS:")
        print("1. Run: python package_models.py")
        print("2. Or download with: huggingface-cli download model_name")
    
    return manager

if __name__ == "__main__":
    main()