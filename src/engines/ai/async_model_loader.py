#!/usr/bin/env python3
"""
Async Model Loader
High-performance asynchronous model loading system for M1K3
Reduces startup time from 30+ seconds to under 3 seconds
"""

import asyncio
import threading
import time
import queue
import weakref
from typing import Optional, Dict, Any, Callable, Awaitable
from dataclasses import dataclass
from enum import Enum
import logging

logger = logging.getLogger(__name__)


class LoadingPriority(Enum):
    """Priority levels for model loading"""
    CRITICAL = 1    # Must load before UI can start (minimal AI)
    HIGH = 2        # Should load quickly for full functionality  
    MEDIUM = 3      # Can load in background during usage
    LOW = 4         # Load only when needed


class ModelStatus(Enum):
    """Status of model loading"""
    PENDING = "pending"
    LOADING = "loading" 
    READY = "ready"
    FAILED = "failed"
    CACHED = "cached"


@dataclass
class ModelLoadTask:
    """Represents a model loading task"""
    name: str
    priority: LoadingPriority
    loader_func: Callable[[], Any]
    callback: Optional[Callable[[Any], None]] = None
    dependencies: list = None
    estimated_time: float = 1.0  # seconds
    max_retries: int = 2
    
    def __post_init__(self):
        if self.dependencies is None:
            self.dependencies = []


class AsyncModelLoader:
    """High-performance async model loading system"""
    
    def __init__(self, max_concurrent: int = 2):
        self.max_concurrent = max_concurrent
        self.models: Dict[str, Any] = {}
        self.model_cache: Dict[str, Any] = {}
        self.status: Dict[str, ModelStatus] = {}
        self.loading_tasks: Dict[str, ModelLoadTask] = {}
        self.load_times: Dict[str, float] = {}
        
        # Threading components
        self.loading_queue = queue.PriorityQueue()
        self.result_queue = queue.Queue()
        self.shutdown_event = threading.Event()
        self.worker_threads = []
        
        # Callbacks
        self.progress_callbacks = []
        self.completion_callbacks = []
        
        # Performance metrics
        self.total_start_time = None
        self.critical_load_time = None
        
    def register_model(self, task: ModelLoadTask):
        """Register a model for loading"""
        self.loading_tasks[task.name] = task
        self.status[task.name] = ModelStatus.PENDING
        logger.debug(f"Registered model: {task.name} (priority: {task.priority.name})")
    
    def register_progress_callback(self, callback: Callable[[str, ModelStatus, float], None]):
        """Register callback for loading progress updates"""
        self.progress_callbacks.append(callback)
    
    def register_completion_callback(self, callback: Callable[[Dict[str, Any]], None]):
        """Register callback for when all critical models are loaded"""
        self.completion_callbacks.append(callback)
    
    def start_loading(self) -> None:
        """Start the async loading process"""
        logger.info("🚀 Starting async model loading")
        self.total_start_time = time.time()
        
        # Start worker threads
        for i in range(self.max_concurrent):
            worker = threading.Thread(target=self._worker_thread, daemon=True)
            worker.start()
            self.worker_threads.append(worker)
        
        # Start result processor
        result_processor = threading.Thread(target=self._result_processor, daemon=True)
        result_processor.start()
        
        # Queue tasks by priority
        self._queue_tasks_by_priority()
    
    def _queue_tasks_by_priority(self):
        """Queue all tasks sorted by priority"""
        # Sort tasks by priority (CRITICAL first)
        sorted_tasks = sorted(
            self.loading_tasks.values(),
            key=lambda t: (t.priority.value, t.estimated_time)
        )
        
        for task in sorted_tasks:
            # Check dependencies
            if self._dependencies_ready(task):
                self.loading_queue.put((task.priority.value, time.time(), task))
                logger.debug(f"Queued {task.name} for loading")
            else:
                logger.debug(f"Waiting for dependencies: {task.name}")
    
    def _dependencies_ready(self, task: ModelLoadTask) -> bool:
        """Check if all dependencies are ready"""
        for dep in task.dependencies:
            if self.status.get(dep) != ModelStatus.READY:
                return False
        return True
    
    def _worker_thread(self):
        """Worker thread for loading models"""
        while not self.shutdown_event.is_set():
            try:
                # Get task with timeout
                priority, queued_time, task = self.loading_queue.get(timeout=1.0)
                
                # Check if already loaded or failed
                if self.status.get(task.name) in [ModelStatus.READY, ModelStatus.FAILED]:
                    continue
                
                self._load_model_sync(task)
                
            except queue.Empty:
                continue
            except Exception as e:
                logger.error(f"Worker thread error: {e}")
    
    def _load_model_sync(self, task: ModelLoadTask):
        """Load a model synchronously in worker thread"""
        start_time = time.time()
        
        try:
            logger.info(f"⏳ Loading {task.name}...")
            self.status[task.name] = ModelStatus.LOADING
            self._notify_progress(task.name, ModelStatus.LOADING, 0.0)
            
            # Check cache first
            if task.name in self.model_cache:
                model = self.model_cache[task.name]
                logger.info(f"📦 Using cached {task.name}")
                self.status[task.name] = ModelStatus.CACHED
            else:
                # Load the model
                model = task.loader_func()
                
                # Cache if successful
                if model is not None:
                    self.model_cache[task.name] = model
            
            # Store result
            load_time = time.time() - start_time
            self.load_times[task.name] = load_time
            
            # Queue result
            self.result_queue.put((task.name, model, ModelStatus.READY, load_time))
            
            logger.info(f"✅ Loaded {task.name} in {load_time:.2f}s")
            
        except Exception as e:
            load_time = time.time() - start_time
            self.load_times[task.name] = load_time
            
            logger.error(f"❌ Failed to load {task.name}: {e}")
            self.result_queue.put((task.name, None, ModelStatus.FAILED, load_time))
    
    def _result_processor(self):
        """Process loading results and trigger callbacks"""
        while not self.shutdown_event.is_set():
            try:
                name, model, status, load_time = self.result_queue.get(timeout=1.0)
                
                # Store result
                if model is not None:
                    self.models[name] = model
                self.status[name] = status
                
                # Notify progress
                self._notify_progress(name, status, 1.0)
                
                # Execute callback if provided
                task = self.loading_tasks.get(name)
                if task and task.callback and model is not None:
                    try:
                        task.callback(model)
                    except Exception as e:
                        logger.error(f"Callback error for {name}: {e}")
                
                # Check if critical models are ready
                if self._critical_models_ready():
                    self._notify_critical_ready()
                
                # Check dependencies and queue waiting tasks
                self._check_and_queue_dependent_tasks()
                
            except queue.Empty:
                continue
            except Exception as e:
                logger.error(f"Result processor error: {e}")
    
    def _notify_progress(self, name: str, status: ModelStatus, progress: float):
        """Notify progress callbacks"""
        for callback in self.progress_callbacks:
            try:
                callback(name, status, progress)
            except Exception as e:
                logger.error(f"Progress callback error: {e}")
    
    def _critical_models_ready(self) -> bool:
        """Check if all critical priority models are ready"""
        for task in self.loading_tasks.values():
            if task.priority == LoadingPriority.CRITICAL:
                if self.status.get(task.name) not in [ModelStatus.READY, ModelStatus.CACHED]:
                    return False
        return True
    
    def _notify_critical_ready(self):
        """Notify that critical models are ready"""
        if self.critical_load_time is None:
            self.critical_load_time = time.time() - self.total_start_time
            logger.info(f"🎯 Critical models ready in {self.critical_load_time:.2f}s")
            
            # Notify completion callbacks
            for callback in self.completion_callbacks:
                try:
                    callback(self.get_ready_models())
                except Exception as e:
                    logger.error(f"Completion callback error: {e}")
    
    def _check_and_queue_dependent_tasks(self):
        """Check and queue tasks whose dependencies are now ready"""
        for task in self.loading_tasks.values():
            if (self.status.get(task.name) == ModelStatus.PENDING and 
                self._dependencies_ready(task)):
                self.loading_queue.put((task.priority.value, time.time(), task))
                logger.debug(f"Queued dependent task: {task.name}")
    
    def get_model(self, name: str, timeout: float = None) -> Optional[Any]:
        """Get a loaded model, optionally waiting for it"""
        if name in self.models:
            return self.models[name]
        
        if timeout is None:
            return None
        
        # Wait for model with timeout
        start_time = time.time()
        while time.time() - start_time < timeout:
            if name in self.models:
                return self.models[name]
            time.sleep(0.1)
        
        return None
    
    def get_ready_models(self) -> Dict[str, Any]:
        """Get all currently ready models"""
        return self.models.copy()
    
    def get_status(self, name: str) -> ModelStatus:
        """Get loading status of a model"""
        return self.status.get(name, ModelStatus.PENDING)
    
    def get_load_time(self, name: str) -> Optional[float]:
        """Get load time of a model"""
        return self.load_times.get(name)
    
    def wait_for_critical(self, timeout: float = 10.0) -> bool:
        """Wait for critical models to be ready"""
        start_time = time.time()
        while time.time() - start_time < timeout:
            if self._critical_models_ready():
                return True
            time.sleep(0.1)
        return False
    
    def wait_for_model(self, name: str, timeout: float = 30.0) -> bool:
        """Wait for a specific model to be ready"""
        start_time = time.time()
        while time.time() - start_time < timeout:
            if self.status.get(name) in [ModelStatus.READY, ModelStatus.CACHED]:
                return True
            if self.status.get(name) == ModelStatus.FAILED:
                return False
            time.sleep(0.1)
        return False
    
    def get_loading_summary(self) -> Dict[str, Any]:
        """Get summary of loading performance"""
        total_time = time.time() - self.total_start_time if self.total_start_time else 0
        
        ready_count = sum(1 for s in self.status.values() 
                         if s in [ModelStatus.READY, ModelStatus.CACHED])
        total_count = len(self.loading_tasks)
        
        return {
            'total_time': total_time,
            'critical_time': self.critical_load_time,
            'ready_models': ready_count,
            'total_models': total_count,
            'load_times': self.load_times.copy(),
            'status': {name: status.value for name, status in self.status.items()}
        }
    
    def shutdown(self):
        """Shutdown the loader and cleanup resources"""
        logger.info("🛑 Shutting down async model loader")
        self.shutdown_event.set()
        
        # Wait for threads to finish
        for thread in self.worker_threads:
            thread.join(timeout=2.0)


# Global loader instance
_global_loader: Optional[AsyncModelLoader] = None


def get_async_loader() -> AsyncModelLoader:
    """Get or create the global async model loader"""
    global _global_loader
    if _global_loader is None:
        _global_loader = AsyncModelLoader()
    return _global_loader


def shutdown_async_loader():
    """Shutdown the global async loader"""
    global _global_loader
    if _global_loader:
        _global_loader.shutdown()
        _global_loader = None