#!/usr/bin/env python3
"""
Philosophy & Meditation Reverb Configuration
Specialized reverb presets optimized for contemplative, philosophical conversations
with nostalgic and cinematic atmosphere.
"""

import sys
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

# Specialized reverb configurations for philosophical dialogue
PHILOSOPHY_REVERB_PRESETS = {
    "contemplative_studio": {
        "description": "Warm studio reverb perfect for deep philosophical reflection",
        "room_size": 0.45,
        "decay_time": 1.2,
        "wet_mix": 0.28,
        "dampening": 0.3,
        "early_reflections": 0.4,
        "use_case": "Main philosophical discussions, meditation guidance"
    },

    "wisdom_hall": {
        "description": "Grand hall acoustics for profound philosophical concepts",
        "room_size": 0.85,
        "decay_time": 2.8,
        "wet_mix": 0.35,
        "dampening": 0.2,
        "early_reflections": 0.6,
        "use_case": "Discussing universal truths, cosmic perspectives"
    },

    "intimate_reflection": {
        "description": "Close, personal space for inner contemplation",
        "room_size": 0.25,
        "decay_time": 0.6,
        "wet_mix": 0.22,
        "dampening": 0.4,
        "early_reflections": 0.3,
        "use_case": "Personal meditation techniques, anxiety guidance"
    },

    "nostalgic_library": {
        "description": "Old library atmosphere with vintage philosophical charm",
        "room_size": 0.6,
        "decay_time": 1.8,
        "wet_mix": 0.32,
        "dampening": 0.35,
        "early_reflections": 0.5,
        "use_case": "Historical philosophy, classical meditation teachings"
    },

    "meditation_cave": {
        "description": "Deep, resonant space for profound inner exploration",
        "room_size": 0.9,
        "decay_time": 3.5,
        "wet_mix": 0.4,
        "dampening": 0.15,
        "early_reflections": 0.7,
        "use_case": "Deep meditation instructions, transcendental concepts"
    },

    "zen_garden": {
        "description": "Peaceful outdoor space with gentle natural reverb",
        "room_size": 0.35,
        "decay_time": 0.9,
        "wet_mix": 0.25,
        "dampening": 0.5,
        "early_reflections": 0.35,
        "use_case": "Mindfulness teachings, present-moment awareness"
    }
}

def create_reverb_effect_with_preset(preset_name):
    """Create a ReverbEffect configured with a philosophy preset."""
    try:
        from src.tts.effects.audio_effects import ReverbEffect

        if preset_name not in PHILOSOPHY_REVERB_PRESETS:
            raise ValueError(f"Unknown preset: {preset_name}")

        preset = PHILOSOPHY_REVERB_PRESETS[preset_name]
        config = {
            "room_size": preset["room_size"],
            "decay_time": preset["decay_time"],
            "wet_mix": preset["wet_mix"],
            "dampening": preset.get("dampening", 0.3),
            "early_reflections": preset.get("early_reflections", 0.4)
        }

        return ReverbEffect(config)

    except ImportError as e:
        print(f"❌ Failed to import ReverbEffect: {e}")
        return None

def demonstrate_philosophy_reverbs():
    """Demonstrate all philosophy-specific reverb presets."""
    try:
        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine

        engine = UnifiedVoiceEngine()
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return False

        print("🧘 Philosophy Reverb Presets Demonstration")
        print("=" * 50)

        test_wisdom = "In the stillness of meditation, we discover the profound peace that exists beneath the turbulence of everyday thought."

        for preset_name, preset_config in PHILOSOPHY_REVERB_PRESETS.items():
            print(f"\n🎧 {preset_name.replace('_', ' ').title()}")
            print(f"   Description: {preset_config['description']}")
            print(f"   Use Case: {preset_config['use_case']}")
            print(f"   Settings: Room {preset_config['room_size']:.2f}, Decay {preset_config['decay_time']:.1f}s, Mix {preset_config['wet_mix']:.2f}")

            # Create custom reverb effect
            reverb_effect = create_reverb_effect_with_preset(preset_name)
            if reverb_effect:
                # Temporarily modify the engine's effects pipeline
                original_pipeline = engine.effects_pipeline.copy()
                engine.effects_pipeline = [reverb_effect]

                print("   🎤 Playing sample...")
                engine.synthesize_and_play(test_wisdom, background=False)

                # Restore original pipeline
                engine.effects_pipeline = original_pipeline

                import time
                time.sleep(1)
            else:
                print("   ❌ Failed to create reverb effect")

        print("\n✨ Philosophy reverb demonstration complete!")
        return True

    except Exception as e:
        print(f"❌ Demonstration failed: {e}")
        return False

def apply_philosophy_profile_to_engine(engine, conversation_topic):
    """Apply the most appropriate philosophy reverb preset based on conversation topic."""

    topic_to_preset = {
        "meditation_basics": "zen_garden",
        "anxiety_relief": "intimate_reflection",
        "focus_training": "contemplative_studio",
        "philosophical_concepts": "wisdom_hall",
        "historical_philosophy": "nostalgic_library",
        "transcendental_topics": "meditation_cave",
        "personal_reflection": "intimate_reflection",
        "universal_wisdom": "wisdom_hall",
        "practical_techniques": "contemplative_studio",
        "deep_contemplation": "meditation_cave"
    }

    preset_name = topic_to_preset.get(conversation_topic, "contemplative_studio")
    preset_config = PHILOSOPHY_REVERB_PRESETS[preset_name]

    print(f"🎭 Switching to {preset_name.replace('_', ' ').title()} reverb")
    print(f"   {preset_config['description']}")

    # Apply the reverb configuration
    reverb_effect = create_reverb_effect_with_preset(preset_name)
    if reverb_effect:
        # Update the engine's effects pipeline
        # Find and replace any existing reverb effects
        new_pipeline = []
        for effect in engine.effects_pipeline:
            if effect.get_name() != "ReverbEffect":
                new_pipeline.append(effect)
        new_pipeline.append(reverb_effect)
        engine.effects_pipeline = new_pipeline
        return True

    return False

def main():
    """Test and demonstrate philosophy reverb configurations."""
    print("🧘 Philosophy & Meditation Reverb Configuration")
    print("=" * 55)
    print("Specialized atmospheric presets for contemplative dialogue\n")

    print("📋 Available Philosophy Reverb Presets:")
    print("-" * 40)

    for name, config in PHILOSOPHY_REVERB_PRESETS.items():
        print(f"🎧 {name.replace('_', ' ').title()}")
        print(f"   {config['description']}")
        print(f"   Use: {config['use_case']}")
        print(f"   Settings: Room {config['room_size']:.2f} | Decay {config['decay_time']:.1f}s | Mix {config['wet_mix']:.2f}")
        print()

    # Offer to demonstrate
    response = input("Would you like to hear a demonstration of these presets? (y/n): ")
    if response.lower().startswith('y'):
        demonstrate_philosophy_reverbs()

    return 0

if __name__ == "__main__":
    try:
        exit_code = main()
        sys.exit(exit_code)
    except KeyboardInterrupt:
        print("\n🧘 Configuration interrupted - Peace be with you")
        sys.exit(0)
    except Exception as e:
        print(f"\n❌ Configuration failed: {e}")
        sys.exit(1)