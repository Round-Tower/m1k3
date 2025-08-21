#!/usr/bin/env python3
"""
Test M1K3 AI responses with common questions
"""

import sys
from ai_inference import LocalAIEngine

def test_ai_responses():
    """Test the AI engine with various questions"""
    
    print("🧪 Testing M1K3 AI Responses\n" + "="*50)
    
    # Initialize the AI engine
    engine = LocalAIEngine()
    
    if not engine.load_model():
        print("❌ Failed to load AI model")
        return
    
    # Test questions
    test_questions = [
        "Hello, how are you?",
        "What is the capital of France?",
        "Can you help me write a Python function?",
        "What's the weather like today?",
        "Tell me a joke",
        "What is 2 + 2?",
        "Explain machine learning in simple terms",
        "What programming languages do you know?"
    ]
    
    for i, question in enumerate(test_questions, 1):
        print(f"\n🔍 Test {i}: {question}")
        print("-" * 60)
        
        # Generate response
        response_tokens = []
        try:
            for token in engine.generate_response(question, max_tokens=100):
                response_tokens.append(token)
                print(token, end="", flush=True)
            
            full_response = "".join(response_tokens)
            print(f"\n\n📊 Response length: {len(full_response)} chars")
            print(f"📝 Response quality: {'✅ Good' if len(full_response.strip()) > 5 else '❌ Poor'}")
            
        except Exception as e:
            print(f"❌ Error: {e}")
        
        print("\n" + "="*60)

if __name__ == "__main__":
    test_ai_responses()