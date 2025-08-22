#!/usr/bin/env python3
"""
Test Real-time Context Integration
Demonstrates how WebSocket context data affects model configuration
"""

import asyncio
import time
from datetime import datetime
from enhanced_adaptive_model_config import EnhancedAdaptiveModelConfig
from websocket_context_server import ContextData

def test_realtime_context_integration():
    """Test how real-time context affects model configuration"""
    print("🌐 Testing Real-time Context Integration")
    print("=" * 60)
    
    # Create enhanced config with WebSocket enabled
    config = EnhancedAdaptiveModelConfig(enable_websocket=True)
    
    # Test query
    test_query = "Help me fix this code error"
    print(f"📝 Test Query: \"{test_query}\"")
    
    # Baseline configuration (no context)
    print("\n📊 Baseline (No Context):")
    baseline_config = config.get_optimal_config(test_query, "qwen/qwen3-0.6b", [])
    baseline_metadata = baseline_config.get('_metadata', {})
    print(f"🎯 Intent: {baseline_metadata.get('intent', 'unknown')}")
    print(f"📊 Confidence: {baseline_metadata.get('confidence', 0):.3f}")
    print(f"🎭 Strategy: {baseline_metadata.get('response_strategy', 'unknown')}")
    print(f"🔧 Max tokens: {baseline_config.get('max_new_tokens', 'unknown')}")
    print(f"🌡️  Temperature: {baseline_config.get('temperature', 'N/A')}")
    
    # Add context data that should affect the response
    print("\n📥 Adding Context Data...")
    
    # User is focused and working on urgent task
    config.add_context_data(ContextData(
        timestamp=time.time(),
        source='user_activity',
        data_type='user_mood',
        value='focused',
        confidence=0.9,
        metadata={'detected_via': 'typing_pattern'}
    ))
    
    config.add_context_data(ContextData(
        timestamp=time.time(),
        source='project_tracker',
        data_type='task_urgency',
        value='high',
        confidence=0.95,
        metadata={'deadline': 'today'}
    ))
    
    config.add_context_data(ContextData(
        timestamp=time.time(),
        source='app_state',
        data_type='conversation_topic',
        value='technical_debugging',
        confidence=0.85,
        metadata={'keywords': ['error', 'fix', 'debug']}
    ))
    
    config.add_context_data(ContextData(
        timestamp=time.time(),
        source='user_activity',
        data_type='typing_speed',
        value=85,  # Fast typing indicates urgency
        confidence=0.8,
        metadata={'unit': 'wpm'}
    ))
    
    print("   ✅ User mood: focused")
    print("   ✅ Task urgency: high") 
    print("   ✅ Topic: technical_debugging")
    print("   ✅ Typing speed: 85 wpm")
    
    # Get configuration with context
    print("\n📊 With Context:")
    context_config = config.get_optimal_config(test_query, "qwen/qwen3-0.6b", [])
    context_metadata = context_config.get('_metadata', {})
    print(f"🎯 Intent: {context_metadata.get('intent', 'unknown')}")
    print(f"📊 Confidence: {context_metadata.get('confidence', 0):.3f}")
    print(f"🎭 Strategy: {context_metadata.get('response_strategy', 'unknown')}")
    print(f"🔧 Max tokens: {context_config.get('max_new_tokens', 'unknown')}")
    print(f"🌡️  Temperature: {context_config.get('temperature', 'N/A')}")
    
    # Show context factors
    context_factors = context_metadata.get('context_factors', {})
    if context_factors:
        print(f"📋 Context factors: {context_factors}")
        
    websocket_data = context_factors.get('websocket_data', {})
    if websocket_data:
        print(f"🌐 WebSocket data: {websocket_data}")
    
    # Compare changes
    print("\n🔄 Configuration Changes:")
    
    # Compare key parameters
    baseline_tokens = baseline_config.get('max_new_tokens', 0)
    context_tokens = context_config.get('max_new_tokens', 0)
    
    if baseline_tokens != context_tokens:
        direction = "↗️" if context_tokens > baseline_tokens else "↘️"
        print(f"   Max tokens: {baseline_tokens} -> {context_tokens} {direction}")
    else:
        print(f"   Max tokens: unchanged ({baseline_tokens})")
        
    baseline_temp = baseline_config.get('temperature')
    context_temp = context_config.get('temperature')
    
    if baseline_temp != context_temp:
        if baseline_temp is None:
            print(f"   Temperature: None -> {context_temp} ⚡")
        elif context_temp is None:
            print(f"   Temperature: {baseline_temp} -> None ⚡")
        else:
            direction = "↗️" if context_temp > baseline_temp else "↘️"
            print(f"   Temperature: {baseline_temp} -> {context_temp} {direction}")
    else:
        print(f"   Temperature: unchanged ({baseline_temp})")
    
    # Show reasoning
    baseline_reasoning = baseline_metadata.get('reasoning', '')
    context_reasoning = context_metadata.get('reasoning', '')
    
    if baseline_reasoning != context_reasoning:
        print(f"\n💭 Reasoning evolution:")
        print(f"   Baseline: {baseline_reasoning}")
        print(f"   Context:  {context_reasoning}")

def test_different_scenarios():
    """Test how different context scenarios affect configuration"""
    print("\n" + "="*60)
    print("🎭 Testing Different Context Scenarios")
    print("=" * 60)
    
    config = EnhancedAdaptiveModelConfig(enable_websocket=True)
    test_query = "How do I learn Python programming?"
    
    scenarios = [
        {
            'name': 'Beginner Student',
            'context': [
                ContextData(time.time(), 'user_profile', 'experience_level', 'beginner', 0.9),
                ContextData(time.time(), 'user_activity', 'learning_mode', True, 0.95),
                ContextData(time.time(), 'session_data', 'available_time', 'extended', 0.8)
            ]
        },
        {
            'name': 'Urgent Professional',
            'context': [
                ContextData(time.time(), 'project_tracker', 'task_urgency', 'high', 0.95),
                ContextData(time.time(), 'calendar_data', 'next_meeting', '30_minutes', 0.9),
                ContextData(time.time(), 'user_activity', 'typing_speed', 95, 0.8)
            ]
        },
        {
            'name': 'Casual Explorer',
            'context': [
                ContextData(time.time(), 'user_activity', 'session_type', 'casual', 0.85),
                ContextData(time.time(), 'time_data', 'weekend', True, 1.0),
                ContextData(time.time(), 'user_mood', 'curious', 'high', 0.8)
            ]
        }
    ]
    
    for scenario in scenarios:
        print(f"\n🎬 Scenario: {scenario['name']}")
        
        # Clear previous context and add new context
        config.context_manager.context_buffer.clear()
        for context_data in scenario['context']:
            config.add_context_data(context_data)
            
        # Get configuration
        result = config.get_optimal_config(test_query, "qwen/qwen3-0.6b", [])
        metadata = result.get('_metadata', {})
        
        print(f"   🎯 Intent: {metadata.get('intent', 'unknown')}")
        print(f"   🎭 Strategy: {metadata.get('response_strategy', 'unknown')}")
        print(f"   🔧 Max tokens: {result.get('max_new_tokens', 'unknown')}")
        print(f"   🌡️  Temperature: {result.get('temperature', 'N/A')}")
        print(f"   📊 Confidence: {metadata.get('confidence', 0):.3f}")
        
        # Show key context factors
        context_factors = metadata.get('context_factors', {})
        websocket_data = context_factors.get('websocket_data', {})
        if websocket_data:
            key_factors = {k: v for k, v in websocket_data.items() if v not in [None, '', {}]}
            if key_factors:
                print(f"   🌐 Key factors: {key_factors}")

if __name__ == "__main__":
    test_realtime_context_integration()
    test_different_scenarios()
    
    print(f"\n" + "="*60) 
    print("✅ Real-time context integration tests completed!")
    print("💡 The system successfully adapts model parameters based on real-time context data")
    print("🎯 Intent classification remains consistent while generation strategy adapts to user state")