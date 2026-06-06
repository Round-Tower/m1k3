#!/usr/bin/env python3
"""
Test just the Vosk engine directly
"""

import sys
import os
sys.path.append(os.path.dirname(__file__))

from src.engines.stt.vosk_stt_engine import VoskSTTEngine

def test_vosk_directly():
    """Test Vosk STT engine directly"""
    print("🎤 Direct Vosk STT Test")
    print("=" * 30)
    
    # Initialize engine
    engine = VoskSTTEngine()
    
    print("🔧 Initializing Vosk engine...")
    if not engine.initialize():
        print("❌ Failed to initialize Vosk engine")
        return False
    
    print(f"✅ Vosk engine initialized")
    print(f"🎯 Engine available: {engine.is_available()}")
    
    if not engine.is_available():
        print("❌ Vosk engine not available")
        return False
    
    try:
        print("\n🎤 Starting speech recognition...")
        print("💬 Please say something clearly when prompted...")
        
        result = engine.listen_once(timeout=8.0, phrase_timeout=2.0)
        
        if result:
            print(f"✅ SUCCESS! Recognized: '{result.text}'")
            print(f"   🎯 Confidence: {result.confidence:.2f}")
            print(f"   🕒 Duration: {result.duration:.2f}s")
            return True
        else:
            print("❌ No result returned from Vosk")
            return False
            
    except Exception as e:
        print(f"❌ Error during recognition: {e}")
        import traceback
        print(f"📍 Traceback: {traceback.format_exc()}")
        return False

if __name__ == "__main__":
    success = test_vosk_directly()
    if success:
        print("\n🎉 Vosk STT is working!")
    else:
        print("\n❌ Vosk STT test failed")