#!/usr/bin/env python3
"""
Test specific system prompt handling across different model types
"""

import sys
sys.path.append('/Users/kevinmurphy/Development/m1k3')

from src.models.loaders.adaptive_prompt_formatter import AdaptivePromptFormatter, PromptFormat

def test_specific_system_prompts():
    """Test that models handle specific, non-generic system prompts appropriately"""
    
    formatter = AdaptivePromptFormatter("config/model_profiles.json")
    
    # Test with a specific, technical system prompt
    specific_system_prompt = "You are a senior Python developer. Provide code examples with detailed explanations of algorithms and best practices."
    test_user_input = "How do I implement a binary search?"
    
    test_models = [
        "smollm2:135m",     # Small creative - simple format
        "llama3.2:3b",      # Instruction tuned - chatml format  
        "codellama:7b",     # Code specialized - code format
        "vicuna:13b"        # Chat optimized - conversation format
    ]
    
    print("🧪 Testing Specific System Prompt Handling\n")
    print(f"System Prompt: '{specific_system_prompt}'\n")
    print(f"User Query: '{test_user_input}'\n")
    
    for model in test_models:
        print(f"{'='*50}")
        print(f"Model: {model}")
        print(f"{'='*50}")
        
        # Get model profile and format
        profile = formatter.detect_model_profile(model)
        format_type = formatter.get_optimal_format(model)
        
        # Format the prompt
        formatted_prompt = formatter.format_prompt(
            user_input=test_user_input,
            system_prompt=specific_system_prompt,
            model_name=model,
            format_type=format_type
        )
        
        # Check if system prompt is included
        has_system_content = any(word in formatted_prompt.lower() for word in 
                               ["python", "developer", "algorithm", "best practices"])
        
        print(f"Profile: {profile}")
        print(f"Format: {format_type.value}")
        print(f"System prompt preserved: {'Yes' if has_system_content else 'No'}")
        print(f"Prompt length: {len(formatted_prompt)} chars")
        print(f"\nFormatted prompt preview:")
        print(f"'{formatted_prompt[:100]}{'...' if len(formatted_prompt) > 100 else ''}'")
        print()
    
    print("🎯 Expected Behavior:")
    print("• All models should preserve specific, technical system prompts")
    print("• Only generic prompts like 'helpful AI assistant' should be optimized away")
    print("• Each format should handle the system prompt appropriately for its structure")

if __name__ == "__main__":
    test_specific_system_prompts()