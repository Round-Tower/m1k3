#!/usr/bin/env python3
"""
M1K3 Improved Voice Quality Demo
Demonstrates the enhanced voice system with multi-tier engines and advanced audio processing
"""

import time
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def test_voice_improvements():
    """Test the voice quality improvements"""
    print("\n" + "="*80)
    print("🎤 M1K3 IMPROVED VOICE QUALITY DEMONSTRATION")
    print("="*80)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        from sound_manager import SoundManager
        
        print("🚀 Loading enhanced voice engine...")
        engine = create_voice_engine()
        sound_mgr = SoundManager()
        
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return 1
            
        print("✅ Enhanced voice engine loaded!")
        
        # Test phrases designed to show improvements
        test_phrases = {
            "clarity": "The sophisticated speech synthesis system successfully processes text with smooth, natural sound quality.",
            "sibilance": "She sells seashells by the seashore, with sharp sounds and smooth sibilance.",
            "articulation": "Peter Piper picked a peck of pickled peppers, pronouncing each perfectly.",
            "natural": "Hello! Welcome to M1K3's improved voice synthesis. You should notice much clearer, more natural speech."
        }
        
        print(f"\n🎯 Voice Quality Improvements Demonstrated:")
        print("   ✅ Optimized KittenTTS voice selection (cleaner voices)")
        print("   ✅ Improved sample rate (24kHz native)")
        print("   ✅ Sibilance reduction (reduces lisp-like sounds)")
        print("   ✅ Formant correction (clearer articulation)")
        print("   ✅ Clarity enhancement (noise reduction & emphasis)")
        print("   ✅ Advanced audio effects pipeline")
        
        # Test different voice profiles with improvements
        profiles_to_test = [
            ("natural", "Optimized natural voice with clarity enhancements"),
            ("assistant", "Professional voice with compression and optimization"),
            ("premium", "Ultra-clear voice with full processing pipeline"),
            ("broadcast", "Radio-quality voice with professional effects")
        ]
        
        for profile_name, description in profiles_to_test:
            print(f"\n🎤 Testing {profile_name.upper()} Profile")
            print("-" * 60)
            print(f"   Description: {description}")
            
            try:
                engine.set_profile(profile_name)
                sound_mgr.play_contextual_sound("success")
                
                # Test with clarity phrase
                phrase = test_phrases["clarity"]
                print(f"   🔊 Speaking: \"{phrase[:40]}...\"")
                engine.synthesize_and_play(phrase, background=False)
                
                print(f"   ✅ {profile_name} profile test complete")
                time.sleep(1)
                
            except Exception as e:
                print(f"   ❌ Error with {profile_name}: {e}")
        
        print(f"\n🧪 Advanced Effects Testing")
        print("-" * 60)
        
        # Test the premium profile with different test phrases
        engine.set_profile("premium")
        
        for test_name, phrase in test_phrases.items():
            print(f"\n   🎯 {test_name.title()} Test:")
            print(f"      Text: \"{phrase[:50]}...\"")
            engine.synthesize_and_play(phrase, background=False)
            time.sleep(1.5)
            
        print(f"\n📊 VOICE QUALITY COMPARISON")
        print("="*80)
        print("BEFORE (Original KittenTTS):")
        print("   ❌ Default voice with potential lisp artifacts")
        print("   ❌ 22kHz sample rate (not native)")
        print("   ❌ No sibilance reduction")
        print("   ❌ No formant correction")
        print("   ❌ Basic normalization only")
        
        print("\nAFTER (Enhanced M1K3 Voice System):")
        print("   ✅ Cleaner KittenTTS voices (expr-voice-2-f, 4-f)")
        print("   ✅ 24kHz native sample rate")
        print("   ✅ Sibilance reduction pipeline")
        print("   ✅ Formant correction for clarity")
        print("   ✅ Multi-stage clarity enhancement")
        print("   ✅ Professional audio effects")
        print("   ✅ Intelligent engine selection")
        
        print(f"\n🚀 FUTURE ENHANCEMENTS AVAILABLE:")
        print("   🎤 Coqui TTS integration (premium tier)")
        print("   🎛️ Multi-tier voice engine selection")
        print("   🔊 Voice cloning with Tortoise TTS")
        print("   🎵 Advanced formant processing")
        
        sound_mgr.play_success_sequence("major")
        engine.synthesize_and_play("Voice quality improvements demonstration complete! You should notice significantly clearer speech.", background=False)
        
    except Exception as e:
        print(f"❌ Error during demonstration: {e}")
        return 1
    
    return 0

def interactive_quality_comparison():
    """Interactive comparison between voice profiles"""
    print(f"\n🎮 INTERACTIVE VOICE QUALITY COMPARISON")
    print("-" * 80)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        
        engine = create_voice_engine()
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return
            
        profiles = ["natural", "assistant", "premium", "broadcast"]
        
        test_sentence = "The sophisticated speech synthesis system successfully processes complex text with smooth, natural sound quality and clear articulation."
        
        print(f"🎤 Test sentence: \"{test_sentence}\"")
        print(f"📋 Available profiles: {', '.join(profiles)}")
        print("💡 Type profile name to test, 'compare' for side-by-side, or 'quit' to exit")
        
        while True:
            user_input = input("\n🎤 Quality Test> ").strip().lower()
            
            if user_input in ['quit', 'exit', 'q']:
                break
                
            elif user_input == 'compare':
                print("🔄 Side-by-side comparison of all profiles...")
                for i, profile in enumerate(profiles, 1):
                    print(f"\n   {i}. {profile.upper()} Profile:")
                    engine.set_profile(profile)
                    engine.synthesize_and_play(test_sentence, background=False)
                    time.sleep(0.5)
                    
            elif user_input in profiles:
                engine.set_profile(user_input)
                print(f"🔊 Testing {user_input} profile with enhanced processing...")
                engine.synthesize_and_play(test_sentence, background=False)
                
            else:
                print(f"❌ Unknown command. Available: {', '.join(profiles)}, compare, quit")
                
    except Exception as e:
        print(f"❌ Error: {e}")

def main():
    """Run the voice quality improvement demonstration"""
    test_voice_improvements()
    interactive_quality_comparison()
    
    print("\n🎉 Voice quality improvement demonstration complete!")
    print("="*80)
    return 0

if __name__ == "__main__":
    sys.exit(main())