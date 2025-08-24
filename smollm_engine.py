#!/usr/bin/env python3
"""
SmolLM2 Inference Engine
Optimized engine specifically for SmolLM2-135M with proper ChatML formatting
"""

import os
import json
import subprocess
import time
import requests
from pathlib import Path
from typing import Optional, Dict, List, Generator
from dataclasses import dataclass
from prompt_logger import get_prompt_logger

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
    """SmolLM2 configuration"""
    temperature: float = 0.7
    top_p: float = 0.9
    max_tokens: int = 512
    repetition_penalty: float = 1.1
    context_length: int = 2048
    stop_tokens: List[str] = None
    system_prompt: str = "You are M1K3, a helpful local AI assistant. Be concise and direct."
    
    def __post_init__(self):
        if self.stop_tokens is None:
            self.stop_tokens = ["<|im_end|>", "<|im_start|>"]

class SmolLMEngine:
    """Dedicated SmolLM2 inference engine with multiple backend support"""
    
    def __init__(self, config_path: Optional[str] = None):
        self.config = self._load_config(config_path)
        self.config_data = self._load_raw_config(config_path)  # Store full config for templates
        self.current_style = "default"  # Current response style
        self.model = None
        self.tokenizer = None
        self.backend = None
        self.context_messages = []
        
        # Model paths
        self.models_dir = Path("models")
        self.gguf_path = self.models_dir / "SmolLM-135M.Q4_K_M.gguf"
        self.ollama_model = "smollm2:135m"
        
        # Initialize logger
        self.logger = get_prompt_logger()
        
        print("🤖 SmolLM2 Engine initialized")
        print(f"📁 Models directory: {self.models_dir}")
        print(f"⚙️ Config: {self.config.max_tokens} tokens, temp={self.config.temperature}")
    
    def _load_config(self, config_path: Optional[str] = None) -> SmolLMConfig:
        """Load SmolLM2 configuration"""
        if config_path and Path(config_path).exists():
            try:
                with open(config_path) as f:
                    data = json.load(f)
                    return SmolLMConfig(**data.get('parameters', {}))
            except Exception as e:
                print(f"⚠️ Error loading config: {e}")
        
        return SmolLMConfig()
    
    def _load_raw_config(self, config_path: Optional[str] = None) -> Dict:
        """Load raw configuration data for template access"""
        if config_path and Path(config_path).exists():
            try:
                with open(config_path) as f:
                    return json.load(f)
            except Exception as e:
                print(f"⚠️ Error loading raw config: {e}")
        return {}
    
    def set_response_style(self, style: str = "default") -> bool:
        """Set response style (default, concise, detailed, coding, creative)"""
        if not self.config_data:
            print("⚠️ No config data available for style switching")
            return False
            
        templates = self.config_data.get("prompt_templates", {})
        if style not in templates:
            available = list(templates.keys())
            print(f"⚠️ Style '{style}' not found. Available: {available}")
            return False
            
        self.current_style = style
        template = templates[style]
        
        # Update system prompt if overridden
        if "system_override" in template:
            self.config.system_prompt = template["system_override"]
            
        # Update parameters if specified
        if "parameters" in template:
            params = template["parameters"]
            for key, value in params.items():
                if hasattr(self.config, key):
                    setattr(self.config, key, value)
                    
        print(f"🎨 Response style set to: {template.get('name', style)}")
        return True
    
    def get_current_style(self) -> str:
        """Get current response style"""
        return self.current_style
    
    def is_ollama_available(self) -> bool:
        """Check if Ollama is running and has SmolLM2"""
        try:
            # Check if Ollama is running
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
            if self._load_gguf_model():
                print("✅ SmolLM2 loaded via GGUF (ultra-fast)")
                self._log_model_info("gguf")
                return True
        
        # Priority 2: Ollama (if running)
        if self.is_ollama_available():
            self.backend = "ollama"
            print("✅ SmolLM2 ready via Ollama API")
            self._log_model_info("ollama")
            return True
        
        # Priority 3: HuggingFace transformers (fallback)
        if self.is_transformers_available():
            if self._load_transformers_model():
                print("✅ SmolLM2 loaded via HuggingFace transformers")
                self._log_model_info("transformers")
                return True
        
        print("❌ No SmolLM2 backend available")
        return False
    
    def _load_gguf_model(self) -> bool:
        """Load GGUF model via ctransformers"""
        try:
            print(f"📁 Loading GGUF: {self.gguf_path}")
            self.model = CTAutoModel.from_pretrained(
                str(self.gguf_path),
                model_type='llama',
                max_new_tokens=self.config.max_tokens,
                context_length=self.config.context_length,
                temperature=self.config.temperature,
                top_p=self.config.top_p,
                repetition_penalty=self.config.repetition_penalty,
                threads=2,
                gpu_layers=0  # CPU only for compatibility
            )
            self.backend = "gguf"
            return True
        except Exception as e:
            print(f"❌ GGUF loading failed: {e}")
            return False
    
    def _load_transformers_model(self) -> bool:
        """Load model via HuggingFace transformers"""
        try:
            model_name = "HuggingFaceTB/SmolLM2-135M-Instruct"
            print(f"📥 Loading transformers: {model_name}")
            
            self.tokenizer = AutoTokenizer.from_pretrained(model_name)
            self.model = AutoModelForCausalLM.from_pretrained(
                model_name,
                torch_dtype=torch.float16,
                device_map="auto"
            )
            
            # Add chat template if not present
            if not hasattr(self.tokenizer, 'chat_template') or not self.tokenizer.chat_template:
                self.tokenizer.chat_template = self._get_chatml_template()
            
            self.backend = "transformers"
            return True
        except Exception as e:
            print(f"❌ Transformers loading failed: {e}")
            return False
    
    def _get_chatml_template(self) -> str:
        """Get ChatML template for SmolLM2"""
        return """{% for message in messages %}{% if loop.first and messages[0]['role'] != 'system' %}<|im_start|>system\n{{ system_prompt }}<|im_end|>\n{% endif %}<|im_start|>{{ message['role'] }}\n{{ message['content'] }}<|im_end|>\n{% endfor %}{% if add_generation_prompt %}<|im_start|>assistant\n{% endif %}"""
    
    def _format_messages_chatml(self, messages: List[Dict[str, str]], session_context: Optional[Dict] = None) -> str:
        """Format messages using ChatML template with enhanced context"""
        formatted = ""
        
        # Add system message if not present
        if not messages or messages[0].get("role") != "system":
            system_prompt = self._build_enhanced_system_prompt(session_context)
            formatted += f"<|im_start|>system\n{system_prompt}<|im_end|>\n"
        
        for message in messages:
            role = message.get("role", "user")
            content = message.get("content", "")
            formatted += f"<|im_start|>{role}\n{content}<|im_end|>\n"
        
        formatted += "<|im_start|>assistant\n"
        return formatted
    
    def generate(self, prompt: str, use_context: bool = True, session_context: Optional[Dict] = None, adaptive_params: Optional[Dict] = None) -> str:
        """Generate response using the loaded model with enhanced context"""
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
            
            # Smart context trimming with importance preservation
            self._smart_context_trim()
            
            formatted_prompt = self._format_messages_chatml(self.context_messages, session_context)
        else:
            # Single turn generation
            messages = [{"role": "user", "content": prompt}]
            formatted_prompt = self._format_messages_chatml(messages, session_context)
        
        # Log formatted prompt
        self.logger.log_formatted_prompt(formatted_prompt, "ChatML")
        
        # Log model parameters
        params = {
            "temperature": self.config.temperature,
            "max_tokens": self.config.max_tokens,
            "top_p": self.config.top_p,
            "repetition_penalty": self.config.repetition_penalty,
            "backend": self.backend
        }
        self.logger.log_model_parameters(params)
        
        # Generate based on backend
        start_time = time.time()
        if self.backend == "ollama":
            response = self._generate_ollama(formatted_prompt)
        elif self.backend == "gguf":
            response = self._generate_gguf(formatted_prompt)
        elif self.backend == "transformers":
            response = self._generate_transformers(formatted_prompt)
        else:
            response = "Error: No backend available"
        
        generation_time = time.time() - start_time
        
        # Log raw response
        self.logger.log_raw_response(response, generation_time)
        
        # Log processed response (same as raw for SmolLM2)
        self.logger.log_processed_response(response, "SmolLM2 ChatML output")
        
        # Update context with response
        if use_context and response and not response.startswith("Error:"):
            self.context_messages.append({"role": "assistant", "content": response})
        
        return response
    
    def _generate_ollama(self, prompt: str) -> str:
        """Generate using Ollama API"""
        try:
            payload = {
                "model": self.ollama_model,
                "prompt": prompt,
                "stream": False,
                "options": {
                    "temperature": self.config.temperature,
                    "top_p": self.config.top_p,
                    "num_predict": self.config.max_tokens,
                    "repeat_penalty": self.config.repetition_penalty,
                    "stop": self.config.stop_tokens
                }
            }
            
            response = requests.post(
                "http://localhost:11434/api/generate",
                json=payload,
                timeout=30
            )
            
            if response.status_code == 200:
                return response.json().get("response", "").strip()
            else:
                return f"Error: Ollama API returned {response.status_code}"
                
        except Exception as e:
            return f"Error: Ollama generation failed - {e}"
    
    def _generate_gguf(self, prompt: str) -> str:
        """Generate using GGUF model"""
        try:
            # Set stop tokens for generation
            stop_tokens = self.config.stop_tokens + ["\n<|im_start|>"]
            
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
            
            # Clean up response
            for stop_token in self.config.stop_tokens:
                if stop_token in response:
                    response = response.split(stop_token)[0]
            
            return response.strip()
            
        except Exception as e:
            return f"Error: Transformers generation failed - {e}"
    
    def clear_context(self):
        """Clear conversation context"""
        self.context_messages = []
        print("🧹 SmolLM2 context cleared")
    
    def _apply_adaptive_params(self, adaptive_params: Dict):
        """Apply adaptive parameters to current configuration"""
        if not adaptive_params:
            return
            
        # Update generation parameters
        param_mapping = {
            'max_new_tokens': 'max_tokens',
            'temperature': 'temperature',
            'top_p': 'top_p',
            'repetition_penalty': 'repetition_penalty'
        }
        
        for param, config_key in param_mapping.items():
            if param in adaptive_params and hasattr(self.config, config_key):
                setattr(self.config, config_key, adaptive_params[param])
                print(f"🎯 Adaptive param: {config_key} = {adaptive_params[param]}")
    
    def _build_enhanced_system_prompt(self, session_context: Optional[Dict] = None) -> str:
        """Build system prompt with context enrichment"""
        base_prompt = self.config.system_prompt
        
        if not session_context:
            return base_prompt
        
        # Add device context
        device_info = []
        if 'device_tier' in session_context:
            device_info.append(f"Running on {session_context['device_tier']} tier hardware")
        if 'platform' in session_context:
            device_info.append(f"Platform: {session_context['platform']}")
        
        # Add interface context
        interface_info = []
        if 'interface_type' in session_context:
            interface_info.append(f"Interface: {session_context['interface_type']}")
        if 'transparency_mode' in session_context:
            interface_info.append(f"Transparency: {session_context['transparency_mode']}")
            
        # Add user preferences
        prefs = session_context.get('user_preferences', {})
        pref_info = []
        if prefs.get('response_length'):
            pref_info.append(f"Preferred response length: {prefs['response_length']}")
        if prefs.get('technical_level'):
            pref_info.append(f"Technical level: {prefs['technical_level']}")
            
        # Add RAG context
        rag_info = []
        rag_context = session_context.get('rag_context', {})
        if rag_context:
            retrieved_docs = rag_context.get('retrieved_docs', [])
            if retrieved_docs:
                rag_info.append(f"Retrieved {len(retrieved_docs)} relevant documents")
                # Add key information from top documents
                for i, doc in enumerate(retrieved_docs[:2]):  # Top 2 docs
                    content = doc.get('content', '')[:200]  # First 200 chars
                    if content:
                        rag_info.append(f"Doc {i+1}: {content}...")
            
        # Combine context
        context_parts = []
        if device_info:
            context_parts.append("Device: " + ", ".join(device_info))
        if interface_info:
            context_parts.append("Interface: " + ", ".join(interface_info))
        if pref_info:
            context_parts.append("User preferences: " + ", ".join(pref_info))
        if rag_info:
            context_parts.append("Knowledge: " + " | ".join(rag_info))
            
        if context_parts:
            enhanced_prompt = f"{base_prompt}\n\nContext: {' | '.join(context_parts)}"
            return enhanced_prompt
            
        return base_prompt
    
    def _smart_context_trim(self):
        """Smart context trimming with importance-based retention"""
        max_messages = 10
        if len(self.context_messages) <= max_messages:
            return
            
        # Score messages by importance
        scored_messages = []
        for i, msg in enumerate(self.context_messages):
            importance = self._calculate_message_importance(msg, i)
            scored_messages.append((importance, i, msg))
        
        # Always keep system message (index 0) and latest messages
        system_msgs = [msg for msg in scored_messages if msg[2].get('role') == 'system']
        recent_msgs = scored_messages[-4:]  # Keep last 4 messages
        other_msgs = [msg for msg in scored_messages[:-4] if msg[2].get('role') != 'system']
        
        # Sort other messages by importance and keep top ones
        other_msgs.sort(reverse=True, key=lambda x: x[0])
        
        # Calculate how many we can keep
        keep_count = max_messages - len(system_msgs) - len(recent_msgs)
        keep_other = other_msgs[:max(0, keep_count)]
        
        # Rebuild context with kept messages
        kept_messages = system_msgs + keep_other + recent_msgs
        kept_messages.sort(key=lambda x: x[1])  # Sort by original index
        
        self.context_messages = [msg[2] for msg in kept_messages]
        
        if len(scored_messages) > max_messages:
            removed_count = len(scored_messages) - len(self.context_messages)
            print(f"🧠 Smart trim: kept {len(self.context_messages)} messages, removed {removed_count} less important ones")
    
    def _calculate_message_importance(self, message: Dict[str, str], index: int) -> float:
        """Calculate importance score for a message"""
        content = message.get('content', '')
        role = message.get('role', '')
        
        importance = 0.0
        
        # Role-based scoring
        if role == 'system':
            importance += 10.0  # Always keep system messages
        elif role == 'assistant':
            importance += 2.0   # Assistant responses are valuable
        else:
            importance += 1.0   # User messages
            
        # Content-based scoring
        content_lower = content.lower()
        
        # Important keywords
        important_keywords = [
            'error', 'exception', 'bug', 'problem', 'issue',
            'define', 'definition', 'explain', 'what is',
            'remember', 'important', 'key', 'critical',
            'function', 'class', 'method', 'variable',
            'how to', 'why', 'when', 'where'
        ]
        
        for keyword in important_keywords:
            if keyword in content_lower:
                importance += 1.0
                
        # Length bonus (longer messages often more important)
        if len(content) > 100:
            importance += 0.5
        if len(content) > 300:
            importance += 0.5
            
        # Question bonus
        if '?' in content:
            importance += 0.5
            
        # Code blocks are important
        if '```' in content or 'def ' in content or 'class ' in content:
            importance += 1.0
            
        return importance
    
    def get_model_info(self) -> Dict:
        """Get current model information"""
        return {
            "model": "SmolLM2-135M",
            "backend": self.backend,
            "config": {
                "temperature": self.config.temperature,
                "max_tokens": self.config.max_tokens,
                "context_length": self.config.context_length
            },
            "context_messages": len(self.context_messages),
            "available_backends": {
                "gguf": self.is_gguf_available(),
                "ollama": self.is_ollama_available(),
                "transformers": self.is_transformers_available()
            }
        }
    
    def _log_model_info(self, backend: str):
        """Log model information to prompt logger"""
        metadata = {
            "model_info": {
                "architecture": "llama",
                "parameter_count": 135000000,
                "context_length": 8192
            },
            "prompts": {
                "system": self.config.system_prompt,
                "template_type": "ChatML"
            }
        }
        self.logger.log_model_info("SmolLM2-135M", backend, metadata)
    
    def stream_generate(self, prompt: str, session_context: Optional[Dict] = None, adaptive_params: Optional[Dict] = None) -> Generator[str, None, None]:
        """Generate streaming response (Ollama only for now)"""
        if self.backend != "ollama":
            # For non-streaming backends, yield complete response
            yield self.generate(prompt, use_context=True, session_context=session_context, adaptive_params=adaptive_params)
            return
        
        # Apply adaptive parameters if provided
        if adaptive_params:
            self._apply_adaptive_params(adaptive_params)
        
        try:
            # Update context
            self.context_messages.append({"role": "user", "content": prompt})
            formatted_prompt = self._format_messages_chatml(self.context_messages, session_context)
            
            payload = {
                "model": self.ollama_model,
                "prompt": formatted_prompt,
                "stream": True,
                "options": {
                    "temperature": self.config.temperature,
                    "top_p": self.config.top_p,
                    "num_predict": self.config.max_tokens,
                    "repeat_penalty": self.config.repetition_penalty,
                    "stop": self.config.stop_tokens
                }
            }
            
            response = requests.post(
                "http://localhost:11434/api/generate",
                json=payload,
                stream=True,
                timeout=30
            )
            
            full_response = ""
            for line in response.iter_lines():
                if line:
                    data = json.loads(line.decode('utf-8'))
                    if 'response' in data:
                        token = data['response']
                        full_response += token
                        yield token
                    if data.get('done', False):
                        break
            
            # Update context with complete response
            if full_response:
                self.context_messages.append({"role": "assistant", "content": full_response})
                
        except Exception as e:
            yield f"Error: Streaming failed - {e}"


def test_smollm_engine():
    """Test SmolLM2 engine"""
    print("🧪 Testing SmolLM2 Engine")
    print("=" * 40)
    
    engine = SmolLMEngine()
    
    print("\n🔄 Loading model...")
    if engine.load_model():
        print("✅ Model loaded successfully")
        
        print(f"\n📊 Model info: {engine.get_model_info()}")
        
        print("\n💬 Test generation:")
        response = engine.generate("Hello, can you help me with Python?")
        print(f"Response: {response}")
        
        print("\n🔄 Test context:")
        response2 = engine.generate("What did I just ask about?")
        print(f"Context response: {response2}")
        
    else:
        print("❌ Model loading failed")
        print("Available backends:")
        print(f"  GGUF: {engine.is_gguf_available()}")
        print(f"  Ollama: {engine.is_ollama_available()}")
        print(f"  Transformers: {engine.is_transformers_available()}")

if __name__ == "__main__":
    test_smollm_engine()