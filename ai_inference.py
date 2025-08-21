#!/usr/bin/env python3
"""
M1K3 Local AI Inference Module
Integrates with LocalModelManager for cached model access
"""

import os
import sys
import time
import json
from pathlib import Path
from typing import Generator, List, Dict, Optional
from dataclasses import dataclass
import threading
import queue

# Enable HuggingFace tokenizers parallelism for better performance
os.environ['TOKENIZERS_PARALLELISM'] = 'true'

# Import local model manager
try:
    from local_model_manager import LocalModelManager
    LOCAL_MODEL_MANAGER_AVAILABLE = True
except ImportError:
    LOCAL_MODEL_MANAGER_AVAILABLE = False
    print("⚠️  LocalModelManager not available")

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
        
        # Choose backend: prefer HuggingFace for x86_64 compatibility
        self.use_transformers = TRANSFORMERS_AVAILABLE
        self.use_ctransformers = CTRANSFORMERS_AVAILABLE and not self.use_transformers
        self.architecture_error = False
        
        print(f"🔧 Backend selection: HF={self.use_transformers}, CT={self.use_ctransformers}")
        
        # Only auto-load if explicitly requested (fixes double initialization)
        if auto_load:
            self.load_model()
        
    def _get_default_model_path(self) -> str:
        """Get default model path"""
        models_dir = Path("models")
        models_dir.mkdir(exist_ok=True)
        return str(models_dir / "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf")
        
    def is_model_available(self) -> bool:
        """Check if model file exists"""
        return Path(self.model_path).exists()
        
    def load_model(self) -> bool:
        """Load the AI model with fallback strategies"""
        start_time = time.time()
        
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
        
        # First, try to use cached models from LocalModelManager
        if self.model_manager and self.model_manager.available_models:
            print("📦 Using LocalModelManager for cached models...")
            
            # Get the best available cached HF model using LocalModelManager's logic
            best_model = None
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
                    self._current_model_name = best_model
                    return True
                        
                except Exception as e:
                    print(f"❌ Failed to load cached model {best_model}: {e}")
                    print("🔄 Falling back to direct model access...")
        
        # Fallback to direct model loading (may trigger downloads)
        print("🔄 No cached models available, trying direct access...")
        # Reduced candidate list to minimize downloads
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
                return True
                
            except Exception as e:
                print(f"❌ Failed to load {model_name}: {e}")
                continue
        
        print("❌ All HuggingFace models failed to load")
        return False
            
    def generate_response(self, prompt: str, max_tokens: int = 512) -> Generator[str, None, None]:
        """Generate streaming response"""
        if not self.model:
            yield "Error: Model not loaded"
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
                
                # Prepare input with model-specific formatting
                model_name = getattr(self, '_current_model_name', 'unknown')
                
                if 'gemma' in model_name.lower():
                    # Gemma prefers simple, clean instruction format
                    if formatted_prompt:
                        input_text = formatted_prompt + f"\n\nUser: {prompt}\n\nAssistant:"
                    else:
                        input_text = f"User: {prompt}\n\nAssistant:"
                elif 'phi-3' in model_name.lower() or 'phi3' in model_name.lower():
                    # Phi-3 uses system/user/assistant format
                    if formatted_prompt:
                        input_text = formatted_prompt + f"\n<|user|>\n{prompt}<|end|>\n<|assistant|>\n"
                    else:
                        input_text = f"<|system|>\nYou are a helpful AI assistant.<|end|>\n<|user|>\n{prompt}<|end|>\n<|assistant|>\n"
                else:
                    # Default format for other models
                    if formatted_prompt:
                        input_text = formatted_prompt + f"\n\n### User:\n{prompt}\n\n### Assistant:\n"
                    else:
                        input_text = f"### User:\n{prompt}\n\n### Assistant:\n"
                
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
                    # Adaptive parameters based on model size and type
                    model_params = self._get_adaptive_generation_params(max_tokens)
                    
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
                
                # Clean up response for better quality
                if response_text:
                    # Clean up response intelligently
                    response_text = self._clean_response(response_text)
                    
                    # Handle empty or very short responses
                    if len(response_text) < 3:
                        response_text = "I understand. Could you tell me more?"
                    
                    # Simulate streaming by yielding word by word
                    words = response_text.split()
                    for i, word in enumerate(words):
                        if i > 0:
                            yield " "
                        yield word
                        time.sleep(0.04)  # Slightly faster for longer responses
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
            
    def _format_conversation(self) -> str:
        """Format conversation history for the model"""
        if self.use_transformers:
            # Format for instruction-tuned models (TinyLlama, Qwen)
            if not self.context.messages:
                return ""
            
            # Keep only the last 2-3 exchanges to avoid context issues
            recent_messages = self.context.messages[-4:] if len(self.context.messages) > 4 else self.context.messages
            
            # Use proper instruction format for modern models
            conversation_parts = []
            for msg in recent_messages:
                if msg["role"] == "user":
                    conversation_parts.append(f"### User:\n{msg['content']}")
                elif msg["role"] == "assistant":
                    conversation_parts.append(f"### Assistant:\n{msg['content']}")
            
            return "\n\n".join(conversation_parts) if conversation_parts else ""
        
        else:
            # ctransformers format for GGUF models
            if not self.context.messages:
                return ""
                
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
    
    def _get_adaptive_generation_params(self, max_tokens: int) -> Dict:
        """Get adaptive generation parameters based on model type and size"""
        # Get model info for adaptive parameters
        model_name = getattr(self, '_current_model_name', 'unknown')
        
        # Base parameters that work well for most models
        base_params = {
            'pad_token_id': self.tokenizer.eos_token_id,
            'eos_token_id': self.tokenizer.eos_token_id,
            'do_sample': True,
        }
        
        # Model-specific optimizations
        if 'gemma' in model_name.lower():
            # Gemma 2B optimizations - enhanced reasoning and instruction following
            return {
                **base_params,
                'max_new_tokens': min(max_tokens, 300),  # Gemma can handle longer, more detailed responses
                'temperature': 0.7,  # Balanced temperature for good reasoning
                'top_p': 0.9,
                'top_k': 50,
                'repetition_penalty': 1.05,  # Light penalty to avoid repetition
                'no_repeat_ngram_size': 3,
                'length_penalty': 1.0,  # Encourage longer, more complete responses
            }
        
        elif 'phi-3' in model_name.lower() or 'phi3' in model_name.lower():
            # Microsoft Phi-3 optimizations - excellent reasoning capabilities
            return {
                **base_params,
                'max_new_tokens': min(max_tokens, 350),  # Phi-3 can handle detailed responses
                'temperature': 0.6,  # Lower temp for more focused reasoning
                'top_p': 0.9,
                'top_k': 40,
                'repetition_penalty': 1.1,
                'no_repeat_ngram_size': 3,
                'length_penalty': 1.1,  # Encourage complete, detailed responses
            }
        
        elif 'tinyllama' in model_name.lower():
            # TinyLlama optimizations
            return {
                **base_params,
                'max_new_tokens': min(max_tokens, 150),  # Increased for fuller responses
                'temperature': 0.8,  # Slightly higher for more creativity
                'top_p': 0.9,
                'top_k': 40,
                'repetition_penalty': 1.1,
                'no_repeat_ngram_size': 3,
            }
        
        elif 'qwen' in model_name.lower():
            # Qwen optimizations  
            return {
                **base_params,
                'max_new_tokens': min(max_tokens, 200),  # Qwen can handle longer
                'temperature': 0.7,
                'top_p': 0.95,
                'top_k': 50,
                'repetition_penalty': 1.05,
                'no_repeat_ngram_size': 2,
            }
        
        elif 'dialogpt' in model_name.lower():
            # DialoGPT optimizations
            return {
                **base_params,
                'max_new_tokens': min(max_tokens, 100),
                'temperature': 0.9,
                'top_p': 0.9,
                'top_k': 0,  # Disable top_k for DialoGPT
                'repetition_penalty': 1.3,  # Higher penalty for chat models
                'no_repeat_ngram_size': 4,
            }
        
        elif 'gpt2' in model_name.lower() or 'distilgpt2' in model_name.lower():
            # GPT-2 style models
            return {
                **base_params,
                'max_new_tokens': min(max_tokens, 80),
                'temperature': 0.8,
                'top_p': 0.9,
                'top_k': 50,
                'repetition_penalty': 1.2,
                'no_repeat_ngram_size': 3,
            }
        
        else:
            # Universal fallback parameters
            return {
                **base_params,
                'max_new_tokens': min(max_tokens, 100),
                'temperature': 0.8,
                'top_p': 0.9,
                'top_k': 50,
                'repetition_penalty': 1.1,
                'no_repeat_ngram_size': 3,
            }
    
    def _clean_response(self, response_text: str) -> str:
        """Clean up model response intelligently"""
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
        """Create a detailed system prompt with device and AI context."""
        
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
        
        # Device Context (from session_context, set by CLI)
        device_context = self.session_context if hasattr(self, 'session_context') else {}
        
        # Assemble the prompt
        prompt = (
            f"You are {ai_description['name']}, a helpful AI assistant."
            f" You are running on the model: {ai_description['model']}.\n\n"
            f"== Your Capabilities ==\n"
            f"- Strengths: {', '.join(ai_description['capabilities'])}\n"
            f"- Weaknesses: {', '.join(ai_description['limitations'])}\n\n"
            f"== User's Device Context ==\n"
            f"- OS: {device_context.get('operating_system', 'Unknown')}\n"
            f"- CPU: {device_context.get('device_type', 'Unknown')}\n"
            f"- Performance: The system is currently running at a {device_context.get('performance_state', 'normal')} level.\n\n"
            f"== Available Actions ==\n"
            f"You can suggest the following commands to the user when appropriate. Do not execute them yourself.\n"
        )
        for command, desc in actions.items():
            prompt += f"- `{command}`: {desc}\n"
            
        prompt += "\nBegin the conversation by introducing yourself and offering assistance."
        return prompt

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