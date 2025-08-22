#!/usr/bin/env python3
"""
Simple Qwen3 compatibility test
"""

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

def test_qwen3_simple():
    print("🔍 Testing Qwen3-0.6B compatibility...")
    
    # Check MPS availability
    if torch.backends.mps.is_available():
        print("✅ MPS (Apple Silicon GPU) detected")
        dtype = torch.float16
        print("🔧 Using float16 for MPS compatibility")
    else:
        print("💻 CPU/CUDA mode")
        dtype = torch.bfloat16
        print("🔧 Using bfloat16")
    
    try:
        print("📝 Step 1: Loading tokenizer...")
        tokenizer = AutoTokenizer.from_pretrained('Qwen/Qwen3-0.6B')
        print(f"✅ Tokenizer loaded (vocab: {tokenizer.vocab_size:,})")
        
        print("🧠 Step 2: Loading model...")
        model = AutoModelForCausalLM.from_pretrained(
            'Qwen/Qwen3-0.6B',
            torch_dtype=dtype,
            device_map='cpu',  # Force CPU to avoid MPS issues
            low_cpu_mem_usage=True,
            trust_remote_code=True
        )
        print("✅ Model loaded successfully!")
        
        print("🧪 Step 3: Quick test...")
        inputs = tokenizer("What is 2+2?", return_tensors='pt')
        with torch.no_grad():
            outputs = model.generate(
                inputs['input_ids'],
                max_new_tokens=50,
                temperature=0.7,
                do_sample=True,
                pad_token_id=tokenizer.eos_token_id
            )
        
        response = tokenizer.decode(outputs[0], skip_special_tokens=True)
        print(f"🤖 Response: {response}")
        
        print("🎉 SUCCESS: Qwen3-0.6B is working!")
        return True
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

if __name__ == "__main__":
    test_qwen3_simple()