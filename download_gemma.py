#!/usr/bin/env python3
"""
Standalone Gemma Model Downloader
Downloads Google Gemma-2-2B-IT model for M1K3 system
"""

import time
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
import os

def download_gemma():
    """Download complete Gemma model with progress tracking"""
    print("🚀 M1K3 Gemma Model Downloader")
    print("=" * 50)
    print("📦 Model: google/gemma-2-2b-it")
    print("📏 Size: ~3.5GB")
    print("🎯 Purpose: Enhanced reasoning for M1K3")
    print()
    
    start_time = time.time()
    
    try:
        # Step 1: Download tokenizer
        print("📝 Step 1/2: Downloading tokenizer...")
        tokenizer = AutoTokenizer.from_pretrained(
            'google/gemma-2-2b-it', 
            force_download=True
        )
        print("✅ Tokenizer downloaded successfully")
        print()
        
        # Step 2: Download model
        print("🧠 Step 2/2: Downloading model weights...")
        print("⏳ This will take several minutes...")
        print()
        
        model = AutoModelForCausalLM.from_pretrained(
            'google/gemma-2-2b-it',
            force_download=True,
            torch_dtype=torch.bfloat16,  # Memory efficient
            device_map='auto',
            low_cpu_mem_usage=True
        )
        
        download_time = time.time() - start_time
        print()
        print("✅ Model downloaded successfully!")
        print(f"⏱️  Total time: {download_time:.1f} seconds ({download_time/60:.1f} minutes)")
        
        # Step 3: Verify functionality
        print()
        print("🔍 Step 3/3: Verifying model functionality...")
        
        test_text = "What is artificial intelligence?"
        inputs = tokenizer(test_text, return_tensors='pt')
        
        with torch.no_grad():
            outputs = model.generate(
                inputs['input_ids'],
                max_new_tokens=50,
                do_sample=True,
                temperature=0.7,
                pad_token_id=tokenizer.eos_token_id
            )
        
        response = tokenizer.decode(outputs[0], skip_special_tokens=True)
        print(f"🎯 Test input: {test_text}")
        print(f"🤖 Test response: {response}")
        print()
        
        # Model info
        param_count = sum(p.numel() for p in model.parameters())
        print("📊 Model Information:")
        print(f"   Parameters: {param_count / 1e9:.2f}B")
        print(f"   Memory usage: ~{param_count * 2 / 1e9:.1f}GB (bfloat16)")
        print(f"   Cache location: ~/.cache/huggingface/hub/models--google--gemma-2-2b-it")
        print()
        
        print("🎉 SUCCESS: Gemma model is ready for M1K3!")
        print("📝 Next steps:")
        print("   1. Return to your M1K3 session")
        print("   2. Test the AI engine - it will now use Gemma by default")
        print("   3. Enjoy enhanced reasoning capabilities!")
        print()
        
        return True
        
    except KeyboardInterrupt:
        print("\n⚠️  Download interrupted by user")
        print("💡 You can resume by running this script again")
        return False
        
    except Exception as e:
        print(f"\n❌ Download failed: {e}")
        print("\n🔧 Troubleshooting:")
        print("   - Check internet connection")
        print("   - Ensure sufficient disk space (~4GB)")
        print("   - Try running: pip install --upgrade transformers torch")
        print("   - Clear cache: rm -rf ~/.cache/huggingface/hub/models--google--gemma-2-2b-it")
        return False

if __name__ == "__main__":
    print("🤖 Starting Gemma download for M1K3...")
    print("💡 This script can run independently in any terminal")
    print()
    
    success = download_gemma()
    
    if success:
        print("✨ Download complete! M1K3 is now ready with Gemma!")
    else:
        print("❌ Download failed. Check the error messages above.")
        exit(1)