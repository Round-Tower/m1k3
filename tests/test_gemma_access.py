#!/usr/bin/env python3
"""
Test Gemma model access across different versions
"""

gemma_models = [
    "google/gemma-2-2b-it",      # Newer version
    "google/gemma-2b-it",        # Original version  
    "google/gemma-2b",           # Base model (non-instruct)
    "google/gemma-7b-it",        # Larger instruct model
]

print("🔍 Testing Gemma Model Access")
print("=" * 40)

for model in gemma_models:
    print(f"\n🧪 Testing: {model}")
    try:
        from transformers import AutoTokenizer
        tokenizer = AutoTokenizer.from_pretrained(model)
        print("✅ ACCESS GRANTED!")
        print(f"🎯 Use this model: {model}")
        break
    except Exception as e:
        if "403" in str(e) or "gated" in str(e).lower():
            print("🔒 Access required - need to accept license")
        elif "404" in str(e):
            print("❌ Model not found")
        else:
            print(f"❌ Error: {e}")

print(f"\n💡 If all models require access:")
print("1. Visit: https://huggingface.co/google/gemma-2-2b-it")
print("2. Look for 'Accept license' or similar button")
print("3. Make sure you're logged in first")
print("4. Check your email for confirmation")