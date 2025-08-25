#!/usr/bin/env python3
"""
Setup script for Gemma3 270M model in M1K3
Downloads and configures Gemma3 as the default model
"""

import subprocess
import sys
import os
from pathlib import Path

def run_command(cmd: str, description: str) -> bool:
    """Run a command and return success status"""
    print(f"🔧 {description}...")
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        if result.returncode == 0:
            print(f"✅ {description} completed")
            return True
        else:
            print(f"❌ {description} failed: {result.stderr}")
            return False
    except Exception as e:
        print(f"❌ {description} failed: {e}")
        return False

def main():
    print("=" * 60)
    print("🚀 M1K3 Gemma3 270M Setup")
    print("=" * 60)
    
    # Check if Ollama is installed
    if not run_command("ollama --version", "Checking Ollama installation"):
        print("❌ Ollama not found. Please install Ollama first.")
        print("   Visit: https://ollama.ai/")
        return False
    
    # Pull Gemma3 270M model
    if not run_command("ollama pull gemma3:270m", "Downloading Gemma3 270M model"):
        print("❌ Failed to download Gemma3 model")
        return False
    
    # Create custom model with M1K3 configuration
    models_dir = Path("models")
    modelfile = models_dir / "Modelfile.gemma3"
    
    if modelfile.exists():
        if not run_command(f"ollama create m1k3-gemma -f {modelfile}", "Creating M1K3 Gemma3 model"):
            print("⚠️ Failed to create custom model, using default")
    
    # Verify model is available
    result = subprocess.run("ollama list", shell=True, capture_output=True, text=True)
    if "gemma3:270m" in result.stdout:
        print("✅ Gemma3 270M model successfully set up!")
        print(f"📊 Model details:")
        print(f"   • Size: ~270M parameters")
        print(f"   • Context: 32K tokens")
        print(f"   • Quantization: Q8_0")
        print(f"   • Usage: ollama run gemma3:270m")
        
        if "m1k3-gemma" in result.stdout:
            print(f"   • M1K3 optimized: ollama run m1k3-gemma")
        
        print(f"\n🎯 Gemma3 is now configured as the default model for M1K3!")
        return True
    else:
        print("❌ Model verification failed")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)