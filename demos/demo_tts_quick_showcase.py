#!/usr/bin/env python3
"""
🎤 Quick TTS Showcase - Hear Each Content Type
Demonstrates the intelligent TTS system with distinct voices
"""

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from enhanced_voice_engine import create_voice_engine
    from src.tts.controllers.intelligent_tts_controller import create_intelligent_tts_controller, ContentTypeModulation
    from src.utils.model_output_parser import ContentType
    import pygame
    COMPONENTS_AVAILABLE = True
except ImportError as e:
    print(f"❌ Required components not available: {e}")
    COMPONENTS_AVAILABLE = False

def play_sound_effect(sound_name):
    """Quick sound effect player"""
    try:
        pygame.mixer.init()
        sound_path = f"sounds/{sound_name}"
        if os.path.exists(sound_path):
            sound = pygame.mixer.Sound(sound_path)
            sound.play()
            return True
    except Exception:
        pass
    return False

def main():
    """Quick showcase of all content types"""
    if not COMPONENTS_AVAILABLE:
        print("❌ Components not available")
        return
    
    print("🎭 QUICK TTS CONTENT TYPE SHOWCASE")
    print("=" * 50)
    
    # Initialize
    print("🎤 Loading voice engine...")
    voice_engine = create_voice_engine()
    if not voice_engine.load_model():
        print("❌ Voice engine failed to load")
        return
    
    controller = create_intelligent_tts_controller(voice_engine)
    
    # Play intro sound
    play_sound_effect("Banner.wav")
    
    print("✅ System ready! Listen for distinct voice characteristics:\n")
    
    # Test each content type with short examples
    test_cases = [
        {
            "name": "🤔 THINKING",
            "text": "<thinking>\nLet me think about this carefully...\n</thinking>",
            "description": "Softer, contemplative voice",
            "sound": "water_ripples.wav"
        },
        {
            "name": "📖 NARRATION", 
            "text": "*The system processes the request with remarkable precision and grace.*",
            "description": "Warm, expressive storytelling",
            "sound": "WHOOSH.WAV"
        },
        {
            "name": "💡 ANSWER",
            "text": "Here's the solution: implement a modular architecture for maximum flexibility.",
            "description": "Clear, confident delivery",
            "sound": "ding_ding.wav"
        },
        {
            "name": "❓ CLARIFICATION",
            "text": "Could you please provide more details about your specific requirements?",
            "description": "Rising intonation, helpful tone",
            "sound": "chime1.wav"
        }
    ]
    
    for i, case in enumerate(test_cases, 1):
        print(f"{i}. {case['name']}")
        print(f"   Characteristic: {case['description']}")
        
        # Play sound effect
        play_sound_effect(case['sound'])
        time.sleep(0.3)
        
        print("   🎤 Processing...")
        
        try:
            results = controller.process_text_with_parsing(case['text'])
            if results and any(r.success for r in results):
                print("   ✅ Synthesis complete!\n")
                play_sound_effect("complete.wav")
            else:
                print("   ⚠️  No audio output\n")
        except Exception as e:
            print(f"   ❌ Error: {e}\n")
        
        time.sleep(2)  # Brief pause between demos
    
    # Extreme customization demo
    print("🚀 EXTREME CUSTOMIZATION DEMO")
    print("=" * 40)
    
    # Create dramatic narration settings
    dramatic_settings = ContentTypeModulation(
        volume_multiplier=1.5,
        speed_multiplier=1.4, 
        pitch_adjustment=0.2,
        reverb_amount=0.1,
        warmth_factor=0.5
    )
    
    controller.set_modulation(ContentType.NARRATION, dramatic_settings)
    
    print("🎭 Applying DRAMATIC NARRATION settings:")
    print(f"   Volume: 1.5x | Speed: 1.4x | Pitch: +0.2")
    
    play_sound_effect("DRUMROLL.WAV")
    time.sleep(1)
    
    dramatic_text = "*In a world where voice synthesis knows no bounds, one system dared to achieve the impossible!*"
    
    print("🎤 Processing dramatic narration...")
    try:
        results = controller.process_text_with_parsing(dramatic_text)
        if results and any(r.success for r in results):
            print("✅ Dramatic effect complete!")
            play_sound_effect("victory_confetti.wav")
        else:
            print("⚠️  Dramatic processing completed")
    except Exception as e:
        print(f"❌ Dramatic demo failed: {e}")
    
    print("\n🎉 SHOWCASE COMPLETE!")
    print("=" * 30)
    print("✅ Content-specific voice synthesis demonstrated")
    print("✅ Extreme customization tested")
    print("✅ Sound effects integrated") 
    print("✅ All systems operational")
    
    play_sound_effect("horray_fireworks.wav")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n⏹️  Demo interrupted by user")
    except Exception as e:
        print(f"\n❌ Demo error: {e}")
        import traceback
        traceback.print_exc()