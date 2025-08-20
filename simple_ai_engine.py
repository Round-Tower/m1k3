#!/usr/bin/env python3
"""
Simple AI Engine for M1K3 - Mock implementation for MVP testing
This provides a working foundation that can be replaced with actual AI inference
"""

import time
import random
from typing import Generator, List, Dict
from dataclasses import dataclass
from pathlib import Path

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
        self.current_tokens += len(content) // 4
        
    def should_trim(self) -> bool:
        """Check if context needs trimming"""
        return self.current_tokens > self.max_tokens * 0.8
        
    def trim_context(self):
        """Trim older messages to stay within token limit"""
        while len(self.messages) > 2 and self.should_trim():
            removed = self.messages.pop(1)
            self.current_tokens -= len(removed["content"]) // 4

class SimpleAIEngine:
    """Simple AI engine that provides realistic responses for MVP testing"""
    
    def __init__(self, model_path: str = None):
        self.model_path = model_path or self._get_default_model_path()
        self.context = ConversationContext()
        self.model_loaded = False
        
        # Response templates for realistic behavior
        self.response_templates = [
            "That's an interesting question about {topic}. Let me think about this...",
            "I understand you're asking about {topic}. Here's what I can tell you:",
            "Great question! Regarding {topic}, I would say:",
            "Let me help you with {topic}. From what I know:",
            "Thanks for asking about {topic}. Here's my perspective:",
        ]
        
        self.thinking_responses = [
            "Based on my understanding",
            "From what I can analyze",
            "Looking at this problem",
            "Considering the context",
            "After processing your request",
        ]
        
    def _get_default_model_path(self) -> str:
        """Get default model path"""
        models_dir = Path("models")
        models_dir.mkdir(exist_ok=True)
        return str(models_dir / "SmolLM-135M-Q4_K_M.gguf")
        
    def is_model_available(self) -> bool:
        """Check if model file exists"""
        return Path(self.model_path).exists()
        
    def load_model(self) -> bool:
        """Simulate model loading"""
        print(f"Loading model from {self.model_path}...")
        start_time = time.time()
        
        # Simulate loading time
        time.sleep(2)
        
        if self.is_model_available():
            load_time = time.time() - start_time
            print(f"Model loaded in {load_time:.2f} seconds")
            self.model_loaded = True
            return True
        else:
            print("Model file not found, running in demo mode")
            self.model_loaded = True  # Allow demo mode
            return True
            
    def generate_response(self, prompt: str, max_tokens: int = 512) -> Generator[str, None, None]:
        """Generate a realistic streaming response"""
        if not self.model_loaded:
            yield "Error: Model not loaded"
            return
            
        # Add user message to context
        self.context.add_message("user", prompt)
        
        # Trim context if needed
        if self.context.should_trim():
            self.context.trim_context()
            
        start_time = time.time()
        
        try:
            # Generate a contextual response
            response = self._generate_contextual_response(prompt)
            
            # Stream the response word by word for realistic effect
            words = response.split()
            response_text = ""
            
            for i, word in enumerate(words):
                # Add natural typing delays
                delay = random.uniform(0.05, 0.15)
                time.sleep(delay)
                
                if i == 0:
                    token = word
                else:
                    token = " " + word
                    
                response_text += token
                yield token
                
            # Add assistant response to context
            if response_text.strip():
                self.context.add_message("assistant", response_text.strip())
                
            generation_time = time.time() - start_time
            tokens_per_sec = len(words) / generation_time if generation_time > 0 else 0
            print(f"\n[Generated in {generation_time:.2f}s, ~{tokens_per_sec:.1f} tokens/sec]")
            
        except Exception as e:
            yield f"Error generating response: {e}"
            
    def _generate_contextual_response(self, prompt: str) -> str:
        """Generate a contextual response based on the prompt"""
        prompt_lower = prompt.lower()
        
        # Programming-related responses
        if any(term in prompt_lower for term in ['code', 'programming', 'python', 'javascript', 'function']):
            return self._generate_programming_response(prompt)
            
        # General help responses
        elif any(term in prompt_lower for term in ['help', 'how', 'what', 'why', 'explain']):
            return self._generate_help_response(prompt)
            
        # Greeting responses
        elif any(term in prompt_lower for term in ['hello', 'hi', 'hey', 'greetings']):
            return "Hello! I'm M1K3, your local AI assistant. How can I help you today?"
            
        # Default contextual response
        else:
            return self._generate_default_response(prompt)
            
    def _generate_programming_response(self, prompt: str) -> str:
        """Generate programming-related response"""
        responses = [
            "I'd be happy to help with programming! While I'm running locally on a small model, I can assist with basic coding concepts and provide guidance on common programming patterns.",
            "Great programming question! Let me think about this. With my current capabilities, I can help explain concepts and provide simple code examples.",
            "Programming is fascinating! Even with my compact model size, I can help with basic syntax questions and general programming principles.",
        ]
        return random.choice(responses)
        
    def _generate_help_response(self, prompt: str) -> str:
        """Generate help-related response"""
        responses = [
            "I'm here to help! As a local AI running on SmolLM-135M, I can assist with general questions, basic programming, and provide explanations on various topics.",
            "Happy to assist you! While I'm a smaller model running locally, I'll do my best to provide helpful information and guidance.",
            "Let me help you with that! I'm running locally which means fast responses, though my knowledge is more focused than larger models.",
        ]
        return random.choice(responses)
        
    def _generate_default_response(self, prompt: str) -> str:
        """Generate default contextual response"""
        # Extract key words from prompt for context
        words = prompt.split()
        topic = "your question"
        if len(words) > 1:
            # Try to find a meaningful word as topic
            meaningful_words = [w for w in words if len(w) > 3 and w.lower() not in ['what', 'how', 'why', 'when', 'where']]
            if meaningful_words:
                topic = meaningful_words[0]
        
        template = random.choice(self.response_templates)
        thinking = random.choice(self.thinking_responses)
        
        return f"{template.format(topic=topic)} {thinking}, I can provide some insights, though please keep in mind I'm a compact local model focused on helpful assistance."
        
    def clear_context(self):
        """Clear conversation context"""
        self.context = ConversationContext()
        print("Conversation context cleared.")
        
    def get_memory_usage(self) -> Dict[str, str]:
        """Get current memory usage info"""
        try:
            import psutil
            process = psutil.Process()
            memory_mb = process.memory_info().rss / 1024 / 1024
        except ImportError:
            memory_mb = 0.0
            
        return {
            "memory_mb": f"{memory_mb:.1f}MB",
            "context_tokens": str(self.context.current_tokens),
            "context_messages": str(len(self.context.messages))
        }

if __name__ == "__main__":
    # Simple CLI test
    engine = SimpleAIEngine()
    
    if not engine.load_model():
        print("Failed to load model")
        exit(1)
        
    print("M1K3 AI Engine Ready! (Demo Mode)")
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