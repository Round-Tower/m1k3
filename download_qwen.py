#!/usr/bin/env python3
"""
Quick Qwen2.5-1.5B-Instruct Downloader for M1K3
Fast, reliable reasoning model - only 1.5GB
"""

import time
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

def download_qwen():
    """Download Qwen2.5-1.5B-Instruct - fast reasoning model"""
    print("🚀 M1K3 Qwen2.5-1.5B Downloader")
    print("=" * 40)
    print("📦 Model: Qwen/Qwen2.5-1.5B-Instruct")
    print("📏 Size: ~1.5GB (much faster than Gemma)")
    print("🎯 Strengths: Excellent reasoning, math, logic")
    print()
    
    start_time = time.time()
    
    try:
        print("📝 Downloading tokenizer...")
        tokenizer = AutoTokenizer.from_pretrained('Qwen/Qwen2.5-1.5B-Instruct')
        print("✅ Tokenizer downloaded")
        
        print("🧠 Downloading model (1.5GB)...")
        model = AutoModelForCausalLM.from_pretrained(
            'Qwen/Qwen2.5-1.5B-Instruct',
            torch_dtype=torch.float16,
            device_map='auto'
        )
        
        download_time = time.time() - start_time
        print(f"✅ Download complete in {download_time:.1f}s!")
        
        # Test reasoning
        print("\n🔍 Testing reasoning capability...")
        test_prompt = "What is 15 + 27? Think step by step."
        inputs = tokenizer(test_prompt, return_tensors='pt')
        
        with torch.no_grad():
            outputs = model.generate(
                inputs['input_ids'],
                max_new_tokens=100,
                temperature=0.7,
                do_sample=True,
                pad_token_id=tokenizer.eos_token_id
            )
        
        response = tokenizer.decode(outputs[0], skip_special_tokens=True)
        print(f"🎯 Test: {test_prompt}")
        print(f"🤖 Response: {response}")
        
        print("\n🎉 SUCCESS: Qwen2.5-1.5B ready for M1K3!")
        print("📝 This model will be automatically selected as default")
        return True
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

if __name__ == "__main__":
    success = download_qwen()
    if success:
        print("\n✨ Ready! Restart M1K3 to use enhanced reasoning.")
    else:
        print("\n❌ Download failed.")