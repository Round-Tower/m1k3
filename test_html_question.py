#!/usr/bin/env python3
"""
Test HTML question with M1K3 AI
"""

from ai_inference import LocalAIEngine

def test_html_question():
    print("🌐 Testing HTML Question\n" + "="*50)
    
    engine = LocalAIEngine()
    if not engine.model:
        print("❌ Model not loaded")
        return
    
    # Test the HTML question
    question = "How do you create a HTML page?"
    
    print(f"❓ Question: {question}")
    print("\n🤖 M1K3 Response:")
    print("-" * 50)
    
    try:
        response_parts = []
        for token in engine.generate_response(question, max_tokens=200):
            response_parts.append(token)
            print(token, end="", flush=True)
        
        full_response = "".join(response_parts)
        
        print(f"\n\n📊 Analysis:")
        print(f"   Length: {len(full_response)} characters")
        print(f"   Word count: {len(full_response.split())} words")
        print(f"   Contains HTML tags: {'✅' if '<' in full_response and '>' in full_response else '❌'}")
        print(f"   Mentions basic structure: {'✅' if any(tag in full_response.lower() for tag in ['html', 'head', 'body', 'title']) else '❌'}")
        print(f"   Overall quality: {'✅ Excellent' if len(full_response) > 100 else '⚠️ Basic'}")
        
    except Exception as e:
        print(f"❌ Error: {e}")

if __name__ == "__main__":
    test_html_question()