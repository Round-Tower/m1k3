#!/usr/bin/env python3
"""
M1K3 Model Hot Reload Manager
Monitors models directory and HuggingFace cache for new model downloads
Enables hot-swapping of models without restarting M1K3
"""

import os
import time
import threading
from pathlib import Path
from typing import Dict, Set, Optional, Callable
from dataclasses import dataclass
import hashlib
import json

@dataclass
class ModelChangeEvent:
    """Represents a model change event"""
    model_name: str
    change_type: str  # "added", "removed", "updated"
    model_path: str
    size_bytes: int
    timestamp: float

class ModelHotReloadManager:
    """Manages hot reloading of AI models"""
    
    def __init__(self, check_interval: float = 30.0, enable_auto_monitoring: bool = False):
        self.check_interval = check_interval
        self.enable_auto_monitoring = enable_auto_monitoring
        
        # Paths to monitor
        self.models_dir = Path("models")
        self.hf_cache_dir = Path.home() / '.cache' / 'huggingface' / 'hub'
        
        # Model tracking
        self.known_models: Dict[str, str] = {}  # model_name -> hash
        self.known_model_paths: Dict[str, Path] = {}  # model_name -> path
        
        # Event callbacks
        self.change_callbacks: list[Callable[[ModelChangeEvent], None]] = []
        
        # Monitoring thread
        self._monitoring_thread: Optional[threading.Thread] = None
        self._stop_monitoring = threading.Event()
        
        # Initialize
        self._discover_initial_models()
    
    def add_change_callback(self, callback: Callable[[ModelChangeEvent], None]):
        """Add a callback to be notified of model changes"""
        self.change_callbacks.append(callback)
    
    def remove_change_callback(self, callback: Callable[[ModelChangeEvent], None]):
        """Remove a change callback"""
        if callback in self.change_callbacks:
            self.change_callbacks.remove(callback)
    
    def start_monitoring(self):
        """Start background monitoring for model changes"""
        if self._monitoring_thread and self._monitoring_thread.is_alive():
            return
        
        self._stop_monitoring.clear()
        self._monitoring_thread = threading.Thread(
            target=self._monitoring_loop,
            daemon=True,
            name="ModelHotReloadMonitor"
        )
        self._monitoring_thread.start()
        print("🔄 Model hot reload monitoring started")
    
    def stop_monitoring(self):
        """Stop background monitoring"""
        if self._monitoring_thread and self._monitoring_thread.is_alive():
            self._stop_monitoring.set()
            self._monitoring_thread.join(timeout=5.0)
            print("⏹️  Model hot reload monitoring stopped")
    
    def check_for_changes(self) -> list[ModelChangeEvent]:
        """Manually check for model changes and return events"""
        events = []
        
        # Discover current models
        current_models = self._discover_models()
        
        # Find new models
        for model_name, model_hash in current_models.items():
            if model_name not in self.known_models:
                # New model
                model_path = self.known_model_paths.get(model_name, Path("unknown"))
                size_bytes = model_path.stat().st_size if model_path.exists() else 0
                
                event = ModelChangeEvent(
                    model_name=model_name,
                    change_type="added",
                    model_path=str(model_path),
                    size_bytes=size_bytes,
                    timestamp=time.time()
                )
                events.append(event)
                
            elif self.known_models[model_name] != model_hash:
                # Updated model
                model_path = self.known_model_paths.get(model_name, Path("unknown"))
                size_bytes = model_path.stat().st_size if model_path.exists() else 0
                
                event = ModelChangeEvent(
                    model_name=model_name,
                    change_type="updated",
                    model_path=str(model_path),
                    size_bytes=size_bytes,
                    timestamp=time.time()
                )
                events.append(event)
        
        # Find removed models
        for model_name in self.known_models.keys():
            if model_name not in current_models:
                # Removed model
                model_path = self.known_model_paths.get(model_name, Path("unknown"))
                
                event = ModelChangeEvent(
                    model_name=model_name,
                    change_type="removed",
                    model_path=str(model_path),
                    size_bytes=0,
                    timestamp=time.time()
                )
                events.append(event)
        
        # Update known models
        self.known_models = current_models.copy()
        
        # Notify callbacks
        for event in events:
            for callback in self.change_callbacks:
                try:
                    callback(event)
                except Exception as e:
                    print(f"⚠️  Model change callback error: {e}")
        
        return events
    
    def get_newly_available_models(self) -> list[str]:
        """Get list of newly available models since last check"""
        events = self.check_for_changes()
        return [event.model_name for event in events if event.change_type == "added"]
    
    def refresh_model_cache(self):
        """Force refresh of model cache"""
        print("🔄 Refreshing model cache...")
        self.known_models.clear()
        self.known_model_paths.clear()
        self._discover_initial_models()
        print(f"✅ Found {len(self.known_models)} models")
    
    def _discover_initial_models(self):
        """Discover models on initialization"""
        self.known_models = self._discover_models()
    
    def _discover_models(self) -> Dict[str, str]:
        """Discover all available models and return name -> hash mapping"""
        models = {}
        
        # Check HuggingFace cache
        if self.hf_cache_dir.exists():
            for model_folder in self.hf_cache_dir.glob("models--*"):
                model_name = model_folder.name.replace("models--", "").replace("--", "/")
                
                # Check if model is complete
                has_config = any(model_folder.rglob("config.json"))
                has_model = any(model_folder.rglob("*.safetensors")) or any(model_folder.rglob("pytorch_model.bin"))
                has_tokenizer = any(model_folder.rglob("tokenizer.json"))
                
                if has_config and has_model and has_tokenizer:
                    # Generate hash based on folder contents
                    model_hash = self._calculate_folder_hash(model_folder)
                    models[model_name] = model_hash
                    self.known_model_paths[model_name] = model_folder
        
        # Check local GGUF models
        if self.models_dir.exists():
            for gguf_file in self.models_dir.glob("*.gguf"):
                if gguf_file.stat().st_size > 1000:  # Not a dummy file
                    model_name = gguf_file.stem
                    model_hash = self._calculate_file_hash(gguf_file)
                    models[model_name] = model_hash
                    self.known_model_paths[model_name] = gguf_file
        
        return models
    
    def _calculate_folder_hash(self, folder_path: Path) -> str:
        """Calculate hash for a folder based on file sizes and modification times"""
        hash_md5 = hashlib.md5()
        
        # Include folder name
        hash_md5.update(folder_path.name.encode())
        
        # Sort files for consistent hashing
        files = sorted(folder_path.rglob('*'))
        
        for file_path in files:
            if file_path.is_file():
                try:
                    # Include file size and modification time
                    stat = file_path.stat()
                    hash_md5.update(f"{file_path.name}:{stat.st_size}:{stat.st_mtime}".encode())
                except:
                    continue
        
        return hash_md5.hexdigest()
    
    def _calculate_file_hash(self, file_path: Path) -> str:
        """Calculate hash for a single file"""
        hash_md5 = hashlib.md5()
        
        try:
            stat = file_path.stat()
            hash_md5.update(f"{file_path.name}:{stat.st_size}:{stat.st_mtime}".encode())
        except:
            hash_md5.update(file_path.name.encode())
        
        return hash_md5.hexdigest()
    
    def _monitoring_loop(self):
        """Background monitoring loop"""
        print(f"🔍 Monitoring for model changes every {self.check_interval} seconds...")
        
        while not self._stop_monitoring.wait(self.check_interval):
            try:
                events = self.check_for_changes()
                
                if events:
                    print(f"📦 Detected {len(events)} model changes:")
                    for event in events:
                        size_mb = event.size_bytes / (1024 * 1024) if event.size_bytes > 0 else 0
                        print(f"   {event.change_type.upper()}: {event.model_name} ({size_mb:.1f}MB)")
                
            except Exception as e:
                print(f"⚠️  Model monitoring error: {e}")
    
    def get_monitoring_status(self) -> Dict:
        """Get current monitoring status"""
        return {
            "monitoring_active": self._monitoring_thread and self._monitoring_thread.is_alive(),
            "check_interval": self.check_interval,
            "known_models_count": len(self.known_models),
            "callbacks_count": len(self.change_callbacks),
            "models_dir": str(self.models_dir),
            "hf_cache_dir": str(self.hf_cache_dir)
        }
    
    def export_model_inventory(self, output_path: Optional[Path] = None) -> str:
        """Export current model inventory to JSON"""
        if output_path is None:
            output_path = Path("model_inventory.json")
        
        inventory = {
            "timestamp": time.time(),
            "total_models": len(self.known_models),
            "models": {}
        }
        
        for model_name, model_hash in self.known_models.items():
            model_path = self.known_model_paths.get(model_name, Path("unknown"))
            size_bytes = model_path.stat().st_size if model_path.exists() else 0
            
            inventory["models"][model_name] = {
                "hash": model_hash,
                "path": str(model_path),
                "size_bytes": size_bytes,
                "size_mb": size_bytes / (1024 * 1024),
                "type": "gguf" if model_path.suffix == ".gguf" else "hf_transformers"
            }
        
        with open(output_path, 'w') as f:
            json.dump(inventory, f, indent=2)
        
        print(f"📄 Model inventory exported to: {output_path}")
        return str(output_path)

# Global instance for easy access
_global_hot_reload_manager: Optional[ModelHotReloadManager] = None

def get_hot_reload_manager() -> ModelHotReloadManager:
    """Get the global hot reload manager instance"""
    global _global_hot_reload_manager
    if _global_hot_reload_manager is None:
        _global_hot_reload_manager = ModelHotReloadManager()
    return _global_hot_reload_manager

def enable_auto_hot_reload(check_interval: float = 30.0):
    """Enable automatic hot reload monitoring"""
    manager = get_hot_reload_manager()
    manager.check_interval = check_interval
    manager.start_monitoring()

def disable_auto_hot_reload():
    """Disable automatic hot reload monitoring"""
    manager = get_hot_reload_manager()
    manager.stop_monitoring()

def check_for_new_models() -> list[str]:
    """Quick check for newly available models"""
    manager = get_hot_reload_manager()
    return manager.get_newly_available_models()

def refresh_models():
    """Force refresh of model cache"""
    manager = get_hot_reload_manager()
    manager.refresh_model_cache()

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="M1K3 Model Hot Reload Manager")
    parser.add_argument("--monitor", action="store_true", help="Start monitoring for model changes")
    parser.add_argument("--check", action="store_true", help="Check for model changes once")
    parser.add_argument("--refresh", action="store_true", help="Refresh model cache")
    parser.add_argument("--export", action="store_true", help="Export model inventory")
    parser.add_argument("--interval", type=float, default=30.0, help="Monitoring interval in seconds")
    
    args = parser.parse_args()
    
    manager = ModelHotReloadManager()
    
    if args.refresh:
        manager.refresh_model_cache()
    
    if args.export:
        manager.export_model_inventory()
    
    if args.check:
        events = manager.check_for_changes()
        if events:
            print(f"Found {len(events)} model changes:")
            for event in events:
                print(f"  {event.change_type.upper()}: {event.model_name}")
        else:
            print("No model changes detected")
    
    if args.monitor:
        manager.check_interval = args.interval
        manager.start_monitoring()
        
        try:
            print("Press Ctrl+C to stop monitoring...")
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\nStopping monitoring...")
            manager.stop_monitoring()