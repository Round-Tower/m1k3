#!/usr/bin/env python3
"""
Enhanced SmolLM Model Downloader with Device Analysis
Downloads SmolLM models and provides device-to-model fit recommendations
"""

import os
import psutil
import platform
from pathlib import Path
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
from huggingface_hub import hf_hub_download
import subprocess

@dataclass
class ModelSpec:
    """Model specification with performance characteristics"""
    name: str
    filename: str
    size_mb: int
    ram_required_mb: int
    description: str
    speed_estimate: str
    
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

class SmolLMDownloader:
    """Enhanced downloader with device analysis and model recommendations"""
    
    def __init__(self):
        self.models_dir = Path("models")
        self.models_dir.mkdir(exist_ok=True)
        
        # Switch to a standard, well-tested model for troubleshooting
        self.available_models = {
            "TinyLlama-Q4_K_M": ModelSpec(
                name="TinyLlama-1.1B-Chat-Q4_K_M", 
                filename="tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                size_mb=669,
                ram_required_mb=1000,
                description="TinyLlama Chat v1.0 - Q4_K_M quantization",
                speed_estimate="~15-20 tokens/sec"
            )
        }
        
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
            
        # Recommend models based on available RAM
        recommended = []
        if available_ram_gb >= 4:
            recommended.extend(["Q8_0", "Q5_K_M"])
        if available_ram_gb >= 2:
            recommended.append("Q4_K_M")
        recommended.append("Q3_K_S")  # Always works
        
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
        """Display device analysis and recommendations"""
        print("=" * 60)
        print("🖥️  DEVICE ANALYSIS & MODEL RECOMMENDATIONS")
        print("=" * 60)
        print(f"💻 System: {device.platform}")
        print(f"🧠 CPU: {device.cpu_model}")
        print(f"⚡ Cores: {device.cpu_cores}")
        print(f"🎮 GPU: {device.gpu_info}")
        print(f"💾 Total RAM: {device.total_ram_gb:.1f} GB")
        print(f"🆓 Available RAM: {device.available_ram_gb:.1f} GB")
        
        print(f"\n📊 MODEL COMPATIBILITY:")
        for model_key in self.available_models.keys():
            model = self.available_models[model_key]
            ram_req_gb = model.ram_required_mb / 1024
            
            if ram_req_gb <= device.available_ram_gb:
                status = "✅ RECOMMENDED" if model_key in device.recommended_models[:2] else "✅ Compatible"
                print(f"  {status} {model.name}: {model.size_mb}MB ({model.description})")
                print(f"    📈 Performance: {model.speed_estimate}")
            else:
                print(f"  ❌ {model.name}: Requires {ram_req_gb:.1f}GB RAM")
                
        print(f"\n🎯 BEST CHOICE: {list(self.available_models.keys())[0]}")
        
    def check_existing_models(self) -> List[Tuple[str, Path]]:
        """Check for existing model files"""
        existing = []
        
        # Check both naming patterns (dot and dash)
        patterns = [
            "SmolLM-135M.Q*.gguf",  # Existing pattern with dots
            "SmolLM-135M-Q*.gguf"   # New pattern with dashes
        ]
        
        for pattern in patterns:
            for model_file in self.models_dir.glob(pattern):
                if model_file.stat().st_size > 1000:  # Not a dummy file
                    # Try to identify which quantization this is
                    name = model_file.name
                    for q_type, spec in self.available_models.items():
                        if q_type.replace("_", "") in name.replace(".", "").replace("-", ""):
                            existing.append((q_type, model_file))
                            break
                    else:
                        existing.append(("Unknown", model_file))
                        
        return existing
    
    def download_model(self, quantization: str = "TinyLlama-Q4_K_M", force: bool = False) -> Optional[str]:
        """Download specified model quantization"""
        if quantization not in self.available_models:
            print(f"❌ Unknown quantization: {quantization}")
            return None
            
        model_spec = self.available_models[quantization]
        model_path = self.models_dir / model_spec.filename
        
        # Check if model already exists
        if not force and model_path.exists() and model_path.stat().st_size > 1000:
            print(f"✅ Model already exists: {model_path}")
            print(f"📦 Size: {model_path.stat().st_size / 1024 / 1024:.1f} MB")
            return str(model_path)
            
        print(f"⬇️  Downloading {model_spec.name} ({model_spec.size_mb}MB)...")
        print(f"📝 Description: {model_spec.description}")
        
        try:
            # Try downloading from HuggingFace using a known-good repository
            downloaded_path = hf_hub_download(
                repo_id="TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
                filename=model_spec.filename,
                local_dir=str(self.models_dir),
                local_dir_use_symlinks=False
            )
            
            final_path = self.models_dir / model_spec.filename
            if Path(downloaded_path) != final_path:
                Path(downloaded_path).rename(final_path)
                
            print(f"✅ Downloaded successfully: {final_path}")
            print(f"📦 Size: {final_path.stat().st_size / 1024 / 1024:.1f} MB")
            return str(final_path)
            
        except Exception as e:
            print(f"❌ Download failed: {e}")
            
            # Try alternative quantization
            if quantization == "Q5_K_M":
                print("🔄 Trying Q4_K_M as fallback...")
                return self.download_model("Q4_K_M")
            elif quantization == "Q4_K_M":
                print("🔄 Trying Q3_K_S as fallback...")
                return self.download_model("Q3_K_S")
            else:
                print("❌ All download attempts failed")
                return None

def download_model(quantization: str = "TinyLlama-Q4_K_M", force: bool = False) -> Optional[str]:
    """Main download function with device analysis"""
    downloader = SmolLMDownloader()
    
    # Analyze device capabilities
    device = downloader.analyze_device()
    downloader.display_device_analysis(device)
    
    # Check existing models
    existing = downloader.check_existing_models()
    if existing:
        print(f"\n📁 EXISTING MODELS:")
        for q_type, path in existing:
            size_mb = path.stat().st_size / 1024 / 1024
            print(f"  {q_type}: {path.name} ({size_mb:.1f}MB)")
    
    # Use the only available model
    quantization = list(downloader.available_models.keys())[0]
    
    print(f"\n⬇️  TARGET MODEL: {quantization}")
    return downloader.download_model(quantization, force=force)

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Download a compatible GGUF model.")
    parser.add_argument("--quantization", default="TinyLlama-Q4_K_M", help="Model quantization to download.")
    parser.add_argument("--force", action="store_true", help="Force re-download even if the model exists.")
    args = parser.parse_args()
    
    download_model(quantization=args.quantization, force=args.force)