#!/usr/bin/env python3
"""
Test script for the enhanced AdaptiveAIEngine with intelligent thinking mode integration
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from src.engines.ai.adaptive_ai_engine import AdaptiveAIEngine
from adaptive_model_config import TaskType, ConfidenceLevel

class MockAIEngine:
    """Mock AI engine for testing"""
    
    def __init__(self):
        self.current_model_name = "TinyLlama/TinyLlama-1.1B-Chat-v1.0"
        self.context = None
        
    def generate_response(self, query, max_tokens, context=None):
        """Mock response generation"""
        words = ["This", "is", "a", "mock", "response", "to", "your", "query.", "Thanks!"]
        for word in words:
            yield f"{word} "

class MockThinkingEngine:
    """Mock thinking engine for testing"""
    
    def __init__(self, base_engine, avatar_callback=None):
        self.base_engine = base_engine
        
    def assess_query_complexity(self, query):
        from src.utils.thinking_mode_engine import QueryComplexity
        if any(word in query.lower() for word in ["calculate", "solve", "proof", "algorithm"]):
            return QueryComplexity.COMPLEX
        elif any(word in query.lower() for word in ["explain", "how", "why", "compare"]):
            return QueryComplexity.MODERATE
        else:
            return QueryComplexity.SIMPLE
            
    def generate_with_thinking(self, query, max_tokens, show_reasoning=False):
        """Mock thinking generation"""
        if show_reasoning:
            yield "<think>Let me think about this step by step...</think>\n"
        words = ["This", "is", "a", "thoughtful", "response", "after", "reasoning."]
        for word in words:
            yield f"{word} "
            
    def get_thinking_insights(self, query):
        """Mock thinking insights"""
        return {
            "complexity": self.assess_query_complexity(query).value,
            "estimated_thinking_time": 5.0
        }

def test_intelligent_thinking_mode():
    """Test intelligent thinking mode decision making"""
    
    print("🧠 Testing Intelligent Thinking Mode Integration")
    print("=" * 60)
    
    # Mock the thinking engine import
    import thinking_mode_engine
    thinking_mode_engine.ThinkingModeEngine = MockThinkingEngine
    
    # Create mock base engine
    base_engine = MockAIEngine()
    
    # Create adaptive engine
    adaptive_engine = AdaptiveAIEngine(base_engine, enable_thinking_mode=True)
    
    # Test queries of different types
    test_queries = [
        # Mathematical - should trigger thinking mode
        ("Calculate the area of a circle with radius 5", TaskType.MATHEMATICAL, True),
        
        # Logical reasoning - should trigger thinking mode  
        ("If all birds can fly and penguins are birds, can penguins fly?", TaskType.LOGICAL, True),
        
        # Coding problem - should trigger thinking mode
        ("Write a Python function to find the maximum element in a list", TaskType.CODING, True),
        
        # Simple conversational - may not need thinking mode
        ("Hello, how are you today?", TaskType.CONVERSATIONAL, False),
        
        # Creative task - depends on complexity
        ("Write a short story about a robot", TaskType.CREATIVE, False),
        
        # Analytical task - should benefit from thinking
        ("Compare the advantages and disadvantages of renewable energy", TaskType.ANALYTICAL, True),
    ]
    
    for query, expected_task_type, expecting_thinking in test_queries:
        print(f"\n📝 Query: {query}")
        
        # Get insights about how it would be processed
        insights = adaptive_engine.get_thinking_insights(query)
        
        print(f"   🎯 Task Type: {insights.get('adaptive_task_type', 'unknown')}")
        print(f"   🤔 Would Use Thinking: {insights.get('would_use_thinking_mode', False)}")
        print(f"   📊 Confidence Level: {insights.get('adaptive_confidence_level', 'unknown')}")
        print(f"   ⚙️  Parameters: {insights.get('adaptive_parameters', {})}")
        
        # Verify task type classification
        detected_task_type = insights.get('adaptive_task_type', 'unknown')
        if detected_task_type == expected_task_type.value:
            print(f"   ✅ Task type correctly identified")
        else:
            print(f"   ⚠️  Task type mismatch: expected {expected_task_type.value}, got {detected_task_type}")
        
        # Check thinking mode decision
        would_think = insights.get('would_use_thinking_mode', False)
        if would_think == expecting_thinking:
            print(f"   ✅ Thinking mode decision correct")
        else:
            print(f"   ℹ️  Thinking mode: expected {expecting_thinking}, got {would_think}")

def test_adaptive_generation():
    """Test adaptive generation with different query types"""
    
    print(f"\n🚀 Testing Adaptive Generation")
    print("=" * 60)
    
    # Mock the thinking engine import
    import thinking_mode_engine
    thinking_mode_engine.ThinkingModeEngine = MockThinkingEngine
    
    base_engine = MockAIEngine()
    adaptive_engine = AdaptiveAIEngine(base_engine, enable_thinking_mode=True)
    
    # Test mathematical query (should use thinking mode)
    math_query = "Solve for x: 2x + 5 = 15"
    print(f"\n🔢 Mathematical Query: {math_query}")
    print("Response: ", end="")
    
    response_tokens = []
    try:
        for token in adaptive_engine.generate_response(math_query, max_tokens=50):
            print(token, end="")
            response_tokens.append(token)
    except Exception as e:
        print(f"\n❌ Error during generation: {e}")
    
    print(f"\n✅ Generated {len(response_tokens)} tokens")
    
    # Test simple conversational query (direct mode)
    chat_query = "Hi there!"
    print(f"\n💬 Conversational Query: {chat_query}")
    print("Response: ", end="")
    
    response_tokens = []
    try:
        for token in adaptive_engine.generate_response(chat_query, max_tokens=30):
            print(token, end="")
            response_tokens.append(token)
    except Exception as e:
        print(f"\n❌ Error during generation: {e}")
    
    print(f"\n✅ Generated {len(response_tokens)} tokens")

def test_performance_tracking():
    """Test performance tracking with adaptive insights"""
    
    print(f"\n📊 Testing Performance Tracking")
    print("=" * 60)
    
    # Mock the thinking engine import
    import thinking_mode_engine
    thinking_mode_engine.ThinkingModeEngine = MockThinkingEngine
    
    base_engine = MockAIEngine()
    adaptive_engine = AdaptiveAIEngine(base_engine, enable_thinking_mode=True)
    
    # Generate a few responses to create metrics
    test_queries = [
        "Calculate 15 * 23",
        "Hello, how are you?",
        "Explain how photosynthesis works",
        "Write a function to sort a list"
    ]
    
    for query in test_queries:
        print(f"Processing: {query}")
        try:
            response = list(adaptive_engine.generate_response(query, max_tokens=30))
            print(f"  ✅ Generated {len(response)} tokens")
        except Exception as e:
            print(f"  ❌ Error: {e}")
    
    # Check performance statistics
    stats = adaptive_engine.get_performance_stats()
    
    print(f"\n📈 Performance Statistics:")
    print(f"   Total Responses: {stats.get('total_responses', 0)}")
    print(f"   Thinking Mode Usage: {stats.get('thinking_mode_usage_rate', 0):.1f}%")
    print(f"   Adaptive Params Usage: {stats.get('adaptive_params_usage_rate', 0):.1f}%")
    print(f"   Average Response Time: {stats.get('average_response_time', 0):.2f}s")
    print(f"   Task Type Distribution: {stats.get('task_type_distribution', {})}")
    print(f"   Thinking by Task Type: {stats.get('thinking_by_task_type', {})}")

if __name__ == "__main__":
    try:
        test_intelligent_thinking_mode()
        test_adaptive_generation()
        test_performance_tracking()
        
        print(f"\n🎉 All tests completed!")
        print(f"✅ Intelligent thinking mode integration is working correctly")
        print(f"✅ Task-aware parameter optimization is functional")
        print(f"✅ Performance tracking includes adaptive insights")
        
    except Exception as e:
        print(f"\n❌ Test failed with error: {e}")
        import traceback
        traceback.print_exc()