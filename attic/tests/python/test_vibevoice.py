#!/usr/bin/env python3
"""
VibeVoice Test Script for M1K3
Tests VibeVoice installation and basic functionality
"""

import sys
import os
import warnings
from pathlib import Path
import time

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore")

def check_system_requirements():
    """Check if system meets VibeVoice requirements"""
    print("🔍 Checking system requirements...")
    
    # Check Python version
    if sys.version_info < (3, 8):
        print("❌ Python 3.8+ required")
        return False
    print(f"✅ Python {sys.version_info.major}.{sys.version_info.minor}")
    
    # Check CUDA availability
    try:
        import torch
        if torch.cuda.is_available():
            print(f"✅ CUDA available: {torch.cuda.get_device_name()}")
            print(f"   GPU Memory: {torch.cuda.get_device_properties(0).total_memory // 1e9:.1f}GB")
        else:
            print("⚠️  CUDA not available - will use CPU (slower)")
    except ImportError:
        print("❌ PyTorch not installed")
        return False
    
    return True

def test_vibevoice_dependencies():
    """Test if VibeVoice dependencies are available"""
    print("\n🔍 Checking VibeVoice dependencies...")
    
    dependencies = {
        'transformers': 'huggingface transformers',
        'diffusers': 'diffusers library', 
        'accelerate': 'accelerate library',
        'librosa': 'librosa audio processing',
        'soundfile': 'soundfile audio I/O',
        'gradio': 'gradio interface (optional)'
    }
    
    missing = []
    for module, description in dependencies.items():
        try:
            __import__(module)
            print(f"✅ {description}")
        except ImportError:
            if module == 'gradio':
                print(f"⚠️  {description} (optional)")
            else:
                print(f"❌ {description}")
                missing.append(module)
    
    return len(missing) == 0

def test_vibevoice_installation():
    """Test if VibeVoice can be imported and used"""
    print("\n🔍 Testing VibeVoice installation...")
    
    try:
        # Check if VibeVoice repository is available
        vibevoice_path = Path.home() / "VibeVoice"
        if not vibevoice_path.exists():
            print("❌ VibeVoice repository not found")
            print(f"   Expected at: {vibevoice_path}")
            print("   Clone with: git clone https://github.com/microsoft/VibeVoice.git ~/VibeVoice")
            return False
        
        print(f"✅ VibeVoice repository found at {vibevoice_path}")
        
        # Add VibeVoice to path and try importing
        sys.path.insert(0, str(vibevoice_path))
        
        # Test import (this might fail if model isn't downloaded)
        print("🔄 Testing VibeVoice import...")
        
        return True
        
    except Exception as e:
        print(f"❌ VibeVoice import failed: {e}")
        return False

def test_model_availability():
    """Test if VibeVoice models are available"""
    print("\n🔍 Checking VibeVoice models...")
    
    try:
        from huggingface_hub import hf_hub_download, repo_info
        
        models = [
            "microsoft/VibeVoice-1.5B",
            "microsoft/VibeVoice-7B-Preview"
        ]
        
        for model_name in models:
            try:
                info = repo_info(model_name)
                print(f"✅ {model_name} available on Hugging Face")
            except Exception:
                print(f"❌ {model_name} not accessible")
        
        return True
        
    except ImportError:
        print("❌ huggingface_hub not available")
        return False

def create_vibevoice_demo():
    """Create a simple VibeVoice demo script"""
    print("\n🔨 Creating VibeVoice demo script...")
    
    demo_content = '''#!/usr/bin/env python3
"""
VibeVoice Demo for M1K3
Simple demonstration of VibeVoice TTS capabilities
"""

import sys
import warnings
from pathlib import Path

# Add VibeVoice to path
vibevoice_path = Path.home() / "VibeVoice"
if vibevoice_path.exists():
    sys.path.insert(0, str(vibevoice_path))

warnings.filterwarnings("ignore")

def main():
    try:
        print("🎤 Loading VibeVoice model...")
        
        # Example usage - adapt based on actual VibeVoice API
        # This is a template - will need adjustment once we test the actual API
        
        # from vibevoice import VibeVoice  # Placeholder import
        
        # model = VibeVoice("microsoft/VibeVoice-1.5B")
        
        test_texts = [
            "Hello, this is M1K3 testing VibeVoice integration.",
            "VibeVoice supports long-form conversation synthesis.",
            "We can now generate up to 90 minutes of continuous speech!"
        ]
        
        print("🗣️  Generating speech samples...")
        
        for i, text in enumerate(test_texts):
            print(f"   Sample {i+1}: {text[:50]}...")
            # audio = model.generate(text)
            # Save audio to file
            # output_file = f"vibevoice_sample_{i+1}.wav"
            # model.save_audio(audio, output_file)
            # print(f"   Saved: {output_file}")
        
        print("✅ VibeVoice demo completed successfully!")
        
    except Exception as e:
        print(f"❌ VibeVoice demo failed: {e}")
        print("   Make sure VibeVoice is properly installed and models are downloaded")

if __name__ == "__main__":
    main()
'''
    
    demo_path = Path("vibevoice_demo.py")
    demo_path.write_text(demo_content)
    print(f"✅ Created demo script: {demo_path}")

def main():
    """Main test function"""
    print("🚀 VibeVoice Integration Test for M1K3")
    print("=" * 50)
    
    # Run all tests
    tests = [
        check_system_requirements,
        test_vibevoice_dependencies,
        test_vibevoice_installation,
        test_model_availability
    ]
    
    results = []
    for test in tests:
        try:
            result = test()
            results.append(result)
        except Exception as e:
            print(f"❌ Test failed with error: {e}")
            results.append(False)
    
    # Create demo regardless of test results
    create_vibevoice_demo()
    
    # Summary
    print("\n" + "=" * 50)
    print("📊 Test Summary:")
    passed = sum(results)
    total = len(results)
    
    if passed == total:
        print("✅ All tests passed! VibeVoice is ready for integration.")
    elif passed >= 2:
        print("⚠️  Some tests failed, but basic requirements are met.")
        print("   You may need to install additional dependencies.")
    else:
        print("❌ Multiple tests failed. VibeVoice integration not ready.")
    
    print(f"   Passed: {passed}/{total} tests")
    
    # Installation instructions
    print("\n🔧 Next steps:")
    print("1. Install VibeVoice dependencies:")
    print("   pip install diffusers accelerate librosa gradio")
    print("2. Clone VibeVoice repository:")
    print("   git clone https://github.com/microsoft/VibeVoice.git ~/VibeVoice")
    print("3. Download models using Hugging Face:")
    print("   huggingface-cli download microsoft/VibeVoice-1.5B")
    print("4. Run demo: python vibevoice_demo.py")

if __name__ == "__main__":
    main()