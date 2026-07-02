#!/usr/bin/env python3
"""
Test voice engine error handling
"""

from enhanced_voice_engine import create_voice_engine

def test_voice_error_handling():
    print("🔊 Testing Voice Engine Error Handling\n" + "="*50)
    
    voice_engine = create_voice_engine()
    
    print("🔄 Loading voice engine...")
    if voice_engine.load_model():
        print("✅ Voice engine loaded successfully")
        
        # Test synthesis with a simple phrase
        test_text = "Hello! This is a test of the voice synthesis system."
        print(f"\n🗣️  Testing synthesis: '{test_text}'")
        
        result = voice_engine.synthesize_and_play(test_text, background=False)
        
        if result:
            print("✅ Voice synthesis completed successfully")
        else:
            print("⚠️  Voice synthesis failed - should have fallen back")
            
        # Check final status
        status = voice_engine.get_status()
        print(f"\n📊 Final Status:")
        print(f"   Engine: {status.get('engine', 'unknown')}")
        print(f"   Available: {status.get('available', False)}")
        print(f"   Enabled: {status.get('enabled', False)}")
        
    else:
        print("❌ Failed to load voice engine")

if __name__ == "__main__":
    test_voice_error_handling()