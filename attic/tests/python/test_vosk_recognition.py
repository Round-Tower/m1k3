#!/usr/bin/env python3
"""
Test script to verify Vosk STT can actually recognize speech
"""

import sys
import os
sys.path.append(os.path.dirname(__file__))

from src.engines.stt.stt_manager import STTManager

def test_vosk_recognition():
    """Test actual speech recognition with Vosk"""
    print("🎤 Testing Vosk Speech Recognition")
    print("=" * 50)
    
    # Initialize STT manager
    manager = STTManager()
    
    if manager.current_engine_name != "vosk":
        print(f"❌ Expected Vosk engine, got: {manager.current_engine_name}")
        return False
    
    print(f"✅ Using {manager.current_engine_name} engine")
    print("📢 Please speak when prompted...")
    
    try:
        # Test single recognition
        print("\n🎤 Listening for speech (10 second timeout)...")
        print("💬 Say something clearly, like 'Hello world' or 'Testing one two three'")
        
        result = manager.listen_once(timeout=10.0, phrase_timeout=2.0)
        
        if result:
            print(f"✅ Recognition successful!")
            print(f"   📝 Text: '{result.text}'")
            print(f"   🎯 Confidence: {result.confidence:.2f}")
            print(f"   🕒 Duration: {result.duration:.2f}s")
            print(f"   🔧 Engine: {result.engine}")
            return True
        else:
            print("❌ No speech was recognized")
            print("💡 Possible issues:")
            print("   - No speech input detected")
            print("   - Speech was too quiet or unclear") 
            print("   - Background noise interference")
            print("   - Microphone not working properly")
            return False
            
    except Exception as e:
        print(f"❌ Recognition failed with error: {e}")
        import traceback
        print(f"📍 Traceback: {traceback.format_exc()}")
        return False

if __name__ == "__main__":
    print("🚀 Vosk STT Recognition Test")
    print("=" * 60)
    
    success = test_vosk_recognition()
    
    if success:
        print("\n🎉 SUCCESS: Vosk STT is working correctly!")
        print("🔧 You can now use voice input with M1K3")
    else:
        print("\n⚠️  FAILURE: Vosk STT recognition not working")
        print("💡 Try speaking more clearly or checking microphone settings")
    
    print("\n🏁 Test complete.")