#!/usr/bin/env python3
"""
M1K3 Async Model Loader
Handles background model loading, preloading, and hot-swapping with progress tracking
"""

import threading
import queue
import time
from typing import Dict, Optional, Callable, Any, Tuple
from dataclasses import dataclass
from enum import Enum
import concurrent.futures
from pathlib import Path
import gc
import weakref

class ModelLoadStatus(Enum):
    """Status of model loading operations"""
    QUEUED = "queued"
    LOADING = "loading"
    LOADED = "loaded"
    FAILED = "failed"
    CANCELLED = "cancelled"

@dataclass
class ModelLoadRequest:
    """Request for model loading"""
    model_name: str
    priority: int = 0  # Higher = more priority
    callback: Optional[Callable] = None
    progress_callback: Optional[Callable] = None
    metadata: Dict[str, Any] = None
    
    def __post_init__(self):
        if self.metadata is None:
            self.metadata = {}

@dataclass
class ModelLoadResult:
    """Result of model loading operation"""
    model_name: str
    status: ModelLoadStatus
    model_object: Any = None
    tokenizer_object: Any = None
    error: Optional[Exception] = None
    load_time: float = 0.0
    memory_usage_mb: float = 0.0
    message: str = ""

class AsyncModelLoader:
    """
    Advanced asynchronous model loader with the following features:
    
    - Background loading queue with priority system
    - Progress callbacks and status tracking
    - Model preloading and caching
    - Memory management and cleanup
    - Hot-swapping support with context preservation
    - Concurrent loading of multiple models
    - Automatic retry on failures
    """
    
    def __init__(self, max_concurrent_loads: int = 2, cache_size: int = 3):
        # Threading infrastructure
        self.max_concurrent_loads = max_concurrent_loads
        self.executor = concurrent.futures.ThreadPoolExecutor(
            max_workers=max_concurrent_loads,
            thread_name_prefix="AsyncModelLoader"
        )
        
        # Request queue (priority queue)
        self.request_queue = queue.PriorityQueue()
        self.active_loads: Dict[str, concurrent.futures.Future] = {}
        
        # Model cache
        self.cache_size = cache_size
        self.model_cache: Dict[str, Tuple[Any, Any, float]] = {}  # model_name -> (model, tokenizer, load_time)
        self.cache_access_times: Dict[str, float] = {}  # For LRU eviction
        
        # Status tracking
        self.load_status: Dict[str, ModelLoadResult] = {}
        self.status_lock = threading.Lock()
        
        # Callbacks
        self.global_progress_callbacks: list[Callable] = []
        
        # Control flags
        self.running = True
        self.worker_thread = threading.Thread(target=self._worker_loop, daemon=True)
        self.worker_thread.start()
        
        # Weak references to AI engines for automatic updates
        self.ai_engine_refs: list[weakref.WeakValueDictionary] = []
        
    def register_ai_engine(self, ai_engine):
        """Register an AI engine for automatic model updates"""
        self.ai_engine_refs.append(weakref.ref(ai_engine))
    
    def add_global_progress_callback(self, callback: Callable[[str, ModelLoadStatus, float], None]):
        """Add global progress callback for all model loading operations"""
        self.global_progress_callbacks.append(callback)
    
    def load_model_async(self, 
                        model_name: str,
                        priority: int = 0,
                        callback: Optional[Callable[[ModelLoadResult], None]] = None,
                        progress_callback: Optional[Callable[[str, float], None]] = None) -> str:
        """
        Queue a model for asynchronous loading.
        
        Args:
            model_name: Name/path of the model to load
            priority: Priority (higher = loaded first)
            callback: Called when loading completes
            progress_callback: Called with progress updates (model_name, progress_0_to_1)
        
        Returns:
            Request ID for tracking
        """
        request_id = f"{model_name}_{time.time()}"
        
        request = ModelLoadRequest(
            model_name=model_name,
            priority=priority,
            callback=callback,
            progress_callback=progress_callback,
            metadata={'request_id': request_id}
        )
        
        # Add to queue (negative priority for max-heap behavior)
        self.request_queue.put((-priority, time.time(), request))
        
        # Initialize status
        with self.status_lock:
            self.load_status[model_name] = ModelLoadResult(
                model_name=model_name,
                status=ModelLoadStatus.QUEUED,
                message="Queued for loading"
            )
        
        self._notify_global_progress(model_name, ModelLoadStatus.QUEUED, 0.0)
        print(f"📝 Queued model for loading: {model_name} (priority: {priority})")
        
        return request_id
    
    def preload_models(self, model_names: list[str], priority: int = 10):
        """Preload multiple models in the background"""
        print(f"🔄 Preloading {len(model_names)} models...")
        
        for model_name in model_names:
            if model_name not in self.model_cache and model_name not in self.load_status:
                self.load_model_async(model_name, priority=priority)
    
    def get_model_sync(self, model_name: str, timeout: float = 30.0) -> Optional[ModelLoadResult]:
        """
        Get a model synchronously (blocks until loaded or timeout).
        Returns cached model if available, otherwise loads it.
        """
        # Check cache first
        if model_name in self.model_cache:
            model_obj, tokenizer_obj, load_time = self.model_cache[model_name]
            self.cache_access_times[model_name] = time.time()
            
            return ModelLoadResult(
                model_name=model_name,
                status=ModelLoadStatus.LOADED,
                model_object=model_obj,
                tokenizer_object=tokenizer_obj,
                load_time=load_time,
                message="Retrieved from cache"
            )
        
        # Check if already loading
        if model_name in self.active_loads:
            print(f"⏳ Waiting for {model_name} to finish loading...")
            try:
                future = self.active_loads[model_name]
                result = future.result(timeout=timeout)
                return result
            except concurrent.futures.TimeoutError:
                print(f"⏰ Timeout waiting for {model_name} after {timeout}s")
                return None
        
        # Start loading synchronously
        print(f"🔄 Loading {model_name} synchronously...")
        request_id = self.load_model_async(model_name, priority=100)  # High priority
        
        # Wait for completion
        start_time = time.time()
        while time.time() - start_time < timeout:
            with self.status_lock:
                result = self.load_status.get(model_name)
                if result and result.status in [ModelLoadStatus.LOADED, ModelLoadStatus.FAILED]:
                    return result
            
            time.sleep(0.1)
        
        print(f"⏰ Timeout loading {model_name} after {timeout}s")
        return None
    
    def get_load_status(self, model_name: str) -> Optional[ModelLoadResult]:
        """Get current loading status of a model"""
        with self.status_lock:
            return self.load_status.get(model_name)
    
    def get_cached_models(self) -> list[str]:
        """Get list of models currently in cache"""
        return list(self.model_cache.keys())
    
    def clear_cache(self, model_name: Optional[str] = None):
        """Clear model cache (specific model or all models)"""
        if model_name:
            if model_name in self.model_cache:
                self._cleanup_model_from_memory(model_name)
                del self.model_cache[model_name]
                self.cache_access_times.pop(model_name, None)
                print(f"🗑️  Cleared {model_name} from cache")
        else:
            for name in list(self.model_cache.keys()):
                self._cleanup_model_from_memory(name)
            self.model_cache.clear()
            self.cache_access_times.clear()
            print("🗑️  Cleared all models from cache")
    
    def get_cache_stats(self) -> Dict[str, Any]:
        """Get cache statistics"""
        cache_memory = 0
        for model_name in self.model_cache:
            try:
                # Estimate memory usage (rough approximation)
                model_obj, tokenizer_obj, _ = self.model_cache[model_name]
                if hasattr(model_obj, 'get_memory_footprint'):
                    cache_memory += model_obj.get_memory_footprint()
                else:
                    # Rough estimate based on parameters
                    cache_memory += 100  # MB default estimate
            except:
                pass
        
        return {
            'cached_models': len(self.model_cache),
            'cache_size_limit': self.cache_size,
            'estimated_memory_mb': cache_memory,
            'active_loads': len(self.active_loads),
            'queue_size': self.request_queue.qsize(),
            'models_in_cache': list(self.model_cache.keys())
        }
    
    def _worker_loop(self):
        """Main worker thread loop"""
        print("🔄 AsyncModelLoader worker thread started")
        
        while self.running:
            try:
                # Get next request from queue (with timeout to check running flag)
                try:
                    neg_priority, timestamp, request = self.request_queue.get(timeout=1.0)
                except queue.Empty:
                    continue
                
                # Check if we're at max concurrent loads
                if len(self.active_loads) >= self.max_concurrent_loads:
                    # Put request back and wait
                    self.request_queue.put((neg_priority, timestamp, request))
                    time.sleep(0.1)
                    continue
                
                # Start loading this model
                future = self.executor.submit(self._load_model, request)
                self.active_loads[request.model_name] = future
                
            except Exception as e:
                print(f"❌ Worker thread error: {e}")
                time.sleep(0.1)
    
    def _load_model(self, request: ModelLoadRequest) -> ModelLoadResult:
        """Load a single model"""
        start_time = time.time()
        model_name = request.model_name
        
        try:
            # Update status
            with self.status_lock:
                self.load_status[model_name] = ModelLoadResult(
                    model_name=model_name,
                    status=ModelLoadStatus.LOADING,
                    message="Loading model..."
                )
            
            self._notify_progress(request, 0.1, "Starting model load...")
            self._notify_global_progress(model_name, ModelLoadStatus.LOADING, 0.1)
            
            # Check if already cached
            if model_name in self.model_cache:
                load_time = time.time() - start_time
                self.cache_access_times[model_name] = time.time()
                
                model_obj, tokenizer_obj, original_load_time = self.model_cache[model_name]
                
                result = ModelLoadResult(
                    model_name=model_name,
                    status=ModelLoadStatus.LOADED,
                    model_object=model_obj,
                    tokenizer_object=tokenizer_obj,
                    load_time=load_time,
                    message="Retrieved from cache"
                )
                
                self._notify_progress(request, 1.0, "Retrieved from cache")
                self._complete_load(request, result)
                return result
            
            # Actually load the model
            result = self._perform_model_loading(request, start_time)
            
            # Cache the model if successful
            if result.status == ModelLoadStatus.LOADED:
                self._cache_model(model_name, result.model_object, result.tokenizer_object, result.load_time)
            
            self._complete_load(request, result)
            return result
            
        except Exception as e:
            load_time = time.time() - start_time
            error_msg = f"Model loading error: {e}"
            print(f"❌ {error_msg}")
            
            result = ModelLoadResult(
                model_name=model_name,
                status=ModelLoadStatus.FAILED,
                error=e,
                load_time=load_time,
                message=error_msg
            )
            
            self._complete_load(request, result)
            return result
        
        finally:
            # Remove from active loads
            self.active_loads.pop(model_name, None)
    
    def _perform_model_loading(self, request: ModelLoadRequest, start_time: float) -> ModelLoadResult:
        """Perform the actual model loading"""
        model_name = request.model_name
        
        self._notify_progress(request, 0.2, "Importing AI libraries...")
        
        # Import AI libraries
        try:
            from transformers import AutoTokenizer, AutoModelForCausalLM
            import torch
        except ImportError as e:
            raise Exception(f"Required AI libraries not available: {e}")
        
        self._notify_progress(request, 0.3, "Loading tokenizer...")
        
        # Load tokenizer
        try:
            tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=True)
            if tokenizer.pad_token is None:
                tokenizer.pad_token = tokenizer.eos_token
        except Exception as e:
            # Try without local_files_only
            try:
                print(f"📥 Tokenizer not cached, downloading for {model_name}...")
                tokenizer = AutoTokenizer.from_pretrained(model_name)
                if tokenizer.pad_token is None:
                    tokenizer.pad_token = tokenizer.eos_token
            except Exception as e:
                raise Exception(f"Failed to load tokenizer: {e}")
        
        self._notify_progress(request, 0.5, "Loading model weights...")
        
        # Load model
        load_kwargs = {
            "low_cpu_mem_usage": True,
            "device_map": "cpu"
        }
        
        try:
            # Try cached first
            load_kwargs["local_files_only"] = True
            if "gemma" in model_name.lower():
                load_kwargs["torch_dtype"] = torch.bfloat16
            else:
                load_kwargs["torch_dtype"] = torch.float32
            
            model = AutoModelForCausalLM.from_pretrained(model_name, **load_kwargs)
        except Exception as e:
            # Try downloading
            try:
                print(f"📥 Model not cached, downloading {model_name}...")
                load_kwargs["local_files_only"] = False
                model = AutoModelForCausalLM.from_pretrained(model_name, **load_kwargs)
            except Exception as e:
                raise Exception(f"Failed to load model: {e}")
        
        self._notify_progress(request, 0.8, "Optimizing model...")
        
        # Move to CPU and optimize
        model = model.to('cpu')
        
        # Fix generation config conflicts
        if hasattr(model, 'generation_config'):
            model.generation_config.do_sample = None
            model.generation_config.temperature = None
            model.generation_config.top_p = None
            model.generation_config.top_k = None
        
        load_time = time.time() - start_time
        
        self._notify_progress(request, 1.0, f"Model loaded successfully in {load_time:.2f}s")
        
        return ModelLoadResult(
            model_name=model_name,
            status=ModelLoadStatus.LOADED,
            model_object=model,
            tokenizer_object=tokenizer,
            load_time=load_time,
            message=f"Loaded successfully in {load_time:.2f}s"
        )
    
    def _cache_model(self, model_name: str, model_obj: Any, tokenizer_obj: Any, load_time: float):
        """Add model to cache with LRU eviction"""
        # Evict oldest model if cache is full
        if len(self.model_cache) >= self.cache_size:
            # Find least recently used model
            lru_model = min(self.cache_access_times.items(), key=lambda x: x[1])
            lru_model_name = lru_model[0]
            
            print(f"🗑️  Evicting {lru_model_name} from cache (LRU)")
            self._cleanup_model_from_memory(lru_model_name)
            del self.model_cache[lru_model_name]
            del self.cache_access_times[lru_model_name]
        
        # Add to cache
        self.model_cache[model_name] = (model_obj, tokenizer_obj, load_time)
        self.cache_access_times[model_name] = time.time()
        print(f"📦 Cached {model_name} (cache size: {len(self.model_cache)}/{self.cache_size})")
    
    def _cleanup_model_from_memory(self, model_name: str):
        """Clean up model from memory"""
        try:
            if model_name in self.model_cache:
                model_obj, tokenizer_obj, _ = self.model_cache[model_name]
                
                # Delete objects
                del model_obj
                del tokenizer_obj
                
                # Force garbage collection
                gc.collect()
                
                # Clear CUDA cache if available
                try:
                    import torch
                    if torch.cuda.is_available():
                        torch.cuda.empty_cache()
                except:
                    pass
                
        except Exception as e:
            print(f"⚠️  Error cleaning up {model_name}: {e}")
    
    def _complete_load(self, request: ModelLoadRequest, result: ModelLoadResult):
        """Complete a model loading operation"""
        # Update status
        with self.status_lock:
            self.load_status[request.model_name] = result
        
        # Notify callbacks
        if request.callback:
            try:
                request.callback(result)
            except Exception as e:
                print(f"⚠️  Load callback error: {e}")
        
        self._notify_global_progress(
            request.model_name, 
            result.status, 
            1.0 if result.status == ModelLoadStatus.LOADED else 0.0
        )
    
    def _notify_progress(self, request: ModelLoadRequest, progress: float, message: str):
        """Notify request-specific progress callback"""
        if request.progress_callback:
            try:
                request.progress_callback(request.model_name, progress, message)
            except Exception as e:
                print(f"⚠️  Progress callback error: {e}")
    
    def _notify_global_progress(self, model_name: str, status: ModelLoadStatus, progress: float):
        """Notify all global progress callbacks"""
        for callback in self.global_progress_callbacks:
            try:
                callback(model_name, status, progress)
            except Exception as e:
                print(f"⚠️  Global progress callback error: {e}")
    
    def shutdown(self):
        """Shutdown the async loader"""
        print("🛑 Shutting down AsyncModelLoader...")
        self.running = False
        
        # Cancel active loads
        for model_name, future in self.active_loads.items():
            future.cancel()
            print(f"❌ Cancelled loading of {model_name}")
        
        # Wait for worker thread
        if self.worker_thread.is_alive():
            self.worker_thread.join(timeout=5.0)
        
        # Shutdown executor
        try:
            self.executor.shutdown(wait=True, timeout=10.0)
        except TypeError:
            # Fallback for older Python versions
            self.executor.shutdown(wait=True)
        
        # Clear cache
        self.clear_cache()
        
        print("✅ AsyncModelLoader shutdown complete")

# Global instance for easy access
_global_async_loader: Optional[AsyncModelLoader] = None

def get_async_model_loader() -> AsyncModelLoader:
    """Get global async model loader instance"""
    global _global_async_loader
    if _global_async_loader is None:
        _global_async_loader = AsyncModelLoader()
    return _global_async_loader

def preload_recommended_models():
    """Preload recommended models in the background"""
    try:
        from local_model_manager import LocalModelManager
        manager = LocalModelManager()
        device = manager.analyze_device()
        
        if device.recommended_models:
            loader = get_async_model_loader()
            loader.preload_models(device.recommended_models[:2])  # Top 2 recommendations
            print(f"🔄 Preloading recommended models: {device.recommended_models[:2]}")
    except Exception as e:
        print(f"⚠️  Failed to preload recommended models: {e}")

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="M1K3 Async Model Loader Test")
    parser.add_argument("--model", default="microsoft/DialoGPT-small", help="Model to test loading")
    parser.add_argument("--preload", action="store_true", help="Test preloading recommended models")
    
    args = parser.parse_args()
    
    def progress_callback(model_name: str, status: ModelLoadStatus, progress: float):
        print(f"📊 {model_name}: {status.value} ({progress*100:.1f}%)")
    
    loader = AsyncModelLoader()
    loader.add_global_progress_callback(progress_callback)
    
    try:
        if args.preload:
            preload_recommended_models()
        
        print(f"🧪 Testing async loading of {args.model}")
        request_id = loader.load_model_async(args.model, priority=10)
        
        # Wait for completion
        result = loader.get_model_sync(args.model, timeout=60.0)
        
        if result and result.status == ModelLoadStatus.LOADED:
            print(f"✅ Successfully loaded {args.model} in {result.load_time:.2f}s")
            
            # Test cache retrieval
            cached_result = loader.get_model_sync(args.model)
            print(f"📦 Cache retrieval: {cached_result.message}")
        else:
            print(f"❌ Failed to load {args.model}")
        
        # Show cache stats
        stats = loader.get_cache_stats()
        print(f"📊 Cache stats: {stats}")
        
    finally:
        loader.shutdown()