#!/usr/bin/env python3
"""
Quick test of Qwen3 with optimized parameters
"""

from src.engines.ai.ai_inference import LocalAIEngine

def test_quick_responses():
    print("🧪 Quick Qwen3 Parameter Test")
    print("=" * 40)
    
    engine = LocalAIEngine(auto_load=True)
    
    questions = [
        "What is 15 + 27?",
        "What is the capital of France?",
        "Define AI in one sentence."
    ]
    
    for q in questions:
        print(f"\n❓ {q}")
        print("🤖 ", end="", flush=True)
        
        response = ""
        for token in engine.generate_response(q, max_tokens=50):
            response += token
            print(token, end="", flush=True)
        
        print(f"\n📏 Length: {len(response)} chars, {len(response.split())} words")

if __name__ == "__main__":
    test_quick_responses()