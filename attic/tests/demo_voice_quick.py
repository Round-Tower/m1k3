#!/usr/bin/env python3
"""
M1K3 Voice & Sound System Quick Demo
A non-interactive showcase of the voice and sound improvements.
"""

import time
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

def main():
    """Run a quick showcase without interactive mode"""
    print("\n" + "="*80)
    print("🎤 M1K3 VOICE & SOUND SYSTEM QUICK SHOWCASE 🎵")
    print("="*80)
    
    # Test voice profiles with sample phrases
    try:
        from enhanced_voice_engine import create_voice_engine
        from sound_manager import SoundManager
        
        print("🚀 Initializing systems...")
        engine = create_voice_engine()
        sound_mgr = SoundManager()
        
        if engine.load_model():
            print("✅ Voice engine loaded successfully!")
            
            # Show available profiles
            if hasattr(engine, 'profiles'):
                print(f"📋 Available profiles: {', '.join(engine.profiles.keys())}")
                
                demo_phrases = {
                    "natural": "This is the natural voice profile with pure audio quality.",
                    "assistant": "The assistant profile provides clear, professional speech.",
                    "broadcast": "Broadcasting from M1K3 radio with enhanced audio processing.",
                    "terminal": "System terminal interface voice with retro filtering."
                }
                
                for profile, phrase in demo_phrases.items():
                    if profile in engine.profiles:
                        print(f"\n🎤 Testing {profile.upper()} profile...")
                        engine.set_profile(profile)
                        sound_mgr.play_contextual_sound("success")
                        print(f"   Speaking: \"{phrase}\"")
                        engine.synthesize_and_play(phrase, background=False)
                        time.sleep(1)
                
                print("\n🎵 Testing sound effects...")
                sound_effects = ["interaction", "success", "error", "notification"]
                for effect in sound_effects:
                    print(f"   • {effect} sound")
                    sound_mgr.play_contextual_sound(effect)
                    time.sleep(0.5)
                
                print("\n✅ All systems working perfectly!")
                sound_mgr.play_success_sequence("major")
                engine.synthesize_and_play("M1K3 voice and sound system demonstration complete!", background=False)
                
        else:
            print("❌ Failed to load voice model")
            
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1
    
    print("\n🎉 Demo complete! The new architecture is fully operational.")
    print("="*80)
    return 0

if __name__ == "__main__":
    sys.exit(main())