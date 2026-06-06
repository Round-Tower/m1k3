#!/usr/bin/env python3
"""
Test script to validate format optimization across different model profiles
"""

import sys
sys.path.append('/Users/kevinmurphy/Development/m1k3')

from src.models.loaders.adaptive_prompt_formatter import AdaptivePromptFormatter, PromptFormat

def test_format_optimization():
    """Test that different model types get appropriate format handling"""
    
    formatter = AdaptivePromptFormatter("config/model_profiles.json")
    
    test_system_prompt = "You are M1K3, a helpful AI assistant. Provide clear, direct responses."
    test_user_input = "What is the capital of France?"
    
    # Test cases for different model types
    test_cases = [
        {
            "model": "smollm2:135m",
            "expected_profile": "small_creative",
            "expected_format": PromptFormat.SIMPLE,
            "description": "Small creative model - should skip generic system prompt"
        },
        {
            "model": "llama3.2:3b",
            "expected_profile": "instruction_tuned", 
            "expected_format": PromptFormat.CHATML,
            "description": "Instruction-tuned model - should use full system prompt"
        },
        {
            "model": "codellama:7b",
            "expected_profile": "code_specialized",
            "expected_format": PromptFormat.CODE,
            "description": "Code model - should enhance basic system prompt for technical tasks"
        },
        {
            "model": "vicuna:13b",
            "expected_profile": "chat_optimized",
            "expected_format": PromptFormat.CONVERSATION,
            "description": "Chat model - should handle conversation style"
        }
    ]
    
    print("🧪 Testing Format Optimization Across Model Profiles\n")
    
    for i, test_case in enumerate(test_cases, 1):
        model = test_case["model"]
        expected_profile = test_case["expected_profile"]
        expected_format = test_case["expected_format"]
        description = test_case["description"]
        
        print(f"{'='*60}")
        print(f"Test {i}: {model}")
        print(f"Description: {description}")
        print(f"{'='*60}")
        
        # Test profile detection
        detected_profile = formatter.detect_model_profile(model)
        profile_match = detected_profile == expected_profile
        
        # Test format detection
        detected_format = formatter.get_optimal_format(model)
        format_match = detected_format == expected_format
        
        # Test prompt formatting
        formatted_prompt = formatter.format_prompt(
            user_input=test_user_input,
            system_prompt=test_system_prompt,
            model_name=model,
            format_type=detected_format
        )
        
        print(f"✅ Profile: {detected_profile} {'✓' if profile_match else '✗'}")
        print(f"✅ Format: {detected_format.value} {'✓' if format_match else '✗'}")
        print(f"\n📝 Formatted Prompt:")
        print(f"┌─ START FORMATTED PROMPT ─┐")
        print(f"│ {formatted_prompt}")
        print(f"└─ END FORMATTED PROMPT ─┘")
        print(f"\n📊 Length: {len(formatted_prompt)} chars")
        
        # Analyze system prompt handling
        has_system_content = "M1K3" in formatted_prompt or "helpful" in formatted_prompt
        print(f"🔍 System prompt included: {'Yes' if has_system_content else 'No'}")
        
        print("\n")
    
    print("🎯 Summary: Format optimization adapts to model capabilities!")
    print("• Small models: Skip generic system prompts for efficiency")
    print("• Instruction models: Use full structured prompts for clarity") 
    print("• Code models: Enhance prompts with technical context")
    print("• Chat models: Preserve conversational flow")

if __name__ == "__main__":
    test_format_optimization()