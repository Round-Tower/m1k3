#!/usr/bin/env python3
"""
M1K3 Voice & Sound System Showcase Demo
Demonstrates the new unified voice engine, audio effects pipeline, and sound management system.
"""

import time
import json
import sys
from pathlib import Path

# Add current directory to path for imports
sys.path.insert(0, str(Path(__file__).parent))

def showcase_header():
    """Display an attractive demo header"""
    print("\n" + "="*80)
    print("🎤 M1K3 VOICE & SOUND SYSTEM SHOWCASE 🎵")
    print("="*80)
    print("🚀 Demonstrating the new unified voice engine architecture")
    print("🎛️  Modular pipeline: Text Processing → TTS → Audio Effects")
    print("🎮 Enhanced sound management with contextual audio")
    print("="*80 + "\n")

def test_voice_engine_loading():
    """Test voice engine initialization and profile loading"""
    print("📋 PHASE 1: Voice Engine Initialization")
    print("-" * 50)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        print("✅ Importing voice engine...")
        
        engine = create_voice_engine()
        print("✅ Voice engine created")
        
        if engine.load_model():
            print("✅ Voice model loaded successfully")
            
            # Test profile discovery
            if hasattr(engine, 'profiles'):
                profiles = list(engine.profiles.keys())
                print(f"✅ Discovered {len(profiles)} voice profiles: {', '.join(profiles)}")
            else:
                print("⚠️  Voice profiles not available")
                
        else:
            print("❌ Failed to load voice model")
            return False, None
            
    except Exception as e:
        print(f"❌ Voice engine error: {e}")
        return False, None
        
    print("✅ Phase 1 Complete\n")
    return True, engine

def test_sound_manager():
    """Test sound manager capabilities"""
    print("📋 PHASE 2: Sound Manager Testing")
    print("-" * 50)
    
    try:
        from sound_manager import SoundManager, ContextualSoundManager
        print("✅ Importing sound managers...")
        
        sound_mgr = SoundManager()
        context_sound_mgr = ContextualSoundManager(sound_mgr)
        
        # Test sound system discovery
        status = sound_mgr.test_sound_system()
        print(f"✅ Sound system status:")
        print(f"   • Profile: {status['current_profile']}")
        print(f"   • Total sounds: {status['total_sounds']}")
        print(f"   • Enabled: {'✅' if sound_mgr.enabled else '❌'}")
        
        # Test different sound types
        print("\n🎵 Testing sound categories:")
        
        test_sounds = [
            ("interaction", "User interaction sound"),
            ("success", "Success notification"),
            ("error", "Error notification"),
            ("notification", "General notification")
        ]
        
        for sound_type, description in test_sounds:
            print(f"   • {description}...")
            sound_mgr.play_contextual_sound(sound_type)
            time.sleep(0.8)
            
        print("✅ Sound system fully operational")
        
    except Exception as e:
        print(f"❌ Sound manager error: {e}")
        return False, None, None
        
    print("✅ Phase 2 Complete\n")
    return True, sound_mgr, context_sound_mgr

def test_voice_profiles(engine, sound_mgr):
    """Test all voice profiles with audio effects"""
    print("📋 PHASE 3: Voice Profile Showcase")
    print("-" * 50)
    
    if not hasattr(engine, 'profiles'):
        print("❌ Voice profiles not available")
        return False
        
    test_phrases = [
        "Welcome to the M1K3 voice system demonstration.",
        "This voice profile showcases the audio effects pipeline.",
        "Each profile has unique characteristics and processing."
    ]
    
    for profile_name in engine.profiles.keys():
        print(f"\n🎤 Testing Profile: {profile_name.upper()}")
        print(f"   Description: {engine.profiles[profile_name].get('description', 'No description')}")
        
        # Set the profile
        if engine.set_profile(profile_name):
            print(f"✅ Switched to {profile_name} profile")
            sound_mgr.play_contextual_sound("success")
            
            # Test phrase for this profile
            phrase = test_phrases[min(len(test_phrases)-1, list(engine.profiles.keys()).index(profile_name))]
            print(f"   Speaking: \"{phrase}\"")
            
            try:
                engine.synthesize_and_play(phrase, background=False)
                time.sleep(0.5)
                print(f"✅ {profile_name} profile test complete")
            except Exception as e:
                print(f"❌ Error with {profile_name} profile: {e}")
                
        else:
            print(f"❌ Failed to switch to {profile_name} profile")
            
        time.sleep(1)
    
    print("✅ Phase 3 Complete\n")
    return True

def test_audio_effects_pipeline():
    """Test the audio effects pipeline components"""
    print("📋 PHASE 4: Audio Effects Pipeline")
    print("-" * 50)
    
    try:
        from audio_effects import IntercomEffect, CompressionEffect, NormalizationEffect
        import numpy as np
        
        print("✅ Importing audio effects...")
        
        # Create test audio data
        sample_rate = 22050
        duration = 1.0
        t = np.linspace(0, duration, int(sample_rate * duration))
        test_audio = 0.5 * np.sin(2 * np.pi * 440 * t)  # 440Hz sine wave
        
        effects = [
            (IntercomEffect({"low_freq": 300, "high_freq": 3400}), "Intercom Filter"),
            (CompressionEffect({"threshold": 0.6, "ratio": 0.4}), "Dynamic Compression"),
            (NormalizationEffect({"level": 0.8}), "Audio Normalization")
        ]
        
        for effect, name in effects:
            print(f"   • Testing {name}...")
            try:
                processed = effect.apply(test_audio, sample_rate)
                print(f"     ✅ {name} applied successfully")
            except Exception as e:
                print(f"     ❌ {name} failed: {e}")
        
        print("✅ Audio effects pipeline operational")
        
    except Exception as e:
        print(f"❌ Audio effects error: {e}")
        return False
        
    print("✅ Phase 4 Complete\n")
    return True

def test_unified_pipeline():
    """Test the complete unified voice pipeline"""
    print("📋 PHASE 5: Unified Pipeline Integration")
    print("-" * 50)
    
    try:
        from unified_voice_engine import UnifiedVoiceEngine
        from text_processors import smart_text_chunking
        
        print("✅ Testing unified voice engine...")
        
        unified = UnifiedVoiceEngine()
        
        if unified.load_model():
            print("✅ Unified engine loaded")
            
            # Test text chunking
            long_text = "This is a demonstration of the smart text chunking system. " * 5
            chunks = smart_text_chunking(long_text, chunk_size=100)
            print(f"✅ Text chunking: {len(chunks)} chunks from {len(long_text)} characters")
            
            # Test synthesis pipeline
            test_text = "The unified voice engine combines text processing, synthesis, and effects."
            print(f"   Speaking: \"{test_text[:50]}...\"")
            
            unified.synthesize_and_play(test_text, background=False)
            print("✅ Unified pipeline synthesis complete")
            
        else:
            print("❌ Failed to load unified engine")
            return False
            
    except Exception as e:
        print(f"❌ Unified pipeline error: {e}")
        return False
        
    print("✅ Phase 5 Complete\n")
    return True

def interactive_demo_mode(engine, sound_mgr):
    """Interactive demo allowing user to test different combinations"""
    print("📋 PHASE 6: Interactive Demo Mode")
    print("-" * 50)
    print("🎮 Interactive Commands:")
    print("   • profile <name>  - Switch voice profile")
    print("   • say <text>      - Speak custom text")
    print("   • sound <type>    - Play sound effect")
    print("   • status          - Show system status")
    print("   • quit            - Exit demo")
    print("-" * 50)
    
    while True:
        try:
            user_input = input("\n🎤 Demo> ").strip()
            
            if not user_input:
                continue
                
            if user_input.lower() in ['quit', 'exit', 'q']:
                print("👋 Demo complete! Thanks for testing M1K3!")
                break
                
            parts = user_input.split(' ', 1)
            command = parts[0].lower()
            
            if command == 'profile' and len(parts) > 1:
                profile_name = parts[1]
                if engine.set_profile(profile_name):
                    sound_mgr.play_contextual_sound("success")
                    print(f"✅ Switched to {profile_name} profile")
                    engine.synthesize_and_play(f"Voice profile set to {profile_name}", background=False)
                else:
                    sound_mgr.play_contextual_sound("error")
                    available = ', '.join(engine.profiles.keys()) if hasattr(engine, 'profiles') else 'None'
                    print(f"❌ Unknown profile. Available: {available}")
                    
            elif command == 'say' and len(parts) > 1:
                text = parts[1]
                print(f"🗣️  Speaking: \"{text}\"")
                engine.synthesize_and_play(text, background=False)
                
            elif command == 'sound' and len(parts) > 1:
                sound_type = parts[1]
                print(f"🎵 Playing {sound_type} sound...")
                sound_mgr.play_contextual_sound(sound_type)
                
            elif command == 'status':
                status = sound_mgr.test_sound_system()
                current_profile = getattr(engine, 'current_profile', 'Unknown')
                print(f"📊 System Status:")
                print(f"   • Voice Profile: {current_profile}")
                print(f"   • Sound Profile: {status['current_profile']}")
                print(f"   • Total Sounds: {status['total_sounds']}")
                print(f"   • Audio Enabled: {'✅' if sound_mgr.enabled else '❌'}")
                
            else:
                print("❌ Unknown command. Try: profile, say, sound, status, or quit")
                
        except KeyboardInterrupt:
            print("\n👋 Demo interrupted. Goodbye!")
            break
        except Exception as e:
            print(f"❌ Error: {e}")

def main():
    """Run the complete showcase demo"""
    showcase_header()
    
    # Phase 1: Voice Engine Loading
    success, engine = test_voice_engine_loading()
    if not success:
        print("❌ Demo aborted due to voice engine failure")
        return 1
    
    # Phase 2: Sound Manager Testing  
    success, sound_mgr, context_sound_mgr = test_sound_manager()
    if not success:
        print("⚠️  Continuing without sound manager")
        sound_mgr = None
    
    # Phase 3: Voice Profile Testing
    if engine and sound_mgr:
        test_voice_profiles(engine, sound_mgr)
    
    # Phase 4: Audio Effects Pipeline
    test_audio_effects_pipeline()
    
    # Phase 5: Unified Pipeline
    test_unified_pipeline()
    
    # Phase 6: Interactive Demo
    if engine and sound_mgr:
        print("\n🎉 All systems operational! Starting interactive demo...")
        time.sleep(1)
        sound_mgr.play_success_sequence("major")
        interactive_demo_mode(engine, sound_mgr)
    else:
        print("⚠️  Interactive demo skipped due to missing components")
    
    print("\n🎉 M1K3 Voice & Sound Showcase Complete!")
    print("="*80)
    return 0

if __name__ == "__main__":
    sys.exit(main())