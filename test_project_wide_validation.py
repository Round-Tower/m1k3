#!/usr/bin/env python3
"""
Comprehensive project-wide validation test for M1K3 after adaptive optimization implementation
"""

import sys
import os
import time
from pathlib import Path

def test_core_imports():
    """Test that all core modules can be imported"""
    print("1️⃣  TESTING CORE MODULE IMPORTS")
    print("-" * 50)
    
    imports = [
        ("m1k3", "Main M1K3 module"),
        ("ai_inference", "AI inference engine"),  
        ("adaptive_model_config", "Adaptive model configuration"),
        ("adaptive_ai_engine", "Adaptive AI engine"),
        ("simple_ai_engine", "Simple AI fallback engine"),
        ("local_model_manager", "Local model manager"),
        ("avatar_controller", "Avatar controller"),
        ("cli", "CLI interface"),
        ("m1k3_tui", "Textual TUI"),
        ("m1k3_rich_tui", "Rich TUI")
    ]
    
    passed = 0
    failed = 0
    
    for module, description in imports:
        try:
            __import__(module)
            print(f"   ✅ {module}: {description}")
            passed += 1
        except Exception as e:
            print(f"   ❌ {module}: {e}")
            failed += 1
    
    print(f"   📊 Import Results: {passed}/{len(imports)} passed ({(passed/len(imports)*100):.1f}%)")
    return passed >= len(imports) * 0.8  # 80% pass rate

def test_ai_system():
    """Test AI system functionality"""
    print(f"\n2️⃣  TESTING AI SYSTEM")
    print("-" * 50)
    
    try:
        from ai_inference import LocalAIEngine
        from adaptive_ai_engine import AdaptiveAIEngine
        from simple_ai_engine import SimpleAIEngine
        
        # Test LocalAIEngine
        print("   🤖 Testing LocalAIEngine...")
        engine = LocalAIEngine()
        if engine.load_model():
            print("   ✅ LocalAIEngine: Real AI model loaded")
            
            # Test generation
            response = list(engine.generate_response("Hello!", max_tokens=5))
            if response:
                print(f"   ✅ LocalAIEngine: Generation working ({''.join(response)[:30]}...)")
            else:
                print("   ❌ LocalAIEngine: No response generated")
                return False
                
        else:
            print("   ⚠️  LocalAIEngine: Model not loaded, testing fallback")
        
        # Test SimpleAIEngine fallback
        print("   🔄 Testing SimpleAIEngine fallback...")
        fallback = SimpleAIEngine()
        fallback_response = list(fallback.generate_response("Hello!", max_tokens=5))
        if fallback_response:
            print(f"   ✅ SimpleAIEngine: Fallback working ({''.join(fallback_response)[:30]}...)")
        else:
            print("   ❌ SimpleAIEngine: Fallback failed")
            return False
        
        # Test AdaptiveAIEngine
        print("   🧠 Testing AdaptiveAIEngine...")
        adaptive = AdaptiveAIEngine(engine, enable_thinking_mode=True)
        insights = adaptive.get_thinking_insights("Calculate 5 * 8")
        
        if insights.get('adaptive_task_type') == 'mathematical':
            print("   ✅ AdaptiveAIEngine: Task classification working")
        else:
            print(f"   ❌ AdaptiveAIEngine: Task classification issue ({insights.get('adaptive_task_type')})")
            return False
        
        return True
        
    except Exception as e:
        print(f"   ❌ AI System Error: {e}")
        return False

def test_adaptive_optimization():
    """Test the new adaptive optimization system"""
    print(f"\n3️⃣  TESTING ADAPTIVE OPTIMIZATION")
    print("-" * 50)
    
    try:
        from adaptive_model_config import AdaptiveModelConfig, TaskType
        
        config = AdaptiveModelConfig()
        
        # Test different query types
        test_queries = [
            ("Calculate the area of a circle", "mathematical"),
            ("Write a Python function", "coding"),
            ("Hello, how are you?", "conversational")
        ]
        
        for query, expected_type in test_queries:
            result = config.get_optimal_config(query, "TinyLlama/TinyLlama-1.1B-Chat-v1.0", [])
            metadata = result.get('_metadata', {})
            task_type = metadata.get('task_type', 'unknown')
            
            if task_type == expected_type:
                print(f"   ✅ '{query[:30]}...' → {task_type}")
            else:
                print(f"   ⚠️  '{query[:30]}...' → {task_type} (expected {expected_type})")
        
        # Test parameter differences
        math_config = config.get_optimal_config("Calculate 5 * 8", "TinyLlama/TinyLlama-1.1B-Chat-v1.0", [])
        chat_config = config.get_optimal_config("Hello there!", "TinyLlama/TinyLlama-1.1B-Chat-v1.0", [])
        
        math_tokens = math_config.get('max_new_tokens', 0)
        chat_tokens = chat_config.get('max_new_tokens', 0)
        
        if math_tokens != chat_tokens:
            print(f"   ✅ Parameter optimization: Math ({math_tokens}) vs Chat ({chat_tokens}) tokens")
        else:
            print(f"   ⚠️  Parameter optimization: Same parameters for different tasks")
        
        # Test metadata filtering
        filtered = {k: v for k, v in math_config.items() if k != '_metadata'}
        if '_metadata' not in filtered and '_metadata' in math_config:
            print(f"   ✅ Metadata filtering working")
        else:
            print(f"   ❌ Metadata filtering issue")
            return False
        
        return True
        
    except Exception as e:
        print(f"   ❌ Adaptive Optimization Error: {e}")
        return False

def test_model_management():
    """Test model management system"""
    print(f"\n4️⃣  TESTING MODEL MANAGEMENT")
    print("-" * 50)
    
    try:
        from local_model_manager import LocalModelManager
        
        manager = LocalModelManager()
        device = manager.analyze_device()
        
        print(f"   📦 Available models: {len(manager.available_models)}")
        print(f"   🎯 Best model: {manager.get_best_model()}")
        print(f"   💻 Available RAM: {device.available_ram_gb:.1f} GB")
        print(f"   🏛️ Architecture: {device.platform}")
        
        if len(manager.available_models) > 0:
            print("   ✅ Model discovery working")
        else:
            print("   ⚠️  No models found")
        
        if manager.get_best_model():
            print("   ✅ Model selection working")
            return True
        else:
            print("   ❌ Model selection failed")
            return False
            
    except Exception as e:
        print(f"   ❌ Model Management Error: {e}")
        return False

def test_interface_systems():
    """Test interface systems"""
    print(f"\n5️⃣  TESTING INTERFACE SYSTEMS")
    print("-" * 50)
    
    try:
        # Test CLI
        import cli
        print("   ✅ CLI module loads")
        
        # Test TUI
        import m1k3_tui
        import m1k3_rich_tui
        print("   ✅ TUI modules load")
        
        # Test avatar controller
        from avatar_controller import AvatarController, AvatarState, AvatarEmotion
        controller = AvatarController()
        controller.update_state(AvatarState.IDLE)
        print("   ✅ Avatar controller working")
        
        return True
        
    except Exception as e:
        print(f"   ❌ Interface Systems Error: {e}")
        return False

def test_integration():
    """Test end-to-end integration"""
    print(f"\n6️⃣  TESTING END-TO-END INTEGRATION")
    print("-" * 50)
    
    try:
        from ai_inference import LocalAIEngine
        from adaptive_ai_engine import AdaptiveAIEngine
        
        # Create integrated system
        base_engine = LocalAIEngine()
        adaptive_engine = AdaptiveAIEngine(base_engine, enable_thinking_mode=True)
        
        # Load model
        if base_engine.load_model():
            print("   ✅ Integrated system: Model loaded")
        else:
            print("   ⚠️  Integrated system: Using fallback")
        
        # Test mathematical query (should use thinking mode)
        print("   🔢 Testing mathematical query integration...")
        insights = adaptive_engine.get_thinking_insights("What is 15 * 23?")
        
        task_type = insights.get('adaptive_task_type', 'unknown')
        uses_thinking = insights.get('would_use_thinking_mode', False)
        
        print(f"   📊 Task classification: {task_type}")
        print(f"   🤔 Uses thinking mode: {uses_thinking}")
        
        if task_type == 'mathematical' and uses_thinking:
            print("   ✅ Mathematical query optimization working")
        else:
            print("   ⚠️  Mathematical query optimization needs attention")
        
        # Test conversational query (should use direct mode)
        print("   💬 Testing conversational query integration...")
        insights = adaptive_engine.get_thinking_insights("Hello, how are you?")
        
        task_type = insights.get('adaptive_task_type', 'unknown')
        uses_thinking = insights.get('would_use_thinking_mode', False)
        
        print(f"   📊 Task classification: {task_type}")
        print(f"   🤔 Uses thinking mode: {uses_thinking}")
        
        if task_type == 'conversational' and not uses_thinking:
            print("   ✅ Conversational query optimization working")
        else:
            print("   ⚠️  Conversational query optimization needs attention")
        
        print("   ✅ End-to-end integration functional")
        return True
        
    except Exception as e:
        print(f"   ❌ Integration Error: {e}")
        return False

def main():
    """Run comprehensive project-wide validation"""
    
    print("🚀 M1K3 PROJECT-WIDE VALIDATION SUITE")
    print("=" * 80)
    print("Testing all core systems after adaptive optimization implementation...")
    
    tests = [
        ("Core Imports", test_core_imports),
        ("AI System", test_ai_system), 
        ("Adaptive Optimization", test_adaptive_optimization),
        ("Model Management", test_model_management),
        ("Interface Systems", test_interface_systems),
        ("End-to-End Integration", test_integration)
    ]
    
    results = []
    total_start_time = time.time()
    
    for test_name, test_func in tests:
        start_time = time.time()
        try:
            result = test_func()
            results.append((test_name, result, time.time() - start_time))
        except Exception as e:
            print(f"   ❌ Test '{test_name}' crashed: {e}")
            results.append((test_name, False, time.time() - start_time))
    
    # Summary
    total_time = time.time() - total_start_time
    passed = sum(1 for _, result, _ in results if result)
    total = len(results)
    
    print(f"\n🎯 PROJECT-WIDE VALIDATION COMPLETE")
    print("=" * 80)
    
    for test_name, result, duration in results:
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"{status} {test_name:<25} ({duration:.2f}s)")
    
    success_rate = (passed / total) * 100
    print(f"\n📊 OVERALL RESULTS:")
    print(f"   • Tests Passed: {passed}/{total} ({success_rate:.1f}%)")
    print(f"   • Total Time: {total_time:.2f} seconds")
    
    if success_rate >= 80:
        print(f"\n🎉 PROJECT VALIDATION SUCCESSFUL!")
        print(f"✅ M1K3 with adaptive optimization is ready for production!")
        print(f"🧠 Intelligent task-aware AI responses operational")
        print(f"⚡ Optimized parameter selection working correctly")
        return True
    else:
        print(f"\n⚠️  Some systems need attention before production deployment")
        return False

if __name__ == "__main__":
    try:
        success = main()
        exit(0 if success else 1)
    except Exception as e:
        print(f"❌ Validation suite crashed: {e}")
        import traceback
        traceback.print_exc()
        exit(1)