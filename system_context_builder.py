#!/usr/bin/env python3
"""
M1K3 System Context Builder
Dynamic system statistics and device info for context-aware AI responses
"""

import os
import platform
import psutil
import time
from typing import Dict, Optional
from dataclasses import dataclass
from pathlib import Path
import subprocess

@dataclass
class SystemStats:
    """System statistics snapshot"""
    cpu_percent: float
    cpu_cores: int
    ram_total_gb: float
    ram_available_gb: float
    ram_percent: float
    platform: str
    architecture: str
    device_tier: str
    
@dataclass
class ModelPerformance:
    """Model performance metrics"""
    backend: str
    tokens_per_second: Optional[float]
    avg_response_ms: Optional[float]
    memory_usage_mb: Optional[float]
    last_response_time: Optional[float]
    
@dataclass
class SessionMetrics:
    """Current session metrics"""
    message_count: int
    session_duration_minutes: float
    context_tokens_used: int
    context_tokens_max: int
    avg_response_time_ms: float
    
class SystemContextBuilder:
    """Builds dynamic system context for enhanced AI responses"""
    
    def __init__(self):
        self.start_time = time.time()
        self.response_times = []
        self.message_count = 0
        self.last_stats_cache = None
        self.cache_expiry = 10  # Cache for 10 seconds
        self.last_cache_time = 0
        
    def get_system_stats(self) -> SystemStats:
        """Get current system statistics"""
        # Use cached data if still fresh
        current_time = time.time()
        if (self.last_stats_cache and 
            current_time - self.last_cache_time < self.cache_expiry):
            return self.last_stats_cache
            
        # Gather fresh stats
        cpu_percent = psutil.cpu_percent(interval=0.1)
        cpu_cores = psutil.cpu_count()
        
        memory = psutil.virtual_memory()
        ram_total_gb = round(memory.total / (1024**3), 1)
        ram_available_gb = round(memory.available / (1024**3), 1)
        ram_percent = memory.percent
        
        platform_name = platform.system()
        architecture = platform.machine()
        
        # Determine device tier based on specs
        device_tier = self._determine_device_tier(cpu_cores, ram_total_gb, cpu_percent)
        
        stats = SystemStats(
            cpu_percent=cpu_percent,
            cpu_cores=cpu_cores,
            ram_total_gb=ram_total_gb,
            ram_available_gb=ram_available_gb,
            ram_percent=ram_percent,
            platform=platform_name,
            architecture=architecture,
            device_tier=device_tier
        )
        
        # Cache the results
        self.last_stats_cache = stats
        self.last_cache_time = current_time
        
        return stats
    
    def _determine_device_tier(self, cpu_cores: int, ram_gb: float, cpu_usage: float) -> str:
        """Determine device performance tier"""
        # High-end: 8+ cores, 16+ GB RAM, low CPU usage
        if cpu_cores >= 8 and ram_gb >= 16 and cpu_usage < 50:
            return "high-performance"
        # Mid-range: 4+ cores, 8+ GB RAM
        elif cpu_cores >= 4 and ram_gb >= 8:
            return "balanced"
        # Budget: 2+ cores, 4+ GB RAM
        elif cpu_cores >= 2 and ram_gb >= 4:
            return "efficient"
        # Low-end: anything else
        else:
            return "minimal"
    
    def get_model_performance(self, backend: str, model_name: str = "SmolLM2-135M") -> ModelPerformance:
        """Get model-specific performance metrics"""
        # Calculate average response time
        avg_response_ms = None
        if self.response_times:
            avg_response_ms = round(sum(self.response_times) / len(self.response_times) * 1000, 1)
        
        # Estimate tokens per second (rough calculation)
        tokens_per_second = None
        if avg_response_ms and avg_response_ms > 0:
            # Assume average response is ~100 tokens for SmolLM2
            estimated_tokens = 100
            tokens_per_second = round(estimated_tokens / (avg_response_ms / 1000), 1)
        
        # Get model memory usage (rough estimate)
        memory_usage_mb = self._estimate_model_memory(backend, model_name)
        
        last_response_time = self.response_times[-1] * 1000 if self.response_times else None
        
        return ModelPerformance(
            backend=backend,
            tokens_per_second=tokens_per_second,
            avg_response_ms=avg_response_ms,
            memory_usage_mb=memory_usage_mb,
            last_response_time=last_response_time
        )
    
    def _estimate_model_memory(self, backend: str, model_name: str) -> Optional[float]:
        """Estimate model memory usage in MB"""
        # These are rough estimates for SmolLM2-135M
        estimates = {
            "gguf": 100,      # Quantized GGUF
            "ollama": 270,    # Ollama overhead
            "transformers": 400,  # HuggingFace with PyTorch
            "simple_ai": 10   # Mock engine
        }
        return estimates.get(backend.lower())
    
    def get_session_metrics(self, context_used: int = 0, context_max: int = 2048) -> SessionMetrics:
        """Get current session metrics"""
        session_duration = (time.time() - self.start_time) / 60  # minutes
        
        avg_response_time_ms = 0.0
        if self.response_times:
            avg_response_time_ms = round(sum(self.response_times) / len(self.response_times) * 1000, 1)
        
        return SessionMetrics(
            message_count=self.message_count,
            session_duration_minutes=round(session_duration, 1),
            context_tokens_used=context_used,
            context_tokens_max=context_max,
            avg_response_time_ms=avg_response_time_ms
        )
    
    def record_response_time(self, response_time_seconds: float):
        """Record a response time for performance tracking"""
        self.response_times.append(response_time_seconds)
        
        # Keep only last 20 response times for rolling average
        if len(self.response_times) > 20:
            self.response_times = self.response_times[-20:]
    
    def increment_message_count(self):
        """Increment the message counter"""
        self.message_count += 1
    
    def get_hardware_acceleration_info(self) -> Dict[str, bool]:
        """Check for available hardware acceleration"""
        acceleration = {
            "cuda": False,
            "mps": False,  # Apple Metal
            "opencl": False,
            "cpu_optimized": False
        }
        
        try:
            # Check for CUDA
            import torch
            acceleration["cuda"] = torch.cuda.is_available()
            acceleration["mps"] = torch.backends.mps.is_available()
        except ImportError:
            pass
        
        # Check for CPU optimization flags
        try:
            cpu_info = subprocess.run(['sysctl', '-n', 'machdep.cpu.features'], 
                                    capture_output=True, text=True, timeout=2)
            if cpu_info.returncode == 0:
                features = cpu_info.stdout.lower()
                acceleration["cpu_optimized"] = any(flag in features for flag in ['avx', 'sse', 'avx2'])
        except:
            # Fallback for non-macOS systems
            try:
                with open('/proc/cpuinfo', 'r') as f:
                    cpu_info = f.read().lower()
                    acceleration["cpu_optimized"] = any(flag in cpu_info for flag in ['avx', 'sse', 'avx2'])
            except:
                # Assume basic CPU optimization is available
                acceleration["cpu_optimized"] = True
        
        return acceleration
    
    def build_context_summary(self, backend: str, model_name: str = "SmolLM2-135M", 
                             context_used: int = 0, context_max: int = 2048) -> Dict[str, str]:
        """Build a comprehensive context summary for system prompts"""
        
        system_stats = self.get_system_stats()
        model_perf = self.get_model_performance(backend, model_name)
        session_metrics = self.get_session_metrics(context_used, context_max)
        hardware_accel = self.get_hardware_acceleration_info()
        
        # Build acceleration summary
        accel_features = []
        if hardware_accel["cuda"]:
            accel_features.append("CUDA")
        if hardware_accel["mps"]:
            accel_features.append("Metal")
        if hardware_accel["cpu_optimized"]:
            accel_features.append("CPU-optimized")
        
        accel_summary = ", ".join(accel_features) if accel_features else "basic"
        
        # Build context dictionary
        context = {
            # System info
            "platform": system_stats.platform,
            "architecture": system_stats.architecture,
            "cpu_cores": str(system_stats.cpu_cores),
            "cpu_usage": f"{system_stats.cpu_percent:.1f}",
            "ram_total_gb": str(system_stats.ram_total_gb),
            "ram_available_gb": str(system_stats.ram_available_gb),
            "ram_usage_percent": f"{system_stats.ram_percent:.1f}",
            "device_tier": system_stats.device_tier,
            
            # Model performance
            "backend": model_perf.backend,
            "tokens_per_sec": str(model_perf.tokens_per_second) if model_perf.tokens_per_second else "calculating",
            "avg_response_ms": str(model_perf.avg_response_ms) if model_perf.avg_response_ms else "calculating",
            "model_memory_mb": str(model_perf.memory_usage_mb) if model_perf.memory_usage_mb else "unknown",
            
            # Session metrics
            "msg_count": str(session_metrics.message_count),
            "session_duration_min": str(session_metrics.session_duration_minutes),
            "context_used": str(session_metrics.context_tokens_used),
            "context_max": str(session_metrics.context_tokens_max),
            "context_utilization": f"{(session_metrics.context_tokens_used / session_metrics.context_tokens_max * 100):.1f}%" if session_metrics.context_tokens_max > 0 else "0%",
            
            # Hardware acceleration
            "acceleration": accel_summary,
            
            # Performance indicators
            "system_load_status": self._get_load_status(system_stats.cpu_percent, system_stats.ram_percent),
            "optimization_mode": self._get_optimization_mode(system_stats.device_tier, system_stats.cpu_percent)
        }
        
        return context
    
    def _get_load_status(self, cpu_percent: float, ram_percent: float) -> str:
        """Get human-readable system load status"""
        if cpu_percent > 80 or ram_percent > 85:
            return "high-load"
        elif cpu_percent > 60 or ram_percent > 70:
            return "moderate-load"
        else:
            return "low-load"
    
    def _get_optimization_mode(self, device_tier: str, cpu_percent: float) -> str:
        """Get recommended optimization mode"""
        if device_tier == "minimal" or cpu_percent > 75:
            return "efficiency"
        elif device_tier in ["balanced", "efficient"]:
            return "balanced"
        else:
            return "quality"


def test_system_context_builder():
    """Test the system context builder"""
    print("🧪 Testing System Context Builder")
    print("=" * 40)
    
    builder = SystemContextBuilder()
    
    # Simulate some activity
    builder.increment_message_count()
    builder.record_response_time(0.15)  # 150ms response
    builder.increment_message_count()
    builder.record_response_time(0.12)  # 120ms response
    
    # Test system stats
    system_stats = builder.get_system_stats()
    print(f"📊 System: {system_stats.platform} {system_stats.architecture}")
    print(f"💻 CPU: {system_stats.cpu_cores} cores @ {system_stats.cpu_percent}% usage")
    print(f"🧠 RAM: {system_stats.ram_available_gb}/{system_stats.ram_total_gb} GB available")
    print(f"🎯 Tier: {system_stats.device_tier}")
    
    # Test model performance
    model_perf = builder.get_model_performance("gguf")
    print(f"\n⚡ Model Performance:")
    print(f"  Backend: {model_perf.backend}")
    print(f"  Tokens/sec: {model_perf.tokens_per_second}")
    print(f"  Avg response: {model_perf.avg_response_ms}ms")
    print(f"  Memory usage: {model_perf.memory_usage_mb}MB")
    
    # Test session metrics
    session = builder.get_session_metrics(500, 2048)
    print(f"\n📈 Session Metrics:")
    print(f"  Messages: {session.message_count}")
    print(f"  Duration: {session.session_duration_minutes} minutes")
    print(f"  Context: {session.context_tokens_used}/{session.context_tokens_max} tokens")
    
    # Test hardware acceleration
    hardware = builder.get_hardware_acceleration_info()
    print(f"\n🚀 Hardware Acceleration:")
    for feature, available in hardware.items():
        print(f"  {feature}: {'✅' if available else '❌'}")
    
    # Test full context summary
    context = builder.build_context_summary("gguf", "SmolLM2-135M", 500, 2048)
    print(f"\n🎯 Context Summary:")
    for key, value in context.items():
        print(f"  {key}: {value}")
    
    print("\n✅ System Context Builder test complete!")

if __name__ == "__main__":
    test_system_context_builder()