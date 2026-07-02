#!/usr/bin/env python3
"""
Temperature Investigation Script
Test how temperature is being set in M1K3's adaptive parameter system
"""

import sys
import os

def test_temperature_settings():
    """Test temperature settings in adaptive config"""
    try:
        from adaptive_model_config import AdaptiveModelConfig, TaskType, ConfidenceLevel
        
        config = AdaptiveModelConfig()
        
        print("🌡️ Temperature Investigation Results\n")
        
        # Test different query types that were timing out
        test_queries = [
            ("What is 15 * 23?", "math calculation"),
            ("Write a basic HTML page", "coding task"),
            ("What year did World War II end?", "factual question"),
            ("What is the capital of France?", "factual question"),
            ("Who wrote Romeo and Juliet?", "factual question")
        ]
        
        for query, description in test_queries:
            print(f"🔍 Query: '{query}' ({description})")
            
            # Classify the task
            task_type = config.classifier.classify_query(query)
            print(f"   📋 Classified as: {task_type}")
            
            # Get parameters for different confidence levels
            for conf_level in [ConfidenceLevel.HIGH, ConfidenceLevel.MEDIUM, ConfidenceLevel.LOW]:
                try:
                    params = config.get_adaptive_parameters(120, query, task_type, conf_level)
                    temp = getattr(params, 'temperature', None)
                    do_sample = getattr(params, 'do_sample', None)
                    rep_penalty = getattr(params, 'repetition_penalty', None)
                    
                    print(f"   🎯 {conf_level.value}: temp={temp}, do_sample={do_sample}, rep_penalty={rep_penalty}")
                    
                    if temp is None:
                        print(f"      ⚠️  WARNING: Temperature is None! This could cause default behavior")
                    elif temp > 0.8:
                        print(f"      🔥 HIGH temperature - might cause rambling")
                    elif temp < 0.2:
                        print(f"      🧊 LOW temperature - might cause repetition")
                        
                except Exception as e:
                    print(f"   ❌ Error getting params for {conf_level}: {e}")
            
            print()
        
        # Test specific problematic patterns
        print("🚨 Problematic Pattern Analysis:")
        print("Queries that timed out often had:")
        
        math_params = config.get_adaptive_parameters(150, "What is 15 * 23?")
        html_params = config.get_adaptive_parameters(150, "Write a basic HTML page")
        
        print(f"Math query temp: {getattr(math_params, 'temperature', 'MISSING')}")
        print(f"HTML query temp: {getattr(html_params, 'temperature', 'MISSING')}")
        
        # Check if sampling is disabled when temperature is None
        print(f"Math do_sample: {getattr(math_params, 'do_sample', 'MISSING')}")
        print(f"HTML do_sample: {getattr(html_params, 'do_sample', 'MISSING')}")
        
        return True
        
    except Exception as e:
        print(f"❌ Temperature investigation failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_qwen_behavior():
    """Test specific Qwen model behavior patterns"""
    print("\n🤖 Qwen Model Behavior Analysis:")
    
    try:
        from adaptive_model_config import AdaptiveModelConfig
        
        config = AdaptiveModelConfig()
        
        # Check Qwen profile
        qwen_profile = config.model_profiles.get('qwen/qwen3-0.6b')
        if qwen_profile:
            print(f"Qwen max_reliable_tokens: {qwen_profile.max_reliable_tokens}")
            print(f"Qwen supports_reasoning: {qwen_profile.supports_reasoning}")
            print(f"Qwen reasoning_quality: {qwen_profile.reasoning_quality}")
        
        # Test if the generation parameters are causing loops
        print("\n🔄 Loop Analysis:")
        print("Factors that could cause infinite loops:")
        print("1. Temperature too high → random completions → never ends")
        print("2. Temperature None → default behavior → unpredictable")  
        print("3. Repetition penalty too low → model repeats itself")
        print("4. do_sample=True with bad temperature → chaos")
        
        return True
        
    except Exception as e:
        print(f"❌ Qwen analysis failed: {e}")
        return False

def main():
    """Run temperature investigation"""
    print("🌡️ M1K3 Temperature Investigation")
    print("="*50)
    
    success1 = test_temperature_settings()
    success2 = test_qwen_behavior()
    
    if success1 and success2:
        print("\n✅ Investigation completed successfully")
        print("\n💡 Key Insights:")
        print("- Check if temperature=None is causing default PyTorch behavior")
        print("- Math/coding queries might need lower temperature (0.1-0.3)")
        print("- do_sample=False might prevent timeouts for factual queries")
        print("- Consider increasing repetition_penalty for loop prevention")
        return 0
    else:
        print("\n❌ Investigation failed")
        return 1

if __name__ == "__main__":
    sys.exit(main())