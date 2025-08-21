#!/usr/bin/env python3
"""
Test HTML code question with M1K3 AI
"""

from ai_inference import LocalAIEngine

def test_html_code():
    print("🌐 Testing HTML Code Question\n" + "="*50)
    
    engine = LocalAIEngine()
    if not engine.model:
        print("❌ Model not loaded")
        return
    
    # Test the HTML code question
    question = "Show me the basic HTML structure with head and body tags"
    
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
        print(f"   Contains HTML tags: {'✅' if '<html>' in full_response or '&lt;html&gt;' in full_response else '❌'}")
        print(f"   Has head section: {'✅' if 'head' in full_response.lower() else '❌'}")
        print(f"   Has body section: {'✅' if 'body' in full_response.lower() else '❌'}")
        print(f"   Shows code structure: {'✅' if '<' in full_response and '>' in full_response else '❌'}")
        
    except Exception as e:
        print(f"❌ Error: {e}")

if __name__ == "__main__":
    test_html_code()