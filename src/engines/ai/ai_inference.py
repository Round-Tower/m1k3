#!/usr/bin/env python3
"""
M1K3 Local AI Inference Module
Integrates with LocalModelManager for cached model access
"""

import os
import sys
import time
import json
import subprocess
from pathlib import Path
from typing import Generator, List, Dict, Optional
from dataclasses import dataclass
import threading
import queue

# Model transparency integration
try:
    from .model_transparency import transparency_engine, log_model_decision, TransparencyLevel
    TRANSPARENCY_AVAILABLE = True
except ImportError:
    TRANSPARENCY_AVAILABLE = False

# Enable HuggingFace tokenizers parallelism for better performance
os.environ['TOKENIZERS_PARALLELISM'] = 'true'

# Import SmolLM2 engine and logging
try:
    from .smollm_engine import SmolLMEngine
    from model_tiers import ModelTierManager, DeviceTier
    from ...utils.logging.prompt_logger import get_prompt_logger
    SMOLLM_ENGINE_AVAILABLE = True
    print("🤖 SmolLM2 engine available")
except ImportError as e:
    SMOLLM_ENGINE_AVAILABLE = False
    print(f"⚠️ SmolLM2 engine not available: {e}")

# Import local model manager
try:
    from .local_model_manager import LocalModelManager
    LOCAL_MODEL_MANAGER_AVAILABLE = True
except ImportError:
    LOCAL_MODEL_MANAGER_AVAILABLE = False
    print("⚠️  LocalModelManager not available")

# Import new template and formatting systems
try:
    from ...models.managers.model_template_manager import ModelTemplateManager, format_conversation_for_model
    from ...utils.response_formatter import ResponseFormatter, format_ai_response
    TEMPLATE_SYSTEM_AVAILABLE = True
    print("✅ Template and formatting systems available")
except ImportError as e:
    TEMPLATE_SYSTEM_AVAILABLE = False
    print(f"⚠️  Template system not available: {e}")

# Import order: prioritize HuggingFace Transformers for better x86_64 compatibility
try:
    from transformers import AutoTokenizer, AutoModelForCausalLM as HFAutoModel
    import torch
    TRANSFORMERS_AVAILABLE = True
    print("✅ HuggingFace Transformers available")
except ImportError:
    TRANSFORMERS_AVAILABLE = False
    print("❌ HuggingFace Transformers not available")

try:
    from ctransformers import AutoModelForCausalLM
    CTRANSFORMERS_AVAILABLE = True
    print("✅ ctransformers available") 
except ImportError:
    CTRANSFORMERS_AVAILABLE = False
    print("❌ ctransformers not available")

if not CTRANSFORMERS_AVAILABLE and not TRANSFORMERS_AVAILABLE:
    print("❌ No AI libraries available. Run: pip install transformers torch")
    raise ImportError("No AI backends available")

@dataclass
class ConversationContext:
    messages: List[Dict[str, str]] = None
    max_tokens: int = 2048
    current_tokens: int = 0
    
    def __post_init__(self):
        if self.messages is None:
            self.messages = []
    
    def add_message(self, role: str, content: str):
        """Add a message to the conversation context"""
        self.messages.append({"role": role, "content": content})
        # Rough token estimation (4 chars per token)
        self.current_tokens += len(content) // 4
        
    def should_trim(self) -> bool:
        """Check if context needs trimming"""
        return self.current_tokens > self.max_tokens * 0.8
        
    def trim_context(self):
        """Trim older messages to stay within token limit"""
        while len(self.messages) > 2 and self.should_trim():
            removed = self.messages.pop(1)  # Keep system message at index 0
            self.current_tokens -= len(removed["content"]) // 4

class LocalAIEngine:
    def __init__(self, model_path: str = None, auto_load: bool = False):
        self.model_path = model_path or self._get_default_model_path()
        self.model = None
        self.tokenizer = None
        self.context = ConversationContext()
        self.loading = False
        
        # Initialize model manager
        self.model_manager = None
        if LOCAL_MODEL_MANAGER_AVAILABLE:
            try:
                self.model_manager = LocalModelManager()
                print(f"📦 Found {len(self.model_manager.available_models)} cached models")
            except Exception as e:
                print(f"⚠️  Model manager initialization failed: {e}")
        
        # Initialize template and formatting systems
        self.template_manager = None
        self.response_formatter = None
        if TEMPLATE_SYSTEM_AVAILABLE:
            try:
                self.template_manager = ModelTemplateManager()
                self.response_formatter = ResponseFormatter()
                print("🎨 Template and formatting systems initialized")
            except Exception as e:
                print(f"⚠️  Template system initialization failed: {e}")
        
        # Initialize cached adaptive configuration (for performance)
        self.adaptive_config = None
        try:
            from enhanced_adaptive_model_config import EnhancedAdaptiveModelConfig
            self.adaptive_config = EnhancedAdaptiveModelConfig(enable_websocket=False)
        except ImportError:
            pass  # Will fall back to runtime creation
        
        # Initialize model metadata and logging
        self.model_metadata = None
        self._current_model_name = None
        self.models_dir = Path("models")
        
        # Initialize prompt logger
        if SMOLLM_ENGINE_AVAILABLE:
            self.logger = get_prompt_logger()
        else:
            self.logger = None
        self.models_dir.mkdir(exist_ok=True)
        
        # Initialize SmolLM2 engine and tier management
        self.smollm_engine = None
        self.tier_manager = None
        self.device_tier = None
        if SMOLLM_ENGINE_AVAILABLE:
            try:
                self.tier_manager = ModelTierManager()
                device_capability, _ = self.tier_manager.recommend_models_for_device()
                self.device_tier = device_capability.tier
                print(f"🎯 Device tier detected: {self.device_tier.value}")
                
                # Initialize SmolLM2 engine for all tiers (can coexist with larger models)
                self.smollm_engine = SmolLMEngine("smollm_config.json")
                print(f"🤖 SmolLM2 engine initialized for {self.device_tier.value} tier")
                
                # Load metadata for SmolLM2
                if hasattr(self.smollm_engine, 'backend') and self.smollm_engine.backend:
                    model_name = "smollm2:135m"  # Default SmolLM2 model name
                    self.model_metadata = self._load_model_metadata(model_name)
                    if self.model_metadata:
                        print(f"📋 Loaded SmolLM2 metadata: {model_name}")
                    else:
                        print(f"⚠️ Could not load SmolLM2 metadata for {model_name}")
            except Exception as e:
                print(f"⚠️ SmolLM2 initialization failed: {e}")
                self.smollm_engine = None
        
        # Choose backend: prefer HuggingFace for x86_64 compatibility
        self.use_transformers = TRANSFORMERS_AVAILABLE
        self.use_ctransformers = CTRANSFORMERS_AVAILABLE and not self.use_transformers
        self.architecture_error = False
        
        print(f"🔧 Backend selection: HF={self.use_transformers}, CT={self.use_ctransformers}")
        
        # Log backend decision for transparency
        if TRANSPARENCY_AVAILABLE:
            backend_chosen = "HuggingFace" if self.use_transformers else "ctransformers" if self.use_ctransformers else "none"
            reasoning = f"TRANSFORMERS_AVAILABLE={TRANSFORMERS_AVAILABLE}, CTRANSFORMERS_AVAILABLE={CTRANSFORMERS_AVAILABLE}"
            log_model_decision("backend_selection", "Available backends", backend_chosen, reasoning)
        
        # Only auto-load if explicitly requested (fixes double initialization)
        if auto_load:
            self.load_model()
        
    def _get_default_model_path(self) -> str:
        """Get default model path"""
        models_dir = Path("models")
        models_dir.mkdir(exist_ok=True)
        return str(models_dir / "SmolLM-135M.Q4_K_M.gguf")
    
    def _load_model_metadata(self, model_name: str) -> Optional[Dict]:
        """Load stored metadata for a model"""
        try:
            # Sanitize model name for filename
            safe_name = model_name.replace("/", "_").replace(":", "_")
            metadata_file = self.models_dir / f"{safe_name}.json"
            
            if metadata_file.exists():
                with open(metadata_file, 'r', encoding='utf-8') as f:
                    metadata = json.load(f)
                    print(f"📋 Loaded metadata for {model_name}")
                    
                    # Log metadata search
                    if self.logger:
                        self.logger.log_metadata_search(str(metadata_file), True, metadata)
                    
                    return metadata
            else:
                print(f"⚠️  No metadata file found for {model_name} at {metadata_file}")
                
                # Log failed metadata search
                if self.logger:
                    self.logger.log_metadata_search(str(metadata_file), False, None)
                    
        except Exception as e:
            print(f"⚠️  Failed to load metadata for {model_name}: {e}")
            
            # Log error in metadata search
            if self.logger:
                self.logger.log_error(f"Failed to load metadata for {model_name}: {e}", 
                                    {"model_name": model_name, "safe_name": safe_name})
        
        return None
    
    def _extract_system_prompt_from_metadata(self, metadata: Dict) -> Optional[str]:
        """Extract system prompt from model metadata"""
        if not metadata:
            return None
        
        try:
            prompts = metadata.get('prompts', {})
            system_prompt = prompts.get('system')
            
            if system_prompt and system_prompt.strip():
                print(f"🎯 Using model-specific system prompt from metadata")
                return system_prompt.strip()
        except Exception as e:
            print(f"⚠️  Failed to extract system prompt from metadata: {e}")
        
        return None
    
    def get_model_info_summary(self) -> Dict:
        """Get a summary of current model information including metadata"""
        # Use ollama model name for display if available
        display_name = getattr(self, '_ollama_model_name', self._current_model_name or 'Unknown')
        inference_model = self._current_model_name or 'Unknown'
        
        summary = {
            'model_name': display_name,
            'inference_model': inference_model if display_name != inference_model else None,
            'model_loaded': self.model is not None,
            'backend': 'HuggingFace' if self.use_transformers else 'ctransformers' if self.use_ctransformers else 'None',
            'has_metadata': self.model_metadata is not None,
            'is_ollama_context': hasattr(self, '_ollama_model_name')
        }
        
        if self.model_metadata:
            model_info = self.model_metadata.get('model_info', {})
            prompts = self.model_metadata.get('prompts', {})
            
            summary.update({
                'architecture': model_info.get('architecture', 'Unknown'),
                'parameter_count': model_info.get('parameter_count', 'Unknown'),
                'context_length': model_info.get('context_length', 'Unknown'),
                'has_system_prompt': bool(prompts.get('system')),
                'template_type': prompts.get('template_type', 'Unknown')
            })
        
        return summary
    
    def _get_available_ollama_models(self) -> List[str]:
        """Get list of available ollama models"""
        try:
            result = subprocess.run(['ollama', 'list'], capture_output=True, text=True, check=True)
            models = []
            lines = result.stdout.strip().split('\n')[1:]  # Skip header
            for line in lines:
                if line.strip():
                    # Extract model name (first column)
                    model_name = line.split()[0]
                    models.append(model_name)
            print(f"🦙 Found {len(models)} ollama models: {', '.join(models)}")
            return models
        except (subprocess.CalledProcessError, FileNotFoundError):
            print("🔍 Ollama not available or no models found")
            return []
    
    def _get_preferred_ollama_model(self) -> Optional[str]:
        """Get the preferred ollama model based on priority list"""
        available_models = self._get_available_ollama_models()
        if not available_models:
            return None
        
        # Priority list - smollm2:135m is now the top choice
        priority_models = [
            'smollm2:135m',
            'smollm2:latest', 
            'llama3.2:1b',
            'llama3.2:latest',
            'tinyllama:latest'
        ]
        
        for preferred in priority_models:
            if preferred in available_models:
                print(f"🎯 Selected preferred ollama model: {preferred}")
                return preferred
        
        # Fallback to first available model
        first_model = available_models[0]
        print(f"🔄 Using first available ollama model: {first_model}")
        return first_model
    
    def _get_hf_equivalent_for_ollama(self, ollama_model: str) -> Optional[str]:
        """Get HuggingFace equivalent model for ollama model - DEPRECATED, use SmolLM2 engine instead"""
        print(f"⚠️ Using deprecated HF mapping for {ollama_model} - consider SmolLM2 engine")
        
        # Check if we should use SmolLM2 engine instead
        if ollama_model.startswith('smollm2'):
            return None  # Will trigger SmolLM2 engine usage
        
        # Limited mapping for non-SmolLM models
        legacy_map = {
            'llama3.2:1b': 'TinyLlama/TinyLlama-1.1B-Chat-v1.0',
            'llama3.2:latest': 'TinyLlama/TinyLlama-1.1B-Chat-v1.0',
            'tinyllama:latest': 'TinyLlama/TinyLlama-1.1B-Chat-v1.0'
        }
        
        hf_model = legacy_map.get(ollama_model)
        if hf_model and self.model_manager and hf_model in self.model_manager.available_models:
            return hf_model
        
        return None  # No mapping, let SmolLM2 engine handle it

    def _get_target_model_name(self) -> Optional[str]:
        """Get target model name for async loading - prioritizes ollama models"""
        # Check if a specific model is being targeted (for hot loading)
        if hasattr(self, '_target_model_name'):
            return self._target_model_name
        
        # First priority: Check for ollama models (fastest and most efficient)
        preferred_ollama = self._get_preferred_ollama_model()
        if preferred_ollama:
            print(f"🚀 Ollama model detected: {preferred_ollama}")
            # Store ollama model name for metadata loading
            self._ollama_model_name = preferred_ollama
            
            # Map ollama models to compatible HuggingFace models for loading
            hf_equivalent = self._get_hf_equivalent_for_ollama(preferred_ollama)
            if hf_equivalent:
                print(f"📦 Using HuggingFace equivalent: {hf_equivalent}")
                return hf_equivalent
            else:
                print(f"⚠️  No HuggingFace equivalent found, using fallback selection")
        
        # Fallback: Try to get recommended model from LocalModelManager
        try:
            if self.model_manager and self.model_manager.available_models:
                device_info = self.model_manager.analyze_device()
                if device_info.recommended_models:
                    # Return first HF transformers model from recommendations
                    for model_name in device_info.recommended_models:
                        if (model_name in self.model_manager.available_models and 
                            self.model_manager.available_models[model_name].model_type == "hf_transformers"):
                            print(f"📦 Using cached HuggingFace model: {model_name}")
                            return model_name
        except Exception:
            pass
        
        return None
        
    def is_model_available(self) -> bool:
        """Check if model file exists"""
        return Path(self.model_path).exists()
        
    def load_model(self) -> bool:
        """Load the AI model with SmolLM2-first strategy"""
        print("🔄 Starting model loading with SmolLM2-first strategy...")
        
        # Priority 1: Use SmolLM2 engine for supported tiers
        if self.smollm_engine:
            print("🤖 Attempting SmolLM2 engine loading...")
            if self.smollm_engine.load_model():
                self._current_model_name = "SmolLM2-135M"
                print(f"✅ SmolLM2 engine loaded successfully - primary inference ready for {self.device_tier.value} tier")
                return True
            else:
                print("⚠️ SmolLM2 engine loading failed, falling back to legacy engines")
        
        # Priority 2: Check if async loader is available and try it
        try:
            from src.models.managers.async_model_loader import get_async_model_loader
            return self.load_model_async()
        except ImportError:
            # Fallback to synchronous loading
            return self.load_model_sync()
        except Exception as e:
            print(f"⚠️  Async loading failed ({e}), using sync loading...")
            return self.load_model_sync()
    
    def load_model_async(self) -> bool:
        """Load model using async model loader for better performance"""
        from src.models.managers.async_model_loader import get_async_model_loader
        from src.utils.performance.performance_monitor import get_performance_monitor, PerformanceEventType
        
        perf_monitor = get_performance_monitor()
        
        with perf_monitor.measure("ai_model_async_load", PerformanceEventType.MODEL_LOAD):
            loader = get_async_model_loader()
            
            # Register this engine with the loader for updates
            loader.register_ai_engine(self)
            
            # Try to get recommended model
            target_model = self._get_target_model_name()
            if target_model:
                print(f"🎯 Loading target model: {target_model}")
                result = loader.get_model_sync(target_model, timeout=30.0)
                
                if result and result.status.value == "loaded":
                    self.model = result.model_object
                    self.tokenizer = result.tokenizer_object
                    self._current_model_name = target_model
                    
                    # Load metadata - prefer ollama model if available
                    metadata_model_name = getattr(self, '_ollama_model_name', target_model)
                    self.model_metadata = self._load_model_metadata(metadata_model_name)
                    
                    # Update display name to show ollama model if that's what we're using for context
                    if hasattr(self, '_ollama_model_name'):
                        print(f"🎯 Using {self._ollama_model_name} context with {target_model} inference")
                    
                    print(f"✅ Model loaded via AsyncModelLoader in {result.load_time:.2f}s")
                    return True
                else:
                    print(f"❌ Failed to load {target_model} via AsyncModelLoader")
                    return self.load_model_sync()
            else:
                # No specific target, use traditional loading
                return self.load_model_sync()
    
    def load_model_sync(self) -> bool:
        """Synchronous model loading (original method)"""
        start_time = time.time()
        
        # Ensure ollama model is detected and stored before HuggingFace loading
        if not hasattr(self, '_ollama_model_name'):
            target = self._get_target_model_name()
            print(f"🔍 Target model selected for sync loading: {target}")
        
        # Try HuggingFace Transformers first for better x86_64 compatibility
        if self.use_transformers:
            print("🔄 Attempting to load HuggingFace model...")
            if self._try_huggingface_model():
                load_time = time.time() - start_time
                print(f"✅ Model loaded with HuggingFace Transformers in {load_time:.2f} seconds")
                return True
            else:
                print("❌ HuggingFace model loading failed, trying ctransformers...")
                self.use_transformers = False
                self.use_ctransformers = True
        
        # Fallback to ctransformers for GGUF files
        if self.use_ctransformers and CTRANSFORMERS_AVAILABLE and self.is_model_available():
            print(f"Loading GGUF model from {self.model_path}...")
            try:
                from ctransformers import AutoModelForCausalLM as CTAutoModel
                self.model = CTAutoModel.from_pretrained(
                    self.model_path,
                    model_type='llama',
                    max_new_tokens=512,
                    context_length=2048,
                    threads=2,  # Reduced threads for x86_64 compatibility
                    gpu_layers=0  # Disable Metal GPU for x86_64 compatibility
                )
                
                load_time = time.time() - start_time
                print(f"✅ Model loaded with ctransformers in {load_time:.2f} seconds")
                return True
                
            except Exception as e:
                error_msg = str(e).lower()
                if "illegal" in error_msg or "instruction" in error_msg:
                    print(f"❌ Architecture error with ctransformers: {e}")
                    print("🔄 Hardware incompatibility detected...")
                    self.architecture_error = True
                else:
                    print(f"❌ ctransformers error: {e}")
        
        # Final fallback - raise ImportError to trigger SimpleAIEngine
        print("❌ All AI backends failed. Falling back to SimpleAIEngine...")
        raise ImportError("Hardware incompatibility - falling back to mock engine")
    
    def _try_huggingface_model(self) -> bool:
        """Try to load a compatible HuggingFace model from local cache"""
        
        # Check if a specific model is being targeted (for hot loading)
        target_model = getattr(self, '_target_model_name', None)
        
        # First, try to use cached models from LocalModelManager
        if self.model_manager and self.model_manager.available_models:
            print("📦 Using LocalModelManager for cached models...")
            
            # If a specific model is targeted, try to use it
            best_model = None
            if target_model and target_model in self.model_manager.available_models:
                if self.model_manager.available_models[target_model].model_type == "hf_transformers":
                    best_model = target_model
                    print(f"🎯 Targeting specific model: {best_model}")
            
            # If no specific target, get the best available cached HF model using LocalModelManager's logic
            if not best_model:
                device_info = self.model_manager.analyze_device()
                
                # Use recommended models from LocalModelManager, filter for HF transformers
                for recommended_model in device_info.recommended_models:
                    if (recommended_model in self.model_manager.available_models and 
                        self.model_manager.available_models[recommended_model].model_type == "hf_transformers"):
                        best_model = recommended_model
                        break
                
                # Fallback: find any HF transformers model if no recommendations
                if not best_model:
                    for model_name, spec in self.model_manager.available_models.items():
                        if spec.model_type == "hf_transformers":
                            best_model = model_name
                            break
            
            if best_model:
                print(f"🎯 Best cached HF model: {best_model}")
                
                # Try to load the cached HF model using the original model name (not file path)
                try:
                    print(f"🔄 Loading cached model: {best_model}")
                    self.tokenizer = AutoTokenizer.from_pretrained(best_model, local_files_only=True)
                    
                    # Handle tokenizer issues
                    if self.tokenizer.pad_token is None:
                        self.tokenizer.pad_token = self.tokenizer.eos_token
                    
                    # Load model from cache
                    load_kwargs = {
                        "local_files_only": True,
                        "low_cpu_mem_usage": True,
                        "device_map": "cpu"
                    }
                    
                    # Special handling for different model types
                    if "gemma" in best_model.lower():
                        try:
                            load_kwargs["torch_dtype"] = torch.bfloat16
                            print("🎯 Using bfloat16 for Gemma model")
                        except:
                            load_kwargs["torch_dtype"] = torch.float32
                    else:
                        load_kwargs["torch_dtype"] = torch.float32
                    
                    self.model = HFAutoModel.from_pretrained(best_model, **load_kwargs)
                    self.model = self.model.to('cpu')
                    
                    print(f"✅ Successfully loaded cached model: {best_model}")
                    
                    # Fix generation config conflicts to prevent PyTorch warnings when using do_sample=False
                    # The issue is that the model's default generation_config.json has sampling parameters
                    # that conflict with deterministic generation (do_sample=False)
                    if hasattr(self.model, 'generation_config'):
                        # Set safe defaults that work with both sampling and deterministic generation
                        self.model.generation_config.do_sample = None  # Let our parameters override
                        self.model.generation_config.temperature = None  # Let our parameters override
                        self.model.generation_config.top_p = None       # Let our parameters override  
                        self.model.generation_config.top_k = None       # Let our parameters override
                    
                    self._current_model_name = best_model
                    
                    # Load metadata - prefer ollama model if available  
                    metadata_model_name = getattr(self, '_ollama_model_name', best_model)
                    print(f"🔍 Loading metadata from: {metadata_model_name}")
                    self.model_metadata = self._load_model_metadata(metadata_model_name)
                    print(f"🔍 Metadata loaded: {self.model_metadata is not None}")
                    
                    # Update display name to show ollama model if that's what we're using for context
                    if hasattr(self, '_ollama_model_name'):
                        print(f"🎯 Using {self._ollama_model_name} context with {best_model} inference")
                    
                    # Clear target model name after successful loading
                    if hasattr(self, '_target_model_name'):
                        delattr(self, '_target_model_name')
                    return True
                        
                except Exception as e:
                    print(f"❌ Failed to load cached model {best_model}: {e}")
                    print("🔄 Falling back to direct model access...")
        
        # Fallback to direct model loading (may trigger downloads)
        print("🔄 No cached models available, trying direct access...")
        # Prioritized candidate list - smollm2 would be handled via ollama above
        model_candidates = [
            "microsoft/DialoGPT-small",           # PROVEN: Great conversational model ~350MB
            "distilgpt2",                         # FALLBACK: Universal compatibility ~350MB
            "TinyLlama/TinyLlama-1.1B-Chat-v1.0", # If cached, use it
        ]
        
        for model_name in model_candidates:
            try:
                print(f"🔄 Trying model: {model_name}")
                
                # First try to load from cache (no download)
                try:
                    self.tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=True)
                    print(f"📦 Found cached tokenizer for {model_name}")
                except:
                    print(f"📥 Tokenizer not cached, downloading for {model_name}...")
                    self.tokenizer = AutoTokenizer.from_pretrained(model_name)
                
                # Handle tokenizer issues
                if self.tokenizer.pad_token is None:
                    self.tokenizer.pad_token = self.tokenizer.eos_token
                
                # Optimize loading based on model type
                load_kwargs = {
                    "low_cpu_mem_usage": True,
                    "device_map": "cpu"  # Force CPU for universal compatibility
                }
                
                # Try cached model first
                try:
                    load_kwargs["local_files_only"] = True
                    load_kwargs["torch_dtype"] = torch.float32
                    
                    self.model = HFAutoModel.from_pretrained(model_name, **load_kwargs)
                    print(f"📦 Loaded cached model: {model_name}")
                except:
                    # If not cached, download (with warning)
                    print(f"📥 Model not cached, downloading {model_name}...")
                    load_kwargs["local_files_only"] = False
                    
                    # Special handling for Gemma 2B models - use bfloat16 if supported
                    if "gemma" in model_name.lower():
                        try:
                            load_kwargs["torch_dtype"] = torch.bfloat16
                            print("🎯 Using bfloat16 for Gemma model (recommended)")
                        except:
                            load_kwargs["torch_dtype"] = torch.float32
                            print("🔄 Fallback to float32 for compatibility")
                    else:
                        load_kwargs["torch_dtype"] = torch.float32  # Use float32 for other models
                    
                    self.model = HFAutoModel.from_pretrained(model_name, **load_kwargs)
                
                self.model = self.model.to('cpu')  # Move to CPU after loading
                
                print(f"✅ Successfully loaded: {model_name}")
                print("ℹ️  Using HuggingFace model for x86_64 compatibility")
                
                # Store model name for adaptive parameters
                self._current_model_name = model_name
                
                # Load metadata - prefer ollama model if available
                metadata_model_name = getattr(self, '_ollama_model_name', model_name)
                print(f"🔍 Loading metadata from: {metadata_model_name}")
                self.model_metadata = self._load_model_metadata(metadata_model_name)
                print(f"🔍 Metadata loaded: {self.model_metadata is not None}")
                
                # Update display name to show ollama model if that's what we're using for context
                if hasattr(self, '_ollama_model_name'):
                    print(f"🎯 Using {self._ollama_model_name} context with {model_name} inference")
                
                return True
                
            except Exception as e:
                print(f"❌ Failed to load {model_name}: {e}")
                continue
        
        print("❌ All HuggingFace models failed to load")
        return False
            
    def generate_response(self, prompt: str, max_tokens: int = 512, show_thinking: bool = False) -> Generator[str, None, None]:
        """Generate streaming response with SmolLM2-first strategy"""
        
        # Priority 1: Use SmolLM2 engine if available and loaded
        if (self.smollm_engine and 
            hasattr(self.smollm_engine, 'model') and 
            (self.smollm_engine.model or self.smollm_engine.backend == "ollama")):
            
            try:
                # Use SmolLM2 for generation
                print("🤖 Using SmolLM2 engine for generation")
                
                # Get adaptive parameters and auto-select style based on intent
                adaptive_params = self._get_adaptive_generation_params(max_tokens, prompt)
                auto_style = self._determine_auto_style(prompt, adaptive_params)
                
                # Apply auto style to SmolLM2
                if auto_style and hasattr(self.smollm_engine, 'set_response_style'):
                    self.smollm_engine.set_response_style(auto_style)
                
                # Pass session context and adaptive params
                session_context = getattr(self, 'session_context', {})
                
                # Add RAG context if available
                rag_context = self._get_rag_context(prompt)
                if rag_context:
                    session_context = session_context.copy()
                    session_context['rag_context'] = rag_context
                
                # Check if streaming is supported
                if hasattr(self.smollm_engine, 'stream_generate') and self.smollm_engine.backend == "ollama":
                    # Stream from SmolLM2 if supported
                    for token in self.smollm_engine.stream_generate(prompt, session_context, adaptive_params):
                        yield token
                else:
                    # Non-streaming generation
                    response = self.smollm_engine.generate(prompt, use_context=True, session_context=session_context, adaptive_params=adaptive_params)
                    yield response
                return
                
            except Exception as e:
                print(f"⚠️ SmolLM2 generation failed: {e}, falling back to legacy engine")
                # Continue to legacy engine below
        
        # Priority 2: Legacy engine fallback
        if not self.model:
            # Try SimpleAI engine as final fallback
            try:
                from .simple_ai_engine import SimpleAIEngine
                print("🔄 Falling back to SimpleAI engine...")
                simple_engine = SimpleAIEngine()
                for token in simple_engine.generate_response(prompt, max_tokens):
                    yield token
                return
            except ImportError:
                pass
            yield "Error: No model loaded (SmolLM2 and legacy engines unavailable)"
            return
            
        try:
            # Add user message to context
            self.context.add_message("user", prompt)
            
            # Trim context if needed
            if self.context.should_trim():
                self.context.trim_context()

            # Create and add system prompt on the first turn
            if len(self.context.messages) <= 1: # Only user prompt is in context
                system_prompt = self._create_system_prompt()
                # Insert system prompt at the beginning of the conversation
                self.context.messages.insert(0, {"role": "system", "content": system_prompt})
                
            # Format conversation for the model
            formatted_prompt = self._format_conversation()
            
            start_time = time.time()
            response_text = ""
            
            # Generate response based on active backend
            if self.use_ctransformers and hasattr(self.model, '__call__'):
                # ctransformers method
                for token in self.model(
                    formatted_prompt,
                    max_new_tokens=max_tokens,
                    stop=["</s>", "<|endoftext|>", "\n\n"],
                    temperature=0.7,
                    top_p=0.9,
                    stream=True
                ):
                    if token:
                        response_text += token
                        yield token
            elif self.use_transformers and self.tokenizer:
                # HuggingFace transformers method for DialoGPT
                
                # Use optimized prompt formatting to reduce hallucinations
                model_name = getattr(self, '_current_model_name', 'unknown')
                
                try:
                    from optimized_inference_config import get_optimized_prompt_format
                    # Convert context messages for the formatter
                    context_history = self.context.messages[:-1] if len(self.context.messages) > 1 else None
                    input_text = get_optimized_prompt_format(model_name, prompt, context_history)
                except ImportError:
                    # Fallback to conservative default
                    input_text = f"Human: {prompt}\n\nAssistant: "
                
                # Tokenize with attention mask
                encoded = self.tokenizer(
                    input_text, 
                    return_tensors="pt",
                    max_length=256,  # Shorter context for better quality
                    truncation=True,
                    padding=True
                )
                
                inputs = encoded['input_ids']
                attention_mask = encoded['attention_mask']
                
                with torch.no_grad():
                    # Intelligent adaptive parameters based on task, model capabilities, and confidence
                    context_messages = [msg["content"] for msg in self.context.messages[-5:] if msg["role"] == "user"]
                    model_params = self._get_adaptive_generation_params(max_tokens, prompt, context_messages)
                    
                    outputs = self.model.generate(
                        inputs,
                        attention_mask=attention_mask,
                        **model_params
                    )
                
                # Decode only the new tokens (response)
                response = self.tokenizer.decode(
                    outputs[0][inputs.shape[1]:], 
                    skip_special_tokens=True
                )
                response_text = response.strip()
                
                # Validate and clean response with anti-hallucination filters
                if response_text:
                    # First apply advanced cleaning and formatting
                    response_text = self._clean_response(response_text, show_thinking)
                    
                    # Then validate for hallucinations
                    try:
                        from optimized_inference_config import validate_response_quality
                        is_valid, cleaned_response = validate_response_quality(response_text, prompt)
                        if is_valid:
                            response_text = cleaned_response
                        else:
                            response_text = cleaned_response  # Will be a safe fallback response
                    except ImportError:
                        # Basic fallback validation
                        if len(response_text) < 3:
                            response_text = "I understand. Could you tell me more?"
                    
                    # Stream response naturally (remove artificial delay)
                    words = response_text.split()
                    for i, word in enumerate(words):
                        if i > 0:
                            yield " "
                        yield word
                else:
                    response_text = "I'm processing that. Could you rephrase?"
                    yield response_text
                        
            # Add assistant response to context
            if response_text.strip():
                self.context.add_message("assistant", response_text.strip())
                
            generation_time = time.time() - start_time
            if generation_time > 0:
                tokens_per_sec = len(response_text.split()) / generation_time
                print(f"\n[Generated in {generation_time:.2f}s, ~{tokens_per_sec:.1f} tokens/sec]")
                
        except Exception as e:
            yield f"Error generating response: {e}"
            
    def get_formatted_prompt(self, prompt: str) -> str:
        """Get the formatted prompt that would be used for generation (for transparency)"""
        try:
            # Temporarily add the prompt to context to get formatted version
            temp_context_length = len(self.context.messages)
            self.context.add_message("user", prompt)
            formatted = self._format_conversation()
            # Remove the temporary message
            if len(self.context.messages) > temp_context_length:
                self.context.messages.pop()
            return formatted
        except:
            return prompt  # Fallback to original prompt
    
    def generate_clarification_response(self, query: str, classification_metadata: dict) -> str:
        """Generate a clarification question for ambiguous queries"""
        
        # Get the reason for clarification from classification
        reasoning = classification_metadata.get('reasoning', '')
        intent = classification_metadata.get('intent', 'unknown')
        
        # Generate appropriate clarification based on the type of ambiguity
        if query.strip().lower() in ['fix', 'do', 'make', 'change', 'update']:
            return f"I'd like to help you {query.strip().lower()} something. Could you be more specific about what you need help with?"
        
        if query.strip().lower() == 'help':
            return "I'm here to help! What would you like assistance with? You can ask me about programming, math, explanations, or just chat."
        
        # Check for pronoun issues
        pronouns_without_context = ['that', 'this', 'it', 'them', 'those']
        first_word = query.strip().split()[0].lower() if query.strip() else ""
        if first_word in pronouns_without_context:
            return f"I'm not sure what '{first_word}' refers to. Could you provide more context or be more specific?"
        
        # Very short queries
        if len(query.strip()) <= 2:
            return "Could you provide more details about what you're looking for?"
        
        # Check for multiple competing intents
        if 'competing' in reasoning.lower() or 'similar' in reasoning.lower():
            return "I see a few ways to interpret your question. Could you clarify what you're specifically asking about?"
        
        # Generic low confidence response
        return "I want to make sure I understand correctly. Could you rephrase or provide more details about what you need?"
    
    def _format_conversation(self) -> str:
        """Format conversation history using model-specific templates"""
        
        if not self.context.messages:
            return ""
        
        # Use new template system if available
        if self.template_manager and hasattr(self, '_current_model_name'):
            try:
                # Keep only recent messages to avoid context issues
                recent_messages = self.context.messages[-4:] if len(self.context.messages) > 4 else self.context.messages
                
                # Get proper template for the current model
                template = self.template_manager.get_template(
                    model_name=self._current_model_name,
                    tokenizer=self.tokenizer
                )
                
                # Handle system prompt injection for non-supporting models
                processed_messages = self.template_manager.inject_system_prompt_for_non_supporting_models(
                    recent_messages, template
                )
                
                # Format using the appropriate template
                return self.template_manager.format_conversation(
                    processed_messages, template, add_generation_prompt=True
                )
                
            except Exception as e:
                print(f"⚠️  Template formatting failed: {e}, falling back to legacy format")
        
        # Fallback to legacy formatting
        if self.use_transformers:
            # Format for instruction-tuned models (TinyLlama, Qwen)
            recent_messages = self.context.messages[-4:] if len(self.context.messages) > 4 else self.context.messages
            
            # Use generic format as fallback
            conversation_parts = []
            for msg in recent_messages:
                if msg["role"] == "user":
                    conversation_parts.append(f"### User:\n{msg['content']}")
                elif msg["role"] == "assistant":
                    conversation_parts.append(f"### Assistant:\n{msg['content']}")
            
            return "\n\n".join(conversation_parts) if conversation_parts else ""
        
        else:
            # ctransformers format for GGUF models
            formatted = ""
            for msg in self.context.messages:
                if msg["role"] == "user":
                    formatted += f"User: {msg['content']}\n"
                elif msg["role"] == "assistant":
                    formatted += f"Assistant: {msg['content']}\n"
                    
            formatted += "Assistant: "
            return formatted
        
    def clear_context(self):
        """Clear conversation context"""
        self.context = ConversationContext()
        
        # Also clear SmolLM2 context if available
        if self.smollm_engine and hasattr(self.smollm_engine, 'clear_context'):
            self.smollm_engine.clear_context()
        
        print("Conversation context cleared.")
        
    def get_memory_usage(self) -> Dict[str, str]:
        """Get current memory usage info"""
        import psutil
        process = psutil.Process()
        memory_mb = process.memory_info().rss / 1024 / 1024
        return {
            "memory_mb": f"{memory_mb:.1f}MB",
            "context_tokens": str(self.context.current_tokens),
            "context_messages": str(len(self.context.messages))
        }
    
    def get_token_usage(self) -> Dict[str, any]:
        """Get current token usage statistics"""
        usage_percent = (self.context.current_tokens / self.context.max_tokens) * 100
        return {
            "current_tokens": self.context.current_tokens,
            "max_tokens": self.context.max_tokens,
            "usage_percent": usage_percent,
            "messages_count": len(self.context.messages),
            "trimming_threshold": int(self.context.max_tokens * 0.8),
            "needs_trimming": self.context.should_trim()
        }
    
    def get_eco_metrics(self) -> Dict[str, str]:
        """Get environmental impact metrics for local AI usage"""
        responses_generated = len([msg for msg in self.context.messages if msg["role"] == "assistant"])
        
        # Estimated savings vs cloud AI (approximate values)
        energy_saved_kwh = responses_generated * 0.003  # ~3 Wh per response saved
        water_saved_gallons = responses_generated * 0.12  # ~120ml per response saved  
        co2_saved_grams = responses_generated * 14  # ~14g CO2 per response saved
        
        return {
            "energy_saved_kwh": f"{energy_saved_kwh:.2f}",
            "water_saved_gallons": f"{water_saved_gallons:.1f}",
            "co2_saved_grams": f"{co2_saved_grams:.0f}",
            "responses_count": str(responses_generated),
            "privacy_score": "100%",  # All processing is local
            "data_transmitted": "0 bytes"  # No cloud transmission
        }
    
    def set_session_context(self, context: Dict[str, str]):
        """Set session context information"""
        self.session_context = context
        
    def add_user_preference(self, key: str, value: str):
        """Add user preference"""
        if not hasattr(self, 'user_preferences'):
            self.user_preferences = {}
        self.user_preferences[key] = value
    
    def _get_adaptive_generation_params(self, max_tokens: int, query: str = "", context: list = None) -> Dict:
        """Get intelligently optimized parameters based on task, model capabilities, and confidence"""
        model_name = getattr(self, '_current_model_name', 'unknown')
        
        # Try enhanced adaptive configuration first (new intelligent system with intent classification)
        try:
            # Use cached config if available, otherwise create new one
            adaptive_config = self.adaptive_config
            if adaptive_config is None:
                from enhanced_adaptive_model_config import EnhancedAdaptiveModelConfig
                adaptive_config = EnhancedAdaptiveModelConfig(enable_websocket=False)
            config_result = adaptive_config.get_optimal_config(query, model_name, context or [])
            
            # Filter out metadata and None values to get clean parameters
            params = {k: v for k, v in config_result.items() if k != '_metadata' and v is not None}
            
            # Set tokenizer-specific IDs for proper generation (if tokenizer exists)
            if self.tokenizer is not None:
                params['pad_token_id'] = self.tokenizer.eos_token_id
                params['eos_token_id'] = self.tokenizer.eos_token_id
            
            # Handle generation config conflicts: when do_sample=False, we should not pass
            # sampling parameters at all, as they conflict with the model's generation_config.json.
            # The solution is to let PyTorch handle the defaults for deterministic generation.
            if params.get('do_sample', True) is False:
                # For deterministic generation, don't pass sampling parameters to avoid conflicts
                # with the model's built-in generation_config.json file
                sampling_params = ['temperature', 'top_p', 'top_k']
                for param in sampling_params:
                    if param in params:
                        del params[param]
            
            # Honor max_tokens limit if specified
            if max_tokens < params.get('max_new_tokens', max_tokens):
                params['max_new_tokens'] = max_tokens
            
            return params
            
        except ImportError:
            # Fallback to original anti-hallucination config
            try:
                from optimized_inference_config import get_anti_hallucination_params
                params = get_anti_hallucination_params(model_name, max_tokens)
                # Set tokenizer-specific IDs (if tokenizer exists)
                if self.tokenizer is not None:
                    params['pad_token_id'] = self.tokenizer.eos_token_id
                    params['eos_token_id'] = self.tokenizer.eos_token_id
                return params
            except ImportError:
                # Ultimate fallback to conservative parameters
                fallback_params = {
                    'do_sample': False,  # Greedy decoding - compatible with all models
                    'max_new_tokens': min(max_tokens, 80),
                    'repetition_penalty': 1.15,
                    'no_repeat_ngram_size': 3,
                }
    
    def _determine_auto_style(self, prompt: str, adaptive_params: Dict) -> Optional[str]:
        """Automatically determine response style based on intent and context"""
        # Handle None adaptive_params
        if adaptive_params is None:
            adaptive_params = {}
            
        # Get intent from adaptive params metadata
        metadata = adaptive_params.get('_metadata', {})
        intent = metadata.get('intent', 'unknown')
        response_strategy = metadata.get('response_strategy', 'balanced')
        
        # Map intents to response styles
        intent_to_style = {
            'factual_query': 'detailed',
            'technical_explanation': 'detailed', 
            'casual_conversation': 'default',
            'code_request': 'coding',
            'creative_writing': 'creative',
            'quick_lookup': 'concise',
            'debugging_help': 'coding',
            'question_answering': 'detailed'
        }
        
        # Map response strategies to styles
        strategy_to_style = {
            'concise': 'concise',
            'detailed': 'detailed',
            'technical': 'coding',
            'creative': 'creative',
            'balanced': 'default'
        }
        
        # Priority: intent mapping first, then strategy mapping
        auto_style = intent_to_style.get(intent)
        if not auto_style:
            auto_style = strategy_to_style.get(response_strategy)
        
        # Simple heuristics for keywords if no intent detected
        if not auto_style or intent == 'unknown':
            prompt_lower = prompt.lower()
            if any(word in prompt_lower for word in ['code', 'program', 'function', 'debug', 'error']):
                auto_style = 'coding'
            elif any(word in prompt_lower for word in ['explain', 'what is', 'how does', 'why']):
                auto_style = 'detailed'
            elif any(word in prompt_lower for word in ['story', 'creative', 'imagine', 'write']):
                auto_style = 'creative'
            elif len(prompt.split()) < 10:  # Short queries
                auto_style = 'concise'
            else:
                auto_style = 'default'
        
        if auto_style:
            print(f"🎨 Auto-selected style: {auto_style} (intent: {intent}, strategy: {response_strategy})")
        
        return auto_style
    
    def _get_rag_context(self, prompt: str) -> Optional[Dict]:
        """Get RAG context if available"""
        # Check if this is a RAG-enabled engine
        if hasattr(self, 'rag_enabled') and self.rag_enabled:
            return None  # RAG engine handles retrieval internally
            
        # For standard engines, check if we can access RAG functionality
        try:
            from src.rag.m1k3_rag_engine import M1K3RAGEngine
            if hasattr(self, '_rag_engine') and self._rag_engine:
                # Retrieve relevant context
                results = self._rag_engine.retrieve_context(prompt, top_k=3)
                if results:
                    return {
                        'retrieved_docs': results,
                        'source_count': len(results),
                        'confidence': results[0].get('score', 0.0) if results else 0.0
                    }
        except ImportError:
            pass
            
        return None
    
    def _clean_response(self, response_text: str, show_thinking: bool = False) -> str:
        """Clean up model response using advanced formatting system"""
        
        # Use new response formatter if available
        if self.response_formatter:
            try:
                model_name = getattr(self, '_current_model_name', None)
                formatted = self.response_formatter.format_response(
                    response_text, 
                    show_thinking=show_thinking,
                    model_name=model_name
                )
                return formatted.content
                
            except Exception as e:
                print(f"⚠️  Response formatting failed: {e}, using legacy cleanup")
        
        # Legacy cleanup for fallback
        # Remove EOS tokens and special tokens
        if hasattr(self.tokenizer, 'eos_token') and self.tokenizer.eos_token:
            response_text = response_text.split(self.tokenizer.eos_token)[0]
        
        # Remove common artifacts
        response_text = response_text.replace('<|endoftext|>', '')
        response_text = response_text.replace('</s>', '')
        
        # For instruction-tuned models, stop at next instruction marker
        if "### User:" in response_text:
            response_text = response_text.split("### User:")[0]
        if "### Assistant:" in response_text:
            response_text = response_text.split("### Assistant:")[0]
        
        # Remove incomplete sentences for better quality
        sentences = response_text.split('. ')
        if len(sentences) > 1 and len(sentences[-1]) < 10:
            response_text = '. '.join(sentences[:-1]) + '.'
        
        # Clean whitespace and return
        return response_text.strip()

    def _create_system_prompt(self) -> str:
        """Create a detailed system prompt optimized for the current model template."""
        
        # Check if we have a model-specific system prompt from metadata
        if self.model_metadata:
            metadata_system_prompt = self._extract_system_prompt_from_metadata(self.model_metadata)
            if metadata_system_prompt:
                return metadata_system_prompt
        
        # AI Self-Description
        model_name = getattr(self, '_current_model_name', 'a local language model')
        ai_description = {
            "name": "M1K3",
            "model": model_name,
            "capabilities": [
                "conversational chat", "answering questions", "creative writing", 
                "code assistance", "technical explanations"
            ],
            "limitations": [
                "I am a relatively small model, so my knowledge can be limited.",
                "I run entirely locally, so I do not have access to real-time information.",
                "I may occasionally make mistakes or generate incorrect information."
            ]
        }
        
        # Available Actions
        actions = {
            "/stats": "Display current system and AI performance statistics.",
            "/clear": "Clear the conversation history to start fresh.",
            "/help": "Show this help message with all available commands.",
            "/exit": "Quit the application."
        }
        
        # Enhanced Device Context (from session_context, set by CLI)
        device_context = self.session_context if hasattr(self, 'session_context') else {}
        
        # Check if current model supports system prompts
        supports_system_prompt = True
        if self.template_manager:
            try:
                template = self.template_manager.get_template(model_name, self.tokenizer)
                supports_system_prompt = template.system_support
            except:
                pass
        
        # Create enhanced system context description
        system_description = self._create_system_context_description(device_context)
        
        # Create appropriate prompt based on model capabilities
        if supports_system_prompt:
            # Full system prompt for models that support system messages
            prompt = (
                f"You are {ai_description['name']}, a helpful AI assistant "
                f"running on {ai_description['model']}. You provide clear, helpful responses "
                f"without showing your thinking process unless specifically asked to explain your reasoning.\n\n"
                f"Your strengths: {', '.join(ai_description['capabilities'])}\n"
                f"Your limitations: {', '.join(ai_description['limitations'])}\n\n"
                f"{system_description}\n\n"
                f"Available commands you can suggest: {', '.join(actions.keys())}\n"
                f"Always be helpful, accurate, and concise."
            )
        else:
            # Shorter prompt for models without system prompt support (will be injected into user message)
            prompt = (
                f"You are {ai_description['name']}, a helpful AI assistant. "
                f"Provide clear, helpful responses. "
                f"Available commands: {', '.join(actions.keys())}"
            )
        
        return prompt
    
    def _create_system_context_description(self, device_context: dict) -> str:
        """Create a rich description of the system context for the AI"""
        
        context_parts = []
        
        # Platform and hardware information
        platform = device_context.get('platform', 'Unknown')
        cpu_cores = device_context.get('cpu_cores', 0)
        available_memory_gb = device_context.get('available_memory_gb', 0.0)
        
        if platform != 'Unknown':
            hw_info = f"Running on {platform}"
            if cpu_cores > 0:
                hw_info += f" with {cpu_cores} CPU cores"
            if available_memory_gb > 0:
                hw_info += f" and {available_memory_gb:.1f}GB available RAM"
            context_parts.append(hw_info + ".")
        
        # Location and time context
        timezone = device_context.get('timezone')
        locale = device_context.get('locale')
        time_of_day = device_context.get('time_of_day', '')
        
        if timezone or locale:
            location_info = "User location:"
            if timezone:
                location_info += f" {timezone} timezone"
            if locale:
                location_info += f", {locale} locale"
            if time_of_day:
                location_info += f" ({time_of_day.lower()})"
            context_parts.append(location_info + ".")
        
        # System capabilities and status
        capabilities = []
        if device_context.get('voice_enabled'):
            capabilities.append("voice synthesis")
        if device_context.get('avatar_enabled'):
            capabilities.append("avatar visualization")
        if device_context.get('sound_enabled'):
            capabilities.append("sound effects")
        
        if capabilities:
            context_parts.append(f"M1K3 capabilities active: {', '.join(capabilities)}.")
        
        # Performance and resource status
        cpu_usage = device_context.get('cpu_usage', 0)
        memory_percent = device_context.get('memory_percent', 0)
        disk_usage = device_context.get('disk_usage', 0)
        
        performance_notes = []
        if cpu_usage > 80:
            performance_notes.append("CPU under heavy load")
        elif cpu_usage < 20:
            performance_notes.append("CPU running efficiently")
        
        if memory_percent > 80:
            performance_notes.append("memory usage is high")
        elif memory_percent < 50:
            performance_notes.append("plenty of memory available")
        
        if disk_usage > 90:
            performance_notes.append("storage nearly full")
        elif disk_usage < 50:
            performance_notes.append("ample storage space")
        
        if performance_notes:
            context_parts.append(f"System status: {', '.join(performance_notes)}.")
        
        # Connectivity information
        connected_devices = device_context.get('connected_devices', 0)
        ble_devices = device_context.get('ble_devices', [])
        network_status = device_context.get('network_status', 'unknown')
        
        connectivity_info = []
        if connected_devices > 0:
            connectivity_info.append(f"{connected_devices} network connections")
        if ble_devices and len(ble_devices) > 0:
            connectivity_info.append(f"{len(ble_devices)} Bluetooth devices nearby")
        if network_status == 'connected':
            connectivity_info.append("network active")
        
        if connectivity_info:
            context_parts.append(f"Connectivity: {', '.join(connectivity_info)}.")
        
        # Interface information
        interface_type = device_context.get('interface_type', 'Unknown')
        transparency_mode = device_context.get('transparency_mode', 'basic')
        
        if interface_type != 'Unknown':
            interface_info = f"User interface: {interface_type}"
            if transparency_mode != 'basic':
                interface_info += f" with {transparency_mode} transparency mode"
            context_parts.append(interface_info + ".")
        
        # Battery information (if available)
        battery_level = device_context.get('battery_level')
        if battery_level is not None:
            if battery_level < 20:
                context_parts.append(f"Battery low ({battery_level}%) - suggest power-saving responses.")
            elif battery_level > 80:
                context_parts.append(f"Battery excellent ({battery_level}%).")
        
        if context_parts:
            return "System Environment:\n" + "\n".join(context_parts)
        else:
            return "System Environment: Basic system information available."

def download_model(model_name: str = "smollm-135m-q4_k_m") -> str:
    """Download model if not present"""
    models_dir = Path("models")
    models_dir.mkdir(exist_ok=True)
    
    model_file = models_dir / f"{model_name}.gguf"
    
    if model_file.exists():
        print(f"Model already exists: {model_file}")
        return str(model_file)
        
    print(f"Downloading {model_name}...")
    
    # Model URLs (these would need to be actual URLs)
    model_urls = {
        "smollm-135m-q4_k_m": "https://huggingface.co/HuggingFaceTB/SmolLM-135M-GGUF/resolve/main/smollm-135m-q4_k_m.gguf"
    }
    
    if model_name not in model_urls:
        print(f"Unknown model: {model_name}")
        return ""
        
    try:
        import requests
        from tqdm import tqdm
        
        url = model_urls[model_name]
        response = requests.get(url, stream=True)
        response.raise_for_status()
        
        total_size = int(response.headers.get('content-length', 0))
        
        with open(model_file, 'wb') as f, tqdm(
            desc=model_name,
            total=total_size,
            unit='B',
            unit_scale=True,
            unit_divisor=1024,
        ) as pbar:
            for chunk in response.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
                    pbar.update(len(chunk))
                    
        print(f"Model downloaded: {model_file}")
        return str(model_file)
        
    except Exception as e:
        print(f"Failed to download model: {e}")
        if model_file.exists():
            model_file.unlink()
        return ""

if __name__ == "__main__":
    # Simple CLI test
    engine = LocalAIEngine(auto_load=True)
    
    if not engine.is_model_available():
        print("Model not found. Downloading...")
        model_path = download_model()
        if not model_path:
            print("Failed to download model")
            sys.exit(1)
            
    if not engine.load_model():
        print("Failed to load model")
        sys.exit(1)
        
    print("M1K3 AI Engine Ready!")
    print("Type 'quit' to exit, 'clear' to clear context, 'stats' for memory info")
    
    while True:
        try:
            user_input = input("\n> ").strip()
            
            if user_input.lower() == 'quit':
                break
            elif user_input.lower() == 'clear':
                engine.clear_context()
                continue
            elif user_input.lower() == 'stats':
                stats = engine.get_memory_usage()
                print(f"Memory: {stats['memory_mb']}, Context: {stats['context_tokens']} tokens, {stats['context_messages']} messages")
                continue
            elif not user_input:
                continue
                
            print("Assistant: ", end="", flush=True)
            for token in engine.generate_response(user_input):
                print(token, end="", flush=True)
            print()
            
        except KeyboardInterrupt:
            print("\nExiting...")
            break
        except Exception as e:
            print(f"Error: {e}")