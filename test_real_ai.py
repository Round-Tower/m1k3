#!/usr/bin/env python3
"""
Test script to verify real AI inference is working
"""

import sys
from pathlib import Path

def test_real_ai():
    """Test the real AI inference engine"""
    print("🧪 Testing Real AI Inference Engine")
    print("=" * 50)
    
    try:
        from ai_inference import LocalAIEngine
        print("✅ Successfully imported LocalAIEngine")
        
        # Check if model exists
        models_dir = Path("models")
        model_path = models_dir / "SmolLM-135M.Q4_K_M.gguf"
        
        print(f"🔍 Checking model: {model_path}")
        print(f"   Exists: {model_path.exists()}")
        
        if model_path.exists():
            size_mb = model_path.stat().st_size / (1024 * 1024)
            print(f"   Size: {size_mb:.2f} MB")
            
            if size_mb > 50:
                print("✅ Model file looks valid")
                
                # Try to load the engine
                print("\n🚀 Testing engine initialization...")
                engine = LocalAIEngine()
                
                print(f"   Model available: {engine.is_model_available()}")
                
                if engine.is_model_available():
                    print("   Model auto-loaded during initialization")
                    success = engine.model is not None
                    print(f"   Load success: {success}")
                    
                    if success:
                        print("\n🎉 Real AI engine is working!")
                        print("Testing response generation...")
                        
                        # Test a simple response
                        print("Assistant: ", end="", flush=True)
                        response_count = 0
                        for token in engine.generate_response("Hello, how are you?"):
                            print(token, end="", flush=True)
                            response_count += 1
                            if response_count > 50:  # Limit for testing
                                break
                        print("\n\n✅ Real AI inference is working correctly!")
                        return True
                    else:
                        print("❌ Failed to load model")
                        return False
                else:
                    print("❌ Model not available")
                    return False
            else:
                print("❌ Model file too small, likely invalid")
                return False
        else:
            print("❌ Model file not found")
            return False
            
    except ImportError as e:
        print(f"❌ Failed to import LocalAIEngine: {e}")
        print("   Make sure ctransformers is installed: pip install ctransformers")
        return False
    except Exception as e:
        print(f"❌ Error testing real AI: {e}")
        return False

if __name__ == "__main__":
    success = test_real_ai()
    if success:
        print("\n🎯 Recommendation: Update CLI to use LocalAIEngine instead of SimpleAIEngine")
    else:
        print("\n⚠️  Real AI engine not working, CLI will use mock engine")
