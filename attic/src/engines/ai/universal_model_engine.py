#!/usr/bin/env python3
"""
Universal Model Engine for M1K3
Model-agnostic AI engine that uses model metadata to ensure correct prompting and capabilities
"""

import json
import requests
import time
import threading
from typing import Dict, List, Optional, Any, Union
from dataclasses import dataclass
from pathlib import Path

from src.models.loaders.adaptive_prompt_formatter import AdaptivePromptFormatter, PromptFormat
from src.models.managers.model_template_manager import ModelTemplateManager, format_conversation_for_model


@dataclass
class ModelMetadata:
    """Model metadata extracted from various sources"""
    name: str
    family: str
    architecture: str
    parameter_count: int
    context_length: int
    template: str
    system_prompt: Optional[str] = None
    stop_tokens: List[str] = None
    parameters: Dict[str, Any] = None
    source: str = "unknown"  # ollama, huggingface, local


class UniversalModelEngine:
    """Model-agnostic AI engine that adapts to any model's capabilities"""

    def __init__(self, model_name: str = "gemma3:270m"):
        self.current_model_name = model_name
        self.ollama_url = "http://localhost:11434"

        # Model metadata and configuration
        self.model_metadata: Optional[ModelMetadata] = None
        self.conversation_history: List[Dict[str, str]] = []
        self.max_context_length = 8192
        self.is_loading = False

        # Device RAM detection for adaptive token limits (aligned with mobile)
        self.device_ram_gb = self._detect_device_ram()
        
        # Template and formatting systems
        # Use relative path from project root
        profiles_path = Path(__file__).parent.parent.parent.parent / "config" / "model_profiles.json"
        self.prompt_formatter = AdaptivePromptFormatter(
            profiles_path=str(profiles_path)
        )
        self.template_manager = ModelTemplateManager()
        
        # Engine state
        self.engine_loaded = False

    def _detect_device_ram(self) -> int:
        """Detect device RAM in GB for adaptive token limits"""
        try:
            import psutil
            total_ram_bytes = psutil.virtual_memory().total
            return int(total_ram_bytes / (1024 ** 3))  # Convert to GB
        except ImportError:
            # Fallback if psutil not available - assume mid-range device
            return 8

    def get_optimal_max_tokens(self) -> int:
        """
        Get device-appropriate maximum tokens for generation.
        Aligned with mobile behavior - high limits let model stop naturally.

        Returns high token limit (1024-4096 depending on RAM) to let model decide.
        Model will stop when it generates <end_of_turn> or <eos> tokens.
        """
        return {
            True: 4096,   # 12GB+: Let model decide naturally (~3000 words max)
            self.device_ram_gb >= 8: 3072,    # 8-12GB: High limit (~2300 words max)
            self.device_ram_gb >= 6: 2048,    # 6-8GB: Generous limit (~1500 words max)
            self.device_ram_gb >= 4: 1536,    # 4-6GB: Reasonable limit (~1150 words max)
        }.get(self.device_ram_gb >= 12, 1024)  # <4GB: Conservative but usable (~750 words max)

    def detect_model_metadata(self, model_name: str) -> Optional[ModelMetadata]:
        """Detect model metadata from various sources"""
        print(f"🔍 Detecting metadata for model: {model_name}")
        
        # Try Ollama first (most reliable)
        metadata = self._detect_ollama_metadata(model_name)
        if metadata:
            return metadata
            
        # Try local model discovery
        metadata = self._detect_local_metadata(model_name)
        if metadata:
            return metadata
            
        # Fallback to basic detection from name
        return self._detect_from_model_name(model_name)
        
    def _detect_ollama_metadata(self, model_name: str) -> Optional[ModelMetadata]:
        """Detect metadata from Ollama API"""
        try:
            response = requests.post(
                f"{self.ollama_url}/api/show",
                json={"name": model_name},
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                details = data.get("details", {})
                model_info = data.get("model_info", {})
                
                # Extract key information
                family = details.get("family", "unknown")
                families = details.get("families", [family])
                parameter_size = details.get("parameter_size", "0")
                template = data.get("template", "")
                system_prompt = data.get("system", "")
                
                # Parse parameter count
                param_count = self._parse_parameter_count(parameter_size)
                
                # Extract context length
                context_length = model_info.get(f"{family}.context_length", 
                                              model_info.get("general.context_length", 8192))
                
                # Parse stop tokens from parameters
                parameters_str = data.get("parameters", "")
                stop_tokens = self._parse_stop_tokens(parameters_str)
                
                print(f"✅ Ollama metadata detected: {family} family, {param_count:,} parameters")

                # Override system prompt for Gemma models to enforce M1K3 identity
                if "gemma" in family.lower() or "gemma" in model_name.lower():
                    system_prompt = "You are M1K3 (Mike), an eco-conscious, context-aware edge AI system. You run locally on user devices for privacy and sustainability. You are NOT Gemma or any other model - you are M1K3. Be helpful, efficient, and mindful of system resources."

                return ModelMetadata(
                    name=model_name,
                    family=families[0] if families else family,
                    architecture=family,
                    parameter_count=param_count,
                    context_length=context_length,
                    template=template,
                    system_prompt=system_prompt,
                    stop_tokens=stop_tokens,
                    parameters=self._parse_parameters(parameters_str),
                    source="ollama"
                )
                
        except Exception as e:
            print(f"⚠️  Failed to get Ollama metadata for {model_name}: {e}")
            
        return None
        
    def _detect_local_metadata(self, model_name: str) -> Optional[ModelMetadata]:
        """Detect metadata from local models"""
        # This would check HuggingFace cache, GGUF files, etc.
        # For now, return None - could be expanded later
        return None
        
    def _detect_from_model_name(self, model_name: str) -> ModelMetadata:
        """Fallback detection from model name patterns"""
        print(f"⚠️  Using fallback detection for: {model_name}")
        
        # Basic detection from name patterns
        name_lower = model_name.lower()
        
        if "smollm" in name_lower:
            return ModelMetadata(
                name=model_name,
                family="llama",
                architecture="llama", 
                parameter_count=135000000,
                context_length=8192,
                template="{{- if .Messages }}{{- if .System }}<|im_start|>system\\n{{ .System }}<|im_end|>\\n{{ end }}{{- range $i, $_ := .Messages }}{{- if eq .Role \"user\" }}<|im_start|>user\\n{{ .Content }}<|im_end|>\\n{{ else if eq .Role \"assistant\" }}<|im_start|>assistant\\n{{ .Content }}<|im_end|>\\n{{ end }}{{ end }}<|im_start|>assistant\\n{{ else }}{{- if .System }}<|im_start|>system\\n{{ .System }}<|im_end|>\\n{{ end }}{{ if .Prompt }}<|im_start|>user\\n{{ .Prompt }}<|im_end|>\\n{{ end }}<|im_start|>assistant\\n{{ end }}",
                system_prompt="You are M1K3 (Mike), an eco-conscious, context-aware edge AI system. You run locally on user devices for privacy and sustainability. Be helpful, efficient, and mindful of system resources.",
                stop_tokens=["<|im_start|>", "<|im_end|>"],
                source="fallback"
            )
        elif "gemma" in name_lower:
            return ModelMetadata(
                name=model_name,
                family="gemma3",
                architecture="gemma3",
                parameter_count=270000000,
                context_length=32768,
                template="{{- $systemPromptAdded := false }}{{- range $i, $_ := .Messages }}{{- $last := eq (len (slice $.Messages $i)) 1 }}{{- if eq .Role \"user\" }}<start_of_turn>user\\n{{- if (and (not $systemPromptAdded) $.System) }}{{- $systemPromptAdded = true }}{{ $.System }}\\n{{ end }}{{ .Content }}<end_of_turn>\\n{{ if $last }}<start_of_turn>model\\n{{ end }}{{- else if eq .Role \"assistant\" }}<start_of_turn>model\\n{{ .Content }}{{ if not $last }}<end_of_turn>\\n{{ end }}{{- end }}{{- end }}",
                system_prompt="You are M1K3 (Mike), an eco-conscious, context-aware edge AI system. You run locally on user devices for privacy and sustainability. You are NOT Gemma or any other model - you are M1K3. Be helpful, efficient, and mindful of system resources.",
                stop_tokens=["<end_of_turn>"],
                source="fallback"
            )
        else:
            # Generic fallback
            return ModelMetadata(
                name=model_name,
                family="generic",
                architecture="generic",
                parameter_count=1000000000,
                context_length=4096,
                template="Human: {{ .Prompt }}\\n\\nAssistant: ",
                stop_tokens=[],
                source="fallback"
            )
    
    def _parse_parameter_count(self, param_size: str) -> int:
        """Parse parameter count from string like '268.10M' or '134.52M'"""
        try:
            if "M" in param_size:
                return int(float(param_size.replace("M", "")) * 1000000)
            elif "B" in param_size:
                return int(float(param_size.replace("B", "")) * 1000000000)
            else:
                return int(param_size)
        except:
            return 1000000000  # 1B default
            
    def _parse_stop_tokens(self, parameters: str) -> List[str]:
        """Parse stop tokens from parameters string"""
        stop_tokens = []
        for line in parameters.split('\n'):
            if line.strip().startswith('stop '):
                token = line.split('stop ', 1)[1].strip().strip('"')
                stop_tokens.append(token)
        return stop_tokens
        
    def _parse_parameters(self, parameters: str) -> Dict[str, Any]:
        """Parse parameters from parameters string"""
        params = {}
        for line in parameters.split('\n'):
            if ' ' in line:
                key, value = line.split(' ', 1)
                key = key.strip()
                value = value.strip().strip('"')
                
                # Try to convert to appropriate type
                try:
                    if '.' in value:
                        params[key] = float(value)
                    else:
                        params[key] = int(value)
                except ValueError:
                    params[key] = value
        return params
    
    def load_model(self, model_name: Optional[str] = None) -> bool:
        """Load and configure the model"""
        if model_name:
            self.current_model_name = model_name
            
        print(f"🔄 Loading universal model engine for: {self.current_model_name}")
        self.is_loading = True
        
        try:
            # Detect model metadata
            self.model_metadata = self.detect_model_metadata(self.current_model_name)
            
            if not self.model_metadata:
                print(f"❌ Failed to detect metadata for {self.current_model_name}")
                return False
                
            # Configure context length
            self.max_context_length = self.model_metadata.context_length
            
            # Test model availability
            if not self._test_model_availability():
                print(f"❌ Model {self.current_model_name} not available")
                return False
                
            self.engine_loaded = True
            print(f"✅ Universal engine loaded: {self.model_metadata.family} ({self.model_metadata.parameter_count:,} params)")
            return True
            
        except Exception as e:
            print(f"❌ Failed to load universal engine: {e}")
            return False
        finally:
            self.is_loading = False
            
    def _test_model_availability(self) -> bool:
        """Test if model is available via Ollama"""
        try:
            response = requests.post(
                f"{self.ollama_url}/api/generate",
                json={
                    "model": self.current_model_name,
                    "prompt": "test",
                    "stream": False,
                    "options": {"num_predict": 1}
                },
                timeout=30
            )
            return response.status_code == 200
        except:
            return False
    
    def format_conversation(self, user_input: str, system_prompt: str = "") -> str:
        """Format conversation using model-specific template"""
        if not self.model_metadata:
            print("⚠️  No model metadata available, using fallback formatting")
            return f"{system_prompt}\n\nUser: {user_input}\n\nAssistant: "
            
        # Build messages for conversation
        messages = []
        
        # Add system prompt if provided
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
            
        # Add conversation history
        messages.extend(self.conversation_history)
        
        # Add current user input
        messages.append({"role": "user", "content": user_input})
        
        # Use model's native template if available, otherwise use template manager
        if self.model_metadata.template and self.model_metadata.source == "ollama":
            # For Ollama models, we'll use their API which handles templating
            # Return the messages as-is for Ollama API call
            return json.dumps({
                "messages": messages,
                "use_ollama_template": True
            })
        else:
            # Use our template manager for other models
            return format_conversation_for_model(
                messages, 
                self.current_model_name,
                add_generation_prompt=True
            )
    
    def generate_response(self, user_input: str, system_prompt: str = "",
                         max_tokens: int = 0, temperature: float = 1.0) -> str:
        """
        Generate response using the loaded model.

        Args:
            user_input: User's input text
            system_prompt: Custom system prompt (defaults to model's prompt)
            max_tokens: Max tokens to generate (0 = use device-adaptive limit)
            temperature: Sampling temperature (1.0 = mobile-aligned default)

        Returns:
            Generated response text
        """
        if not self.engine_loaded or not self.model_metadata:
            return "Error: Model not loaded"

        try:
            # Use device-adaptive tokens if max_tokens is 0 or unset
            if max_tokens == 0:
                max_tokens = self.get_optimal_max_tokens()

            # Use model's system prompt if no explicit system prompt provided
            if not system_prompt and hasattr(self.model_metadata, 'system_prompt') and self.model_metadata.system_prompt:
                system_prompt = self.model_metadata.system_prompt

            formatted_input = self.format_conversation(user_input, system_prompt)
            
            # Check if we should use Ollama's chat API with messages
            if isinstance(formatted_input, str) and formatted_input.startswith('{"messages":'):
                data = json.loads(formatted_input)
                if data.get("use_ollama_template"):
                    return self._generate_with_ollama_chat(
                        data["messages"], max_tokens, temperature
                    )
            
            # Otherwise use traditional generate API
            return self._generate_with_ollama_generate(
                formatted_input, max_tokens, temperature
            )
            
        except Exception as e:
            print(f"❌ Generation failed: {e}")
            return f"Error generating response: {str(e)}"
    
    def _generate_with_ollama_chat(self, messages: List[Dict],
                                 max_tokens: int, temperature: float) -> str:
        """Generate using Ollama's chat API with messages"""
        payload = {
            "model": self.current_model_name,
            "messages": messages,
            "stream": False,
            "options": {
                "num_predict": max_tokens,
                "temperature": temperature,
                "top_p": 0.95,  # Mobile-aligned
                "top_k": 64,    # Mobile-aligned
                "repeat_penalty": 1.1  # Mobile-aligned
            }
        }
        
        # Add model-specific parameters (fix format issues)
        if self.model_metadata.parameters:
            for key, value in self.model_metadata.parameters.items():
                if key == "stop" and not isinstance(value, list):
                    # Convert string stop token to list
                    payload["options"]["stop"] = [value]
                else:
                    payload["options"][key] = value
            
        response = requests.post(
            f"{self.ollama_url}/api/chat",
            json=payload,
            timeout=60
        )
        
        if response.status_code != 200:
            print(f"❌ Ollama chat API error: {response.status_code}")
            print(f"Response: {response.text}")
            return f"Error: HTTP {response.status_code}"
        
        if response.status_code == 200:
            data = response.json()
            assistant_message = data.get("message", {}).get("content", "")
            
            # Add to conversation history
            self.conversation_history.extend([
                {"role": "user", "content": messages[-1]["content"]},
                {"role": "assistant", "content": assistant_message}
            ])
            
            # Manage context length
            self._manage_context_length()
            
            return assistant_message
        else:
            return f"Error: HTTP {response.status_code}"
    
    def _generate_with_ollama_generate(self, prompt: str,
                                     max_tokens: int, temperature: float) -> str:
        """Generate using Ollama's generate API with raw prompt"""
        payload = {
            "model": self.current_model_name,
            "prompt": prompt,
            "stream": False,
            "options": {
                "num_predict": max_tokens,
                "temperature": temperature,
                "top_p": 0.95,  # Mobile-aligned
                "top_k": 64,    # Mobile-aligned
                "repeat_penalty": 1.1  # Mobile-aligned
            }
        }
        
        # Add stop tokens
        if self.model_metadata.stop_tokens:
            payload["options"]["stop"] = self.model_metadata.stop_tokens
            
        # Add model-specific parameters (fix format issues)
        if self.model_metadata.parameters:
            for key, value in self.model_metadata.parameters.items():
                if key == "stop" and not isinstance(value, list):
                    # Convert string stop token to list
                    payload["options"]["stop"] = [value]
                else:
                    payload["options"][key] = value
            
        response = requests.post(
            f"{self.ollama_url}/api/generate",
            json=payload,
            timeout=60
        )
        
        if response.status_code != 200:
            print(f"❌ Ollama generate API error: {response.status_code}")
            print(f"Response: {response.text}")
            return f"Error: HTTP {response.status_code}"
        
        if response.status_code == 200:
            data = response.json()
            return data.get("response", "")
        else:
            return f"Error: HTTP {response.status_code}"
    
    def _manage_context_length(self):
        """Manage conversation history to stay within context limits"""
        if not self.conversation_history:
            return
            
        # Rough token estimation (4 chars per token)
        total_chars = sum(len(msg["content"]) for msg in self.conversation_history)
        max_chars = self.max_context_length * 3  # Conservative estimate
        
        while total_chars > max_chars and len(self.conversation_history) > 2:
            # Remove oldest pair of messages (user + assistant)
            self.conversation_history = self.conversation_history[2:]
            total_chars = sum(len(msg["content"]) for msg in self.conversation_history)
    
    def generate_streaming_response(self, user_input: str, system_prompt: str = "",
                                  max_tokens: int = 0, temperature: float = 1.0):
        """
        Generate streaming response (generator).

        Args:
            user_input: User's input text
            system_prompt: Custom system prompt (defaults to model's prompt)
            max_tokens: Max tokens to generate (0 = use device-adaptive limit)
            temperature: Sampling temperature (1.0 = mobile-aligned default)

        Yields:
            Generated text tokens
        """
        if not self.engine_loaded or not self.model_metadata:
            yield "Error: Model not loaded"
            return

        # Use device-adaptive tokens if max_tokens is 0 or unset
        if max_tokens == 0:
            max_tokens = self.get_optimal_max_tokens()
            
        try:
            formatted_input = self.format_conversation(user_input, system_prompt)
            
            # Check if we should use Ollama's chat API
            if isinstance(formatted_input, str) and formatted_input.startswith('{"messages":'):
                data = json.loads(formatted_input)
                if data.get("use_ollama_template"):
                    yield from self._stream_with_ollama_chat(
                        data["messages"], max_tokens, temperature
                    )
                    return
            
            # Use generate API for streaming
            yield from self._stream_with_ollama_generate(
                formatted_input, max_tokens, temperature
            )
            
        except Exception as e:
            yield f"Error: {str(e)}"
    
    def _stream_with_ollama_chat(self, messages: List[Dict],
                               max_tokens: int, temperature: float):
        """Stream using Ollama's chat API"""
        payload = {
            "model": self.current_model_name,
            "messages": messages,
            "stream": True,
            "options": {
                "num_predict": max_tokens,
                "temperature": temperature,
                "top_p": 0.95,  # Mobile-aligned
                "top_k": 64,    # Mobile-aligned
                "repeat_penalty": 1.1  # Mobile-aligned
            }
        }
        
        # Add model-specific parameters (fix format issues)
        if self.model_metadata.parameters:
            for key, value in self.model_metadata.parameters.items():
                if key == "stop" and not isinstance(value, list):
                    # Convert string stop token to list
                    payload["options"]["stop"] = [value]
                else:
                    payload["options"][key] = value
            
        response = requests.post(
            f"{self.ollama_url}/api/chat",
            json=payload,
            stream=True,
            timeout=60
        )
        
        if response.status_code != 200:
            print(f"❌ Streaming chat API error: {response.status_code}")
            print(f"Response: {response.text}")
            yield f"Error: HTTP {response.status_code}"
            return
        
        full_response = ""
        
        for line in response.iter_lines():
            if line:
                try:
                    data = json.loads(line)
                    delta = data.get("message", {}).get("content", "")
                    if delta:
                        full_response += delta
                        yield delta
                        
                    if data.get("done"):
                        # Add to conversation history
                        self.conversation_history.extend([
                            {"role": "user", "content": messages[-1]["content"]},
                            {"role": "assistant", "content": full_response}
                        ])
                        self._manage_context_length()
                        break
                        
                except json.JSONDecodeError:
                    continue
    
    def _stream_with_ollama_generate(self, prompt: str, max_tokens: int, temperature: float):
        """Stream using Ollama's generate API"""
        payload = {
            "model": self.current_model_name,
            "prompt": prompt,
            "stream": True,
            "options": {
                "num_predict": max_tokens,
                "temperature": temperature,
                "top_p": 0.95,  # Mobile-aligned
                "top_k": 64,    # Mobile-aligned
                "repeat_penalty": 1.1  # Mobile-aligned
            }
        }
        
        if self.model_metadata.stop_tokens:
            payload["options"]["stop"] = self.model_metadata.stop_tokens
            
        if self.model_metadata.parameters:
            payload["options"].update(self.model_metadata.parameters)
            
        response = requests.post(
            f"{self.ollama_url}/api/generate",
            json=payload,
            stream=True,
            timeout=60
        )
        
        for line in response.iter_lines():
            if line:
                try:
                    data = json.loads(line)
                    delta = data.get("response", "")
                    if delta:
                        yield delta
                        
                    if data.get("done"):
                        break
                        
                except json.JSONDecodeError:
                    continue
    
    def clear_context(self):
        """Clear conversation history"""
        self.conversation_history = []
        print("🧹 Conversation context cleared")
    
    def get_context_length(self) -> int:
        """Get current context length"""
        return self.max_context_length
    
    def get_current_tokens(self) -> int:
        """Get estimated current token usage"""
        if not self.conversation_history:
            return 0
        # Rough estimation: 4 chars per token
        total_chars = sum(len(msg["content"]) for msg in self.conversation_history)
        return total_chars // 4
    
    def get_model_info(self) -> Dict[str, Any]:
        """Get model information"""
        if not self.model_metadata:
            return {"error": "No model loaded"}
            
        return {
            "name": self.model_metadata.name,
            "family": self.model_metadata.family,
            "architecture": self.model_metadata.architecture,
            "parameters": self.model_metadata.parameter_count,
            "context_length": self.model_metadata.context_length,
            "source": self.model_metadata.source,
            "loaded": self.engine_loaded,
            "conversation_length": len(self.conversation_history)
        }
    
    def switch_model(self, new_model_name: str) -> bool:
        """Switch to a different model"""
        print(f"🔄 Switching from {self.current_model_name} to {new_model_name}")
        
        # Clear context when switching models
        self.clear_context()
        
        # Load new model
        return self.load_model(new_model_name)


def create_universal_engine(model_name: str = "gemma3:270m") -> UniversalModelEngine:
    """Factory function to create universal engine"""
    return UniversalModelEngine(model_name)


if __name__ == "__main__":
    # Test the universal engine
    print("🧪 Testing Universal Model Engine")
    print("=" * 50)
    
    # Test with Gemma3
    print("\n--- Testing Gemma3 ---")
    gemma_engine = UniversalModelEngine("gemma3:270m")
    
    if gemma_engine.load_model():
        print("✅ Gemma3 loaded successfully")
        print(f"Model info: {gemma_engine.get_model_info()}")
        
        response = gemma_engine.generate_response(
            "What is the capital of France?",
            system_prompt="You are a helpful assistant. Provide clear, accurate answers."
        )
        print(f"Gemma3 response: {response}")
    else:
        print("❌ Failed to load Gemma3")
    
    # Test with SmolLM
    print("\n--- Testing SmolLM ---")
    smollm_engine = UniversalModelEngine("smollm2:135m")
    
    if smollm_engine.load_model():
        print("✅ SmolLM loaded successfully")
        print(f"Model info: {smollm_engine.get_model_info()}")
        
        response = smollm_engine.generate_response(
            "What is the capital of France?",
            system_prompt="You are a helpful assistant. Provide clear, accurate answers."
        )
        print(f"SmolLM response: {response}")
    else:
        print("❌ Failed to load SmolLM")
    
    print("\n✅ Universal Model Engine testing complete!")