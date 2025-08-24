#!/usr/bin/env python3
"""
Model Tiers System
Device capability detection and intelligent model selection for M1K3
"""

import psutil
import platform
import json
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum

class DeviceTier(Enum):
    """Device capability tiers"""
    MINIMAL = "minimal"      # < 4GB RAM, basic CPU
    BALANCED = "balanced"    # 4-8GB RAM, decent CPU  
    PERFORMANCE = "performance"  # 8GB+ RAM, good CPU
    WORKSTATION = "workstation"  # 16GB+ RAM, powerful CPU

@dataclass 
class DeviceCapability:
    """Device capability analysis"""
    tier: DeviceTier
    total_ram_gb: float
    available_ram_gb: float
    cpu_cores: int
    cpu_model: str
    platform: str
    gpu_available: bool
    recommended_models: List[str]
    performance_score: float

@dataclass
class ModelTierConfig:
    """Model configuration for a specific tier"""
    tier: DeviceTier
    primary_models: List[str]
    fallback_models: List[str]
    max_context: int
    recommended_params: Dict
    description: str

class ModelTierManager:
    """Manages model selection based on device capabilities"""
    
    def __init__(self, config_path: Optional[str] = None):
        self.config_path = config_path or "smollm_config.json"
        self.models_dir = Path("models")
        self.tier_configs = self._load_tier_configs()
        
    def _load_tier_configs(self) -> Dict[DeviceTier, ModelTierConfig]:
        """Load tier configurations"""
        configs = {
            DeviceTier.MINIMAL: ModelTierConfig(
                tier=DeviceTier.MINIMAL,
                primary_models=["SmolLM-135M.Q4_K_M"],
                fallback_models=["simple_ai"],
                max_context=1024,
                recommended_params={
                    "temperature": 0.6,
                    "max_tokens": 256,
                    "top_p": 0.9
                },
                description="Ultra-light configuration for low-resource devices"
            ),
            DeviceTier.BALANCED: ModelTierConfig(
                tier=DeviceTier.BALANCED,
                primary_models=["smollm2:135m", "SmolLM-135M.Q4_K_M"],
                fallback_models=["tinyllama-1.1b-chat-v1.0.Q4_K_M", "microsoft/DialoGPT-small"],
                max_context=2048,
                recommended_params={
                    "temperature": 0.7,
                    "max_tokens": 512,
                    "top_p": 0.9
                },
                description="Balanced performance with SmolLM2 + fallbacks"
            ),
            DeviceTier.PERFORMANCE: ModelTierConfig(
                tier=DeviceTier.PERFORMANCE,
                primary_models=["llama3.2:1b", "smollm2:135m", "TinyLlama/TinyLlama-1.1B-Chat-v1.0"],
                fallback_models=["SmolLM-135M.Q4_K_M"],
                max_context=4096,
                recommended_params={
                    "temperature": 0.7,
                    "max_tokens": 1024,
                    "top_p": 0.9
                },
                description="High-performance configuration with larger models"
            ),
            DeviceTier.WORKSTATION: ModelTierConfig(
                tier=DeviceTier.WORKSTATION,
                primary_models=["llama3.2:latest", "Qwen/Qwen2.5-1.5B-Instruct", "microsoft/Phi-3-mini-4k-instruct"],
                fallback_models=["llama3.2:1b", "smollm2:135m"],
                max_context=8192,
                recommended_params={
                    "temperature": 0.7,
                    "max_tokens": 2048,
                    "top_p": 0.9
                },
                description="Maximum performance with large context models"
            )
        }
        
        # Try to load custom configurations from file
        if Path(self.config_path).exists():
            try:
                with open(self.config_path) as f:
                    data = json.load(f)
                    device_reqs = data.get("device_requirements", {})
                    
                    # Update configs from file
                    for tier_name, req_data in device_reqs.items():
                        tier = DeviceTier(tier_name) if tier_name in [t.value for t in DeviceTier] else None
                        if tier and tier in configs:
                            if "recommended_variant" in req_data:
                                # Map variant to actual model
                                variant = req_data["recommended_variant"]
                                variants = data.get("model_variants", {})
                                if variant in variants:
                                    model_name = variants[variant]["name"]
                                    configs[tier].primary_models.insert(0, model_name)
            except Exception as e:
                print(f"⚠️ Error loading tier config: {e}")
        
        return configs
    
    def analyze_device(self) -> DeviceCapability:
        """Analyze current device capabilities"""
        # Memory analysis
        memory = psutil.virtual_memory()
        total_ram_gb = memory.total / (1024**3)
        available_ram_gb = memory.available / (1024**3)
        
        # CPU analysis
        cpu_cores = psutil.cpu_count(logical=True)
        cpu_model = self._get_cpu_model()
        
        # GPU detection
        gpu_available = self._detect_gpu()
        
        # Performance scoring (0-100)
        performance_score = self._calculate_performance_score(
            total_ram_gb, cpu_cores, gpu_available
        )
        
        # Determine tier
        tier = self._determine_tier(total_ram_gb, cpu_cores, performance_score)
        
        # Get recommended models for this tier
        recommended_models = self._get_recommended_models_for_tier(tier)
        
        return DeviceCapability(
            tier=tier,
            total_ram_gb=total_ram_gb,
            available_ram_gb=available_ram_gb,
            cpu_cores=cpu_cores,
            cpu_model=cpu_model,
            platform=platform.system(),
            gpu_available=gpu_available,
            recommended_models=recommended_models,
            performance_score=performance_score
        )
    
    def _get_cpu_model(self) -> str:
        """Get CPU model string"""
        try:
            if platform.system() == "Darwin":  # macOS
                import subprocess
                result = subprocess.run(['sysctl', '-n', 'machdep.cpu.brand_string'], 
                                      capture_output=True, text=True)
                if result.returncode == 0:
                    return result.stdout.strip()
            elif platform.system() == "Linux":
                with open('/proc/cpuinfo', 'r') as f:
                    for line in f:
                        if line.startswith('model name'):
                            return line.split(':')[1].strip()
        except:
            pass
        return "Unknown CPU"
    
    def _detect_gpu(self) -> bool:
        """Detect if GPU is available"""
        try:
            # Check for NVIDIA GPU
            import subprocess
            result = subprocess.run(['nvidia-smi'], capture_output=True)
            if result.returncode == 0:
                return True
        except:
            pass
        
        try:
            # Check for Apple Silicon GPU
            cpu_model = self._get_cpu_model()
            if "Apple" in cpu_model and ("M1" in cpu_model or "M2" in cpu_model or "M3" in cpu_model):
                return True
        except:
            pass
        
        return False
    
    def _calculate_performance_score(self, ram_gb: float, cpu_cores: int, gpu_available: bool) -> float:
        """Calculate device performance score 0-100"""
        # Base score from RAM (40% weight)
        ram_score = min(ram_gb * 5, 40)  # Max 40 points from RAM
        
        # CPU score (40% weight)
        cpu_score = min(cpu_cores * 3, 40)  # Max 40 points from CPU
        
        # GPU bonus (20% weight)
        gpu_score = 20 if gpu_available else 0
        
        return ram_score + cpu_score + gpu_score
    
    def _determine_tier(self, ram_gb: float, cpu_cores: int, performance_score: float) -> DeviceTier:
        """Determine device tier based on capabilities"""
        if ram_gb >= 16 and cpu_cores >= 8 and performance_score >= 80:
            return DeviceTier.WORKSTATION
        elif ram_gb >= 8 and cpu_cores >= 4 and performance_score >= 60:
            return DeviceTier.PERFORMANCE
        elif ram_gb >= 4 and cpu_cores >= 2 and performance_score >= 30:
            return DeviceTier.BALANCED
        else:
            return DeviceTier.MINIMAL
    
    def _get_recommended_models_for_tier(self, tier: DeviceTier) -> List[str]:
        """Get recommended models for a specific tier"""
        config = self.tier_configs.get(tier)
        if not config:
            return ["SmolLM-135M.Q4_K_M"]  # Minimal fallback
        
        # Check which models are actually available
        available_models = []
        
        for model_name in config.primary_models + config.fallback_models:
            if self._is_model_available(model_name):
                available_models.append(model_name)
        
        return available_models[:3]  # Return top 3 available
    
    def _is_model_available(self, model_name: str) -> bool:
        """Check if a model is available locally"""
        # Check GGUF files
        if model_name.endswith('.gguf') or '.Q4_K_M' in model_name:
            gguf_path = self.models_dir / f"{model_name}.gguf"
            if not gguf_path.exists():
                gguf_path = self.models_dir / model_name
            return gguf_path.exists()
        
        # Check Ollama models
        if ':' in model_name and not '/' in model_name:
            try:
                import subprocess
                result = subprocess.run(['ollama', 'list'], capture_output=True, text=True)
                return model_name in result.stdout
            except:
                return False
        
        # Check HuggingFace cache
        if '/' in model_name:
            cache_dir = Path.home() / '.cache' / 'huggingface' / 'hub'
            model_cache = cache_dir / f"models--{model_name.replace('/', '--')}"
            return model_cache.exists()
        
        return False
    
    def get_tier_config(self, tier: DeviceTier) -> Optional[ModelTierConfig]:
        """Get configuration for a specific tier"""
        return self.tier_configs.get(tier)
    
    def recommend_models_for_device(self) -> Tuple[DeviceCapability, List[str]]:
        """Get device analysis and model recommendations"""
        device = self.analyze_device()
        
        # Get models from tier config
        tier_config = self.get_tier_config(device.tier)
        if tier_config:
            # Filter to available models
            available_models = []
            for model in tier_config.primary_models:
                if self._is_model_available(model):
                    available_models.append(model)
            
            # Add fallbacks if needed
            if not available_models:
                for model in tier_config.fallback_models:
                    if self._is_model_available(model):
                        available_models.append(model)
            
            device.recommended_models = available_models
        
        return device, device.recommended_models
    
    def display_tier_analysis(self):
        """Display comprehensive tier analysis"""
        device, recommendations = self.recommend_models_for_device()
        
        print("=" * 70)
        print("🎯 M1K3 MODEL TIER ANALYSIS")
        print("=" * 70)
        
        print(f"💻 Device Information:")
        print(f"  Platform: {device.platform}")
        print(f"  CPU: {device.cpu_model}")
        print(f"  Cores: {device.cpu_cores}")
        print(f"  RAM: {device.total_ram_gb:.1f}GB total, {device.available_ram_gb:.1f}GB available")
        print(f"  GPU: {'✅ Available' if device.gpu_available else '❌ None detected'}")
        print(f"  Performance Score: {device.performance_score:.0f}/100")
        
        print(f"\n🎯 Recommended Tier: {device.tier.value.upper()}")
        tier_config = self.get_tier_config(device.tier)
        if tier_config:
            print(f"  Description: {tier_config.description}")
            print(f"  Max Context: {tier_config.max_context} tokens")
            print(f"  Recommended Parameters:")
            for key, value in tier_config.recommended_params.items():
                print(f"    {key}: {value}")
        
        print(f"\n📦 Available Models ({len(recommendations)}):")
        if recommendations:
            for i, model in enumerate(recommendations, 1):
                status = "🎯 PRIMARY" if i == 1 else "🔄 FALLBACK"
                backend = self._detect_model_backend(model)
                print(f"  {i}. {status} {model}")
                print(f"     Backend: {backend}")
        else:
            print("  ❌ No models available for this tier")
            print("  💡 Consider downloading SmolLM2 models")
        
        print(f"\n💡 Optimization Tips:")
        if device.tier == DeviceTier.MINIMAL:
            print("  • Use GGUF models for best performance")
            print("  • Keep context window small (< 1K tokens)")
            print("  • Use lower temperature (0.6) for consistency")
        elif device.tier == DeviceTier.BALANCED:
            print("  • Ollama models provide good balance")
            print("  • 2K context window recommended")
            print("  • Consider batch processing for efficiency")
        elif device.tier in [DeviceTier.PERFORMANCE, DeviceTier.WORKSTATION]:
            print("  • Can handle larger models and context")
            print("  • Enable GPU acceleration if available")
            print("  • Consider multiple model backends")
    
    def _detect_model_backend(self, model_name: str) -> str:
        """Detect which backend would be used for a model"""
        if '.gguf' in model_name or '.Q4_K_M' in model_name:
            return "ctransformers (GGUF)"
        elif ':' in model_name and not '/' in model_name:
            return "Ollama API"
        elif '/' in model_name:
            return "HuggingFace Transformers"
        else:
            return "Unknown"


def main():
    """Test tier analysis"""
    print("🚀 M1K3 Model Tier Analysis")
    
    manager = ModelTierManager()
    manager.display_tier_analysis()
    
    print(f"\n🔧 All Tier Configurations:")
    for tier in DeviceTier:
        config = manager.get_tier_config(tier)
        if config:
            print(f"\n{tier.value.upper()}:")
            print(f"  Primary: {', '.join(config.primary_models)}")
            print(f"  Context: {config.max_context} tokens")

if __name__ == "__main__":
    main()