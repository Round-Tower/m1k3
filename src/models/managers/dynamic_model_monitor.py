#!/usr/bin/env python3
"""
M1K3 Dynamic Model Monitor
Real-time model discovery, health checking, and intelligent recommendations
"""

import os
import json
import time
import subprocess
import threading
from pathlib import Path
from typing import Dict, List, Optional, Any
from dataclasses import dataclass, asdict
from datetime import datetime
import psutil
import hashlib


@dataclass
class ModelHealth:
    """Model health status"""
    name: str
    status: str  # "healthy", "degraded", "failed", "unknown"
    last_check: float
    response_time_ms: Optional[float]
    memory_usage_mb: Optional[float]
    error_message: Optional[str]
    availability_score: float  # 0-1 score


@dataclass
class ModelMetrics:
    """Comprehensive model metrics"""
    name: str
    type: str  # "ollama", "huggingface", "gguf"
    size_mb: float
    ram_required_mb: int
    last_updated: float
    download_status: str
    compatibility_score: float
    performance_tier: str  # "ultra-fast", "fast", "balanced", "powerful"
    health: ModelHealth


class DynamicModelMonitor:
    """Enhanced model monitoring with real-time discovery and health checks"""
    
    def __init__(self, models_dir: Path = None):
        self.models_dir = models_dir or Path("models")
        self.models_dir.mkdir(exist_ok=True)
        
        self.monitor_data = {}
        self.last_scan = 0
        self.scan_interval = 30  # 30 seconds
        self.health_check_interval = 300  # 5 minutes
        
        # Performance tiers based on tokens/sec estimates
        self.performance_tiers = {
            "ultra-fast": {"min_speed": 50, "color": "🚀"},
            "fast": {"min_speed": 25, "color": "⚡"},
            "balanced": {"min_speed": 10, "color": "⚖️"},
            "powerful": {"min_speed": 0, "color": "🧠"}
        }
        
        self._start_background_monitor()
    
    def _start_background_monitor(self):
        """Start background monitoring thread"""
        self.shutdown_monitor = False
        
        def monitor_loop():
            while not self.shutdown_monitor:
                try:
                    if time.time() - self.last_scan > self.scan_interval:
                        # Use silent mode for background scans, no cleanup
                        self.scan_all_models(silent=True, cleanup=False)
                        self.last_scan = time.time()
                    
                    # Health checks less frequently (but not during background monitoring)
                    # Skip health checks in background to avoid console spam
                    
                    time.sleep(10)  # Check every 10 seconds
                except Exception as e:
                    # Silently log errors in background mode
                    time.sleep(30)  # Wait longer on error
        
        monitor_thread = threading.Thread(target=monitor_loop, daemon=True)
        monitor_thread.start()
    
    def shutdown(self):
        """Shutdown the background monitor"""
        self.shutdown_monitor = True
    
    def scan_all_models(self, silent: bool = False, cleanup: bool = False) -> Dict[str, ModelMetrics]:
        """Comprehensive model discovery and analysis"""
        if not silent:
            print("🔍 Scanning for models...")
        
        all_models = {}
        
        # 1. Scan Ollama models (highest priority)
        ollama_models = self._scan_ollama_models()
        all_models.update(ollama_models)
        
        # 2. Scan HuggingFace cache
        hf_models = self._scan_huggingface_models()
        all_models.update(hf_models)
        
        # 3. Scan local GGUF files
        gguf_models = self._scan_gguf_models()
        all_models.update(gguf_models)
        
        # 4. Scan for other model formats
        other_models = self._scan_other_model_formats()
        all_models.update(other_models)
        
        # 5. Clean up orphaned models (only when explicitly requested)
        if cleanup:
            self._cleanup_orphaned_models()
        
        self.monitor_data = all_models
        self._save_monitor_data()
        
        return all_models
    
    def _scan_ollama_models(self) -> Dict[str, ModelMetrics]:
        """Scan and analyze Ollama models with detailed metadata"""
        models = {}
        
        try:
            # Get model list
            result = subprocess.run(['ollama', 'list'], capture_output=True, text=True, check=True)
            lines = result.stdout.strip().split('\n')[1:]  # Skip header
            
            for line in lines:
                if not line.strip():
                    continue
                
                parts = line.split()
                if len(parts) >= 3:
                    model_name = parts[0]
                    model_id = parts[1]
                    size_str = parts[2]
                    
                    # Parse size
                    size_mb = self._parse_size_string(size_str)
                    
                    # Get detailed metadata if available
                    metadata_path = self.models_dir / f"{model_name.replace(':', '_')}.json"
                    metadata = {}
                    if metadata_path.exists():
                        with open(metadata_path) as f:
                            metadata = json.load(f)
                    
                    # Calculate compatibility score
                    compatibility = self._calculate_compatibility_score(model_name, size_mb, "ollama")
                    
                    # Determine performance tier
                    performance_tier = self._determine_performance_tier(model_name, size_mb)
                    
                    # Create health status
                    health = ModelHealth(
                        name=model_name,
                        status="unknown",
                        last_check=0,
                        response_time_ms=None,
                        memory_usage_mb=None,
                        error_message=None,
                        availability_score=0.9  # Ollama models typically very reliable
                    )
                    
                    models[model_name] = ModelMetrics(
                        name=model_name,
                        type="ollama",
                        size_mb=size_mb,
                        ram_required_mb=int(size_mb * 1.2),  # 20% overhead
                        last_updated=time.time(),
                        download_status="ready",
                        compatibility_score=compatibility,
                        performance_tier=performance_tier,
                        health=health
                    )
                    
        except subprocess.CalledProcessError:
            print("🔍 Ollama not available - skipping ollama scan")
        except FileNotFoundError:
            print("🔍 Ollama not installed - skipping ollama scan")
        
        return models
    
    def _scan_huggingface_models(self) -> Dict[str, ModelMetrics]:
        """Scan HuggingFace cache for complete models"""
        models = {}
        hf_cache_dir = Path.home() / '.cache' / 'huggingface' / 'hub'
        
        if not hf_cache_dir.exists():
            return models
        
        for model_folder in hf_cache_dir.glob("models--*"):
            model_name = model_folder.name.replace("models--", "").replace("--", "/")
            
            # Check completeness
            has_config = any(model_folder.rglob("config.json"))
            has_model = any(model_folder.rglob("*.safetensors")) or any(model_folder.rglob("pytorch_model.bin"))
            has_tokenizer = any(model_folder.rglob("tokenizer.json"))
            
            if has_config and has_model and has_tokenizer:
                # Calculate size
                total_size = sum(f.stat().st_size for f in model_folder.rglob('*') if f.is_file())
                size_mb = total_size / (1024 * 1024)
                
                compatibility = self._calculate_compatibility_score(model_name, size_mb, "huggingface")
                performance_tier = self._determine_performance_tier(model_name, size_mb)
                
                health = ModelHealth(
                    name=model_name,
                    status="unknown", 
                    last_check=0,
                    response_time_ms=None,
                    memory_usage_mb=None,
                    error_message=None,
                    availability_score=0.7  # HF models less reliable due to tokenizer issues
                )
                
                models[model_name] = ModelMetrics(
                    name=model_name,
                    type="huggingface",
                    size_mb=round(size_mb, 1),
                    ram_required_mb=int(size_mb * 1.5),  # 50% overhead
                    last_updated=time.time(),
                    download_status="ready",
                    compatibility_score=compatibility,
                    performance_tier=performance_tier,
                    health=health
                )
        
        return models
    
    def _scan_gguf_models(self) -> Dict[str, ModelMetrics]:
        """Scan for local GGUF files recursively"""
        models = {}
        
        # Search patterns for model files
        search_patterns = [
            "*.gguf",
            "**/*.gguf",  # Recursive search
        ]
        
        # Additional search locations
        search_dirs = [
            self.models_dir,
            Path("."),  # Project root
            Path("src/models"),
            Path("cache/models") if Path("cache/models").exists() else None,
        ]
        
        # Remove None values
        search_dirs = [d for d in search_dirs if d and d.exists()]
        
        for search_dir in search_dirs:
            for pattern in search_patterns:
                for gguf_file in search_dir.glob(pattern):
                    if gguf_file.stat().st_size > 1000:  # Not a dummy file
                        size_mb = gguf_file.stat().st_size / (1024 * 1024)
                        model_name = gguf_file.stem
                        
                        # Avoid duplicates by using absolute path as key check
                        abs_path = str(gguf_file.resolve())
                        if any(abs_path == str(Path(existing_model.name).resolve()) for existing_model in models.values() if hasattr(existing_model, 'name')):
                            continue
                        
                        compatibility = self._calculate_compatibility_score(model_name, size_mb, "gguf")
                        performance_tier = self._determine_performance_tier(model_name, size_mb)
                        
                        health = ModelHealth(
                            name=model_name,
                            status="unknown", 
                            last_check=0,
                            response_time_ms=None,
                            memory_usage_mb=None,
                            error_message=None,
                            availability_score=0.8  # GGUF models generally reliable
                        )
                
                models[model_name] = ModelMetrics(
                    name=model_name,
                    type="gguf",
                    size_mb=round(size_mb, 1),
                    ram_required_mb=int(size_mb * 1.2),  # 20% overhead
                    last_updated=time.time(),
                    download_status="ready",
                    compatibility_score=compatibility,
                    performance_tier=performance_tier,
                    health=health
                )
        
        return models
    
    def _parse_size_string(self, size_str: str) -> float:
        """Parse size strings like '270 MB', '2.0 GB'"""
        try:
            parts = size_str.split()
            if len(parts) >= 2:
                size_val = float(parts[0])
                size_unit = parts[1].upper()
                
                if size_unit == 'GB':
                    return size_val * 1024
                elif size_unit == 'MB':
                    return size_val
                elif size_unit == 'KB':
                    return size_val / 1024
        except (ValueError, IndexError):
            pass
        return 100.0  # Default fallback
    
    def _calculate_compatibility_score(self, model_name: str, size_mb: float, model_type: str) -> float:
        """Calculate compatibility score 0-1 based on various factors"""
        score = 0.5  # Base score
        
        # Model type bonus
        if model_type == "ollama":
            score += 0.3  # Ollama models are generally more compatible
        elif model_type == "gguf":
            score += 0.2  # GGUF models are efficient
        
        # Size penalty/bonus
        if size_mb < 1000:  # Under 1GB
            score += 0.2
        elif size_mb > 5000:  # Over 5GB
            score -= 0.2
        
        # Known good models
        good_models = ["smollm2", "tinyllama", "distilgpt2", "dialogpt-small"]
        if any(good in model_name.lower() for good in good_models):
            score += 0.2
        
        # Known problematic models
        problem_models = ["phi-3", "gemma"]
        if any(prob in model_name.lower() for prob in problem_models):
            score -= 0.3
        
        return max(0.0, min(1.0, score))
    
    def _determine_performance_tier(self, model_name: str, size_mb: float) -> str:
        """Determine performance tier based on model characteristics"""
        name_lower = model_name.lower()
        
        if "smollm2:135m" in name_lower or (size_mb < 200 and "smollm" in name_lower):
            return "ultra-fast"
        elif size_mb < 500:
            return "fast"
        elif size_mb < 2000:
            return "balanced"
        else:
            return "powerful"
    
    def run_health_checks(self):
        """Run health checks on all models"""
        print("🏥 Running health checks...")
        
        for model_name, metrics in self.monitor_data.items():
            if metrics.type == "ollama":
                health = self._check_ollama_health(model_name)
            elif metrics.type == "huggingface":
                health = self._check_huggingface_health(model_name)
            else:
                health = self._basic_health_check(model_name)
            
            metrics.health = health
        
        self._save_monitor_data()
    
    def _check_ollama_health(self, model_name: str) -> ModelHealth:
        """Check health of Ollama model"""
        start_time = time.time()
        
        try:
            # Quick test with ollama
            result = subprocess.run(
                ['ollama', 'run', model_name, 'Hi'], 
                capture_output=True, 
                text=True, 
                timeout=30,
                check=True
            )
            
            response_time = (time.time() - start_time) * 1000
            
            return ModelHealth(
                name=model_name,
                status="healthy",
                last_check=time.time(),
                response_time_ms=response_time,
                memory_usage_mb=None,
                error_message=None,
                availability_score=0.95
            )
            
        except subprocess.TimeoutExpired:
            return ModelHealth(
                name=model_name,
                status="degraded",
                last_check=time.time(),
                response_time_ms=None,
                memory_usage_mb=None,
                error_message="Timeout on health check",
                availability_score=0.5
            )
        except Exception as e:
            return ModelHealth(
                name=model_name,
                status="failed",
                last_check=time.time(),
                response_time_ms=None,
                memory_usage_mb=None,
                error_message=str(e),
                availability_score=0.1
            )
    
    def _check_huggingface_health(self, model_name: str) -> ModelHealth:
        """Check health of HuggingFace model"""
        try:
            # Basic tokenizer test
            from transformers import AutoTokenizer
            tokenizer = AutoTokenizer.from_pretrained(model_name)
            
            return ModelHealth(
                name=model_name,
                status="healthy",
                last_check=time.time(),
                response_time_ms=None,
                memory_usage_mb=None,
                error_message=None,
                availability_score=0.8
            )
        except Exception as e:
            return ModelHealth(
                name=model_name,
                status="failed",
                last_check=time.time(),
                response_time_ms=None,
                memory_usage_mb=None,
                error_message=str(e),
                availability_score=0.2
            )
    
    def _basic_health_check(self, model_name: str) -> ModelHealth:
        """Basic health check for other model types"""
        return ModelHealth(
            name=model_name,
            status="unknown",
            last_check=time.time(),
            response_time_ms=None,
            memory_usage_mb=None,
            error_message=None,
            availability_score=0.6
        )
    
    def _scan_other_model_formats(self) -> Dict[str, ModelMetrics]:
        """Scan for additional model formats (.bin, .safetensors, etc.)"""
        models = {}
        
        # Model file patterns to search for
        model_patterns = [
            "*.bin",
            "*.safetensors", 
            "*.pt",
            "*.pth",
            "*.pkl",
            "*.pickle",
            "**/*.bin",        # Recursive search
            "**/*.safetensors",
            "**/*.pt",
            "**/*.pth",
        ]
        
        # Additional search locations
        search_dirs = [
            self.models_dir,
            Path("."),  # Project root
            Path("src/models"),
            Path("cache/models") if Path("cache/models").exists() else None,
            Path("weights") if Path("weights").exists() else None,
            Path("checkpoints") if Path("checkpoints").exists() else None,
        ]
        
        # Remove None values and non-existent dirs
        search_dirs = [d for d in search_dirs if d and d.exists()]
        
        for search_dir in search_dirs:
            for pattern in model_patterns:
                try:
                    for model_file in search_dir.glob(pattern):
                        # Skip very small files (likely config or metadata)
                        if model_file.stat().st_size < 1024 * 100:  # Less than 100KB
                            continue
                        
                        # Skip files in hidden directories or cache subdirs we already scanned
                        if any(part.startswith('.') for part in model_file.parts):
                            continue
                        
                        # Skip if filename suggests it's not a main model file
                        skip_patterns = ['config', 'tokenizer', 'vocab', 'special_tokens', 'added_tokens']
                        if any(skip_pattern in model_file.name.lower() for skip_pattern in skip_patterns):
                            continue
                        
                        size_mb = model_file.stat().st_size / (1024 * 1024)
                        model_name = f"{model_file.stem}_{model_file.suffix[1:]}"  # e.g., "model_bin"
                        
                        # Avoid duplicates
                        if model_name in models:
                            continue
                        
                        # Determine model type based on file extension
                        model_type = {
                            '.bin': 'pytorch',
                            '.safetensors': 'safetensors',
                            '.pt': 'pytorch',
                            '.pth': 'pytorch', 
                            '.pkl': 'pickle',
                            '.pickle': 'pickle'
                        }.get(model_file.suffix, 'other')
                        
                        compatibility = self._calculate_compatibility_score(model_name, size_mb, model_type)
                        performance_tier = self._determine_performance_tier(model_name, size_mb)
                        
                        health = ModelHealth(
                            name=model_name,
                            status="unknown",
                            last_check=0,
                            response_time_ms=None,
                            memory_usage_mb=None, 
                            error_message=None,
                            availability_score=0.6  # Lower score for non-standard formats
                        )
                        
                        models[model_name] = ModelMetrics(
                            name=model_name,
                            type=model_type,
                            size_mb=round(size_mb, 1),
                            ram_required_mb=int(size_mb * 1.5),  # Higher overhead for other formats
                            last_updated=time.time(),
                            download_status="ready",
                            compatibility_score=compatibility,
                            performance_tier=performance_tier,
                            health=health
                        )
                except Exception as e:
                    # Skip directories that cause permission errors
                    continue
        
        return models
    
    def _cleanup_orphaned_models(self):
        """Clean up broken/incomplete model downloads"""
        cleanup_candidates = []
        
        # Check for incomplete HF downloads
        hf_cache_dir = Path.home() / '.cache' / 'huggingface' / 'hub'
        if hf_cache_dir.exists():
            for model_folder in hf_cache_dir.glob("models--*"):
                model_name = model_folder.name.replace("models--", "").replace("--", "/")
                
                has_config = any(model_folder.rglob("config.json"))
                has_model = any(model_folder.rglob("*.safetensors")) or any(model_folder.rglob("pytorch_model.bin"))
                
                if has_config and not has_model:
                    cleanup_candidates.append((model_name, str(model_folder), "incomplete_download"))
        
        if cleanup_candidates:
            print(f"\n🧹 Found {len(cleanup_candidates)} cleanup candidates:")
            for name, path, reason in cleanup_candidates:
                print(f"  - {name}: {reason}")
    
    def _save_monitor_data(self):
        """Save monitoring data to file"""
        monitor_file = self.models_dir / "monitor_data.json"
        
        # Convert dataclasses to dict for JSON serialization
        json_data = {}
        for name, metrics in self.monitor_data.items():
            json_data[name] = {
                **asdict(metrics),
                'health': asdict(metrics.health)
            }
        
        with open(monitor_file, 'w') as f:
            json.dump({
                'last_update': time.time(),
                'scan_count': len(json_data),
                'models': json_data
            }, f, indent=2)
    
    def get_recommendations(self) -> List[str]:
        """Get intelligent model recommendations"""
        if not self.monitor_data:
            self.scan_all_models()
        
        # Get system RAM
        available_ram_gb = psutil.virtual_memory().available / (1024**3)
        
        recommendations = []
        
        # Filter out broken models and respect RAM limits
        viable_models = []
        for model_name, metrics in self.monitor_data.items():
            ram_required_gb = metrics.ram_required_mb / 1024
            
            # Skip broken models
            if metrics.health.status == "failed" or metrics.download_status != "ready":
                continue
            
            # Skip models that require too much RAM
            if ram_required_gb > available_ram_gb:
                continue
            
            viable_models.append((model_name, metrics))
        
        # Sort by compatibility score and performance
        sorted_models = sorted(
            viable_models,
            key=lambda x: (x[1].compatibility_score, -x[1].size_mb),
            reverse=True
        )
        
        for model_name, metrics in sorted_models[:5]:
            recommendations.append(model_name)
        
        return recommendations
    
    def display_status_dashboard(self):
        """Display comprehensive status dashboard"""
        if not self.monitor_data:
            self.scan_all_models()
        
        print("=" * 80)
        print("🖥️  M1K3 DYNAMIC MODEL MONITOR DASHBOARD")
        print("=" * 80)
        
        # System info
        memory = psutil.virtual_memory()
        print(f"💻 System RAM: {memory.available / (1024**3):.1f}GB available / {memory.total / (1024**3):.1f}GB total")
        print(f"📊 Models Found: {len(self.monitor_data)}")
        print(f"🕐 Last Scan: {datetime.fromtimestamp(self.last_scan).strftime('%H:%M:%S')}")
        
        if not self.monitor_data:
            print("\n❌ No models found")
            return
        
        # Group by type
        by_type = {}
        for name, metrics in self.monitor_data.items():
            model_type = metrics.type
            if model_type not in by_type:
                by_type[model_type] = []
            by_type[model_type].append((name, metrics))
        
        # Display by type
        for model_type, models in by_type.items():
            models.sort(key=lambda x: x[1].compatibility_score, reverse=True)
            
            type_emoji = {"ollama": "🦙", "huggingface": "🤗", "gguf": "🔧"}
            print(f"\n{type_emoji.get(model_type, '📦')} {model_type.upper()} MODELS:")
            
            for model_name, metrics in models:
                tier_emoji = self.performance_tiers[metrics.performance_tier]["color"]
                
                # Health status
                health_emoji = {
                    "healthy": "💚",
                    "degraded": "💛", 
                    "failed": "❤️",
                    "unknown": "⚪"
                }.get(metrics.health.status, "⚪")
                
                # Compatibility bar
                compat_bar = "█" * int(metrics.compatibility_score * 10) + "░" * (10 - int(metrics.compatibility_score * 10))
                
                print(f"  {health_emoji} {tier_emoji} {model_name}")
                print(f"    💾 {metrics.size_mb:.0f}MB | 🧠 {metrics.ram_required_mb//1024:.1f}GB | 🎯 {compat_bar} {metrics.compatibility_score:.1f}")
                
                if metrics.health.error_message:
                    print(f"    ❌ {metrics.health.error_message}")
        
        # Recommendations
        recommendations = self.get_recommendations()
        if recommendations:
            print(f"\n🎯 TOP RECOMMENDATIONS:")
            for i, model_name in enumerate(recommendations[:3], 1):
                metrics = self.monitor_data[model_name]
                tier_emoji = self.performance_tiers[metrics.performance_tier]["color"]
                print(f"  {i}. {tier_emoji} {model_name} ({metrics.type})")
        
        print(f"\n💡 Monitor updates every {self.scan_interval}s | Health checks every {self.health_check_interval//60}min")


def main():
    """Main interface for dynamic model monitoring"""
    monitor = DynamicModelMonitor()
    
    print("🚀 M1K3 Dynamic Model Monitor")
    print("Starting background monitoring...")
    
    # Initial scan
    monitor.scan_all_models()
    
    # Display dashboard
    monitor.display_status_dashboard()
    
    print(f"\n🔄 Background monitoring active")
    print("📊 Use monitor.display_status_dashboard() to refresh")
    print("🏥 Use monitor.run_health_checks() to force health check")
    
    return monitor


if __name__ == "__main__":
    main()