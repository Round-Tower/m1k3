#!/usr/bin/env python3
"""
Lazy Loader System
Implements lazy loading for non-essential M1K3 components
"""

import threading
import weakref
import time
from typing import Dict, Any, Callable, Optional, Type
from dataclasses import dataclass
from enum import Enum
import logging

logger = logging.getLogger(__name__)


class ComponentPriority(Enum):
    """Component loading priorities"""
    CRITICAL = 1    # Must load immediately
    HIGH = 2        # Load during startup
    MEDIUM = 3      # Load on first access
    LOW = 4         # Load in background when idle


@dataclass
class LazyComponent:
    """Represents a lazily loaded component"""
    name: str
    loader_func: Callable[[], Any]
    priority: ComponentPriority
    dependencies: list = None
    loaded: bool = False
    loading: bool = False
    instance: Any = None
    load_time: float = 0.0
    access_count: int = 0
    last_access: float = 0.0
    
    def __post_init__(self):
        if self.dependencies is None:
            self.dependencies = []


class LazyLoader:
    """Manages lazy loading of components"""
    
    def __init__(self):
        self.components: Dict[str, LazyComponent] = {}
        self.loading_lock = threading.RLock()
        self.loading_threads: Dict[str, threading.Thread] = {}
        
        # Performance tracking
        self.total_saved_time = 0.0
        self.background_loads = 0
        
        # Access patterns for optimization
        self.access_patterns: Dict[str, list] = {}
    
    def register(self, name: str, loader_func: Callable[[], Any], 
                priority: ComponentPriority = ComponentPriority.MEDIUM,
                dependencies: list = None) -> None:
        """Register a component for lazy loading"""
        component = LazyComponent(
            name=name,
            loader_func=loader_func,
            priority=priority,
            dependencies=dependencies or []
        )
        
        self.components[name] = component
        self.access_patterns[name] = []
        
        logger.debug(f"📦 Registered lazy component: {name} (priority: {priority.name})")
        
        # If critical priority, load immediately
        if priority == ComponentPriority.CRITICAL:
            self._load_component_sync(component)
    
    def get(self, name: str, timeout: float = 30.0) -> Optional[Any]:
        """Get a component, loading it if necessary"""
        if name not in self.components:
            logger.warning(f"Unknown component: {name}")
            return None
        
        component = self.components[name]
        
        # Track access pattern
        current_time = time.time()
        component.access_count += 1
        component.last_access = current_time
        self.access_patterns[name].append(current_time)
        
        # Return if already loaded
        if component.loaded and component.instance is not None:
            return component.instance
        
        # Load synchronously if not loading
        if not component.loading:
            return self._load_component_sync(component)
        
        # Wait for ongoing load
        return self._wait_for_component(component, timeout)
    
    def get_async(self, name: str, callback: Callable[[Any], None] = None) -> bool:
        """Start async loading of a component"""
        if name not in self.components:
            logger.warning(f"Unknown component: {name}")
            return False
        
        component = self.components[name]
        
        # Return immediately if already loaded
        if component.loaded:
            if callback and component.instance:
                callback(component.instance)
            return True
        
        # Start async loading
        return self._load_component_async(component, callback)
    
    def preload(self, priority_threshold: ComponentPriority = ComponentPriority.HIGH) -> None:
        """Preload components up to a priority threshold"""
        logger.info(f"🚀 Preloading components (priority <= {priority_threshold.name})")
        
        for component in self.components.values():
            if (component.priority.value <= priority_threshold.value and 
                not component.loaded and not component.loading):
                
                self._load_component_async(component)
    
    def _load_component_sync(self, component: LazyComponent) -> Optional[Any]:
        """Load a component synchronously"""
        with self.loading_lock:
            # Double-check after acquiring lock
            if component.loaded:
                return component.instance
            
            if component.loading:
                return self._wait_for_component(component, 30.0)
            
            # Check dependencies
            if not self._check_dependencies(component):
                logger.warning(f"Dependencies not ready for {component.name}")
                return None
            
            # Load the component
            component.loading = True
            
        try:
            logger.debug(f"🔄 Loading {component.name} (sync)")
            start_time = time.time()
            
            instance = component.loader_func()
            load_time = time.time() - start_time
            
            with self.loading_lock:
                component.instance = instance
                component.loaded = True
                component.loading = False
                component.load_time = load_time
            
            logger.info(f"✅ Loaded {component.name} in {load_time:.2f}s")
            return instance
            
        except Exception as e:
            logger.error(f"❌ Failed to load {component.name}: {e}")
            with self.loading_lock:
                component.loading = False
            return None
    
    def _load_component_async(self, component: LazyComponent, 
                            callback: Callable[[Any], None] = None) -> bool:
        """Load a component asynchronously"""
        with self.loading_lock:
            if component.loaded:
                if callback and component.instance:
                    callback(component.instance)
                return True
            
            if component.loading:
                return True  # Already loading
            
            # Check dependencies
            if not self._check_dependencies(component):
                logger.debug(f"Dependencies not ready for {component.name}, deferring")
                return False
            
            component.loading = True
        
        # Start loading thread
        def load_thread():
            try:
                logger.debug(f"🔄 Loading {component.name} (async)")
                start_time = time.time()
                
                instance = component.loader_func()
                load_time = time.time() - start_time
                
                with self.loading_lock:
                    component.instance = instance
                    component.loaded = True
                    component.loading = False
                    component.load_time = load_time
                
                self.background_loads += 1
                logger.info(f"✅ Loaded {component.name} in {load_time:.2f}s (async)")
                
                # Execute callback
                if callback and instance:
                    try:
                        callback(instance)
                    except Exception as e:
                        logger.error(f"Callback error for {component.name}: {e}")
                
                # Check if this unblocks other components
                self._check_dependent_components(component.name)
                
            except Exception as e:
                logger.error(f"❌ Failed to load {component.name}: {e}")
                with self.loading_lock:
                    component.loading = False
            finally:
                # Clean up thread reference
                with self.loading_lock:
                    if component.name in self.loading_threads:
                        del self.loading_threads[component.name]
        
        thread = threading.Thread(target=load_thread, daemon=True)
        self.loading_threads[component.name] = thread
        thread.start()
        
        return True
    
    def _wait_for_component(self, component: LazyComponent, timeout: float) -> Optional[Any]:
        """Wait for a component to finish loading"""
        start_time = time.time()
        
        while component.loading and (time.time() - start_time) < timeout:
            time.sleep(0.1)
        
        if component.loaded:
            return component.instance
        
        logger.warning(f"Timeout waiting for {component.name}")
        return None
    
    def _check_dependencies(self, component: LazyComponent) -> bool:
        """Check if all dependencies are loaded"""
        for dep_name in component.dependencies:
            if dep_name not in self.components:
                logger.warning(f"Unknown dependency: {dep_name}")
                return False
            
            dep_component = self.components[dep_name]
            if not dep_component.loaded:
                return False
        
        return True
    
    def _check_dependent_components(self, loaded_component: str) -> None:
        """Check if any components are waiting for this dependency"""
        for component in self.components.values():
            if (loaded_component in component.dependencies and 
                not component.loaded and not component.loading):
                
                if self._check_dependencies(component):
                    logger.debug(f"Dependencies ready for {component.name}, starting load")
                    self._load_component_async(component)
    
    def is_loaded(self, name: str) -> bool:
        """Check if a component is loaded"""
        component = self.components.get(name)
        return component.loaded if component else False
    
    def is_loading(self, name: str) -> bool:
        """Check if a component is currently loading"""
        component = self.components.get(name)
        return component.loading if component else False
    
    def get_status(self) -> Dict[str, Any]:
        """Get loading status of all components"""
        status = {}
        
        for name, component in self.components.items():
            status[name] = {
                'loaded': component.loaded,
                'loading': component.loading,
                'load_time': component.load_time,
                'access_count': component.access_count,
                'priority': component.priority.name
            }
        
        return status
    
    def get_performance_stats(self) -> Dict[str, Any]:
        """Get performance statistics"""
        total_components = len(self.components)
        loaded_components = sum(1 for c in self.components.values() if c.loaded)
        loading_components = sum(1 for c in self.components.values() if c.loading)
        
        total_load_time = sum(c.load_time for c in self.components.values() if c.loaded)
        avg_load_time = total_load_time / loaded_components if loaded_components > 0 else 0
        
        return {
            'total_components': total_components,
            'loaded_components': loaded_components,
            'loading_components': loading_components,
            'background_loads': self.background_loads,
            'total_load_time': total_load_time,
            'average_load_time': avg_load_time,
            'estimated_saved_time': self.total_saved_time
        }
    
    def optimize_loading_order(self) -> List[str]:
        """Optimize loading order based on access patterns"""
        # Sort by access frequency and recency
        components_by_usage = []
        current_time = time.time()
        
        for name, component in self.components.items():
            if not component.loaded:
                # Calculate usage score
                recent_accesses = len([
                    t for t in self.access_patterns[name] 
                    if current_time - t < 300  # Last 5 minutes
                ])
                
                usage_score = component.access_count + (recent_accesses * 2)
                components_by_usage.append((usage_score, name))
        
        # Sort by usage score (descending)
        components_by_usage.sort(reverse=True)
        
        return [name for _, name in components_by_usage]
    
    def cleanup(self):
        """Cleanup resources"""
        logger.info("🧹 Cleaning up lazy loader")
        
        # Wait for ongoing loads to complete
        for thread in list(self.loading_threads.values()):
            thread.join(timeout=5.0)
        
        # Clear all data
        self.components.clear()
        self.loading_threads.clear()
        self.access_patterns.clear()


# Global lazy loader instance
_global_lazy_loader: Optional[LazyLoader] = None


def get_lazy_loader() -> LazyLoader:
    """Get or create global lazy loader"""
    global _global_lazy_loader
    if _global_lazy_loader is None:
        _global_lazy_loader = LazyLoader()
    return _global_lazy_loader


def lazy_import(name: str, loader_func: Callable[[], Any], 
               priority: ComponentPriority = ComponentPriority.MEDIUM,
               dependencies: list = None):
    """Decorator for lazy importing"""
    def decorator(func):
        # Register the component
        get_lazy_loader().register(name, loader_func, priority, dependencies)
        
        def wrapper(*args, **kwargs):
            # Get the component before calling the function
            component = get_lazy_loader().get(name)
            if component is None:
                raise ImportError(f"Failed to load component: {name}")
            
            return func(component, *args, **kwargs)
        
        return wrapper
    return decorator


def cleanup_lazy_loader():
    """Cleanup global lazy loader"""
    global _global_lazy_loader
    if _global_lazy_loader:
        _global_lazy_loader.cleanup()
        _global_lazy_loader = None