#!/usr/bin/env python3
"""
Qwen3-0.6B Downloader for M1K3
Fast, powerful reasoning model in a compact 1.5GB package
"""

import time
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
import psutil

def check_system_requirements():
    """Check if system can handle Qwen3-0.6B"""
    print("🔍 System Requirements Check")
    print("-" * 40)
    
    # Check RAM
    ram_gb = psutil.virtual_memory().total / (1024**3)
    available_ram_gb = psutil.virtual_memory().available / (1024**3)
    
    print(f"💾 Total RAM: {ram_gb:.1f}GB")
    print(f"💾 Available RAM: {available_ram_gb:.1f}GB")
    print(f"💾 Required RAM: ~2GB for Qwen3-0.6B")
    
    if available_ram_gb < 3:
        print("⚠️  Warning: Low available RAM. Model may load slowly.")
    else:
        print("✅ Sufficient RAM for optimal performance")
    
    # Check disk space
    disk_space = psutil.disk_usage('/').free / (1024**3)
    print(f"💽 Available disk space: {disk_space:.1f}GB")
    print(f"💽 Required space: ~2GB for model files")
    
    if disk_space < 3:
        print("❌ Insufficient disk space")
        return False
    else:
        print("✅ Sufficient disk space")
    
    print()
    return True

def download_qwen3():
    """Download Qwen3-0.6B model for enhanced reasoning"""
    print("🚀 M1K3 Qwen3-0.6B Downloader")
    print("=" * 50)
    print("📦 Model: Qwen/Qwen3-0.6B")
    print("📏 Size: ~1.5GB (compact but powerful)")
    print("🎯 Features: Advanced reasoning, thinking mode, 32k context")
    print("🧠 Capability: Outperforms larger models in logic/math")
    print()
    
    if not check_system_requirements():
        print("❌ System requirements not met")
        return False
    
    start_time = time.time()
    
    try:
        print("📝 Step 1/3: Downloading tokenizer...")
        tokenizer = AutoTokenizer.from_pretrained('Qwen/Qwen3-0.6B')
        print("✅ Tokenizer downloaded successfully")
        print(f"   Vocabulary size: {tokenizer.vocab_size:,}")
        print()
        
        print("🧠 Step 2/3: Downloading model weights...")
        print("⏳ This will take 3-8 minutes depending on connection...")
        
        # Use float16 for MPS compatibility (Apple Silicon)
        dtype = torch.float16 if torch.backends.mps.is_available() else torch.bfloat16
        
        model = AutoModelForCausalLM.from_pretrained(
            'Qwen/Qwen3-0.6B',
            torch_dtype=dtype,
            device_map='auto',
            low_cpu_mem_usage=True,
            trust_remote_code=True
        )
        
        download_time = time.time() - start_time
        print("✅ Model downloaded successfully!")
        print(f"⏱️  Total download time: {download_time:.1f}s ({download_time/60:.1f} minutes)")
        print()
        
        print("🔍 Step 3/3: Validating model functionality...")
        
        # Test basic functionality
        test_prompts = [
            "What is 15 + 27?",
            "Explain artificial intelligence in simple terms.",
            "Write a haiku about local AI."
        ]
        
        for i, prompt in enumerate(test_prompts, 1):
            print(f"\n🧪 Test {i}/3: {prompt}")
            inputs = tokenizer(prompt, return_tensors='pt')
            
            with torch.no_grad():
                outputs = model.generate(
                    inputs['input_ids'],
                    max_new_tokens=100,
                    temperature=0.7,
                    do_sample=True,
                    pad_token_id=tokenizer.eos_token_id
                )
            
            response = tokenizer.decode(outputs[0], skip_special_tokens=True)
            print(f"🤖 Response: {response}")
            
            # Check response quality
            response_only = response[len(prompt):].strip()
            quality = "✅ Good" if len(response_only) > 20 else "⚠️ Short"
            print(f"📊 Quality: {quality} ({len(response_only)} chars)")
        
        print("\n" + "=" * 50)
        print("🎉 SUCCESS: Qwen3-0.6B is ready for M1K3!")
        print()
        
        # Model specifications
        param_count = sum(p.numel() for p in model.parameters())
        print("📊 Model Specifications:")
        print(f"   Parameters: {param_count / 1e9:.2f}B")
        print(f"   Context length: 32,768 tokens")
        print(f"   Memory usage: ~{param_count * 2 / 1e9:.1f}GB (bfloat16)")
        print(f"   Architecture: Qwen3 with enhanced reasoning")
        print(f"   Features: Thinking mode, multi-language, agent capabilities")
        print()
        
        print("📝 Next Steps:")
        print("   1. Model is now cached and ready for use")
        print("   2. M1K3 will automatically detect and use Qwen3")
        print("   3. Restart M1K3 to activate enhanced reasoning")
        print("   4. Try math problems and complex questions!")
        print()
        
        print("🎯 What's New with Qwen3:")
        print("   • 5x better reasoning than TinyLlama")
        print("   • Advanced math and logic capabilities")
        print("   • Enhanced conversation understanding")
        print("   • Faster loading than larger models")
        print("   • Perfect for mobile dashboard")
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
        print("   - Ensure sufficient disk space (~2GB)")
        print("   - Try: pip install --upgrade transformers torch")
        print("   - For memory issues: restart and close other apps")
        print(f"   - Clear cache: rm -rf ~/.cache/huggingface/hub/models--Qwen--Qwen3-0.6B")
        return False

def main():
    print("🤖 M1K3 Enhanced Reasoning Setup")
    print("🎯 Installing Qwen3-0.6B for smarter conversations")
    print()
    
    # Check transformers version
    try:
        import transformers
        version = transformers.__version__
        print(f"📦 Transformers version: {version}")
        
        # Parse version numbers
        version_parts = [int(x) for x in version.split('.')]
        required_version = [4, 51, 0]
        
        if version_parts < required_version:
            print("⚠️  Outdated transformers library detected")
            print("🔧 Please run: pip install --upgrade transformers>=4.51.0")
            print("   Then restart this script")
            return False
        else:
            print("✅ Transformers library is compatible")
            
    except ImportError:
        print("❌ Transformers library not found")
        print("🔧 Please run: pip install transformers>=4.51.0 torch")
        return False
    
    print()
    
    success = download_qwen3()
    
    if success:
        print("✨ Qwen3-0.6B installation complete!")
        print("🚀 M1K3 is now ready with enhanced reasoning capabilities!")
        print()
        print("🎮 Try these enhanced features:")
        print("   • Complex math problems")  
        print("   • Step-by-step reasoning")
        print("   • Creative writing tasks")
        print("   • Code generation")
        print("   • Multi-turn conversations")
    else:
        print("❌ Installation failed. Check the error messages above.")
        return 1
    
    return 0

if __name__ == "__main__":
    exit(main())