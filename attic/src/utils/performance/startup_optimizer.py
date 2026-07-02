#!/usr/bin/env python3
"""
Startup Optimizer
Performance monitoring and optimization for M1K3 startup sequence
"""

import time
import psutil
import threading
import weakref
from typing import Dict, List, Optional, Callable, Any
from dataclasses import dataclass, field
from enum import Enum
import logging

logger = logging.getLogger(__name__)


class StartupPhase(Enum):
    """Phases of startup process"""
    INITIALIZING = "initializing"
    LOADING_CRITICAL = "loading_critical"
    LOADING_SECONDARY = "loading_secondary"
    READY = "ready"
    ERROR = "error"


@dataclass
class PerformanceMetric:
    """Performance measurement data"""
    name: str
    start_time: float
    end_time: Optional[float] = None
    memory_start: float = 0.0
    memory_end: float = 0.0
    cpu_percent: float = 0.0
    
    @property
    def duration(self) -> float:
        if self.end_time is None:
            return time.time() - self.start_time
        return self.end_time - self.start_time
    
    @property
    def memory_usage(self) -> float:
        return self.memory_end - self.memory_start


@dataclass 
class DeviceCapabilities:
    """Device capability assessment"""
    cpu_count: int = 0
    memory_gb: float = 0.0
    is_mobile: bool = False
    has_gpu: bool = False
    platform: str = ""
    performance_tier: str = "basic"  # basic, standard, high


class StartupOptimizer:
    """Optimizes and monitors M1K3 startup performance"""
    
    def __init__(self):
        self.metrics: Dict[str, PerformanceMetric] = {}
        self.phase = StartupPhase.INITIALIZING
        self.start_time = time.time()
        self.critical_ready_time: Optional[float] = None
        self.full_ready_time: Optional[float] = None
        
        # Device assessment
        self.device_caps = self._assess_device_capabilities()
        
        # Performance targets
        self.targets = self._get_performance_targets()
        
        # Callbacks
        self.phase_callbacks: List[Callable[[StartupPhase], None]] = []
        self.metric_callbacks: List[Callable[[str, PerformanceMetric], None]] = []
        
        # Optimization strategies
        self.optimization_strategies = self._determine_optimization_strategies()
        
        logger.info(f"🎯 Performance targets: {self.targets}")
        logger.info(f"⚙️  Device tier: {self.device_caps.performance_tier}")
    
    def _assess_device_capabilities(self) -> DeviceCapabilities:
        """Assess device capabilities for optimization"""
        import platform
        
        caps = DeviceCapabilities()
        caps.cpu_count = psutil.cpu_count()
        caps.memory_gb = psutil.virtual_memory().total / (1024 ** 3)
        caps.platform = platform.system().lower()
        
        # Detect mobile (simplified detection)
        caps.is_mobile = caps.memory_gb < 4.0 or caps.cpu_count <= 2
        
        # Detect GPU (simplified)
        try:
            import torch
            caps.has_gpu = torch.cuda.is_available()
        except ImportError:
            caps.has_gpu = False
        
        # Determine performance tier
        if caps.memory_gb >= 8.0 and caps.cpu_count >= 4:
            caps.performance_tier = "high"
        elif caps.memory_gb >= 4.0 and caps.cpu_count >= 2:
            caps.performance_tier = "standard"
        else:
            caps.performance_tier = "basic"
        
        return caps
    
    def _get_performance_targets(self) -> Dict[str, float]:
        """Get performance targets based on device capabilities"""
        if self.device_caps.performance_tier == "high":
            return {
                'critical_ready': 2.0,    # seconds
                'full_ready': 5.0,        # seconds  
                'memory_limit': 1024,     # MB
                'response_time': 1.5      # seconds
            }
        elif self.device_caps.performance_tier == "standard":
            return {
                'critical_ready': 3.0,
                'full_ready': 8.0,
                'memory_limit': 800,
                'response_time': 2.5
            }
        else:  # basic
            return {
                'critical_ready': 5.0,
                'full_ready': 15.0,
                'memory_limit': 600,
                'response_time': 4.0
            }
    
    def _determine_optimization_strategies(self) -> Dict[str, Any]:
        """Determine optimization strategies based on device"""
        strategies = {
            'async_loading': True,
            'model_caching': True,
            'lazy_loading': True,
            'memory_optimization': self.device_caps.memory_gb < 6.0,
            'concurrent_workers': min(2, self.device_caps.cpu_count),
            'prefetch_models': self.device_caps.performance_tier == "high"
        }
        
        # Mobile-specific optimizations
        if self.device_caps.is_mobile:
            strategies.update({
                'aggressive_caching': True,
                'minimal_ui': True,
                'background_loading': False  # Preserve battery
            })
        
        return strategies
    
    def start_metric(self, name: str) -> None:
        """Start measuring a performance metric"""
        current_memory = psutil.virtual_memory().used / (1024 ** 2)  # MB
        
        metric = PerformanceMetric(
            name=name,
            start_time=time.time(),
            memory_start=current_memory,
            cpu_percent=psutil.cpu_percent()
        )
        
        self.metrics[name] = metric
        logger.debug(f"📊 Started measuring: {name}")
    
    def end_metric(self, name: str) -> Optional[PerformanceMetric]:
        """End measuring a performance metric"""
        if name not in self.metrics:
            logger.warning(f"Metric not found: {name}")
            return None
        
        metric = self.metrics[name]
        metric.end_time = time.time()
        metric.memory_end = psutil.virtual_memory().used / (1024 ** 2)  # MB
        
        logger.info(f"📊 {name}: {metric.duration:.2f}s, {metric.memory_usage:.1f}MB")
        
        # Notify callbacks
        for callback in self.metric_callbacks:
            try:
                callback(name, metric)
            except Exception as e:
                logger.error(f"Metric callback error: {e}")
        
        return metric
    
    def set_phase(self, phase: StartupPhase) -> None:
        """Set current startup phase"""
        old_phase = self.phase
        self.phase = phase
        
        # Record phase transition times
        current_time = time.time()
        elapsed = current_time - self.start_time
        
        if phase == StartupPhase.LOADING_CRITICAL and old_phase == StartupPhase.INITIALIZING:
            logger.info(f"🔧 Initialization complete: {elapsed:.2f}s")
        elif phase == StartupPhase.READY and self.critical_ready_time is None:
            self.critical_ready_time = elapsed
            logger.info(f"🎯 Critical systems ready: {elapsed:.2f}s")
        elif phase == StartupPhase.READY:
            self.full_ready_time = elapsed
            logger.info(f"✅ Full system ready: {elapsed:.2f}s")
        
        # Notify phase callbacks
        for callback in self.phase_callbacks:
            try:
                callback(phase)
            except Exception as e:
                logger.error(f"Phase callback error: {e}")
    
    def register_phase_callback(self, callback: Callable[[StartupPhase], None]):
        """Register callback for phase changes"""
        self.phase_callbacks.append(callback)
    
    def register_metric_callback(self, callback: Callable[[str, PerformanceMetric], None]):
        """Register callback for metric updates"""
        self.metric_callbacks.append(callback)
    
    def get_optimization_config(self) -> Dict[str, Any]:
        """Get optimization configuration for current device"""
        return {
            'device_capabilities': self.device_caps,
            'performance_targets': self.targets,
            'optimization_strategies': self.optimization_strategies,
            'concurrent_workers': self.optimization_strategies['concurrent_workers'],
            'memory_limit_mb': self.targets['memory_limit'],
            'enable_caching': self.optimization_strategies['model_caching']
        }
    
    def check_performance_targets(self) -> Dict[str, bool]:
        """Check if performance targets are being met"""
        results = {}
        
        if self.critical_ready_time is not None:
            results['critical_ready'] = self.critical_ready_time <= self.targets['critical_ready']
        
        if self.full_ready_time is not None:
            results['full_ready'] = self.full_ready_time <= self.targets['full_ready']
        
        # Check memory usage
        current_memory = psutil.virtual_memory().used / (1024 ** 2)  # MB
        results['memory_usage'] = current_memory <= self.targets['memory_limit']
        
        return results
    
    def get_performance_report(self) -> Dict[str, Any]:
        """Get comprehensive performance report"""
        current_time = time.time()
        elapsed = current_time - self.start_time
        
        report = {
            'startup_time': elapsed,
            'critical_ready_time': self.critical_ready_time,
            'full_ready_time': self.full_ready_time,
            'current_phase': self.phase.value,
            'device_capabilities': {
                'cpu_count': self.device_caps.cpu_count,
                'memory_gb': self.device_caps.memory_gb,
                'performance_tier': self.device_caps.performance_tier,
                'is_mobile': self.device_caps.is_mobile
            },
            'performance_targets': self.targets,
            'target_compliance': self.check_performance_targets(),
            'metrics': {
                name: {
                    'duration': metric.duration,
                    'memory_usage': metric.memory_usage,
                    'cpu_percent': metric.cpu_percent
                }
                for name, metric in self.metrics.items()
            },
            'optimization_strategies': self.optimization_strategies
        }
        
        return report
    
    def suggest_optimizations(self) -> List[str]:
        """Suggest optimizations based on performance data"""
        suggestions = []
        targets_met = self.check_performance_targets()
        
        if not targets_met.get('critical_ready', True):
            suggestions.append("Consider reducing critical component loading")
            suggestions.append("Enable more aggressive caching")
            suggestions.append("Increase concurrent worker threads")
        
        if not targets_met.get('memory_usage', True):
            suggestions.append("Enable memory optimization mode")
            suggestions.append("Reduce model size or precision")
            suggestions.append("Implement more aggressive lazy loading")
        
        if self.device_caps.is_mobile:
            suggestions.append("Consider mobile-specific model variants")
            suggestions.append("Implement background loading throttling")
        
        # Analyze slow metrics
        slow_metrics = [
            name for name, metric in self.metrics.items()
            if metric.duration > 2.0
        ]
        
        if slow_metrics:
            suggestions.append(f"Optimize slow operations: {', '.join(slow_metrics)}")
        
        return suggestions


# Global optimizer instance
_global_optimizer: Optional[StartupOptimizer] = None


def get_startup_optimizer() -> StartupOptimizer:
    """Get or create the global startup optimizer"""
    global _global_optimizer
    if _global_optimizer is None:
        _global_optimizer = StartupOptimizer()
    return _global_optimizer


def create_performance_context(name: str):
    """Context manager for measuring performance"""
    class PerformanceContext:
        def __init__(self, metric_name: str):
            self.name = metric_name
            self.optimizer = get_startup_optimizer()
        
        def __enter__(self):
            self.optimizer.start_metric(self.name)
            return self
        
        def __exit__(self, exc_type, exc_val, exc_tb):
            self.optimizer.end_metric(self.name)
    
    return PerformanceContext(name)