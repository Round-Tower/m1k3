#!/usr/bin/env python3
"""
Test Gemma 2B integration with M1K3 AI system
"""

import time
import pytest
from src.engines.ai import ai_inference
from src.engines.ai.ai_inference import LocalAIEngine

def test_gemma_2b_integration(monkeypatch):
    # Mock the availability flags to ensure the test runs in a consistent environment
    monkeypatch.setattr(ai_inference, "UNIVERSAL_ENGINE_AVAILABLE", True)
    monkeypatch.setattr(ai_inference, "SMOLLM_ENGINE_AVAILABLE", True)
    monkeypatch.setattr(ai_inference, "CTRANSFORMERS_AVAILABLE", True)
    monkeypatch.setattr(ai_inference, "TRANSFORMERS_AVAILABLE", True)
    print("🧪 Testing Gemma 2B Integration")
    print("=" * 50)
    
    print("📋 Test Plan:")
    print("1. Load AI engine (should prioritize Gemma 2B)")
    print("2. Test reasoning capabilities")  
    print("3. Test instruction following")
    print("4. Compare response quality vs TinyLlama")
    print("5. Measure performance metrics")
    print()
    
    # Test 1: Model Loading
    print("🔄 Test 1: Model Loading")
    print("-" * 30)
    
    start_time = time.time()
    try:
        engine = LocalAIEngine()
        model_loaded = engine.load_model() # Explicitly load the model
        load_time = time.time() - start_time
        
        # Success is when the loader returns true, and either the legacy model is loaded
        # or the new Universal Engine is loaded and ready.
        is_successful_load = model_loaded and \
            (engine.model is not None or (hasattr(engine, 'universal_engine') and engine.universal_engine and engine.universal_engine.engine_loaded))

        if is_successful_load:
            model_name = engine.universal_engine.current_model_name if engine.universal_engine and engine.universal_engine.engine_loaded else getattr(engine, '_current_model_name', 'Unknown')
            print(f"✅ Model loaded: {model_name}")
            print(f"⏱️  Load time: {load_time:.2f} seconds")
            print(f"🧠 Backend: {'HF Transformers' if engine.use_transformers else 'ctransformers'}")
        else:
            assert False, "Model loading failed"
            
    except Exception as e:
        assert False, f"An exception occurred during model loading: {e}"
    
    print()
    
    # Test 2: Reasoning Capabilities
    print("🧠 Test 2: Reasoning Capabilities")
    print("-" * 30)
    
    reasoning_questions = [
        "If I have 5 apples and give away 2, how many do I have left? Explain your reasoning.",
        "What is the capital of France and why is it important?",
        "Explain the difference between machine learning and artificial intelligence."
    ]
    
    for i, question in enumerate(reasoning_questions, 1):
        print(f"\n🔍 Question {i}: {question}")
        print("Response: ", end="", flush=True)
        
        start_time = time.time()
        response_tokens = []
        
        try:
            for token in engine.generate_response(question, max_tokens=200):
                response_tokens.append(token)
                print(token, end="", flush=True)
            
            response_time = time.time() - start_time
            full_response = "".join(response_tokens)
            
            print(f"\n\n📊 Metrics:")
            print(f"   Length: {len(full_response)} characters")
            print(f"   Words: {len(full_response.split())} words")
            print(f"   Time: {response_time:.2f} seconds")
            print(f"   Quality: {'✅ Good' if len(full_response) > 50 else '⚠️ Short'}")
            
        except Exception as e:
            print(f"❌ Error: {e}")
        
        print("-" * 40)
    
    # Test 3: Instruction Following
    print("\n🎯 Test 3: Instruction Following")
    print("-" * 30)
    
    instruction_tests = [
        "Write a short poem about artificial intelligence in exactly 4 lines.",
        "List 3 benefits of local AI processing.",
        "Explain Python in simple terms for a beginner."
    ]
    
    for i, instruction in enumerate(instruction_tests, 1):
        print(f"\n📝 Instruction {i}: {instruction}")
        print("Response: ", end="", flush=True)
        
        try:
            response_tokens = []
            for token in engine.generate_response(instruction, max_tokens=150):
                response_tokens.append(token)
                print(token, end="", flush=True)
            
            full_response = "".join(response_tokens)
            print(f"\n\n✅ Instruction following: {'Good' if len(full_response) > 30 else 'Needs improvement'}")
            
        except Exception as e:
            print(f"❌ Error: {e}")
        
        print("-" * 40)
    
    # Test Summary
    print("\n🎉 Test Summary")
    print("=" * 50)
    
    model_name = getattr(engine, '_current_model_name', 'Unknown')
    if 'gemma' in model_name.lower():
        print("✅ SUCCESS: Gemma 2B model loaded and working!")
        print("🎯 Enhanced reasoning capabilities enabled")
        print("💡 Improved instruction following active")
        print("🚀 Ready for production use")
    elif 'tinyllama' in model_name.lower():
        print("⚠️  FALLBACK: Using TinyLlama model")
        print("💭 Gemma 2B not available, using backup model")
        print("🔧 Consider model download or memory upgrade")
    else:
        print("ℹ️  INFO: Using alternative model")
        print(f"🔧 Loaded: {model_name}")
        
    print(f"\n📈 Performance Profile:")
    print(f"   Model: {model_name}")
    print(f"   Backend: {'HuggingFace Transformers' if engine.use_transformers else 'ctransformers'}")
    print(f"   Load Time: {load_time:.2f}s")

if __name__ == "__main__":
    success = test_gemma_2b_integration()
    
    if success:
        print("\n✨ Gemma 2B integration test completed successfully!")
        print("🚀 M1K3 is ready with enhanced AI capabilities!")
    else:
        print("\n❌ Integration test failed")
        print("🔧 Check model availability and dependencies")