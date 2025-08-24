#!/usr/bin/env python3
"""
M1K3 Performance Monitor
Comprehensive performance tracking, profiling, and optimization system
"""

import time
import threading
import psutil
import statistics
from typing import Dict, List, Optional, Any, Callable
from dataclasses import dataclass, field
from enum import Enum
import json
from pathlib import Path
import contextlib
import functools
import queue
from collections import defaultdict, deque

class PerformanceEventType(Enum):
    """Types of performance events"""
    STARTUP = "startup"
    MODEL_LOAD = "model_load"
    GENERATION = "generation"
    VOICE_SYNTHESIS = "voice_synthesis"
    AVATAR_UPDATE = "avatar_update"
    COMMAND_PROCESSING = "command_processing"
    SYSTEM_OPERATION = "system_operation"
    MEMORY_OPERATION = "memory_operation"
    NETWORK_OPERATION = "network_operation"

@dataclass
class PerformanceEvent:
    """Individual performance measurement event"""
    name: str
    event_type: PerformanceEventType
    start_time: float
    end_time: Optional[float] = None
    duration: Optional[float] = None
    metadata: Dict[str, Any] = field(default_factory=dict)
    thread_id: int = 0
    memory_before: float = 0.0  # MB
    memory_after: float = 0.0   # MB
    cpu_percent: float = 0.0
    success: bool = True
    error: Optional[str] = None
    
    def __post_init__(self):
        self.thread_id = threading.get_ident()
        if self.end_time and self.start_time:
            self.duration = self.end_time - self.start_time

@dataclass
class PerformanceStats:
    """Aggregated performance statistics"""
    total_events: int = 0
    total_duration: float = 0.0
    average_duration: float = 0.0
    min_duration: float = float('inf')
    max_duration: float = 0.0
    median_duration: float = 0.0
    p95_duration: float = 0.0
    success_rate: float = 100.0
    events_per_second: float = 0.0
    memory_growth_mb: float = 0.0

class PerformanceMonitor:
    """
    Advanced performance monitoring system with the following features:
    
    - Real-time performance tracking
    - Context manager for automatic timing
    - Decorator for function profiling
    - Memory usage monitoring
    - CPU usage tracking
    - Performance regression detection
    - Detailed statistics and reporting
    - Export capabilities for analysis
    """
    
    def __init__(self, max_events: int = 10000, enable_memory_tracking: bool = True):
        self.max_events = max_events
        self.enable_memory_tracking = enable_memory_tracking
        
        # Event storage
        self.events: deque[PerformanceEvent] = deque(maxlen=max_events)
        self.active_events: Dict[str, PerformanceEvent] = {}
        
        # Thread safety
        self.lock = threading.RLock()
        
        # Statistics cache
        self._stats_cache: Dict[str, PerformanceStats] = {}
        self._cache_invalidated = True
        
        # Callbacks for real-time monitoring
        self.event_callbacks: List[Callable[[PerformanceEvent], None]] = []
        
        # Performance baselines for regression detection
        self.baselines: Dict[str, float] = {}
        self.regression_threshold = 1.5  # 50% slower = regression
        
        # Background statistics updating
        self.stats_update_interval = 10.0  # seconds
        self.stats_thread = threading.Thread(target=self._stats_update_loop, daemon=True)
        self.stats_running = True
        self.stats_thread.start()
        
        # System monitoring
        self.process = psutil.Process()
        
        print("📊 Performance Monitor initialized")
    
    def add_event_callback(self, callback: Callable[[PerformanceEvent], None]):
        """Add callback for real-time event notifications"""
        self.event_callbacks.append(callback)
    
    def start_event(self, name: str, event_type: PerformanceEventType, metadata: Dict[str, Any] = None) -> str:
        """Start timing a performance event"""
        if metadata is None:
            metadata = {}
        
        event_id = f"{name}_{threading.get_ident()}_{time.time()}"
        
        # Get memory usage if enabled
        memory_before = 0.0
        if self.enable_memory_tracking:
            try:
                memory_before = self.process.memory_info().rss / 1024 / 1024  # MB
            except:
                pass
        
        event = PerformanceEvent(
            name=name,
            event_type=event_type,
            start_time=time.time(),
            metadata=metadata,
            memory_before=memory_before
        )
        
        with self.lock:
            self.active_events[event_id] = event
        
        return event_id
    
    def end_event(self, event_id: str, success: bool = True, error: Optional[str] = None):
        """End timing a performance event"""
        end_time = time.time()
        
        with self.lock:
            if event_id not in self.active_events:
                return
            
            event = self.active_events.pop(event_id)
            event.end_time = end_time
            event.duration = end_time - event.start_time
            event.success = success
            event.error = error
            
            # Get memory usage if enabled
            if self.enable_memory_tracking:
                try:
                    event.memory_after = self.process.memory_info().rss / 1024 / 1024  # MB
                except:
                    pass
            
            # Get CPU usage
            try:
                event.cpu_percent = self.process.cpu_percent()
            except:
                pass
            
            # Add to events history
            self.events.append(event)
            self._cache_invalidated = True
            
            # Check for performance regression
            self._check_regression(event)
            
            # Notify callbacks
            for callback in self.event_callbacks:
                try:
                    callback(event)
                except Exception as e:
                    print(f"⚠️  Performance callback error: {e}")
    
    @contextlib.contextmanager
    def measure(self, name: str, event_type: PerformanceEventType, metadata: Dict[str, Any] = None):
        """Context manager for measuring performance"""
        event_id = self.start_event(name, event_type, metadata)
        try:
            yield
            self.end_event(event_id, success=True)
        except Exception as e:
            self.end_event(event_id, success=False, error=str(e))
            raise
    
    def profile_function(self, event_type: PerformanceEventType = PerformanceEventType.SYSTEM_OPERATION):
        """Decorator for profiling functions"""
        def decorator(func):
            @functools.wraps(func)
            def wrapper(*args, **kwargs):
                with self.measure(func.__name__, event_type):
                    return func(*args, **kwargs)
            return wrapper
        return decorator
    
    def get_stats(self, 
                  event_name: Optional[str] = None, 
                  event_type: Optional[PerformanceEventType] = None,
                  time_window: Optional[float] = None) -> PerformanceStats:
        """Get performance statistics"""
        
        # Filter events
        filtered_events = []
        cutoff_time = time.time() - time_window if time_window else 0
        
        with self.lock:
            for event in self.events:
                if event.duration is None:
                    continue
                if event_name and event.name != event_name:
                    continue
                if event_type and event.event_type != event_type:
                    continue
                if time_window and event.start_time < cutoff_time:
                    continue
                filtered_events.append(event)
        
        if not filtered_events:
            return PerformanceStats()
        
        # Calculate statistics
        durations = [e.duration for e in filtered_events if e.duration is not None]
        successful_events = [e for e in filtered_events if e.success]
        
        stats = PerformanceStats(
            total_events=len(filtered_events),
            total_duration=sum(durations),
            average_duration=statistics.mean(durations) if durations else 0.0,
            min_duration=min(durations) if durations else 0.0,
            max_duration=max(durations) if durations else 0.0,
            success_rate=(len(successful_events) / len(filtered_events) * 100) if filtered_events else 100.0
        )
        
        # Calculate percentiles
        if durations:
            sorted_durations = sorted(durations)
            stats.median_duration = statistics.median(sorted_durations)
            stats.p95_duration = sorted_durations[int(0.95 * len(sorted_durations))] if len(sorted_durations) > 1 else sorted_durations[0]
        
        # Calculate events per second
        if filtered_events:
            time_span = max(e.end_time for e in filtered_events) - min(e.start_time for e in filtered_events)
            if time_span > 0:
                stats.events_per_second = len(filtered_events) / time_span
        
        # Calculate memory growth
        if self.enable_memory_tracking and filtered_events:
            first_event = min(filtered_events, key=lambda e: e.start_time)
            last_event = max(filtered_events, key=lambda e: e.end_time or 0)
            if first_event.memory_before > 0 and last_event.memory_after > 0:
                stats.memory_growth_mb = last_event.memory_after - first_event.memory_before
        
        return stats
    
    def get_system_performance(self) -> Dict[str, Any]:
        """Get current system performance metrics"""
        try:
            cpu_percent = psutil.cpu_percent(interval=0.1)
            memory = psutil.virtual_memory()
            disk = psutil.disk_usage('/')
            
            # Process-specific metrics
            process_memory = self.process.memory_info().rss / 1024 / 1024  # MB
            process_cpu = self.process.cpu_percent()
            
            return {
                'system_cpu_percent': cpu_percent,
                'system_memory_percent': memory.percent,
                'system_memory_available_gb': memory.available / 1024 / 1024 / 1024,
                'system_disk_percent': disk.percent,
                'process_memory_mb': process_memory,
                'process_cpu_percent': process_cpu,
                'active_threads': threading.active_count(),
                'timestamp': time.time()
            }
        except Exception as e:
            return {'error': str(e), 'timestamp': time.time()}
    
    def set_baseline(self, event_name: str, baseline_duration: float):
        """Set performance baseline for regression detection"""
        self.baselines[event_name] = baseline_duration
        print(f"📊 Set baseline for {event_name}: {baseline_duration:.3f}s")
    
    def get_performance_summary(self) -> Dict[str, Any]:
        """Get comprehensive performance summary"""
        with self.lock:
            total_events = len(self.events)
            if total_events == 0:
                return {'message': 'No performance data available'}
            
            # Overall statistics
            overall_stats = self.get_stats()
            
            # By event type
            type_stats = {}
            for event_type in PerformanceEventType:
                stats = self.get_stats(event_type=event_type)
                if stats.total_events > 0:
                    type_stats[event_type.value] = {
                        'total_events': stats.total_events,
                        'average_duration': stats.average_duration,
                        'success_rate': stats.success_rate,
                        'max_duration': stats.max_duration
                    }
            
            # Recent events (last 10)
            recent_events = []
            for event in list(self.events)[-10:]:
                if event.duration is not None:
                    recent_events.append({
                        'name': event.name,
                        'type': event.event_type.value,
                        'duration': event.duration,
                        'success': event.success,
                        'memory_delta_mb': event.memory_after - event.memory_before if event.memory_after > 0 else 0
                    })
            
            # System performance
            system_perf = self.get_system_performance()
            
            return {
                'overall_stats': {
                    'total_events': overall_stats.total_events,
                    'average_duration': overall_stats.average_duration,
                    'success_rate': overall_stats.success_rate,
                    'events_per_second': overall_stats.events_per_second,
                    'memory_growth_mb': overall_stats.memory_growth_mb
                },
                'by_event_type': type_stats,
                'recent_events': recent_events,
                'system_performance': system_perf,
                'regressions_detected': len([e for e in self.events if self._is_regression(e)]),
                'baselines_set': len(self.baselines)
            }
    
    def export_performance_data(self, filepath: Optional[Path] = None) -> str:
        """Export performance data to JSON file"""
        if filepath is None:
            filepath = Path(f"performance_data_{int(time.time())}.json")
        
        export_data = {
            'metadata': {
                'export_time': time.time(),
                'total_events': len(self.events),
                'time_range': {
                    'start': min(e.start_time for e in self.events) if self.events else 0,
                    'end': max(e.end_time or e.start_time for e in self.events) if self.events else 0
                }
            },
            'events': [],
            'summary': self.get_performance_summary()
        }
        
        # Export events
        for event in self.events:
            export_data['events'].append({
                'name': event.name,
                'type': event.event_type.value,
                'start_time': event.start_time,
                'end_time': event.end_time,
                'duration': event.duration,
                'success': event.success,
                'error': event.error,
                'memory_before': event.memory_before,
                'memory_after': event.memory_after,
                'cpu_percent': event.cpu_percent,
                'metadata': event.metadata,
                'thread_id': event.thread_id
            })
        
        # Write to file
        with open(filepath, 'w') as f:
            json.dump(export_data, f, indent=2)
        
        print(f"📄 Performance data exported to: {filepath}")
        return str(filepath)
    
    def _check_regression(self, event: PerformanceEvent):
        """Check if event represents a performance regression"""
        if event.name in self.baselines and event.duration:
            baseline = self.baselines[event.name]
            if event.duration > baseline * self.regression_threshold:
                print(f"⚠️  Performance regression detected in {event.name}: "
                      f"{event.duration:.3f}s vs baseline {baseline:.3f}s "
                      f"({event.duration/baseline:.1f}x slower)")
                
                # Add to metadata for tracking
                event.metadata['regression'] = True
                event.metadata['baseline'] = baseline
                event.metadata['slowdown_factor'] = event.duration / baseline
    
    def _is_regression(self, event: PerformanceEvent) -> bool:
        """Check if event is a regression"""
        return event.metadata.get('regression', False)
    
    def _stats_update_loop(self):
        """Background thread for updating statistics cache"""
        while self.stats_running:
            try:
                if self._cache_invalidated:
                    # Update cache for common queries
                    self._stats_cache.clear()
                    for event_type in PerformanceEventType:
                        self._stats_cache[f"type_{event_type.value}"] = self.get_stats(event_type=event_type)
                    
                    self._cache_invalidated = False
                
                time.sleep(self.stats_update_interval)
            except Exception as e:
                print(f"⚠️  Stats update error: {e}")
                time.sleep(5.0)
    
    def shutdown(self):
        """Shutdown the performance monitor"""
        self.stats_running = False
        if self.stats_thread.is_alive():
            self.stats_thread.join(timeout=2.0)
        
        print(f"📊 Performance Monitor shutdown. Tracked {len(self.events)} events.")

# Global instance
_global_performance_monitor: Optional[PerformanceMonitor] = None

def get_performance_monitor() -> PerformanceMonitor:
    """Get global performance monitor instance"""
    global _global_performance_monitor
    if _global_performance_monitor is None:
        _global_performance_monitor = PerformanceMonitor()
    return _global_performance_monitor

# Convenience functions
def measure_performance(name: str, event_type: PerformanceEventType = PerformanceEventType.SYSTEM_OPERATION):
    """Context manager for measuring performance"""
    return get_performance_monitor().measure(name, event_type)

def profile(event_type: PerformanceEventType = PerformanceEventType.SYSTEM_OPERATION):
    """Decorator for profiling functions"""
    return get_performance_monitor().profile_function(event_type)

def set_baseline(event_name: str, duration: float):
    """Set performance baseline"""
    get_performance_monitor().set_baseline(event_name, duration)

def get_performance_summary() -> Dict[str, Any]:
    """Get performance summary"""
    return get_performance_monitor().get_performance_summary()

if __name__ == "__main__":
    # Demo and test the performance monitor
    monitor = PerformanceMonitor()
    
    def progress_callback(event: PerformanceEvent):
        if event.duration:
            print(f"📊 {event.name}: {event.duration:.3f}s ({'✅' if event.success else '❌'})")
    
    monitor.add_event_callback(progress_callback)
    
    # Test measurements
    print("🧪 Testing performance measurements...")
    
    # Test context manager
    with monitor.measure("test_operation", PerformanceEventType.SYSTEM_OPERATION):
        time.sleep(0.1)
    
    # Test decorator
    @monitor.profile_function(PerformanceEventType.GENERATION)
    def test_function():
        time.sleep(0.05)
        return "test result"
    
    result = test_function()
    
    # Test manual timing
    event_id = monitor.start_event("manual_test", PerformanceEventType.MODEL_LOAD)
    time.sleep(0.02)
    monitor.end_event(event_id)
    
    # Get statistics
    stats = monitor.get_stats()
    print(f"📊 Statistics: {stats.total_events} events, avg: {stats.average_duration:.3f}s")
    
    # Get summary
    summary = monitor.get_performance_summary()
    print(f"📋 Summary: {summary['overall_stats']}")
    
    # Export data
    monitor.export_performance_data()
    
    monitor.shutdown()