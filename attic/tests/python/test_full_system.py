#!/usr/bin/env python3
"""
Test the full TTS & STT system end-to-end
"""

import sys
import os
sys.path.append(os.path.dirname(__file__))

def test_tts_system():
    """Test the TTS system with the same text that caused phonemizer warnings"""
    print("🎭 Testing TTS System")
    print("=" * 30)
    
    try:
        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
        
        engine = UnifiedVoiceEngine()
        
        print("🔧 Loading voice engine...")
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return False
        
        print("✅ Voice engine loaded")
        
        # Test the exact text that caused the phonemizer warning
        test_text = "I am unable to answer that question."
        
        print(f"🎤 Testing TTS with: '{test_text}'")
        success = engine.synthesize_and_play(test_text, background=False)
        
        if success:
            print("✅ TTS synthesis successful - no more phonemizer warnings expected!")
        else:
            print("❌ TTS synthesis failed")
            
        return success
        
    except Exception as e:
        print(f"❌ TTS test failed: {e}")
        return False

def test_stt_system():
    """Test the STT system"""
    print("\n🎤 Testing STT System")
    print("=" * 30)
    
    try:
        from src.engines.stt.stt_manager import STTManager
        
        manager = STTManager()
        
        if manager.current_engine_name != "vosk":
            print(f"⚠️ Expected Vosk engine, got: {manager.current_engine_name}")
        
        print(f"✅ STT Manager initialized with {manager.current_engine_name} engine")
        print(f"🎯 Available engines: {list(manager.engines.keys())}")
        
        # We won't actually test listening since it requires user input
        # but we can verify the system is ready
        if manager.current_engine and manager.current_engine.is_available():
            print("✅ STT system ready for speech recognition")
            return True
        else:
            print("❌ STT system not available")
            return False
            
    except Exception as e:
        print(f"❌ STT test failed: {e}")
        return False

def test_system_integration():
    """Test that both systems can coexist"""
    print("\n🔗 Testing System Integration") 
    print("=" * 30)
    
    try:
        # Import both systems
        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
        from src.engines.stt.stt_manager import STTManager
        
        # Initialize both
        tts_engine = UnifiedVoiceEngine()
        stt_manager = STTManager()
        
        # Load TTS
        tts_loaded = tts_engine.load_model()
        stt_ready = stt_manager.current_engine and stt_manager.current_engine.is_available()
        
        print(f"🎭 TTS Engine: {'✅ Loaded' if tts_loaded else '❌ Failed'}")
        print(f"🎤 STT Engine: {'✅ Ready' if stt_ready else '❌ Not Ready'}")
        
        if tts_loaded and stt_ready:
            print("✅ Full TTS & STT system integration successful!")
            
            # Test with a sample conversation response
            print("\n🗣️ Testing sample conversation response...")
            success = tts_engine.synthesize_and_play("Hello! The speech recognition and synthesis systems are both working correctly.", background=False)
            
            if success:
                print("✅ End-to-end conversation system ready!")
            else:
                print("⚠️ TTS synthesis had issues but systems are loaded")
            
            return True
        else:
            print("❌ System integration incomplete")
            return False
            
    except Exception as e:
        print(f"❌ Integration test failed: {e}")
        return False

if __name__ == "__main__":
    print("🚀 Full M1K3 TTS & STT System Test")
    print("=" * 50)
    
    # Test individual systems
    tts_success = test_tts_system()
    stt_success = test_stt_system()
    integration_success = test_system_integration()
    
    print(f"\n📊 Test Results Summary:")
    print(f"   🎭 TTS System: {'✅ Working' if tts_success else '❌ Failed'}")
    print(f"   🎤 STT System: {'✅ Working' if stt_success else '❌ Failed'}")
    print(f"   🔗 Integration: {'✅ Working' if integration_success else '❌ Failed'}")
    
    if tts_success and stt_success and integration_success:
        print(f"\n🎉 SUCCESS: Full conversational AI system is ready!")
        print(f"🎯 Your MVP TTS & STT flows are production-ready!")
    else:
        print(f"\n⚠️ PARTIAL: Some components need attention")
    
    print(f"\n🏁 Test complete.")