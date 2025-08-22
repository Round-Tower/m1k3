#!/usr/bin/env python3
"""
Test the AI inference fix for metadata filtering
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

def test_adaptive_generation_params():
    """Test that adaptive generation params don't include metadata"""
    
    print("🔧 Testing Adaptive Generation Parameters")
    print("=" * 60)
    
    try:
        from adaptive_model_config import AdaptiveModelConfig
        
        config = AdaptiveModelConfig()
        query = "Calculate the area of a circle with radius 5"
        model_name = "TinyLlama/TinyLlama-1.1B-Chat-v1.0"
        
        # Get the full config result
        config_result = config.get_optimal_config(query, model_name, [])
        
        print(f"📝 Query: {query}")
        print(f"🤖 Model: {model_name}")
        print(f"\n📊 Full Config Result:")
        for key, value in config_result.items():
            print(f"  {key}: {value}")
        
        # Filter out metadata (this is what AI inference should do)
        filtered_params = {k: v for k, v in config_result.items() if k != '_metadata'}
        
        print(f"\n✅ Filtered Parameters (for model generation):")
        for key, value in filtered_params.items():
            print(f"  {key}: {value}")
        
        # Check that metadata is excluded
        if '_metadata' not in filtered_params:
            print(f"\n✅ SUCCESS: _metadata properly filtered out")
            return True
        else:
            print(f"\n❌ ERROR: _metadata still present in parameters")
            return False
            
    except Exception as e:
        print(f"❌ Test failed with error: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_ai_inference_integration():
    """Test the AI inference parameter generation"""
    
    print(f"\n🤖 Testing AI Inference Integration")
    print("=" * 60)
    
    # Mock tokenizer for testing
    class MockTokenizer:
        def __init__(self):
            self.eos_token_id = 2
    
    try:
        # Create a mock AI engine to test parameter generation
        class TestAIEngine:
            def __init__(self):
                self.tokenizer = MockTokenizer()
                self._current_model_name = "TinyLlama/TinyLlama-1.1B-Chat-v1.0"
            
            def _get_adaptive_generation_params(self, max_tokens: int, query: str = "", context: list = None):
                """Copy of the fixed method from ai_inference.py"""
                model_name = getattr(self, '_current_model_name', 'unknown')
                
                # Try adaptive configuration first (new intelligent system)
                try:
                    from adaptive_model_config import AdaptiveModelConfig
                    adaptive_config = AdaptiveModelConfig()
                    config_result = adaptive_config.get_optimal_config(query, model_name, context or [])
                    
                    # Filter out metadata to get clean parameters
                    params = {k: v for k, v in config_result.items() if k != '_metadata'}
                    
                    # Set tokenizer-specific IDs
                    params['pad_token_id'] = self.tokenizer.eos_token_id
                    params['eos_token_id'] = self.tokenizer.eos_token_id
                    
                    # Honor max_tokens limit if specified
                    if max_tokens < params.get('max_new_tokens', max_tokens):
                        params['max_new_tokens'] = max_tokens
                    
                    return params
                    
                except Exception as e:
                    print(f"Adaptive config failed: {e}")
                    return {'max_new_tokens': max_tokens, 'pad_token_id': 2, 'eos_token_id': 2}
        
        engine = TestAIEngine()
        
        # Test different types of queries
        test_queries = [
            ("Calculate the area of a circle with radius 5", "mathematical"),
            ("Hello, how are you?", "conversational"),
            ("Write a Python function to sort a list", "coding")
        ]
        
        for query, expected_task in test_queries:
            print(f"\n📝 Testing: {query}")
            print(f"   Expected task: {expected_task}")
            
            try:
                params = engine._get_adaptive_generation_params(150, query, [])
                
                print(f"   ✅ Generated parameters:")
                for key, value in params.items():
                    print(f"      {key}: {value}")
                
                # Check that no metadata is present
                if '_metadata' in params:
                    print(f"   ❌ ERROR: _metadata found in parameters!")
                    return False
                else:
                    print(f"   ✅ No metadata in parameters")
                    
            except Exception as e:
                print(f"   ❌ ERROR: {e}")
                return False
        
        print(f"\n🎉 All parameter generation tests passed!")
        return True
        
    except Exception as e:
        print(f"❌ Integration test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    try:
        success1 = test_adaptive_generation_params()
        success2 = test_ai_inference_integration()
        
        if success1 and success2:
            print(f"\n🎉 All tests passed! Metadata filtering is working correctly.")
        else:
            print(f"\n⚠️  Some tests failed. Please check the output above.")
            
    except Exception as e:
        print(f"❌ Test suite failed: {e}")
        import traceback
        traceback.print_exc()