#!/usr/bin/env python3
"""
M1K3 Adaptive Prompt Formatter
Multi-format prompting system that optimizes for different model types
"""

import json
import re
from typing import Dict, List, Optional, Tuple, Any
from dataclasses import dataclass
from pathlib import Path
from enum import Enum

class PromptFormat(Enum):
    SIMPLE = "simple"
    CHATML = "chatml" 
    CONVERSATION = "conversation"
    CODE = "code"

@dataclass
class ModelProfile:
    """Model profile with prompting preferences"""
    name: str
    description: str
    parameter_range: str
    characteristics: List[str]
    optimal_format: PromptFormat
    fallback_formats: List[PromptFormat]
    context_strategy: str
    max_system_prompt_length: int
    models: List[str]

@dataclass
class FormatDefinition:
    """Prompt format definition with templates"""
    name: str
    description: str
    template: str
    system_integration: str
    best_for: List[str]

class AdaptivePromptFormatter:
    """Adaptive prompting system for different model types"""
    
    def __init__(self, profiles_path: str = "model_profiles.json"):
        self.profiles_path = Path(profiles_path)
        self.profiles: Dict[str, ModelProfile] = {}
        self.formats: Dict[PromptFormat, FormatDefinition] = {}
        self.auto_detection_rules = []
        self.fallback_profile = "instruction_tuned"
        
        self._load_profiles()
    
    def _load_profiles(self):
        """Load model profiles and format definitions"""
        try:
            with open(self.profiles_path) as f:
                data = json.load(f)
            
            # Load model profiles
            for profile_name, profile_data in data["model_profiles"].items():
                self.profiles[profile_name] = ModelProfile(
                    name=profile_name,
                    description=profile_data["description"],
                    parameter_range=profile_data["parameter_range"],
                    characteristics=profile_data["characteristics"],
                    optimal_format=PromptFormat(profile_data["optimal_format"]),
                    fallback_formats=[PromptFormat(f) for f in profile_data["fallback_formats"]],
                    context_strategy=profile_data["context_strategy"],
                    max_system_prompt_length=profile_data["max_system_prompt_length"],
                    models=profile_data["models"]
                )
            
            # Load format definitions
            for format_name, format_data in data["format_definitions"].items():
                self.formats[PromptFormat(format_name)] = FormatDefinition(
                    name=format_data["name"],
                    description=format_data["description"],
                    template=format_data["template"],
                    system_integration=format_data["system_integration"],
                    best_for=format_data["best_for"]
                )
            
            # Load auto-detection rules
            self.auto_detection_rules = data["auto_detection"]["rules"]
            self.fallback_profile = data["auto_detection"]["fallback_profile"]
            
            print(f"✅ Loaded {len(self.profiles)} model profiles and {len(self.formats)} formats")
            
        except Exception as e:
            print(f"⚠️ Error loading profiles: {e}")
            self._create_minimal_profiles()
    
    def _create_minimal_profiles(self):
        """Create minimal profiles as fallback"""
        # Minimal fallback configuration
        self.profiles["small_creative"] = ModelProfile(
            name="small_creative",
            description="Small creative models",
            parameter_range="< 1B",
            characteristics=["Creative", "Simple"],
            optimal_format=PromptFormat.SIMPLE,
            fallback_formats=[PromptFormat.CONVERSATION],
            context_strategy="minimal",
            max_system_prompt_length=200,
            models=["smollm2:*"]
        )
        
        self.profiles["instruction_tuned"] = ModelProfile(
            name="instruction_tuned", 
            description="Instruction-following models",
            parameter_range="1B+",
            characteristics=["Structured", "Accurate"],
            optimal_format=PromptFormat.CHATML,
            fallback_formats=[PromptFormat.CONVERSATION],
            context_strategy="structured",
            max_system_prompt_length=1000,
            models=["llama3*"]
        )
    
    def detect_model_profile(self, model_name: str, parameter_count: Optional[int] = None) -> str:
        """Detect the best profile for a given model"""
        
        # Try pattern matching first
        for rule in self.auto_detection_rules:
            pattern = rule["pattern"].replace("*", ".*")
            if re.match(pattern, model_name, re.IGNORECASE):
                confidence = rule["confidence"]
                profile_name = rule["profile"]
                
                print(f"🎯 Model '{model_name}' matched pattern '{rule['pattern']}' -> {profile_name} (confidence: {confidence})")
                
                if confidence >= 0.8:  # High confidence threshold
                    return profile_name
        
        # Fallback to parameter count if available
        if parameter_count:
            if parameter_count < 1000000000:  # < 1B
                return "small_creative"
            elif parameter_count < 8000000000:  # < 8B
                return "instruction_tuned"
            else:
                return "instruction_tuned"
        
        # Ultimate fallback
        print(f"⚠️ No profile match for '{model_name}', using fallback: {self.fallback_profile}")
        return self.fallback_profile
    
    def get_optimal_format(self, model_name: str, task_type: Optional[str] = None) -> PromptFormat:
        """Get optimal prompt format for a model"""
        profile_name = self.detect_model_profile(model_name)
        profile = self.profiles.get(profile_name)
        
        if not profile:
            print(f"⚠️ Profile '{profile_name}' not found, using ChatML")
            return PromptFormat.CHATML
        
        # Consider task type for format optimization
        if task_type:
            if task_type in ["code", "programming", "technical"] and PromptFormat.CODE in self.formats:
                return PromptFormat.CODE
            elif task_type in ["creative", "story", "poem"] and profile.optimal_format == PromptFormat.SIMPLE:
                return PromptFormat.SIMPLE
            elif task_type in ["chat", "conversation"] and PromptFormat.CONVERSATION in profile.fallback_formats:
                return PromptFormat.CONVERSATION
        
        return profile.optimal_format
    
    def format_prompt(self, 
                     user_input: str,
                     system_prompt: str = "",
                     model_name: str = "",
                     format_type: Optional[PromptFormat] = None,
                     context_messages: Optional[List[Dict]] = None) -> str:
        """Format prompt using adaptive strategy"""
        
        # Auto-detect format if not specified
        if format_type is None:
            format_type = self.get_optimal_format(model_name)
        
        format_def = self.formats.get(format_type)
        if not format_def:
            print(f"⚠️ Format '{format_type}' not found, using simple")
            format_type = PromptFormat.SIMPLE
            format_def = self.formats[format_type]
        
        # Get profile for system prompt optimization
        profile_name = self.detect_model_profile(model_name)
        profile = self.profiles.get(profile_name, self.profiles[self.fallback_profile])
        
        # Optimize system prompt length
        optimized_system = self._optimize_system_prompt(system_prompt, profile)
        
        # Format based on type
        if format_type == PromptFormat.SIMPLE:
            return self._format_simple(user_input, optimized_system)
        elif format_type == PromptFormat.CHATML:
            return self._format_chatml(user_input, optimized_system, context_messages)
        elif format_type == PromptFormat.CONVERSATION:
            return self._format_conversation(user_input, optimized_system, context_messages)
        elif format_type == PromptFormat.CODE:
            return self._format_code(user_input, optimized_system)
        else:
            # Fallback to simple
            return self._format_simple(user_input, optimized_system)
    
    def _optimize_system_prompt(self, system_prompt: str, profile: ModelProfile) -> str:
        """Optimize system prompt for model profile"""
        if not system_prompt:
            return ""
        
        max_length = profile.max_system_prompt_length
        
        if len(system_prompt) <= max_length:
            return system_prompt
        
        # Smart truncation - keep the most important parts
        if profile.context_strategy == "minimal":
            # For small models, use very basic instructions
            return "You are a helpful AI assistant. Provide clear, direct responses."
        elif profile.context_strategy == "structured":
            # Keep structured elements, trim details
            lines = system_prompt.split('\\n')
            essential_lines = []
            current_length = 0
            
            for line in lines:
                if current_length + len(line) < max_length:
                    essential_lines.append(line)
                    current_length += len(line) + 1
                else:
                    break
            
            result = '\\n'.join(essential_lines)
            if len(result) < max_length * 0.7:  # If we cut too much, add key directive
                result += "\\nProvide comprehensive, well-structured responses."
            
            return result
        else:
            # Conservative truncation
            return system_prompt[:max_length-50] + "\\nProvide helpful responses."
    
    def _format_simple(self, user_input: str, system_prompt: str = "") -> str:
        """Simple, direct prompting format"""
        if system_prompt:
            # Very direct integration for small models
            return f"{system_prompt}\\n\\n{user_input}"
        else:
            return user_input
    
    def _format_chatml(self, user_input: str, system_prompt: str = "", context_messages: Optional[List[Dict]] = None) -> str:
        """ChatML structured format"""
        formatted = ""
        
        if system_prompt:
            formatted += f"<|im_start|>system\\n{system_prompt}<|im_end|>\\n"
        
        # Add context messages if provided
        if context_messages:
            for msg in context_messages:
                role = msg.get("role", "user")
                content = msg.get("content", "")
                formatted += f"<|im_start|>{role}\\n{content}<|im_end|>\\n"
        
        formatted += f"<|im_start|>user\\n{user_input}<|im_end|>\\n<|im_start|>assistant\\n"
        return formatted
    
    def _format_conversation(self, user_input: str, system_prompt: str = "", context_messages: Optional[List[Dict]] = None) -> str:
        """Natural conversation format"""
        formatted = ""
        
        if system_prompt:
            formatted += f"{system_prompt}\\n\\n"
        
        # Add context messages in conversation style
        if context_messages:
            for msg in context_messages:
                role = msg.get("role", "user")
                content = msg.get("content", "")
                if role == "user":
                    formatted += f"Human: {content}\\n\\n"
                elif role == "assistant":
                    formatted += f"Assistant: {content}\\n\\n"
        
        formatted += f"Human: {user_input}\\n\\nAssistant: "
        return formatted
    
    def _format_code(self, user_input: str, system_prompt: str = "") -> str:
        """Code-optimized format"""
        instruction = system_prompt or "Provide technical assistance"
        return f"# Task: {instruction}\\n\\n## Input:\\n{user_input}\\n\\n## Response:"
    
    def test_format_compatibility(self, model_name: str, test_queries: Optional[Dict] = None) -> Dict[str, Any]:
        """Test format compatibility for a model"""
        profile_name = self.detect_model_profile(model_name)
        profile = self.profiles.get(profile_name)
        
        if not profile:
            return {"error": f"No profile found for {model_name}"}
        
        results = {
            "model": model_name,
            "profile": profile_name,
            "optimal_format": profile.optimal_format.value,
            "fallback_formats": [f.value for f in profile.fallback_formats],
            "tests": {}
        }
        
        # Use default test queries if none provided
        if not test_queries:
            test_queries = {
                "simple": "What is Python?",
                "factual": "What is the capital of France?",
                "creative": "Write a short poem."
            }
        
        # Test each format
        for query_type, query in test_queries.items():
            format_results = {}
            
            # Test optimal format
            optimal_prompt = self.format_prompt(query, model_name=model_name)
            format_results["optimal"] = {
                "format": profile.optimal_format.value,
                "prompt_length": len(optimal_prompt),
                "prompt": optimal_prompt[:100] + "..." if len(optimal_prompt) > 100 else optimal_prompt
            }
            
            # Test fallback formats
            for fallback_format in profile.fallback_formats:
                fallback_prompt = self.format_prompt(query, model_name=model_name, format_type=fallback_format)
                format_results[fallback_format.value] = {
                    "format": fallback_format.value,
                    "prompt_length": len(fallback_prompt),
                    "prompt": fallback_prompt[:100] + "..." if len(fallback_prompt) > 100 else fallback_prompt
                }
            
            results["tests"][query_type] = format_results
        
        return results
    
    def get_model_recommendations(self, model_name: str) -> Dict[str, Any]:
        """Get optimization recommendations for a model"""
        profile_name = self.detect_model_profile(model_name)
        profile = self.profiles.get(profile_name)
        
        if not profile:
            return {"error": f"No profile found for {model_name}"}
        
        return {
            "model": model_name,
            "detected_profile": profile_name,
            "characteristics": profile.characteristics,
            "optimal_format": profile.optimal_format.value,
            "recommendations": {
                "system_prompt_max_length": profile.max_system_prompt_length,
                "context_strategy": profile.context_strategy,
                "best_for": self.formats[profile.optimal_format].best_for,
                "fallback_options": [f.value for f in profile.fallback_formats]
            }
        }


def test_adaptive_formatter():
    """Test the adaptive formatter with different models"""
    formatter = AdaptivePromptFormatter()
    
    test_models = [
        "smollm2:135m",
        "llama3.2:latest", 
        "codellama:7b",
        "unknown-model:1b"
    ]
    
    print("🧪 Testing Adaptive Prompt Formatter")
    print("=" * 50)
    
    for model in test_models:
        print(f"\\n--- Testing {model} ---")
        
        # Test model detection
        profile = formatter.detect_model_profile(model)
        print(f"Detected profile: {profile}")
        
        # Test optimal format
        optimal_format = formatter.get_optimal_format(model)
        print(f"Optimal format: {optimal_format.value}")
        
        # Test formatting
        test_prompt = formatter.format_prompt(
            "Explain Python programming",
            "You are a helpful assistant",
            model
        )
        print(f"Sample prompt length: {len(test_prompt)} chars")
        print(f"Preview: {test_prompt[:150]}...")
        
        # Get recommendations
        recommendations = formatter.get_model_recommendations(model)
        if "error" not in recommendations:
            print(f"Max system prompt: {recommendations['recommendations']['system_prompt_max_length']} chars")
            print(f"Best for: {recommendations['recommendations']['best_for']}")


if __name__ == "__main__":
    test_adaptive_formatter()