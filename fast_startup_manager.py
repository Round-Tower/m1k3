#!/usr/bin/env python3
"""
M1K3 Fast Startup Manager
Orchestrates parallel initialization of all M1K3 components for dramatic startup speed improvements
"""

import time
import threading
import concurrent.futures
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, Callable, Any, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
import queue
import traceback

class ComponentStatus(Enum):
    """Status of initialization components"""
    PENDING = "pending"
    LOADING = "loading"
    READY = "ready"
    FAILED = "failed"
    SKIPPED = "skipped"

@dataclass
class ComponentResult:
    """Result of component initialization"""
    name: str
    status: ComponentStatus
    result: Any = None
    error: Optional[Exception] = None
    duration: float = 0.0
    message: str = ""

class FastStartupManager:
    """
    Manages parallel initialization of M1K3 components for optimal startup performance.
    
    Key features:
    - Parallel loading of independent components
    - Progress tracking and real-time status updates
    - Essential vs optional component prioritization
    - Graceful failure handling with fallbacks
    - Performance metrics and timing analysis
    """
    
    def __init__(self, cli_instance, max_workers: int = 4):
        self.cli = cli_instance
        self.max_workers = max_workers
        self.executor = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="M1K3-Startup")
        
        # Tracking
        self.start_time = time.time()
        self.component_results: Dict[str, ComponentResult] = {}
        self.futures: Dict[str, concurrent.futures.Future] = {}
        
        # Status callbacks for real-time updates
        self.status_callbacks: list[Callable[[str, ComponentStatus, str], None]] = []
        
        # Component dependencies and priorities
        self.essential_components = {'ai_model'}  # Must succeed for M1K3 to function
        self.optional_components = {'voice_model', 'avatar_server', 'system_monitoring', 'sound_manager'}
        
        # Progress tracking
        self.progress_lock = threading.Lock()
        self.total_components = 0
        self.completed_components = 0
        
    def add_status_callback(self, callback: Callable[[str, ComponentStatus, str], None]):
        """Add callback for real-time status updates"""
        self.status_callbacks.append(callback)
    
    def _notify_status_change(self, component: str, status: ComponentStatus, message: str = ""):
        """Notify all callbacks of status changes"""
        for callback in self.status_callbacks:
            try:
                callback(component, status, message)
            except Exception as e:
                print(f"⚠️  Status callback error: {e}")
    
    def initialize_parallel(self) -> Dict[str, ComponentResult]:
        """
        Start parallel initialization of all components.
        Returns immediately with futures for tracking progress.
        """
        print("🚀 Starting parallel initialization...")
        
        # Define all initialization tasks
        tasks = {
            'ai_model': self._init_ai_model,
            'voice_model': self._init_voice_model,
            'system_monitoring': self._init_system_monitoring,
            'avatar_server': self._init_avatar_server,
            'sound_manager': self._init_sound_manager,
            'websocket_bridge': self._init_websocket_bridge,
        }
        
        self.total_components = len(tasks)
        
        # Submit all tasks to thread pool
        for component_name, init_func in tasks.items():
            future = self.executor.submit(self._run_component_init, component_name, init_func)
            self.futures[component_name] = future
            
            # Initialize result tracking
            self.component_results[component_name] = ComponentResult(
                name=component_name,
                status=ComponentStatus.PENDING
            )
            self._notify_status_change(component_name, ComponentStatus.PENDING)
        
        return self.component_results
    
    def wait_for_essential(self, timeout: float = 30.0) -> bool:
        """
        Wait for essential components to complete.
        Returns True if all essential components loaded successfully.
        """
        print("⏳ Waiting for essential components...")
        
        essential_futures = {name: future for name, future in self.futures.items() 
                           if name in self.essential_components}
        
        try:
            # Wait for essential components with timeout
            done, not_done = concurrent.futures.wait(
                essential_futures.values(), 
                timeout=timeout,
                return_when=concurrent.futures.ALL_COMPLETED
            )
            
            # Collect results for essential components first
            for component_name in self.essential_components:
                future = self.futures.get(component_name)
                if future and future.done():
                    try:
                        result = future.result()
                        self.component_results[component_name] = result
                    except Exception as e:
                        self.component_results[component_name] = ComponentResult(
                            name=component_name,
                            status=ComponentStatus.FAILED,
                            error=e,
                            message=f"Exception: {e}"
                        )
            
            # Check if all essential components completed successfully
            all_essential_ready = True
            for component_name in self.essential_components:
                result = self.component_results.get(component_name)
                if not result or result.status != ComponentStatus.READY:
                    all_essential_ready = False
                    if result:
                        print(f"❌ Essential component failed: {component_name} (status: {result.status.value})")
                        if result.error:
                            print(f"   Error: {result.error}")
                    else:
                        print(f"❌ Essential component failed: {component_name} (no result)")
                    break
            
            if all_essential_ready:
                print(f"✅ Essential components ready in {time.time() - self.start_time:.2f}s")
                return True
            else:
                print("❌ Some essential components failed to initialize")
                return False
                
        except concurrent.futures.TimeoutError:
            print(f"⏰ Timeout waiting for essential components after {timeout}s")
            return False
    
    def wait_for_all(self, timeout: float = 60.0) -> Dict[str, ComponentResult]:
        """
        Wait for all components to complete (or timeout).
        Returns final results for all components.
        """
        print("⏳ Waiting for all components...")
        
        try:
            # Wait for all components
            concurrent.futures.wait(
                self.futures.values(),
                timeout=timeout,
                return_when=concurrent.futures.ALL_COMPLETED
            )
        except concurrent.futures.TimeoutError:
            print(f"⏰ Some components timed out after {timeout}s")
        
        # Collect final results
        for component_name, future in self.futures.items():
            if future.done():
                try:
                    result = future.result()
                    self.component_results[component_name] = result
                except Exception as e:
                    self.component_results[component_name] = ComponentResult(
                        name=component_name,
                        status=ComponentStatus.FAILED,
                        error=e,
                        message=f"Execution error: {e}"
                    )
        
        total_time = time.time() - self.start_time
        print(f"🏁 Parallel initialization completed in {total_time:.2f}s")
        
        return self.component_results
    
    def get_progress_status(self) -> Dict[str, Any]:
        """Get current progress and status of all components"""
        with self.progress_lock:
            completed = sum(1 for result in self.component_results.values() 
                          if result.status in [ComponentStatus.READY, ComponentStatus.FAILED, ComponentStatus.SKIPPED])
            
            return {
                'total_components': self.total_components,
                'completed_components': completed,
                'progress_percent': (completed / self.total_components * 100) if self.total_components > 0 else 0,
                'elapsed_time': time.time() - self.start_time,
                'component_status': {name: result.status.value for name, result in self.component_results.items()},
                'essential_ready': all(
                    self.component_results.get(name, ComponentResult(name, ComponentStatus.PENDING)).status == ComponentStatus.READY
                    for name in self.essential_components
                )
            }
    
    def _run_component_init(self, component_name: str, init_func: Callable) -> ComponentResult:
        """Run a single component initialization with timing and error handling"""
        start_time = time.time()
        
        try:
            self._notify_status_change(component_name, ComponentStatus.LOADING, f"Loading {component_name}...")
            
            # Run the initialization function
            result = init_func()
            
            duration = time.time() - start_time
            
            if result:
                status = ComponentStatus.READY
                message = f"{component_name} ready in {duration:.2f}s"
                print(f"✅ {message}")
            else:
                status = ComponentStatus.FAILED
                message = f"{component_name} failed to initialize"
                print(f"❌ {message}")
            
            component_result = ComponentResult(
                name=component_name,
                status=status,
                result=result,
                duration=duration,
                message=message
            )
            
            self._notify_status_change(component_name, status, message)
            
            with self.progress_lock:
                self.completed_components += 1
            
            return component_result
            
        except Exception as e:
            duration = time.time() - start_time
            error_msg = f"{component_name} error: {e}"
            print(f"❌ {error_msg}")
            
            component_result = ComponentResult(
                name=component_name,
                status=ComponentStatus.FAILED,
                error=e,
                duration=duration,
                message=error_msg
            )
            
            self._notify_status_change(component_name, ComponentStatus.FAILED, error_msg)
            
            with self.progress_lock:
                self.completed_components += 1
            
            return component_result
    
    # Component initialization functions
    
    def _init_ai_model(self) -> bool:
        """Initialize AI model (essential component)"""
        try:
            if not hasattr(self.cli, 'ai_engine'):
                print("❌ AI model init: No ai_engine attribute")
                return False
            
            # Check if model exists first
            if not self.cli.ai_engine.is_model_available():
                print("📥 Model not found, downloading...")
                try:
                    from download_model import download_model
                    model_path = download_model()
                    if not model_path:
                        print("❌ Model download failed")
                        return False
                    print(f"✅ Model downloaded to: {model_path}")
                except Exception as e:
                    print(f"❌ Model download error: {e}")
                    return False
            
            # Load the model
            print("🔄 Loading AI model...")
            success = self.cli.ai_engine.load_model()
            if success:
                print("✅ AI model loaded successfully")
            else:
                print("❌ AI model loading returned False")
            return success
            
        except Exception as e:
            print(f"❌ AI model initialization error: {e}")
            import traceback
            traceback.print_exc()
            return False
    
    def _init_voice_model(self) -> bool:
        """Initialize voice model (optional component)"""
        try:
            if not self.cli.voice_enabled or not hasattr(self.cli, 'voice_engine'):
                return True  # Skip if disabled
            
            return self.cli.voice_engine.load_model()
            
        except Exception as e:
            print(f"⚠️  Voice model initialization error: {e}")
            return False
    
    def _init_system_monitoring(self) -> bool:
        """Initialize system monitoring (optional component)"""
        try:
            if hasattr(self.cli, 'system_monitor'):
                # Pre-collect metrics in background
                metrics = self.cli.system_monitor.collect_metrics()
                return metrics is not None
            return True
            
        except Exception as e:
            print(f"⚠️  System monitoring initialization error: {e}")
            return False
    
    def _init_avatar_server(self) -> bool:
        """Initialize avatar server if requested (optional component)"""
        try:
            if not self.cli.auto_avatar:
                return True  # Skip if not requested
            
            # Import avatar functions
            try:
                from avatar_server import start_avatar_server, is_avatar_server_running
            except ImportError:
                print("⚠️  Avatar server not available")
                return False
            
            if not is_avatar_server_running():
                return start_avatar_server()
            else:
                return True  # Already running
                
        except Exception as e:
            print(f"⚠️  Avatar server initialization error: {e}")
            return False
    
    def _init_sound_manager(self) -> bool:
        """Initialize sound manager (optional component)"""
        try:
            if hasattr(self.cli, 'sound_manager'):
                # Pre-load sound effects
                if hasattr(self.cli.sound_manager, 'preload_sounds'):
                    self.cli.sound_manager.preload_sounds()
                return True
            return True
            
        except Exception as e:
            print(f"⚠️  Sound manager initialization error: {e}")
            return False
    
    def _init_websocket_bridge(self) -> bool:
        """Initialize WebSocket bridge (optional component)"""
        try:
            # WebSocket bridge is initialized on-demand, so just return success
            return True
            
        except Exception as e:
            print(f"⚠️  WebSocket bridge initialization error: {e}")
            return False
    
    def get_performance_summary(self) -> Dict[str, Any]:
        """Get detailed performance summary of initialization"""
        total_time = time.time() - self.start_time
        
        # Calculate component timing stats
        successful_components = [r for r in self.component_results.values() if r.status == ComponentStatus.READY]
        failed_components = [r for r in self.component_results.values() if r.status == ComponentStatus.FAILED]
        
        timing_stats = {
            'total_initialization_time': total_time,
            'successful_components': len(successful_components),
            'failed_components': len(failed_components),
            'success_rate': (len(successful_components) / len(self.component_results) * 100) if self.component_results else 0,
            'component_timings': {r.name: r.duration for r in self.component_results.values()},
            'slowest_component': max(successful_components, key=lambda r: r.duration).name if successful_components else None,
            'fastest_component': min(successful_components, key=lambda r: r.duration).name if successful_components else None,
        }
        
        return timing_stats
    
    def cleanup(self):
        """Clean up resources"""
        try:
            # Python 3.8+ has timeout parameter, older versions don't
            try:
                self.executor.shutdown(wait=True, timeout=5.0)
            except TypeError:
                # Fallback for older Python versions
                self.executor.shutdown(wait=True)
        except Exception as e:
            print(f"⚠️  Startup manager cleanup error: {e}")

# Progress display utilities
def create_progress_display_callback():
    """Create a callback function for displaying real-time progress"""
    progress_indicators = {
        ComponentStatus.PENDING: "⏳",
        ComponentStatus.LOADING: "🔄", 
        ComponentStatus.READY: "✅",
        ComponentStatus.FAILED: "❌",
        ComponentStatus.SKIPPED: "⏭️"
    }
    
    def display_progress(component: str, status: ComponentStatus, message: str):
        """Display progress update"""
        indicator = progress_indicators.get(status, "❓")
        timestamp = time.strftime("%H:%M:%S")
        print(f"[{timestamp}] {indicator} {component}: {message}")
    
    return display_progress

# Convenience function for CLI integration
def fast_initialize_m1k3(cli_instance, show_progress: bool = True) -> Tuple[bool, Dict[str, ComponentResult]]:
    """
    Convenience function to perform fast parallel initialization of M1K3.
    
    Args:
        cli_instance: The M1K3CLI instance to initialize
        show_progress: Whether to show real-time progress updates
    
    Returns:
        Tuple of (success, component_results)
        success: True if all essential components loaded successfully
        component_results: Dictionary of initialization results for all components
    """
    manager = FastStartupManager(cli_instance)
    
    if show_progress:
        manager.add_status_callback(create_progress_display_callback())
    
    try:
        # Start parallel initialization
        manager.initialize_parallel()
        
        # Wait for essential components first
        essential_success = manager.wait_for_essential(timeout=30.0)
        
        if essential_success:
            print("🎉 Essential components ready! M1K3 is now functional.")
            print("🔄 Optional components still loading in background...")
        
        # Wait for all components (with longer timeout)
        final_results = manager.wait_for_all(timeout=60.0)
        
        # Show performance summary
        perf_summary = manager.get_performance_summary()
        print(f"\n📊 Startup Performance Summary:")
        print(f"   Total time: {perf_summary['total_initialization_time']:.2f}s")
        print(f"   Success rate: {perf_summary['success_rate']:.1f}%")
        print(f"   Components: {perf_summary['successful_components']}/{len(final_results)} successful")
        
        return essential_success, final_results
        
    finally:
        manager.cleanup()

if __name__ == "__main__":
    # Demo mode - create a mock CLI instance for testing
    class MockCLI:
        def __init__(self):
            self.voice_enabled = True
            self.auto_avatar = False
    
    print("🧪 Testing FastStartupManager...")
    
    mock_cli = MockCLI()
    success, results = fast_initialize_m1k3(mock_cli, show_progress=True)
    
    print(f"\n✅ Test completed. Success: {success}")
    for name, result in results.items():
        print(f"   {name}: {result.status.value} ({result.duration:.2f}s)")