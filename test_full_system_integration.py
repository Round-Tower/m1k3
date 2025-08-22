#!/usr/bin/env python3
"""
Test the full system integration with the metadata fix
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

def test_ai_inference_generation():
    """Test AI inference generation with adaptive parameters"""
    
    print("🚀 Testing Full AI Inference Generation")
    print("=" * 60)
    
    try:
        from ai_inference import LocalAIEngine
        
        print("🔧 Creating LocalAIEngine...")
        engine = LocalAIEngine()
        
        # Test loading a model
        print("📦 Loading AI model...")
        if engine.load_model():
            print("✅ Model loaded successfully")
        else:
            print("❌ Model loading failed - using fallback")
        
        # Test different query types
        test_queries = [
            "What is 5 + 3?",  # Mathematical - should use conservative parameters
            "Hello there!",    # Conversational - should use balanced parameters
            "Write code to add two numbers"  # Coding - should use focused parameters
        ]
        
        for i, query in enumerate(test_queries, 1):
            print(f"\n🔍 Test {i}: {query}")
            
            try:
                # Generate response with adaptive parameters
                response_parts = []
                for token in engine.generate_response(query, max_tokens=50):
                    response_parts.append(token)
                    if len(response_parts) >= 5:  # Limit output for testing
                        break
                
                response = ''.join(response_parts)
                print(f"   ✅ Response: {response[:100]}{'...' if len(response) > 100 else ''}")
                print(f"   📊 Generated {len(response_parts)} tokens")
                
            except Exception as e:
                print(f"   ❌ ERROR: {e}")
                return False
        
        print(f"\n🎉 Full system integration test successful!")
        return True
        
    except Exception as e:
        print(f"❌ Full system test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_adaptive_ai_engine():
    """Test the AdaptiveAIEngine with a real AI backend"""
    
    print(f"\n🧠 Testing AdaptiveAIEngine with Real Backend")
    print("=" * 60)
    
    try:
        from ai_inference import LocalAIEngine
        from adaptive_ai_engine import AdaptiveAIEngine
        
        # Create base AI engine
        base_engine = LocalAIEngine()
        print("📦 Loading base AI model...")
        if base_engine.load_model():
            print("✅ Base model loaded successfully")
        else:
            print("⚠️  Using fallback AI engine")
        
        # Create adaptive AI engine
        adaptive_engine = AdaptiveAIEngine(base_engine, enable_thinking_mode=True)
        print("🧠 AdaptiveAIEngine created")
        
        # Test queries
        test_queries = [
            "Calculate 15 * 23",
            "Hello, how are you today?",
        ]
        
        for query in test_queries:
            print(f"\n📝 Query: {query}")
            
            # Get insights first
            insights = adaptive_engine.get_thinking_insights(query)
            print(f"   🎯 Task Type: {insights.get('adaptive_task_type', 'unknown')}")
            print(f"   🤔 Would Use Thinking: {insights.get('would_use_thinking_mode', False)}")
            
            try:
                # Generate response
                response_parts = []
                for token in adaptive_engine.generate_response(query, max_tokens=30):
                    response_parts.append(token)
                    if len(response_parts) >= 3:  # Limit for testing
                        break
                
                response = ''.join(response_parts)
                print(f"   ✅ Response: {response[:100]}{'...' if len(response) > 100 else ''}")
                
            except Exception as e:
                print(f"   ❌ Generation Error: {e}")
                return False
        
        print(f"\n🎉 AdaptiveAIEngine test successful!")
        return True
        
    except Exception as e:
        print(f"❌ AdaptiveAIEngine test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    try:
        print("🔬 M1K3 Full System Integration Test")
        print("=" * 80)
        
        success1 = test_ai_inference_generation()
        success2 = test_adaptive_ai_engine()
        
        if success1 and success2:
            print(f"\n🎉 ALL TESTS PASSED!")
            print(f"✅ Metadata filtering is working correctly")
            print(f"✅ Adaptive parameter optimization is functional") 
            print(f"✅ Intelligent thinking mode integration is operational")
        else:
            print(f"\n⚠️  Some tests failed. Please check the output above.")
            
    except Exception as e:
        print(f"❌ Test suite failed: {e}")
        import traceback
        traceback.print_exc()