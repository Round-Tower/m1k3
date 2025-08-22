#!/usr/bin/env python3
"""
Debug Math Classification and Temperature Issue
"""

def test_classification():
    """Test how math queries are classified"""
    try:
        from adaptive_model_config import AdaptiveModelConfig, TaskType, ConfidenceLevel
        
        config = AdaptiveModelConfig()
        
        # Test the specific problematic query
        query = "What is 15 * 23?"
        
        print(f"🔍 Testing: '{query}'")
        
        # Classify the task
        task_type, confidence = config.classifier.classify_query(query)
        print(f"📋 Classification: {task_type} (confidence: {confidence:.3f})")
        
        # Get model profile (Qwen)
        model_key = "qwen/qwen3-0.6b"  
        model_profile = config.model_profiles.get(model_key)
        if model_profile:
            print(f"🤖 Model: {model_profile.name}")
            print(f"   Max reliable tokens: {model_profile.max_reliable_tokens}")
            print(f"   Optimal tasks: {[t.value for t in model_profile.optimal_tasks]}")
        
        # Determine confidence level  
        conf_level = config._determine_confidence_level(task_type, confidence, model_profile)
        print(f"🎯 Determined confidence level: {conf_level}")
        
        # Get parameters
        param_key = (task_type, conf_level)
        if param_key in config.parameter_profiles:
            params = config.parameter_profiles[param_key]
            print(f"⚙️  Parameters found for {param_key}:")
            print(f"   max_new_tokens: {params.max_new_tokens}")
            print(f"   temperature: {params.temperature}")
            print(f"   do_sample: {params.do_sample}")
            print(f"   repetition_penalty: {params.repetition_penalty}")
        else:
            print(f"❌ No parameters found for {param_key}")
            print("Available parameter combinations:")
            for key in config.parameter_profiles.keys():
                print(f"   {key}")
        
        return True
        
    except Exception as e:
        print(f"❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    test_classification()