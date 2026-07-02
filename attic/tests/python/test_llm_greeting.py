#!/usr/bin/env python3
"""
Test script for LLM-powered greeting system
"""

import sys
import os

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

def test_llm_greeting():
    print("🧪 Testing LLM-Powered Greeting System")
    print("=" * 50)
    
    try:
        # Import required modules
        from src.utils.performance.system_metrics import SystemMonitor
        from llm_greeting_engine import generate_llm_greeting, create_greeting_context
        from src.engines.ai.ai_inference import LocalAIEngine
        from src.engines.ai.adaptive_ai_engine import AdaptiveAIEngine
        
        print("✅ All modules imported successfully")
        
        # Initialize AI engine
        print("🔄 Loading AI engine...")
        ai_engine = LocalAIEngine(auto_load=True)
        if not ai_engine.load_model():
            print("❌ Failed to load AI model")
            return False
        
        print(f"✅ AI engine loaded: {getattr(ai_engine, 'current_model_name', 'Unknown')}")
        
        # Create adaptive engine
        adaptive_engine = AdaptiveAIEngine(
            base_ai_engine=ai_engine,
            avatar_controller=None,
            enable_thinking_mode=False,  # Keep greetings simple
            show_reasoning_by_default=False
        )
        print("✅ Adaptive AI engine created")
        
        # Collect system metrics
        monitor = SystemMonitor()
        metrics = monitor.collect_metrics()
        print(f"✅ System metrics collected")
        
        # Create M1K3 context
        m1k3_context = {
            'ai_model': getattr(ai_engine, 'current_model_name', 'Local AI'),
            'voice_enabled': True,
            'avatar_enabled': True,
            'session_count': 1
        }
        
        print("\n🎯 Generating LLM-powered greeting...")
        print("-" * 30)
        
        # Generate greeting
        greeting = generate_llm_greeting(
            adaptive_engine, 
            metrics, 
            m1k3_context, 
            max_length=80
        )
        
        print(f"💬 Generated greeting: {greeting}")
        print(f"📏 Length: {len(greeting)} characters")
        
        # Test multiple greetings to show variety
        print("\n🔄 Testing greeting variety (3 samples):")
        print("-" * 40)
        
        for i in range(3):
            # Slightly modify context to get different greetings
            test_context = m1k3_context.copy()
            test_context['session_count'] = i + 1
            
            greeting = generate_llm_greeting(
                adaptive_engine, 
                metrics, 
                test_context, 
                max_length=70
            )
            print(f"{i+1}. {greeting}")
        
        print("\n✅ LLM greeting system working successfully!")
        return True
        
    except Exception as e:
        print(f"❌ Error testing LLM greeting: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_fallback_greeting():
    """Test the fallback greeting system"""
    print("\n🧪 Testing Fallback Greeting System")
    print("=" * 50)
    
    try:
        from src.utils.performance.system_metrics import SystemMonitor, generate_dynamic_greeting
        
        monitor = SystemMonitor()
        metrics = monitor.collect_metrics()
        
        m1k3_context = {
            'ai_model': 'Test AI',
            'voice_enabled': True,
            'avatar_enabled': False
        }
        
        greeting = generate_dynamic_greeting(metrics, m1k3_context)
        print(f"💬 Fallback greeting: {greeting}")
        print("✅ Fallback system working")
        
        return True
        
    except Exception as e:
        print(f"❌ Fallback greeting error: {e}")
        return False

if __name__ == "__main__":
    print("🚀 M1K3 Greeting System Test Suite\n")
    
    # Test fallback first (doesn't require AI)
    fallback_success = test_fallback_greeting()
    
    # Test LLM greeting
    llm_success = test_llm_greeting()
    
    print(f"\n📊 Test Results:")
    print(f"   Fallback greeting: {'✅' if fallback_success else '❌'}")
    print(f"   LLM greeting: {'✅' if llm_success else '❌'}")
    
    if llm_success:
        print("\n🎉 LLM-powered greeting system is ready for production!")
    else:
        print("\n⚠️  LLM greeting needs debugging, but fallback is available")
    
    sys.exit(0 if (fallback_success and llm_success) else 1)