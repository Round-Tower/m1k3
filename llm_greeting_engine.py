#!/usr/bin/env python3
"""
M1K3 LLM-Powered Greeting Engine
Generates dynamic, contextual startup greetings using the AI engine
"""

import time
import datetime
from typing import Optional, Dict, Any
from dataclasses import dataclass

@dataclass
class GreetingContext:
    """Context information for generating greetings"""
    time_of_day: str
    cpu_usage: float
    memory_percent: float
    battery_level: Optional[int] = None
    battery_status: Optional[str] = None
    thermal_state: str = "normal"
    voice_enabled: bool = True
    avatar_enabled: bool = True
    ai_model: str = "unknown"
    session_count: int = 1
    uptime_hours: float = 0.0
    
class LLMGreetingEngine:
    """
    LLM-powered greeting generator that creates contextual startup messages
    """
    
    def __init__(self, adaptive_ai_engine=None):
        self.adaptive_ai_engine = adaptive_ai_engine
        self.greeting_cache = {}
        self.last_greeting_time = 0
        self.cache_duration = 300  # 5 minutes
        
    def generate_greeting(self, context: GreetingContext, max_length: int = 80) -> str:
        """
        Generate a contextual greeting using the LLM
        
        Args:
            context: System context for greeting generation
            max_length: Maximum greeting length in characters
            
        Returns:
            Generated greeting string
        """
        
        # Check cache first to avoid regenerating identical greetings
        cache_key = self._create_cache_key(context)
        current_time = time.time()
        
        if (cache_key in self.greeting_cache and 
            current_time - self.last_greeting_time < self.cache_duration):
            return self.greeting_cache[cache_key]
        
        # Generate new greeting using LLM
        if self.adaptive_ai_engine:
            try:
                greeting = self._generate_llm_greeting(context, max_length)
                
                # Cache the result
                self.greeting_cache[cache_key] = greeting
                self.last_greeting_time = current_time
                
                return greeting
                
            except Exception as e:
                print(f"LLM greeting generation failed: {e}")
                # Fall back to rule-based greeting
                return self._generate_fallback_greeting(context)
        else:
            # No AI engine available, use fallback
            return self._generate_fallback_greeting(context)
    
    def _generate_llm_greeting(self, context: GreetingContext, max_length: int) -> str:
        """Generate greeting using the LLM with intelligent prompting"""
        
        # Create a context-aware prompt for greeting generation
        prompt = self._create_greeting_prompt(context, max_length)
        
        # Use the adaptive AI engine to generate the greeting
        full_response = ""
        for token in self.adaptive_ai_engine.generate_response(
            prompt, 
            max_tokens=50,  # Keep it concise for greetings
            show_reasoning=False  # We don't need to show reasoning for greetings
        ):
            full_response += token
        
        # Extract and clean the greeting
        greeting = self._extract_greeting(full_response, max_length)
        return greeting
    
    def _create_greeting_prompt(self, context: GreetingContext, max_length: int) -> str:
        """Create a contextual prompt for greeting generation"""
        
        # Build context information
        context_parts = []
        
        # Time context
        context_parts.append(f"It's {context.time_of_day}")
        
        # System performance context
        if context.cpu_usage > 80:
            context_parts.append("the system is working hard")
        elif context.cpu_usage < 20:
            context_parts.append("the system is relaxed")
        
        if context.memory_percent > 80:
            context_parts.append("memory is quite full")
        elif context.memory_percent < 30:
            context_parts.append("plenty of memory available")
        
        # Battery context
        if context.battery_level is not None:
            if context.battery_level > 80:
                context_parts.append("battery is excellent")
            elif context.battery_level < 20:
                context_parts.append("battery is getting low")
        
        # Thermal context
        if context.thermal_state == "hot":
            context_parts.append("the system is running warm")
        elif context.thermal_state == "cool":
            context_parts.append("the system is nice and cool")
        
        # Capabilities context
        capabilities = []
        if context.voice_enabled:
            capabilities.append("voice synthesis")
        if context.avatar_enabled:
            capabilities.append("avatar visualization")
        capabilities.append("local AI")
        
        context_str = ", ".join(context_parts)
        capabilities_str = ", ".join(capabilities)
        
        prompt = f"""I'm M1K3, a local AI assistant. It's {context.time_of_day.lower()} and I just started up. My capabilities include {capabilities_str}. How should I greet the user?"""
        
        return prompt
    
    def _extract_greeting(self, llm_response: str, max_length: int) -> str:
        """Extract and clean the greeting from LLM response"""
        
        # Clean the response first
        response = llm_response.strip()
        
        # Remove common unwanted patterns
        unwanted_patterns = [
            "[Your response]", "[Your answer]", "Your greeting:", "Generate greeting:",
            "Context:", "Features:", "Model:", "Examples:", "Create a", "Your greeting is:",
            "Now, check if", "meets the requirements", "Generate a", "Here's a"
        ]
        
        for pattern in unwanted_patterns:
            response = response.replace(pattern, "").strip()
        
        # Split by common delimiters and find the actual greeting
        lines = [line.strip() for line in response.split('\n') if line.strip()]
        
        # Look for the generated greeting
        potential_greetings = []
        for line in lines:
            # Skip empty lines and obvious non-greetings
            if not line or len(line) < 5:
                continue
                
            # Skip template-looking lines
            if any(skip in line.lower() for skip in ['generate', 'create', 'example', 'character', 'length']):
                continue
                
            # Remove quotes if present
            if line.startswith('"') and line.endswith('"'):
                line = line[1:-1].strip()
            if line.startswith("'") and line.endswith("'"):
                line = line[1:-1].strip()
            
            # Check if it looks like a greeting
            if any(word in line.lower() for word in ['hello', 'hi', 'good', 'welcome', 'm1k3', 'ready', 'morning', 'evening', 'afternoon']):
                potential_greetings.append(line)
        
        # Use the first valid greeting found
        if potential_greetings:
            greeting = potential_greetings[0]
        else:
            # If no clear greeting found, try to extract from the first substantial line
            for line in lines:
                if len(line) > 10 and not any(skip in line.lower() for skip in ['generate', 'create', 'example']):
                    greeting = line
                    break
            else:
                greeting = "M1K3 ready!"
        
        # Clean up any remaining artifacts
        greeting = greeting.replace("- ", "").replace("* ", "").strip()
        
        # Ensure it's within length limit
        if len(greeting) > max_length:
            # Try to truncate at a word boundary
            words = greeting.split()
            truncated = ""
            for word in words:
                if len(truncated + word + " ") <= max_length - 3:  # Leave room for "..."
                    truncated += word + " "
                else:
                    break
            greeting = truncated.strip() + "..." if truncated else greeting[:max_length-3] + "..."
        
        return greeting.strip()
    
    def _generate_fallback_greeting(self, context: GreetingContext) -> str:
        """Generate a simple fallback greeting when LLM is unavailable"""
        
        greetings = [
            f"{context.time_of_day}! M1K3 AI assistant ready with voice and avatar!",
            f"Hello! M1K3 is online with {context.ai_model} - how can I help?",
            f"{context.time_of_day}! Your local M1K3 assistant is ready to chat!",
            f"Welcome! M1K3 AI is loaded and ready for conversation!",
            f"Hi there! M1K3 with voice synthesis is at your service!"
        ]
        
        # Simple selection based on context
        import random
        random.seed(int(context.cpu_usage * context.memory_percent))
        return random.choice(greetings)
    
    def _create_cache_key(self, context: GreetingContext) -> str:
        """Create a cache key from context (rounded to avoid too many variations)"""
        
        # Round values to create reasonable cache buckets
        return f"{context.time_of_day}_{int(context.cpu_usage/10)}_{int(context.memory_percent/10)}_{context.battery_level and int(context.battery_level/20)}_{context.thermal_state}_{context.voice_enabled}_{context.avatar_enabled}"

# Utility functions for easy integration

def create_greeting_context(metrics, m1k3_context: dict = None) -> GreetingContext:
    """Create a GreetingContext from system metrics and M1K3 context"""
    
    # Determine time of day
    current_hour = datetime.datetime.now().hour
    if 5 <= current_hour < 12:
        time_of_day = "Good morning"
    elif 12 <= current_hour < 17:
        time_of_day = "Good afternoon"
    elif 17 <= current_hour < 22:
        time_of_day = "Good evening"
    else:
        time_of_day = "Hello"
    
    # Extract thermal state
    thermal_state = "normal"
    if hasattr(metrics, 'cpu_temp') and metrics.cpu_temp:
        if metrics.cpu_temp > 70:
            thermal_state = "hot"
        elif metrics.cpu_temp < 45:
            thermal_state = "cool"
    
    # Get M1K3 specific context
    ai_model = "Local AI"
    voice_enabled = True
    avatar_enabled = True
    
    if m1k3_context:
        ai_model = m1k3_context.get('ai_model', 'Local AI')
        voice_enabled = m1k3_context.get('voice_enabled', True)
        avatar_enabled = m1k3_context.get('avatar_enabled', True)
    
    return GreetingContext(
        time_of_day=time_of_day,
        cpu_usage=metrics.cpu_usage,
        memory_percent=metrics.memory_percent,
        battery_level=getattr(metrics, 'battery_level', None),
        battery_status=getattr(metrics, 'battery_status', None),
        thermal_state=thermal_state,
        voice_enabled=voice_enabled,
        avatar_enabled=avatar_enabled,
        ai_model=ai_model,
        uptime_hours=getattr(metrics, 'uptime_hours', 0.0)
    )

def generate_llm_greeting(adaptive_ai_engine, metrics, m1k3_context: dict = None, max_length: int = 80) -> str:
    """
    Convenient function to generate an LLM-powered greeting
    
    Args:
        adaptive_ai_engine: The AI engine to use for generation
        metrics: System metrics for context
        m1k3_context: M1K3-specific context information
        max_length: Maximum greeting length
        
    Returns:
        Generated greeting string
    """
    
    greeting_engine = LLMGreetingEngine(adaptive_ai_engine)
    context = create_greeting_context(metrics, m1k3_context)
    
    return greeting_engine.generate_greeting(context, max_length)

if __name__ == "__main__":
    # Test the greeting engine
    from system_metrics import SystemMonitor
    
    # Create test context
    monitor = SystemMonitor()
    metrics = monitor.collect_metrics()
    
    test_context = {
        'ai_model': 'Qwen3-0.6B',
        'voice_enabled': True,
        'avatar_enabled': True
    }
    
    # Test fallback greeting (without AI engine)
    greeting_engine = LLMGreetingEngine()
    context = create_greeting_context(metrics, test_context)
    
    print("🧪 Testing LLM Greeting Engine")
    print("=" * 40)
    print(f"Context: {context}")
    print(f"Fallback greeting: {greeting_engine.generate_greeting(context)}")
    print("LLM greeting requires adaptive AI engine integration")