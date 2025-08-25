#!/usr/bin/env python3
"""
Enhanced SmolLM2 Inference Engine with Adaptive Prompting
Multi-format prompting system optimized for different model types
"""

import os
import json
import subprocess
import time
import requests
from pathlib import Path
from typing import Optional, Dict, List, Generator
from dataclasses import dataclass

# Import adaptive prompting system
from src.models.loaders.adaptive_prompt_formatter import AdaptivePromptFormatter, PromptFormat
from src.utils.logging.prompt_logger import get_prompt_logger

# Import system context builder for dynamic stats
try:
    from system_context_builder import SystemContextBuilder
    CONTEXT_BUILDER_AVAILABLE = True
except ImportError:
    CONTEXT_BUILDER_AVAILABLE = False

# Try to import required libraries
try:
    from ctransformers import AutoModelForCausalLM as CTAutoModel
    CTRANSFORMERS_AVAILABLE = True
except ImportError:
    CTRANSFORMERS_AVAILABLE = False

try:
    from transformers import AutoTokenizer, AutoModelForCausalLM
    import torch
    TRANSFORMERS_AVAILABLE = True
except ImportError:
    TRANSFORMERS_AVAILABLE = False

@dataclass
class SmolLMConfig:
    """SmolLM2 configuration with adaptive prompting support"""
    temperature: float = 0.7
    top_p: float = 0.9
    max_tokens: int = 512
    repetition_penalty: float = 1.1
    context_length: int = 2048
    stop_tokens: List[str] = None
    system_prompt: str = "You are M1K3, a helpful AI assistant."
    adaptive_prompting: bool = True  # Enable adaptive prompting
    force_format: Optional[str] = None  # Override format detection
    
    def __post_init__(self):
        if self.stop_tokens is None:
            self.stop_tokens = ["<|im_end|>", "<|im_start|>"]

class SmolLMEngine:
    """SmolLM2 engine with adaptive prompting capabilities"""
    
    def __init__(self, config_path: Optional[str] = None, profiles_path: Optional[str] = None):
        self.config = self._load_config(config_path)
        self.model = None
        self.tokenizer = None
        self.backend = None
        self.context_messages = []
        self.current_model_name = "smollm2:135m"  # Default model name
        
        # Initialize adaptive prompting system
        self.adaptive_formatter = AdaptivePromptFormatter(profiles_path or "config/model_profiles.json")
        self.model_profile = None
        self.optimal_format = None
        
        # Model paths
        self.models_dir = Path("models")
        self.gguf_path = self.models_dir / "SmolLM-135M.Q4_K_M.gguf"
        self.ollama_model = "smollm2:135m"
        
        # Initialize logger
        self.logger = get_prompt_logger()
        
        # Initialize system context builder for dynamic stats
        if CONTEXT_BUILDER_AVAILABLE:
            self.context_builder = SystemContextBuilder()
        else:
            self.context_builder = None
        
        print("🤖 SmolLM2 Engine with Adaptive Prompting initialized")
        self._detect_model_profile()
    
    def _load_config(self, config_path: Optional[str] = None) -> SmolLMConfig:
        """Load SmolLM2 configuration"""
        if config_path and Path(config_path).exists():
            try:
                with open(config_path) as f:
                    data = json.load(f)
                    params = data.get('parameters', {})
                    
                    # Convert to SmolLMConfig, preserving adaptive settings
                    return SmolLMConfig(
                        temperature=params.get('temperature', 0.7),
                        top_p=params.get('top_p', 0.9),
                        max_tokens=params.get('max_tokens', 512),
                        repetition_penalty=params.get('repetition_penalty', 1.1),
                        context_length=params.get('context_length', 2048),
                        stop_tokens=params.get('stop_tokens', None),
                        system_prompt=params.get('system_prompt', "You are M1K3, a helpful AI assistant."),
                        adaptive_prompting=params.get('adaptive_prompting', True),
                        force_format=params.get('force_format', None)
                    )
            except Exception as e:
                print(f"⚠️ Error loading config: {e}")
        
        return SmolLMConfig()
    
    def _detect_model_profile(self):
        """Detect and cache model profile information"""
        if not self.config.adaptive_prompting:
            print("📄 Adaptive prompting disabled, using legacy ChatML format")
            return
        
        # Detect model profile
        profile_name = self.adaptive_formatter.detect_model_profile(self.current_model_name)
        try:
            self.model_profile = self.adaptive_formatter.profiles.get(profile_name) if self.adaptive_formatter.profiles else None
        except AttributeError as e:
            print(f"⚠️ Error accessing profiles: {e}")
            self.model_profile = None
        
        # Determine optimal format
        if self.config.force_format:
            try:
                self.optimal_format = PromptFormat(self.config.force_format)
                print(f"🎯 Using forced format: {self.optimal_format.value}")
            except ValueError:
                print(f"⚠️ Invalid forced format '{self.config.force_format}', using auto-detection")
                self.optimal_format = self.adaptive_formatter.get_optimal_format(self.current_model_name)
        else:
            self.optimal_format = self.adaptive_formatter.get_optimal_format(self.current_model_name)
        
        if self.model_profile:
            print(f"📊 Model Profile: {self.model_profile.name}")
            print(f"🎯 Optimal Format: {self.optimal_format.value}")
            print(f"📝 Max System Prompt: {self.model_profile.max_system_prompt_length} chars")
            print(f"💡 Best For: {', '.join(self.model_profile.characteristics)}")
    
    def set_model(self, model_name: str):
        """Set the current model and re-detect profile"""
        self.current_model_name = model_name
        if hasattr(self, 'adaptive_formatter'):
            self._detect_model_profile()
        print(f"🔄 Model set to: {model_name}")
    
    def is_ollama_available(self) -> bool:
        """Check if Ollama is running and has the model"""
        try:
            response = requests.get("http://localhost:11434/api/tags", timeout=2)
            if response.status_code == 200:
                models = response.json().get("models", [])
                return any(model.get("name", "").startswith("smollm2") for model in models)
        except:
            pass
        return False
    
    def is_gguf_available(self) -> bool:
        """Check if GGUF model is available"""
        return CTRANSFORMERS_AVAILABLE and self.gguf_path.exists()
    
    def is_transformers_available(self) -> bool:
        """Check if transformers backend is available"""
        return TRANSFORMERS_AVAILABLE
    
    def load_model(self) -> bool:
        """Load SmolLM2 model with best available backend"""
        print("🔄 Loading SmolLM2 model...")
        
        # Priority 1: GGUF (fastest, smallest memory)
        if self.is_gguf_available():
            self.backend = "gguf"
            if self._load_gguf_model():
                print("✅ SmolLM2 loaded via GGUF (ultra-fast)")
                return True
            else:
                self.backend = None
        
        # Priority 2: Ollama (if running)
        if self.is_ollama_available():
            self.backend = "ollama"
            print("✅ SmolLM2 ready via Ollama API")
            return True
        
        # Priority 3: HuggingFace transformers (fallback)
        if self.is_transformers_available():
            self.backend = "transformers"
            if self._load_transformers_model():
                print("✅ SmolLM2 loaded via HuggingFace transformers")
                return True
            else:
                self.backend = None
        
        print("❌ No SmolLM2 backend available")
        self.backend = None
        return False
    
    def _load_gguf_model(self) -> bool:
        """Load GGUF model via ctransformers"""
        try:
            self.model = CTAutoModel.from_pretrained(
                str(self.gguf_path),
                model_type='llama',
                max_new_tokens=self.config.max_tokens,
                context_length=self.config.context_length,
                temperature=self.config.temperature,
                top_p=self.config.top_p,
                repetition_penalty=self.config.repetition_penalty,
                threads=2,
                gpu_layers=0
            )
            return True
        except Exception as e:
            print(f"❌ GGUF loading failed: {e}")
            return False
    
    def _load_transformers_model(self) -> bool:
        """Load model via HuggingFace transformers"""
        try:
            model_name = "HuggingFaceTB/SmolLM2-135M-Instruct"
            self.tokenizer = AutoTokenizer.from_pretrained(model_name)
            self.model = AutoModelForCausalLM.from_pretrained(
                model_name,
                torch_dtype=torch.float16,
                device_map="auto"
            )
            return True
        except Exception as e:
            print(f"❌ Transformers loading failed: {e}")
            return False
    
    def _format_prompt_adaptive(self, user_input: str, session_context: Optional[Dict] = None) -> str:
        """Format prompt using adaptive prompting system"""
        if not self.config.adaptive_prompting:
            # Fallback to legacy ChatML format
            return self._format_messages_chatml_legacy(user_input, session_context)
        
        # Build enhanced system prompt with context
        system_prompt = self._build_enhanced_system_prompt(session_context)
        
        # Use adaptive formatter
        formatted = self.adaptive_formatter.format_prompt(
            user_input=user_input,
            system_prompt=system_prompt,
            model_name=self.current_model_name,
            format_type=self.optimal_format,
            context_messages=self.context_messages
        )
        
        return formatted
    
    def _format_messages_chatml_legacy(self, user_input: str, session_context: Optional[Dict] = None) -> str:
        """Legacy ChatML formatting for backward compatibility"""
        formatted = ""
        
        # Add system message if not present
        if not self.context_messages or self.context_messages[0].get("role") != "system":
            system_prompt = self._build_enhanced_system_prompt(session_context)
            formatted += f"<|im_start|>system\\n{system_prompt}<|im_end|>\\n"
        
        # Add context messages
        for message in self.context_messages:
            role = message.get("role", "user")
            content = message.get("content", "")
            formatted += f"<|im_start|>{role}\\n{content}<|im_end|>\\n"
        
        # Add current user message
        formatted += f"<|im_start|>user\\n{user_input}<|im_end|>\\n<|im_start|>assistant\\n"
        return formatted
    
    def _build_enhanced_system_prompt(self, session_context: Optional[Dict] = None) -> str:
        """Build system prompt with adaptive optimization"""
        base_prompt = self.config.system_prompt
        
        if self.model_profile and self.config.adaptive_prompting:
            # Optimize for model profile
            if len(base_prompt) > self.model_profile.max_system_prompt_length:
                # Smart truncation based on profile strategy
                if self.model_profile.context_strategy == "minimal":
                    base_prompt = "You are M1K3, a helpful AI assistant. Provide clear, direct responses."
                elif self.model_profile.context_strategy == "structured":
                    # Keep essential parts
                    lines = base_prompt.split('\\n')
                    essential = []
                    char_count = 0
                    for line in lines:
                        if char_count + len(line) < self.model_profile.max_system_prompt_length:
                            essential.append(line)
                            char_count += len(line) + 1
                        else:
                            break
                    base_prompt = '\\n'.join(essential)
        
        # Add dynamic context if available and appropriate
        if self.context_builder and session_context:
            try:
                context_data = self.context_builder.build_context_summary(
                    backend=self.backend or "unknown",
                    model_name="SmolLM2-135M",
                    context_used=len(self.context_messages) * 50,
                    context_max=self.config.context_length
                )
                
                # Only add context for models that can handle it
                if self.model_profile and self.model_profile.context_strategy in ["structured", "technical"] and context_data:
                    context_summary = f"Device: {context_data.get('platform', 'unknown')} ({context_data.get('device_tier', 'unknown')} tier)"
                    base_prompt += f"\\n\\n[Context: {context_summary}]"
                    
            except Exception as e:
                pass  # Skip context injection on error
        
        return base_prompt
    
    def generate(self, prompt: str, use_context: bool = True, session_context: Optional[Dict] = None, adaptive_params: Optional[Dict] = None) -> str:
        """Generate response using adaptive prompting"""
        if not self.model and self.backend != "ollama":
            return "Error: No model loaded"
        
        # Apply adaptive parameters if provided
        if adaptive_params:
            self._apply_adaptive_params(adaptive_params)
        
        # Log raw prompt
        context_messages = self.context_messages.copy() if use_context else []
        self.logger.log_raw_prompt(prompt, context_messages)
        
        # Update context
        if use_context:
            self.context_messages.append({"role": "user", "content": prompt})
            self._smart_context_trim()
        
        # Format prompt using adaptive system
        if use_context:
            formatted_prompt = self._format_prompt_adaptive(prompt, session_context)
        else:
            # Single turn with adaptive formatting
            if self.config.adaptive_prompting:
                system_prompt = self._build_enhanced_system_prompt(session_context)
                formatted_prompt = self.adaptive_formatter.format_prompt(
                    user_input=prompt,
                    system_prompt=system_prompt,
                    model_name=self.current_model_name,
                    format_type=self.optimal_format
                )
            else:
                formatted_prompt = self._format_messages_chatml_legacy(prompt, session_context)
        
        # Log formatted prompt with format info
        format_info = f"Adaptive-{self.optimal_format.value}" if self.config.adaptive_prompting else "Legacy-ChatML"
        self.logger.log_formatted_prompt(formatted_prompt, format_info)
        
        # Generate based on backend
        start_time = time.time()
        if self.backend == "ollama":
            response = self._generate_ollama_adaptive(formatted_prompt)
        elif self.backend == "gguf":
            response = self._generate_gguf(formatted_prompt)
        elif self.backend == "transformers":
            response = self._generate_transformers(formatted_prompt)
        else:
            response = "Error: No backend available"
        
        generation_time = time.time() - start_time
        
        # Update context builder metrics if available
        if self.context_builder:
            self.context_builder.record_response_time(generation_time)
            if use_context:
                self.context_builder.increment_message_count()
        
        # Log response
        self.logger.log_raw_response(response, generation_time)
        
        # Log processed response (same as raw for adaptive system)
        self.logger.log_processed_response(response, f"Adaptive-{self.optimal_format.value if self.config.adaptive_prompting else 'Legacy'}")
        
        # Update context with response
        if use_context and response and not response.startswith("Error:"):
            self.context_messages.append({"role": "assistant", "content": response})
        
        return response
    
    def _generate_ollama_adaptive(self, prompt: str) -> str:
        """Generate using Ollama API with adaptive format considerations"""
        try:
            # Prepare options based on format and model profile
            options = {
                "temperature": self.config.temperature,
                "top_p": self.config.top_p,
                "num_predict": self.config.max_tokens,
                "repeat_penalty": self.config.repetition_penalty,
            }
            
            # Adaptive stop tokens based on format
            if self.optimal_format == PromptFormat.CHATML:
                options["stop"] = self.config.stop_tokens
            elif self.optimal_format == PromptFormat.SIMPLE:
                # Simple format doesn't need special stop tokens
                options["stop"] = []
            elif self.optimal_format == PromptFormat.CONVERSATION:
                options["stop"] = ["\\n\\nHuman:", "\\n\\nAssistant:"]
            elif self.optimal_format == PromptFormat.CODE:
                options["stop"] = ["\\n\\n## ", "\\n\\n# "]
            
            payload = {
                "model": self.ollama_model,
                "prompt": prompt,
                "stream": False,
                "options": options
            }
            
            response = requests.post(
                "http://localhost:11434/api/generate",
                json=payload,
                timeout=30
            )
            
            if response.status_code == 200:
                try:
                    json_data = response.json()
                    if json_data and isinstance(json_data, dict):
                        response_text = json_data.get("response", "").strip()
                        return response_text
                    else:
                        return f"Error: Invalid JSON response from Ollama API: {json_data}"
                except (ValueError, TypeError) as json_error:
                    return f"Error: Failed to parse Ollama response - {json_error}"
            else:
                return f"Error: Ollama API returned {response.status_code}: {response.text}"
                
        except Exception as e:
            return f"Error: Ollama generation failed - {e}"
    
    def _generate_gguf(self, prompt: str) -> str:
        """Generate using GGUF model"""
        try:
            # Adaptive stop tokens
            stop_tokens = self.config.stop_tokens.copy()
            if self.optimal_format == PromptFormat.SIMPLE:
                stop_tokens = ["\\n\\n", "\\nUser:", "\\nHuman:"]
            
            response = self.model(
                prompt,
                max_new_tokens=self.config.max_tokens,
                temperature=self.config.temperature,
                top_p=self.config.top_p,
                repetition_penalty=self.config.repetition_penalty,
                stop=stop_tokens
            )
            
            # Clean up response
            response = response.strip()
            for stop_token in stop_tokens:
                if stop_token in response:
                    response = response.split(stop_token)[0]
            
            return response.strip()
            
        except Exception as e:
            return f"Error: GGUF generation failed - {e}"
    
    def _generate_transformers(self, prompt: str) -> str:
        """Generate using HuggingFace transformers"""
        try:
            inputs = self.tokenizer.encode(prompt, return_tensors="pt")
            
            with torch.no_grad():
                outputs = self.model.generate(
                    inputs,
                    max_new_tokens=self.config.max_tokens,
                    temperature=self.config.temperature,
                    top_p=self.config.top_p,
                    repetition_penalty=self.config.repetition_penalty,
                    do_sample=True,
                    pad_token_id=self.tokenizer.eos_token_id,
                    eos_token_id=self.tokenizer.eos_token_id
                )
            
            response = self.tokenizer.decode(outputs[0][len(inputs[0]):], skip_special_tokens=True)
            
            # Clean up response with format-aware stop tokens
            stop_tokens = self.config.stop_tokens
            if self.optimal_format == PromptFormat.SIMPLE:
                stop_tokens = ["\\n\\nUser:", "\\nHuman:", "\\n\\n"]
            
            for stop_token in stop_tokens:
                if stop_token in response:
                    response = response.split(stop_token)[0]
            
            return response.strip()
            
        except Exception as e:
            return f"Error: Transformers generation failed - {e}"
    
    def _smart_context_trim(self):
        """Smart context trimming with format awareness"""
        max_messages = 10
        
        # Adjust based on model profile
        if self.model_profile:
            if self.model_profile.context_strategy == "minimal":
                max_messages = 6  # Keep fewer messages for small models
            elif self.model_profile.context_strategy == "conversational":
                max_messages = 12  # Keep more for chat models
        
        if len(self.context_messages) <= max_messages:
            return
        
        # Keep system messages and recent messages
        system_msgs = [msg for msg in self.context_messages if msg.get('role') == 'system']
        recent_msgs = self.context_messages[-4:]  # Always keep last 4
        
        # Fill remaining slots with important messages
        other_msgs = [msg for msg in self.context_messages[:-4] if msg.get('role') != 'system']
        remaining_slots = max_messages - len(system_msgs) - len(recent_msgs)
        
        if remaining_slots > 0:
            # Simple strategy: keep most recent of the remaining
            keep_other = other_msgs[-remaining_slots:]
        else:
            keep_other = []
        
        # Rebuild context
        self.context_messages = system_msgs + keep_other + recent_msgs
        
        print(f"🧠 Smart trim: kept {len(self.context_messages)} messages for {self.model_profile.name if self.model_profile else 'unknown'} profile")
    
    def _apply_adaptive_params(self, adaptive_params: Dict):
        """Apply adaptive parameters"""
        if not adaptive_params:
            return
            
        param_mapping = {
            'max_new_tokens': 'max_tokens',
            'temperature': 'temperature', 
            'top_p': 'top_p',
            'repetition_penalty': 'repetition_penalty'
        }
        
        for param, config_key in param_mapping.items():
            if param in adaptive_params and hasattr(self.config, config_key):
                setattr(self.config, config_key, adaptive_params[param])
    
    def clear_context(self):
        """Clear conversation context"""
        self.context_messages = []
        print("🧹 SmolLM2 context cleared")
    
    def get_model_info(self) -> Dict:
        """Get current model information with profile data"""
        info = {
            "model": "SmolLM2-135M",
            "backend": self.backend,
            "adaptive_prompting": self.config.adaptive_prompting,
            "config": {
                "temperature": self.config.temperature,
                "max_tokens": self.config.max_tokens,
                "context_length": self.config.context_length
            },
            "context_messages": len(self.context_messages)
        }
        
        if self.model_profile and self.config.adaptive_prompting:
            info["profile"] = {
                "name": self.model_profile.name,
                "optimal_format": self.optimal_format.value,
                "characteristics": self.model_profile.characteristics,
                "context_strategy": self.model_profile.context_strategy,
                "max_system_prompt": self.model_profile.max_system_prompt_length
            }
        
        return info


def test_smollm_engine():
    """Test the SmolLM2 engine with adaptive prompting"""
    print("🧪 Testing SmolLM2 Engine with Adaptive Prompting")
    print("=" * 50)
    
    engine = SmolLMEngine()
    
    if engine.load_model():
        print("✅ Enhanced engine loaded successfully")
        print(f"📊 Model info: {engine.get_model_info()}")
        
        # Test with factual query
        print("\\n💬 Test generation (adaptive prompting):")
        response = engine.generate("Jimmy Hendrix was born in Chicago in 1942. Please correct any errors.")
        print(f"Response: {response}")
        
        # Test with adaptive prompting disabled
        engine.config.adaptive_prompting = False
        print("\\n💬 Test generation (legacy ChatML):")
        response2 = engine.generate("What is Python programming?")
        print(f"Legacy response: {response2}")
        
    else:
        print("❌ Enhanced engine loading failed")


if __name__ == "__main__":
    test_smollm_engine()