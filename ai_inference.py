#!/usr/bin/env python3
"""
M1K3 Local AI Inference Module
Integrates llama.cpp with SmolLM-135M for local AI inference
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

try:
    from ctransformers import AutoModelForCausalLM
except ImportError:
    print("ctransformers not installed. Run: pip install ctransformers")
    sys.exit(1)

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
    def __init__(self, model_path: str = None):
        self.model_path = model_path or self._get_default_model_path()
        self.model: Optional[AutoModelForCausalLM] = None
        self.context = ConversationContext()
        self.loading = False
        
    def _get_default_model_path(self) -> str:
        """Get default model path"""
        models_dir = Path("models")
        models_dir.mkdir(exist_ok=True)
        return str(models_dir / "SmolLM-135M.Q4_K_M.gguf")
        
    def is_model_available(self) -> bool:
        """Check if model file exists"""
        return Path(self.model_path).exists()
        
    def load_model(self) -> bool:
        """Load the AI model"""
        if not self.is_model_available():
            print(f"Model not found at {self.model_path}")
            return False
            
        print(f"Loading model from {self.model_path}...")
        start_time = time.time()
        
        try:
            self.model = AutoModelForCausalLM.from_pretrained(
                self.model_path,
                model_type="llama",
                max_new_tokens=512,
                context_length=2048,
                threads=4,
                gpu_layers=0  # CPU inference for compatibility
            )
            
            load_time = time.time() - start_time
            print(f"Model loaded in {load_time:.2f} seconds")
            return True
            
        except Exception as e:
            print(f"Failed to load model: {e}")
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
                
            # Format conversation for the model
            formatted_prompt = self._format_conversation()
            
            start_time = time.time()
            response_text = ""
            
            # Generate response with streaming
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
        if not self.context.messages:
            return ""
            
        # Simple format for SmolLM
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
    engine = LocalAIEngine()
    
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