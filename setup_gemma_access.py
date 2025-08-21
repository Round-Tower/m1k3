#!/usr/bin/env python3
"""
Setup Gemma Model Access for M1K3
Helps configure HuggingFace authentication for gated models
"""

import os
import sys
from pathlib import Path

def check_huggingface_auth():
    """Check if HuggingFace authentication is set up"""
    try:
        from huggingface_hub import HfApi, login
        api = HfApi()
        
        # Check if already logged in
        try:
            user_info = api.whoami()
            print(f"✅ Already logged in as: {user_info['name']}")
            return True
        except:
            print("❌ Not logged in to HuggingFace")
            return False
            
    except ImportError:
        print("❌ HuggingFace Hub not installed")
        return False

def setup_authentication():
    """Guide user through HuggingFace authentication"""
    print("🔐 Setting up HuggingFace Authentication for Gemma Access")
    print("=" * 60)
    
    print("📋 Steps to access Gemma models:")
    print("1. Create a HuggingFace account at https://huggingface.co/join")
    print("2. Visit https://huggingface.co/google/gemma-2b-it")
    print("3. Accept the license agreement for Gemma models")
    print("4. Get your access token from https://huggingface.co/settings/tokens")
    print("5. Use one of the authentication methods below")
    
    print(f"\n🔑 Authentication Options:")
    print("-" * 30)
    
    print("Option A - Interactive Login:")
    print("   python -c \"from huggingface_hub import login; login()\"")
    
    print(f"\nOption B - Environment Variable:")
    print("   export HF_TOKEN='your_token_here'")
    print("   # Add to ~/.bashrc or ~/.zshrc for persistence")
    
    print(f"\nOption C - Token File:")
    print("   echo 'your_token_here' > ~/.cache/huggingface/token")
    
    # Check current auth status
    if check_huggingface_auth():
        print(f"\n🎉 Authentication is already set up!")
        return True
    else:
        print(f"\n⚠️  Authentication required to access Gemma models")
        return False

def test_gemma_access():
    """Test if we can access Gemma models"""
    try:
        from transformers import AutoTokenizer
        print(f"\n🧪 Testing Gemma model access...")
        
        # Try to access the tokenizer (lighter than full model)
        try:
            tokenizer = AutoTokenizer.from_pretrained("google/gemma-2b-it")
            print("✅ Gemma model access successful!")
            print("🎯 You can now use enhanced reasoning models")
            return True
        except Exception as e:
            if "401" in str(e) or "gated" in str(e).lower():
                print("❌ Access denied - authentication required")
                print("   Make sure you've accepted the license at:")
                print("   https://huggingface.co/google/gemma-2b-it")
            else:
                print(f"❌ Error accessing Gemma: {e}")
            return False
            
    except ImportError:
        print("❌ Transformers not available")
        return False

def enable_gemma_in_m1k3():
    """Enable Gemma models in M1K3 configuration"""
    ai_file = Path("ai_inference.py")
    if not ai_file.exists():
        print("⚠️  ai_inference.py not found in current directory")
        return False
    
    try:
        content = ai_file.read_text()
        
        # Check if Gemma is already enabled
        if '"google/gemma-2b-it"' in content and not content.count('# "google/gemma-2b-it"'):
            print("✅ Gemma models already enabled in M1K3")
            return True
        
        # Enable Gemma by uncommenting
        old_line = '            # "google/gemma-2b-it",'
        new_line = '            "google/gemma-2b-it",'
        
        if old_line in content:
            content = content.replace(old_line, new_line)
            ai_file.write_text(content)
            print("✅ Gemma models enabled in M1K3!")
            return True
        else:
            print("⚠️  Gemma model configuration not found")
            return False
            
    except Exception as e:
        print(f"❌ Error enabling Gemma: {e}")
        return False

def main():
    print("🚀 M1K3 Gemma Model Setup")
    print("=" * 40)
    
    # Step 1: Check/setup authentication
    print("Step 1: Check HuggingFace Authentication")
    auth_ok = setup_authentication()
    
    if not auth_ok:
        print(f"\n🔧 Next steps:")
        print("1. Set up HuggingFace authentication using one of the options above")
        print("2. Run this script again: python setup_gemma_access.py")
        return
    
    # Step 2: Test access
    print("Step 2: Test Gemma Model Access")
    access_ok = test_gemma_access()
    
    if not access_ok:
        print(f"\n🔧 Troubleshooting:")
        print("- Make sure you've accepted the Gemma license")
        print("- Check your token has the correct permissions")
        print("- Try logging in again with: huggingface-cli login")
        return
    
    # Step 3: Enable in M1K3
    print("Step 3: Enable Gemma in M1K3")
    enable_ok = enable_gemma_in_m1k3()
    
    if enable_ok:
        print(f"\n🎉 Setup Complete!")
        print("✅ Gemma 2B models are now available in M1K3")
        print("🚀 Restart M1K3 to use enhanced reasoning capabilities")
        
        print(f"\n💡 Test with:")
        print("   python ai_inference.py")
        print("   python test_gemma_2b.py")
    else:
        print(f"\n⚠️  Manual configuration required")
        print("Edit ai_inference.py to uncomment Gemma models")

if __name__ == "__main__":
    main()