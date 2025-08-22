#!/usr/bin/env python3
"""
Direct test of the AdaptiveModelConfig system
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from adaptive_model_config import AdaptiveModelConfig, TaskType, ConfidenceLevel

def test_adaptive_config_direct():
    """Test the adaptive configuration system directly"""
    
    print("🎯 Testing AdaptiveModelConfig System")
    print("=" * 60)
    
    config = AdaptiveModelConfig()
    
    # Test queries of different types
    test_queries = [
        ("Calculate the area of a circle with radius 5", "TinyLlama/TinyLlama-1.1B-Chat-v1.0"),
        ("If all birds can fly and penguins are birds, can penguins fly?", "TinyLlama/TinyLlama-1.1B-Chat-v1.0"),
        ("Write a Python function to find the maximum element in a list", "TinyLlama/TinyLlama-1.1B-Chat-v1.0"),
        ("Hello, how are you today?", "TinyLlama/TinyLlama-1.1B-Chat-v1.0"),
        ("Write a short story about a robot", "TinyLlama/TinyLlama-1.1B-Chat-v1.0"),
        ("Compare the advantages and disadvantages of renewable energy", "TinyLlama/TinyLlama-1.1B-Chat-v1.0"),
    ]
    
    for query, model_name in test_queries:
        print(f"\n📝 Query: {query}")
        print(f"🤖 Model: {model_name}")
        
        try:
            # Get adaptive configuration
            adaptive_config = config.get_optimal_config(
                query=query,
                model_name=model_name,
                context=[]
            )
            
            print(f"   🎯 Task Type: {adaptive_config.get('task_type', 'unknown')}")
            if hasattr(adaptive_config.get('task_type'), 'value'):
                print(f"   🎯 Task Type Value: {adaptive_config.get('task_type').value}")
            
            print(f"   📊 Confidence Level: {adaptive_config.get('confidence_level', 'unknown')}")
            if hasattr(adaptive_config.get('confidence_level'), 'value'):
                print(f"   📊 Confidence Level Value: {adaptive_config.get('confidence_level').value}")
            
            print(f"   ⚙️  Parameters: {adaptive_config.get('parameters', {})}")
            print(f"   🧠 Thinking Recommended: {adaptive_config.get('thinking_recommended', False)}")
            
        except Exception as e:
            print(f"   ❌ Error: {e}")
            import traceback
            traceback.print_exc()

def test_get_optimal_config_method():
    """Test the get_optimal_config method specifically"""
    
    print("\n🔧 Testing get_optimal_config Method")
    print("=" * 60)
    
    config = AdaptiveModelConfig()
    
    # Test mathematical query
    query = "Calculate the area of a circle with radius 5"
    model_name = "TinyLlama/TinyLlama-1.1B-Chat-v1.0"
    
    print(f"Testing: {query}")
    
    try:
        result = config.get_optimal_config(query, model_name, [])
        print(f"Result type: {type(result)}")
        print(f"Result keys: {list(result.keys()) if isinstance(result, dict) else 'Not a dict'}")
        print(f"Full result: {result}")
        
        # Test task classification directly
        task_type, confidence = config.classifier.classify_query(query)
        print(f"Direct classification: {task_type} ({confidence})")
        
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    try:
        test_get_optimal_config_method()
        test_adaptive_config_direct()
        
    except Exception as e:
        print(f"❌ Test failed with error: {e}")
        import traceback
        traceback.print_exc()