#!/usr/bin/env python3
"""
🎪 TTS SHOWCASE WITH SOUND EFFECTS 🎪
Interactive demo of the intelligent TTS system with immersive audio
Features extreme customization testing and sound effects integration
"""

import os
import sys
import time
import random
import threading
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from enhanced_voice_engine import create_voice_engine
    from intelligent_tts_controller import create_intelligent_tts_controller, ContentTypeModulation
    from model_output_parser import parse_model_output, ContentType
    import pygame
    COMPONENTS_AVAILABLE = True
except ImportError as e:
    print(f"❌ Required components not available: {e}")
    COMPONENTS_AVAILABLE = False

class SFXManager:
    """Manages sound effects for the demo"""
    
    def __init__(self, sounds_dir="sounds"):
        self.sounds_dir = Path(sounds_dir)
        self.sounds = {}
        self.pygame_initialized = False
        
        # Initialize pygame mixer
        try:
            pygame.mixer.init(frequency=22050, size=-16, channels=2, buffer=512)
            self.pygame_initialized = True
            print("🔊 Sound effects system initialized")
        except Exception as e:
            print(f"⚠️  Sound effects not available: {e}")
    
    def load_sound(self, name, filename):
        """Load a sound effect"""
        if not self.pygame_initialized:
            return
        
        try:
            sound_path = self.sounds_dir / filename
            if sound_path.exists():
                self.sounds[name] = pygame.mixer.Sound(str(sound_path))
                return True
        except Exception as e:
            print(f"⚠️  Failed to load {filename}: {e}")
        return False
    
    def play(self, name, volume=1.0):
        """Play a sound effect"""
        if name in self.sounds and self.pygame_initialized:
            try:
                sound = self.sounds[name]
                sound.set_volume(volume)
                sound.play()
                return True
            except Exception:
                pass
        return False
    
    def play_async(self, name, volume=1.0, delay=0):
        """Play sound effect asynchronously with optional delay"""
        def play_delayed():
            if delay > 0:
                time.sleep(delay)
            self.play(name, volume)
        
        if name in self.sounds:
            threading.Thread(target=play_delayed, daemon=True).start()

def setup_sound_effects():
    """Set up all sound effects for the demo"""
    sfx = SFXManager()
    
    # Load demo sound effects
    sfx_mappings = {
        "intro": "Banner.wav",
        "drumroll": "DRUMROLL.WAV", 
        "voltage": "VOLTAGE.WAV",
        "whoosh": "WHOOSH.WAV",
        "complete": "complete.wav",
        "chime": "chime1.wav",
        "coin": "coin.wav",
        "ding": "ding_ding.wav",
        "magic": "cast_a_spell_sound.wav",
        "laser": "laser_shot.wav",
        "victory": "victory_confetti.wav",
        "splash": "Splash_Big.wav",
        "error": "click_error.wav",
        "incorrect": "incorrect_sfx.wav",
        "crowd": "crowd_cheer_sfx.wav",
        "fireworks": "horray_fireworks.wav",
        "water": "water_ripples.wav"
    }
    
    loaded_count = 0
    for name, filename in sfx_mappings.items():
        if sfx.load_sound(name, filename):
            loaded_count += 1
    
    print(f"🎵 Loaded {loaded_count}/{len(sfx_mappings)} sound effects")
    return sfx

def create_extreme_modulations():
    """Create extreme modulation configurations for testing"""
    return {
        "whisper_thinking": ContentTypeModulation(
            volume_multiplier=0.3,
            speed_multiplier=0.6,
            pitch_adjustment=-0.4,
            reverb_amount=0.4,
            warmth_factor=0.0
        ),
        "dramatic_narration": ContentTypeModulation(
            volume_multiplier=1.5,
            speed_multiplier=1.4,
            pitch_adjustment=0.2,
            reverb_amount=0.1,
            warmth_factor=0.5
        ),
        "robot_answer": ContentTypeModulation(
            volume_multiplier=1.2,
            speed_multiplier=0.8,
            pitch_adjustment=-0.3,
            reverb_amount=0.0,
            warmth_factor=0.0
        ),
        "excited_clarification": ContentTypeModulation(
            volume_multiplier=1.3,
            speed_multiplier=1.3,
            pitch_adjustment=0.4,
            reverb_amount=0.0,
            warmth_factor=0.2
        )
    }

def demo_intro(sfx):
    """Spectacular intro sequence"""
    sfx.play("intro", 0.7)
    
    print("🎪" + "="*70 + "🎪")
    print("🎭          INTELLIGENT TTS SHOWCASE WITH SOUND EFFECTS          🎭")
    print("🎪" + "="*70 + "🎪")
    
    time.sleep(1)
    sfx.play("magic", 0.5)
    
    print("\n🎵 Welcome to the most advanced TTS demo ever created!")
    print("🎯 Features:")
    print("   • Content-aware voice synthesis")
    print("   • Extreme customization testing") 
    print("   • Immersive sound effects")
    print("   • Real-time audio processing")
    
    sfx.play("chime", 0.6)
    time.sleep(0.5)

def demo_content_types(controller, sfx):
    """Demo each content type with sound effects"""
    print(f"\n🎭 CONTENT TYPE SHOWCASE")
    print("=" * 50)
    
    content_demos = [
        {
            "name": "THINKING",
            "sfx_intro": "water",
            "text": """<thinking>
Let me ponder this deeply... The implications are fascinating. I need to consider multiple angles here. This requires careful contemplation and analysis.
</thinking>""",
            "description": "Soft, contemplative voice with reverb depth"
        },
        {
            "name": "NARRATION", 
            "sfx_intro": "whoosh",
            "text": "*The mysterious figure approached slowly, their footsteps echoing in the empty corridor. The atmosphere was thick with tension and anticipation.*",
            "description": "Warm, expressive storytelling voice"
        },
        {
            "name": "ANSWER",
            "sfx_intro": "ding",
            "text": "Based on my comprehensive analysis, the optimal solution involves implementing a multi-layered approach that combines efficiency with scalability.",
            "description": "Clear, confident, authoritative delivery"
        },
        {
            "name": "CLARIFICATION",
            "sfx_intro": "chime", 
            "text": "Could you please elaborate on your specific requirements? What timeline are you working with for this project?",
            "description": "Rising intonation, helpful questioning tone"
        }
    ]
    
    for i, demo in enumerate(content_demos, 1):
        print(f"\n🎬 Demo {i}/4: {demo['name']}")
        print(f"🎯 Characteristic: {demo['description']}")
        
        # Play intro sound effect
        sfx.play(demo['sfx_intro'], 0.4)
        time.sleep(0.3)
        
        print("🎤 Processing...")
        
        try:
            results = controller.process_text_with_parsing(demo['text'])
            
            if results and any(r.success for r in results):
                print("✅ Synthesis successful!")
                sfx.play("complete", 0.3)
            else:
                print("⚠️  Synthesis completed (no audio)")
                
        except Exception as e:
            print(f"❌ Error: {e}")
            sfx.play("error", 0.5)
        
        time.sleep(1)

def demo_extreme_customization(controller, sfx):
    """Test extreme modulation configurations"""
    print(f"\n🚀 EXTREME CUSTOMIZATION TESTING")
    print("=" * 50)
    
    sfx.play("voltage", 0.6)
    time.sleep(0.5)
    
    extreme_mods = create_extreme_modulations()
    
    test_scenarios = [
        {
            "name": "Whisper Mode",
            "modulation": "whisper_thinking",
            "content_type": ContentType.THINKING,
            "text": "<thinking>This is a whisper-quiet thinking mode test with maximum depth and minimal volume.</thinking>",
            "sfx": "water"
        },
        {
            "name": "Dramatic Storyteller",
            "modulation": "dramatic_narration", 
            "content_type": ContentType.NARRATION,
            "text": "*In a world where voice synthesis knows no bounds, one system dared to push the limits of what's possible!*",
            "sfx": "drumroll"
        },
        {
            "name": "Robot Voice",
            "modulation": "robot_answer",
            "content_type": ContentType.ANSWER, 
            "text": "SYSTEM ANALYSIS COMPLETE. ROBOTIC VOICE MODE ENGAGED. ALL PARAMETERS OPTIMIZED.",
            "sfx": "laser"
        },
        {
            "name": "Hyper-Excited",
            "modulation": "excited_clarification",
            "content_type": ContentType.CLARIFICATION,
            "text": "OH WOW! Isn't this absolutely amazing?! Can you believe how customizable this system is?!",
            "sfx": "fireworks"
        }
    ]
    
    for i, scenario in enumerate(test_scenarios, 1):
        print(f"\n🎭 EXTREME TEST {i}/4: {scenario['name']}")
        
        # Apply extreme modulation
        extreme_modulation = extreme_mods[scenario['modulation']]
        controller.set_modulation(scenario['content_type'], extreme_modulation)
        
        # Show the extreme settings
        print(f"🎚️  Settings:")
        print(f"   Volume: {extreme_modulation.volume_multiplier:.1f}x")
        print(f"   Speed: {extreme_modulation.speed_multiplier:.1f}x") 
        print(f"   Pitch: {extreme_modulation.pitch_adjustment:+.1f}")
        print(f"   Reverb: {extreme_modulation.reverb_amount:.1f}")
        
        # Play dramatic intro sound
        sfx.play(scenario['sfx'], 0.5)
        time.sleep(0.2)
        
        print("🎤 Processing extreme configuration...")
        
        try:
            results = controller.process_text_with_parsing(scenario['text'])
            
            if results and any(r.success for r in results):
                print("✅ Extreme test successful!")
                sfx.play("victory", 0.4)
            else:
                print("⚠️  Test completed")
                
        except Exception as e:
            print(f"❌ Extreme test failed: {e}")
            sfx.play("incorrect", 0.5)
        
        time.sleep(1.5)

def demo_mixed_content_showcase(controller, sfx):
    """Showcase complex mixed content with sound effects"""
    print(f"\n🎭 MIXED CONTENT SPECTACULAR")
    print("=" * 50)
    
    sfx.play("splash", 0.7)
    
    complex_scenarios = [
        {
            "name": "AI Problem Solving",
            "text": """<thinking>
This is a complex multi-faceted problem. Let me break it down systematically.
I need to consider the technical constraints, user requirements, and scalability factors.
</thinking>

Based on my analysis, here's the optimal approach: *gestures confidently*

We'll implement a three-phase solution. First, we establish the foundation with robust error handling. Then we scale progressively.

However, I have a few questions to ensure we're aligned - what's your preferred timeline for phase one deployment?""",
            "sfx_sequence": ["magic", "ding", "chime"]
        },
        {
            "name": "Storytelling Adventure",
            "text": """*Once upon a time, in a digital realm where artificial intelligence dreamed of perfect speech...*

<thinking>
I should make this story engaging and demonstrate the full range of voice capabilities.
</thinking>

The brave little TTS system embarked on a quest to master every nuance of human communication. 

But wait - dear listener, which path should our hero take next? The mysterious Algorithm Forest, or the Valley of Voice Modulation?""",
            "sfx_sequence": ["whoosh", "water", "drumroll", "chime"]
        }
    ]
    
    for i, scenario in enumerate(complex_scenarios, 1):
        print(f"\n🎬 SPECTACULAR {i}/2: {scenario['name']}")
        print("🎵 Listen for the seamless transitions between content types!")
        
        # Play intro sound effect sequence
        for j, sfx_name in enumerate(scenario['sfx_sequence'][:1]):  # Just first sound for intro
            sfx.play(sfx_name, 0.3)
            time.sleep(0.2)
        
        print("🎤 Processing mixed content masterpiece...")
        
        try:
            # Parse first to show segments
            parsed = parse_model_output(scenario['text'])
            print(f"📊 Identified {len(parsed.segments)} content segments")
            
            # Process with sound effects during pauses
            results = controller.process_text_with_parsing(scenario['text'])
            
            success_count = sum(1 for r in results if r.success)
            print(f"✅ Mixed content success: {success_count}/{len(results)} segments")
            
            if success_count > 0:
                sfx.play("crowd", 0.6)
                
        except Exception as e:
            print(f"❌ Mixed content failed: {e}")
            sfx.play("error", 0.5)
        
        time.sleep(2)

def demo_system_knowledge(controller, sfx):
    """Demo the system's knowledge integration"""
    print(f"\n🧠 SYSTEM KNOWLEDGE INTEGRATION")
    print("=" * 50)
    
    sfx.play("magic", 0.5)
    
    knowledge_queries = [
        {
            "category": "Technology",
            "text": """<thinking>
The user is asking about WiFi troubleshooting. Let me access my networking knowledge base.
</thinking>

For WiFi connectivity issues, start by checking these key factors: *counts on fingers*

First, verify your router's power and internet connection. Then check if other devices can connect.

Are you experiencing slow speeds, or complete connection failures? This will help me provide more specific guidance.""",
            "sfx": "laser"
        },
        {
            "category": "Science",
            "text": """*Fascinating scientific fact incoming!*

<thinking>
I should explain this clearly while making it engaging and educational.
</thinking>

Did you know that honey never spoils? Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3000 years old and still perfectly edible!

Would you like to know the scientific reason behind honey's incredible preservation properties?""",
            "sfx": "ding"
        }
    ]
    
    for i, query in enumerate(knowledge_queries, 1):
        print(f"\n🎓 Knowledge Demo {i}/2: {query['category']}")
        
        sfx.play(query['sfx'], 0.4)
        time.sleep(0.3)
        
        print("🧠 Processing knowledge-enhanced content...")
        
        try:
            results = controller.process_text_with_parsing(query['text'])
            
            if results and any(r.success for r in results):
                print("✅ Knowledge integration successful!")
                sfx.play("complete", 0.4)
            else:
                print("⚠️  Knowledge processing completed")
                
        except Exception as e:
            print(f"❌ Knowledge demo failed: {e}")
            sfx.play("error", 0.5)
        
        time.sleep(1.5)

def demo_finale(sfx):
    """Grand finale with sound effects"""
    print(f"\n🎆 GRAND FINALE")
    print("=" * 30)
    
    # Finale sound sequence
    sfx.play("drumroll", 0.8)
    time.sleep(1)
    
    print("🎪 The Intelligent TTS Showcase is complete!")
    
    sfx.play("fireworks", 0.6)
    time.sleep(0.5)
    
    sfx.play("victory", 0.7)
    
    print("\n🎯 Demonstrated Features:")
    print("   ✅ Content-specific voice synthesis")
    print("   ✅ Extreme customization capabilities")
    print("   ✅ Mixed content processing")
    print("   ✅ Sound effects integration")
    print("   ✅ System knowledge integration")
    print("   ✅ Real-time audio processing")
    
    sfx.play("crowd", 0.5)
    time.sleep(0.5)
    
    print(f"\n🎉 Thank you for experiencing the future of TTS!")
    
    sfx.play("complete", 0.8)

def main():
    """Run the complete TTS showcase with sound effects"""
    if not COMPONENTS_AVAILABLE:
        print("❌ Required components not available for demo")
        return
    
    # Initialize sound effects
    print("🎵 Initializing sound effects system...")
    sfx = setup_sound_effects()
    
    # Spectacular intro
    demo_intro(sfx)
    
    # Initialize TTS system
    print(f"\n🎤 Initializing intelligent TTS system...")
    try:
        voice_engine = create_voice_engine()
        if not voice_engine.load_model():
            print("❌ Voice engine failed to load")
            return
        
        controller = create_intelligent_tts_controller(voice_engine)
        print("✅ TTS system ready!")
        
        sfx.play("chime", 0.6)
        
    except Exception as e:
        print(f"❌ TTS initialization failed: {e}")
        return
    
    time.sleep(1)
    
    # Run all demo sections
    try:
        demo_content_types(controller, sfx)
        demo_extreme_customization(controller, sfx) 
        demo_mixed_content_showcase(controller, sfx)
        demo_system_knowledge(controller, sfx)
        demo_finale(sfx)
        
    except KeyboardInterrupt:
        print(f"\n\n⏹️  Demo interrupted by user")
        sfx.play("whoosh", 0.5)
    except Exception as e:
        print(f"\n❌ Demo error: {e}")
        sfx.play("error", 0.7)
    
    print(f"\n🎪 Demo session complete!")

if __name__ == "__main__":
    main()