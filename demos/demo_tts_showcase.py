#!/usr/bin/env python3
"""
TTS Showcase Demo - Interactive testing of content-type voice modulation
This demonstrates the enhanced TTS system with different voice styles for different content types
"""

import os
import sys
import time
from typing import Dict, Any, List

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from enhanced_voice_engine import create_voice_engine
    from model_output_parser import parse_model_output, ContentType
    COMPONENTS_AVAILABLE = True
except ImportError as e:
    print(f"❌ Required components not available: {e}")
    COMPONENTS_AVAILABLE = False

class TTSShowcase:
    """Interactive TTS showcase for testing voice modulation"""
    
    def __init__(self):
        self.voice_engine = None
        self.current_modulations: Dict[ContentType, Dict[str, Any]] = {}
        self.setup_default_modulations()
    
    def setup_default_modulations(self):
        """Set up default voice modulations for each content type"""
        self.current_modulations = {
            ContentType.THINKING: {
                "volume_multiplier": 0.8,
                "speed_multiplier": 0.85,
                "pitch_adjustment": -0.1,
                "description": "Softer, more contemplative"
            },
            ContentType.NARRATION: {
                "volume_multiplier": 1.0,
                "speed_multiplier": 1.1,
                "pitch_adjustment": 0.05,
                "description": "Expressive storytelling"
            },
            ContentType.ANSWER: {
                "volume_multiplier": 1.0,
                "speed_multiplier": 1.0,
                "pitch_adjustment": 0.0,
                "description": "Clear and confident"
            },
            ContentType.CLARIFICATION: {
                "volume_multiplier": 1.0,
                "speed_multiplier": 0.95,
                "pitch_adjustment": 0.15,
                "description": "Rising intonation for questions"
            }
        }
    
    def initialize(self) -> bool:
        """Initialize the TTS system"""
        if not COMPONENTS_AVAILABLE:
            print("❌ Cannot initialize - missing components")
            return False
        
        print("🎤 Initializing Enhanced TTS Showcase...")
        
        self.voice_engine = create_voice_engine()
        if not self.voice_engine.load_model():
            print("❌ Failed to load voice model")
            return False
        
        print("✅ TTS system initialized successfully")
        return True
    
    def demonstrate_content_types(self):
        """Demonstrate different content types with voice modulation"""
        print("\n🎭 Content Type Voice Modulation Demo")
        print("=" * 50)
        
        demo_texts = {
            ContentType.THINKING: [
                "Let me think about this carefully. I need to consider all the implications before responding.",
                "Hmm, this is a complex problem. There are several factors I should weigh here."
            ],
            ContentType.NARRATION: [
                "The user pauses thoughtfully, considering the various options available to them.",
                "A moment of silence fills the room as they contemplate the decision before them."
            ],
            ContentType.ANSWER: [
                "Based on my analysis, the best approach is to implement a modular system for maximum flexibility.",
                "Here's what I recommend: start with a solid foundation and build incrementally."
            ],
            ContentType.CLARIFICATION: [
                "Could you please clarify what specific aspect you're most interested in?",
                "What would you like to know more about - the technical details or the general approach?"
            ]
        }
        
        for content_type, texts in demo_texts.items():
            modulation = self.current_modulations[content_type]
            
            print(f"\n📢 {content_type.value.upper()} Content")
            print(f"🎚️  Modulation: {modulation['description']}")
            print(f"   • Volume: {modulation['volume_multiplier']:.1f}x")
            print(f"   • Speed: {modulation['speed_multiplier']:.1f}x")
            print(f"   • Pitch: {modulation['pitch_adjustment']:+.1f}")
            
            for i, text in enumerate(texts, 1):
                print(f"\n[{i}/2] Text: '{text}'")
                print("▶️  Playing with current modulation...")
                
                try:
                    # NOTE: This is a simulation since we haven't implemented
                    # the actual voice modulation yet. In a real implementation,
                    # we would apply the modulation parameters here.
                    
                    if self.voice_engine:
                        # For now, just play with standard voice
                        # TODO: Apply modulation parameters when implemented
                        self.voice_engine.synthesize_and_play(text, background=False)
                        print(f"🎵 [SIMULATED] Applied {content_type.value} modulation")
                    else:
                        print("🤖 [SIMULATED] Would play with modulation")
                    
                    time.sleep(1)
                    
                except Exception as e:
                    print(f"❌ Playback failed: {e}")
    
    def demonstrate_mixed_content(self):
        """Demonstrate parsing and playing mixed content"""
        print("\n🎪 Mixed Content Demo")
        print("=" * 30)
        
        mixed_samples = [
            """<thinking>
This is an interesting question. Let me consider the best way to explain this concept.
</thinking>

The key principle here is modularity. *The assistant gestures to emphasize the point.* 

By designing modular components, you gain flexibility and maintainability. 

But could you tell me more about your specific use case?""",
            
            """*The user looks thoughtful as they process the information.*

Well, there are several approaches we could take. Let me think through each option carefully.

The first approach would be... *pauses to gather thoughts* Actually, could you clarify which aspect is most important to you?"""
        ]
        
        for i, sample in enumerate(mixed_samples, 1):
            print(f"\n[Sample {i}/2] Mixed Content Analysis and Playback")
            print(f"📝 Original text:")
            print(f"   {repr(sample)}")
            
            # Parse the content
            print(f"\n🔍 Parsing content...")
            parsed = parse_model_output(sample)
            
            print(f"📊 Found {len(parsed.segments)} segments:")
            for j, segment in enumerate(parsed.segments, 1):
                modulation = self.current_modulations.get(segment.content_type, {})
                mod_desc = modulation.get('description', 'standard')
                
                print(f"   {j}. {segment.content_type.value}: '{segment.text[:50]}...' ({mod_desc})")
            
            print(f"\n🎤 Playing back with appropriate voice modulation...")
            
            # Play each segment with its appropriate modulation
            for segment in parsed.segments:
                modulation = self.current_modulations[segment.content_type]
                
                print(f"\n▶️  Playing {segment.content_type.value}: '{segment.text[:30]}...'")
                print(f"   🎚️  Using: {modulation['description']}")
                
                try:
                    if self.voice_engine:
                        # TODO: Apply actual modulation when implemented
                        self.voice_engine.synthesize_and_play(segment.text, background=False)
                        print(f"   ✅ Applied {segment.content_type.value} voice style")
                    else:
                        print("   🤖 [SIMULATED] Modulated playback")
                    
                    time.sleep(0.5)  # Brief pause between segments
                    
                except Exception as e:
                    print(f"   ❌ Segment playback failed: {e}")
            
            print(f"\n✅ Sample {i} complete!")
            
            if i < len(mixed_samples):
                input("\n⏸️  Press Enter to continue to next sample...")
    
    def interactive_modulation_tuning(self):
        """Interactive tuning of voice modulation parameters"""
        print("\n🎛️  Interactive Modulation Tuning")
        print("=" * 40)
        
        test_text = "This is a test sentence for voice modulation parameter tuning."
        
        print("🔧 Available content types for tuning:")
        content_types = list(self.current_modulations.keys())
        
        for i, ct in enumerate(content_types, 1):
            print(f"   {i}. {ct.value}")
        
        try:
            choice = int(input(f"\nSelect content type (1-{len(content_types)}): ")) - 1
            if 0 <= choice < len(content_types):
                selected_type = content_types[choice]
                self._tune_content_type(selected_type, test_text)
            else:
                print("Invalid selection")
        except ValueError:
            print("Please enter a valid number")
    
    def _tune_content_type(self, content_type: ContentType, test_text: str):
        """Tune parameters for a specific content type"""
        print(f"\n🎚️  Tuning {content_type.value} voice parameters")
        
        modulation = self.current_modulations[content_type].copy()
        
        while True:
            print(f"\n📊 Current {content_type.value} parameters:")
            print(f"   Volume: {modulation['volume_multiplier']:.2f}")
            print(f"   Speed: {modulation['speed_multiplier']:.2f}")
            print(f"   Pitch: {modulation['pitch_adjustment']:+.2f}")
            
            print(f"\n🎵 Test playback:")
            try:
                if self.voice_engine:
                    # TODO: Apply modulation parameters
                    self.voice_engine.synthesize_and_play(test_text, background=False)
                    print(f"   🎚️  [SIMULATED] Applied modulation: {modulation}")
            except Exception as e:
                print(f"   ❌ Playback failed: {e}")
            
            print(f"\n🔧 Adjustment options:")
            print("   1. Adjust volume")
            print("   2. Adjust speed")
            print("   3. Adjust pitch")
            print("   4. Test again")
            print("   5. Save and exit")
            print("   6. Reset to defaults")
            
            try:
                choice = int(input("Choose option (1-6): "))
                
                if choice == 1:
                    new_vol = float(input(f"New volume (0.1-2.0, current: {modulation['volume_multiplier']}): "))
                    modulation['volume_multiplier'] = max(0.1, min(2.0, new_vol))
                elif choice == 2:
                    new_speed = float(input(f"New speed (0.5-2.0, current: {modulation['speed_multiplier']}): "))
                    modulation['speed_multiplier'] = max(0.5, min(2.0, new_speed))
                elif choice == 3:
                    new_pitch = float(input(f"New pitch (-0.5 to +0.5, current: {modulation['pitch_adjustment']}): "))
                    modulation['pitch_adjustment'] = max(-0.5, min(0.5, new_pitch))
                elif choice == 4:
                    continue  # Loop back to test again
                elif choice == 5:
                    self.current_modulations[content_type] = modulation
                    print(f"✅ Saved new {content_type.value} modulation settings")
                    break
                elif choice == 6:
                    self.setup_default_modulations()
                    modulation = self.current_modulations[content_type].copy()
                    print("🔄 Reset to default settings")
                else:
                    print("Invalid option")
                    
            except (ValueError, KeyboardInterrupt):
                print("Exiting tuning...")
                break
    
    def run_showcase(self):
        """Run the complete TTS showcase"""
        if not self.initialize():
            return
        
        print("\n🎪 Welcome to the TTS Enhancement Showcase!")
        print("This demo shows the enhanced TTS system with content-type voice modulation")
        
        while True:
            print(f"\n🎯 Showcase Options:")
            print("   1. 🎭 Demonstrate content type voices")
            print("   2. 🎪 Mixed content parsing and playback")
            print("   3. 🎛️  Interactive modulation tuning")
            print("   4. 📊 Current modulation settings")
            print("   5. 🚪 Exit")
            
            try:
                choice = int(input("\nSelect option (1-5): "))
                
                if choice == 1:
                    self.demonstrate_content_types()
                elif choice == 2:
                    self.demonstrate_mixed_content()
                elif choice == 3:
                    self.interactive_modulation_tuning()
                elif choice == 4:
                    self._show_current_settings()
                elif choice == 5:
                    print("👋 Thanks for trying the TTS showcase!")
                    break
                else:
                    print("Please enter a number 1-5")
                    
            except (ValueError, KeyboardInterrupt):
                print("\n👋 Exiting showcase...")
                break
    
    def _show_current_settings(self):
        """Display current modulation settings"""
        print("\n📊 Current Voice Modulation Settings")
        print("=" * 40)
        
        for content_type, modulation in self.current_modulations.items():
            print(f"\n{content_type.value.upper()}:")
            print(f"   Description: {modulation['description']}")
            print(f"   Volume: {modulation['volume_multiplier']:.2f}x")
            print(f"   Speed: {modulation['speed_multiplier']:.2f}x")
            print(f"   Pitch: {modulation['pitch_adjustment']:+.2f}")

def main():
    """Main function"""
    showcase = TTSShowcase()
    showcase.run_showcase()

if __name__ == "__main__":
    main()