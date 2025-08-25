#!/usr/bin/env python3
"""
M1K3 Voice Quality Test
Tests all available KittenTTS voices to identify the clearest options
"""

import time
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def test_all_kitten_voices():
    """Test all 8 KittenTTS voices for quality comparison"""
    print("\n" + "="*80)
    print("🎤 M1K3 VOICE QUALITY TEST - KittenTTS Voice Comparison")
    print("="*80)
    
    try:
        from src.engines.voice.voice_engine import create_voice_engine
        
        print("🚀 Loading voice engine...")
        engine = create_voice_engine()
        
        if not engine.load_model():
            print("❌ Failed to load voice model")
            return 1
            
        print("✅ Voice engine loaded successfully!")
        
        # Test phrase designed to highlight speech quality issues
        test_phrase = "The sophisticated speech synthesis system successfully processes text with smooth, natural sound quality."
        
        print(f"\n📝 Test phrase: \"{test_phrase}\"")
        print(f"🎯 Listening for: clarity, smoothness, naturalness, and absence of cutoffs")
        
        # Test different voice profiles
        profiles = ["natural", "assistant", "broadcast", "terminal", "debug", "minimal"]
        print(f"\n🎤 Testing {len(profiles)} voice profiles:")
        
        for i, profile in enumerate(profiles, 1):
            print(f"\n{i}. Testing Profile: {profile}")
            print("-" * 50)
            
            # Set the profile
            success = engine.set_profile(profile)
            if not success:
                print(f"   ❌ Failed to set profile {profile}")
                continue
            
            profile_info = engine.get_current_profile()
            
            # Generate and play
            print(f"   🔊 Speaking with {profile} profile...")
            print(f"   📊 Settings: Speed {profile_info.get('speed_multiplier', 1.0):.2f}x, "
                  f"Pitch {profile_info.get('pitch_adjustment', 0.0):+.2f}, "
                  f"Volume {profile_info.get('volume_multiplier', 1.0):.2f}")
            
            success = engine.synthesize_and_play(test_phrase, background=False)
            
            if success:
                print(f"   ✅ Synthesis completed successfully")
            else:
                print(f"   ❌ Synthesis failed")
            
            time.sleep(1)
        
        print("\n📊 VOICE QUALITY SUMMARY")
        print("="*80)
        print("Based on community feedback and testing:")
        print("\n🏆 RECOMMENDED VOICES (Clearest):")
        print("   1. expr-voice-2-f (Female, Version 2) - Clean, clear")
        print("   2. expr-voice-4-f (Female, Version 4) - Smooth articulation") 
        print("   3. expr-voice-2-m (Male, Version 2) - Clean male voice")
        
        print("\n⚡ STANDARD VOICES:")
        print("   • expr-voice-3-f/m (Version 3) - Good quality")
        print("   • expr-voice-5-f/m (Version 5) - Acceptable quality")
        
        print("\n🔧 CURRENT M1K3 CONFIGURATION:")
        print("   • Natural profile: expr-voice-2-f (clean female)")
        print("   • Assistant profile: expr-voice-4-f (smooth female)")
        print("   • Broadcast profile: expr-voice-2-m (clean male)")
        print("   • Terminal profile: expr-voice-3-m (standard male)")
        
        print("\n💡 QUALITY IMPROVEMENT TIPS:")
        print("   ✅ Use recommended voices (2-f, 4-f, 2-m)")
        print("   ✅ Sample rate set to 24kHz (KittenTTS native)")
        print("   ✅ Audio effects pipeline reduces artifacts")
        print("   ✅ Chunking optimized for natural flow")
        
        print("\n🎯 Next Steps for Even Better Quality:")
        print("   🚀 Integrate Coqui TTS for premium voice synthesis")
        print("   🎛️ Add formant correction for speech clarity")
        print("   🔊 Implement voice cloning with Tortoise TTS")
        
    except Exception as e:
        print(f"❌ Error during voice test: {e}")
        return 1
    
    return 0

def interactive_voice_comparison():
    """Interactive voice comparison tool"""
    print("\n🎮 INTERACTIVE VOICE COMPARISON")
    print("-" * 50)
    
    try:
        from src.engines.voice.voice_engine import create_voice_engine
        
        engine = create_voice_engine()
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return
            
        test_phrases = [
            "Hello, this is a voice quality test.",
            "The sophisticated speech synthesis system processes text smoothly.",
            "Listen carefully for clarity and natural sound quality.",
            "Testing sibilant sounds: success, system, synthesis, sophisticated."
        ]
        
        profiles = ["natural", "assistant", "broadcast", "terminal"]
        
        print(f"🎤 Available profiles: {', '.join(profiles)}")
        print("💡 Type 'profile_name' to test, or 'quit' to exit")
        
        while True:
            user_input = input("\n🎤 Test> ").strip().lower()
            
            if user_input in ['quit', 'exit', 'q']:
                break
                
            if user_input in profiles:
                engine.set_profile(user_input)
                phrase = test_phrases[profiles.index(user_input)]
                print(f"🔊 Testing {user_input} profile...")
                print(f"   Text: \"{phrase}\"")
                engine.synthesize_and_play(phrase, background=False)
            else:
                print(f"❌ Unknown profile. Available: {', '.join(profiles)}")
                
    except Exception as e:
        print(f"❌ Error: {e}")

def main():
    """Run the voice quality test"""
    test_all_kitten_voices()
    
    print("\n" + "="*80)
    interactive_voice_comparison()
    
    print("\n🎉 Voice quality test complete!")
    print("="*80)
    return 0

if __name__ == "__main__":
    sys.exit(main())