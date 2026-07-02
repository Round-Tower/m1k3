#!/usr/bin/env python3
"""
Device Capability Detection
Detects device capabilities and recommends optimal configurations for M1K3
"""

import platform
import psutil
import subprocess
import os
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
import logging

logger = logging.getLogger(__name__)


class DeviceTier(Enum):
    """Device performance tiers"""
    BASIC = "basic"         # 2-4GB RAM, 2-4 cores
    STANDARD = "standard"   # 4-8GB RAM, 4-6 cores  
    HIGH = "high"          # 8-16GB RAM, 6+ cores
    PREMIUM = "premium"    # 16GB+ RAM, 8+ cores


class PlatformType(Enum):
    """Platform types"""
    DESKTOP = "desktop"
    MOBILE = "mobile" 
    SERVER = "server"
    EMBEDDED = "embedded"


@dataclass
class DeviceCapabilities:
    """Comprehensive device capability assessment"""
    # Hardware specs
    cpu_count: int = 0
    cpu_frequency: float = 0.0  # GHz
    memory_total_gb: float = 0.0
    memory_available_gb: float = 0.0
    disk_free_gb: float = 0.0
    
    # Platform info
    platform_system: str = ""
    platform_version: str = ""
    architecture: str = ""
    platform_type: PlatformType = PlatformType.DESKTOP
    
    # AI capabilities
    has_gpu: bool = False
    gpu_memory_gb: float = 0.0
    supports_metal: bool = False  # Apple Silicon
    supports_cuda: bool = False
    supports_opencl: bool = False
    
    # Performance classification
    device_tier: DeviceTier = DeviceTier.BASIC
    is_mobile: bool = False
    is_low_power: bool = False
    
    # Feature recommendations
    recommended_ai_model: str = "simple"
    recommended_workers: int = 1
    enable_caching: bool = True
    enable_async_loading: bool = True
    max_memory_mb: int = 512


class DeviceDetector:
    """Detects and analyzes device capabilities"""
    
    def __init__(self):
        self.caps = DeviceCapabilities()
        self._detect_all()
    
    def _detect_all(self):
        """Run all detection methods"""
        logger.info("🔍 Detecting device capabilities")
        
        self._detect_cpu()
        self._detect_memory()
        self._detect_storage()
        self._detect_platform()
        self._detect_gpu()
        self._classify_device()
        self._generate_recommendations()
        
        logger.info(f"🎯 Device tier: {self.caps.device_tier.value}")
        logger.info(f"💾 Available memory: {self.caps.memory_available_gb:.1f}GB")
        logger.info(f"🧠 Recommended AI model: {self.caps.recommended_ai_model}")
    
    def _detect_cpu(self):
        """Detect CPU capabilities"""
        self.caps.cpu_count = psutil.cpu_count(logical=True)
        
        # Try to get CPU frequency
        try:
            cpu_freq = psutil.cpu_freq()
            if cpu_freq:
                self.caps.cpu_frequency = cpu_freq.max / 1000.0  # Convert to GHz
        except Exception:
            self.caps.cpu_frequency = 2.5  # Default assumption
        
        logger.debug(f"CPU: {self.caps.cpu_count} cores @ {self.caps.cpu_frequency:.1f}GHz")
    
    def _detect_memory(self):
        """Detect memory capabilities"""
        memory = psutil.virtual_memory()
        self.caps.memory_total_gb = memory.total / (1024 ** 3)
        self.caps.memory_available_gb = memory.available / (1024 ** 3)
        
        logger.debug(f"Memory: {self.caps.memory_available_gb:.1f}GB available of {self.caps.memory_total_gb:.1f}GB")
    
    def _detect_storage(self):
        """Detect storage capabilities"""
        try:
            disk_usage = psutil.disk_usage('/')
            self.caps.disk_free_gb = disk_usage.free / (1024 ** 3)
        except Exception:
            self.caps.disk_free_gb = 10.0  # Conservative default
        
        logger.debug(f"Storage: {self.caps.disk_free_gb:.1f}GB free")
    
    def _detect_platform(self):
        """Detect platform and architecture"""
        self.caps.platform_system = platform.system()
        self.caps.platform_version = platform.version()
        self.caps.architecture = platform.machine()
        
        # Detect platform type
        if self.caps.platform_system == "Darwin":
            if "iPhone" in self.caps.platform_version or "iPad" in self.caps.platform_version:
                self.caps.platform_type = PlatformType.MOBILE
                self.caps.is_mobile = True
            else:
                self.caps.platform_type = PlatformType.DESKTOP
        elif self.caps.platform_system == "Linux":
            # Simple heuristic for mobile vs desktop Linux
            if self.caps.memory_total_gb < 4.0 and self.caps.cpu_count <= 4:
                self.caps.platform_type = PlatformType.EMBEDDED
                self.caps.is_mobile = True
            else:
                self.caps.platform_type = PlatformType.DESKTOP
        else:
            self.caps.platform_type = PlatformType.DESKTOP
        
        # Detect low power mode (mobile, embedded, or battery constraints)
        self.caps.is_low_power = (
            self.caps.is_mobile or 
            self.caps.memory_total_gb < 4.0 or
            self.caps.cpu_count <= 2
        )
        
        logger.debug(f"Platform: {self.caps.platform_system} {self.caps.architecture}")
    
    def _detect_gpu(self):
        """Detect GPU capabilities"""
        # Check for CUDA
        try:
            import torch
            if torch.cuda.is_available():
                self.caps.has_gpu = True
                self.caps.supports_cuda = True
                self.caps.gpu_memory_gb = torch.cuda.get_device_properties(0).total_memory / (1024 ** 3)
                logger.debug(f"CUDA GPU detected: {self.caps.gpu_memory_gb:.1f}GB")
        except ImportError:
            pass
        
        # Check for Metal (Apple Silicon)
        if self.caps.platform_system == "Darwin" and "arm" in self.caps.architecture.lower():
            self.caps.supports_metal = True
            self.caps.has_gpu = True
            logger.debug("Apple Silicon Metal support detected")
        
        # Check for OpenCL (basic detection)
        try:
            result = subprocess.run(["clinfo"], capture_output=True, text=True, timeout=5)
            if result.returncode == 0 and "Platform" in result.stdout:
                self.caps.supports_opencl = True
                logger.debug("OpenCL support detected")
        except (subprocess.TimeoutExpired, FileNotFoundError):
            pass
    
    def _classify_device(self):
        """Classify device into performance tier"""
        # Score based on key metrics
        memory_score = min(4, self.caps.memory_total_gb / 4.0)
        cpu_score = min(4, self.caps.cpu_count / 4.0)
        gpu_bonus = 1 if self.caps.has_gpu else 0
        
        total_score = memory_score + cpu_score + gpu_bonus
        
        if total_score >= 7:
            self.caps.device_tier = DeviceTier.PREMIUM
        elif total_score >= 5:
            self.caps.device_tier = DeviceTier.HIGH
        elif total_score >= 3:
            self.caps.device_tier = DeviceTier.STANDARD
        else:
            self.caps.device_tier = DeviceTier.BASIC
        
        # Mobile devices get downgraded one tier for power efficiency
        if self.caps.is_mobile and self.caps.device_tier != DeviceTier.BASIC:
            tiers = list(DeviceTier)
            current_idx = tiers.index(self.caps.device_tier)
            self.caps.device_tier = tiers[max(0, current_idx - 1)]
    
    def _generate_recommendations(self):
        """Generate optimization recommendations"""
        if self.caps.device_tier == DeviceTier.PREMIUM:
            self.caps.recommended_ai_model = "large"
            self.caps.recommended_workers = min(4, self.caps.cpu_count)
            self.caps.max_memory_mb = 2048
        elif self.caps.device_tier == DeviceTier.HIGH:
            self.caps.recommended_ai_model = "medium"
            self.caps.recommended_workers = min(3, self.caps.cpu_count)
            self.caps.max_memory_mb = 1024
        elif self.caps.device_tier == DeviceTier.STANDARD:
            self.caps.recommended_ai_model = "small"
            self.caps.recommended_workers = min(2, self.caps.cpu_count)
            self.caps.max_memory_mb = 800
        else:  # BASIC
            self.caps.recommended_ai_model = "tiny"
            self.caps.recommended_workers = 1
            self.caps.max_memory_mb = 512
        
        # Mobile-specific adjustments
        if self.caps.is_mobile:
            self.caps.max_memory_mb = min(self.caps.max_memory_mb, 600)
            self.caps.recommended_workers = 1  # Preserve battery
        
        # Memory-constrained adjustments
        available_mb = self.caps.memory_available_gb * 1024
        if available_mb < self.caps.max_memory_mb:
            self.caps.max_memory_mb = int(available_mb * 0.8)  # Leave 20% buffer
    
    def get_capabilities(self) -> DeviceCapabilities:
        """Get detected capabilities"""
        return self.caps
    
    def get_optimization_config(self) -> Dict[str, any]:
        """Get optimization configuration dictionary"""
        return {
            'device_tier': self.caps.device_tier.value,
            'platform_type': self.caps.platform_type.value,
            'cpu_count': self.caps.cpu_count,
            'memory_gb': self.caps.memory_total_gb,
            'memory_available_gb': self.caps.memory_available_gb,
            'has_gpu': self.caps.has_gpu,
            'is_mobile': self.caps.is_mobile,
            'is_low_power': self.caps.is_low_power,
            'recommended_ai_model': self.caps.recommended_ai_model,
            'recommended_workers': self.caps.recommended_workers,
            'max_memory_mb': self.caps.max_memory_mb,
            'enable_caching': self.caps.enable_caching,
            'enable_async_loading': self.caps.enable_async_loading,
            'supports_metal': self.caps.supports_metal,
            'supports_cuda': self.caps.supports_cuda
        }
    
    def get_feature_recommendations(self) -> Dict[str, bool]:
        """Get feature enable/disable recommendations"""
        return {
            'async_model_loading': True,  # Always beneficial
            'model_caching': self.caps.memory_total_gb >= 4.0,
            'background_loading': not self.caps.is_mobile,  # Preserve mobile battery
            'gpu_acceleration': self.caps.has_gpu,
            'multi_threading': self.caps.cpu_count >= 4,
            'streaming_tts': self.caps.device_tier in [DeviceTier.HIGH, DeviceTier.PREMIUM],
            'advanced_rag': self.caps.memory_total_gb >= 6.0,
            'voice_synthesis': self.caps.device_tier != DeviceTier.BASIC,
            'avatar_system': self.caps.device_tier in [DeviceTier.STANDARD, DeviceTier.HIGH, DeviceTier.PREMIUM],
            'transparency_debug': not self.caps.is_mobile  # Too verbose for mobile
        }
    
    def suggest_model_variants(self) -> List[str]:
        """Suggest appropriate model variants"""
        suggestions = []
        
        if self.caps.device_tier == DeviceTier.PREMIUM:
            suggestions = ["SmolLM-1.7B", "TinyLlama-1.1B", "SmolLM-360M"]
        elif self.caps.device_tier == DeviceTier.HIGH:
            suggestions = ["TinyLlama-1.1B", "SmolLM-360M", "SmolLM-135M"]
        elif self.caps.device_tier == DeviceTier.STANDARD:
            suggestions = ["SmolLM-360M", "SmolLM-135M", "SimpleAI"]
        else:  # BASIC
            suggestions = ["SmolLM-135M", "SimpleAI"]
        
        return suggestions
    
    def get_startup_target_times(self) -> Dict[str, float]:
        """Get recommended startup time targets"""
        if self.caps.device_tier == DeviceTier.PREMIUM:
            return {'critical': 1.5, 'full': 4.0}
        elif self.caps.device_tier == DeviceTier.HIGH:
            return {'critical': 2.0, 'full': 6.0}
        elif self.caps.device_tier == DeviceTier.STANDARD:
            return {'critical': 3.0, 'full': 10.0}
        else:  # BASIC
            return {'critical': 5.0, 'full': 15.0}


# Global detector instance
_global_detector: Optional[DeviceDetector] = None


def get_device_detector() -> DeviceDetector:
    """Get or create global device detector"""
    global _global_detector
    if _global_detector is None:
        _global_detector = DeviceDetector()
    return _global_detector


def detect_device_capabilities() -> DeviceCapabilities:
    """Quick function to detect device capabilities"""
    return get_device_detector().get_capabilities()


def get_optimization_recommendations() -> Dict[str, any]:
    """Quick function to get optimization recommendations"""
    return get_device_detector().get_optimization_config()