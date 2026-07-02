#!/usr/bin/env python3
"""
M1K3 Voice Quality Guide
Helps users choose the best TTS engine for their needs
"""

import sys
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

def main():
    print("🎤 M1K3 Voice Quality Guide")
    print("=" * 50)
    print("Let's find the best voice quality for your needs!\n")

    try:
        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
        from src.tts.controllers.kittentts_manager import KittenManager
        from src.tts.controllers.espeak_tts_manager import espeak_manager
    except ImportError as e:
        print(f"❌ Import failed: {e}")
        return 1

    engine = UnifiedVoiceEngine()

    print("🔍 CHECKING AVAILABLE ENGINES")
    print("-" * 35)

    # Check KittenTTS
    kitten_available = KittenManager.is_available() if hasattr(KittenManager, 'is_available') else True
    print(f"🐱 KittenTTS: {'✅ Available' if kitten_available else '❌ Not available'}")
    if kitten_available:
        print("   Quality: ⭐⭐⭐⭐ Excellent (Neural TTS)")
        print("   Speed: ⚡⚡⚡ Fast")
        print("   Use for: Daily conversation, AI assistant")

    # Check eSpeak
    espeak_available = espeak_manager.is_available()
    print(f"⚡ eSpeak: {'✅ Available' if espeak_available else '❌ Not available'}")
    if espeak_available:
        print("   Quality: ⭐ Poor (Robotic, sounds like Stephen Hawking)")
        print("   Speed: ⚡⚡⚡⚡⚡ Ultra-fast")
        print("   Use for: Emergency speed only, system notifications")

    print(f"\n🎯 RECOMMENDATIONS")
    print("-" * 20)

    if kitten_available:
        print("🥇 BEST CHOICE: KittenTTS")
        print("   • Natural-sounding neural voice")
        print("   • Good balance of quality and speed")
        print("   • Perfect for conversational AI")
        print("   • Command: python cli.py --tts-engine kitten --voice-profile natural")

        print("\n🎭 VOICE PROFILES FOR KITTENTTS:")
        print("   • natural: Default with audio effects (recommended)")
        print("   • minimal: Raw neural voice (faster)")
        print("   • assistant: Professional AI tone")
        print("   • broadcast: Clear announcer style")
        print("   • studio: Premium studio sound with reverb")
        print("   • hall: Spacious hall acoustics")
        print("   • intimate: Cozy room ambiance")
        print("   • studio_chat: Optimized for real-time chat")
        print("   • realtime_chat: Fastest chat synthesis")
    else:
        print("❌ KittenTTS not available")
        print("   Install with: pip install kittentts")

    if espeak_available:
        print(f"\n⚡ SPEED OPTION: eSpeak (Warning: Poor Quality)")
        print("   • Very robotic and computerized")
        print("   • Only use when speed is critical")
        print("   • Command: python cli.py --tts-engine espeak --voice-profile instant")

    print(f"\n🎚️ AUDIO EFFECTS COMPARISON")
    print("-" * 30)
    print("Raw Synthesis (minimal profile):")
    print("   ✅ Faster processing")
    print("   ❌ May sound harsh or inconsistent")

    print("\nWith Audio Effects (natural profile):")
    print("   ✅ Better quality and consistency")
    print("   ✅ AI assistant character (intercom effect)")
    print("   ✅ Normalized volume levels")
    print("   ❌ Slightly slower processing")

    print(f"\n🚀 QUICK START COMMANDS")
    print("-" * 25)
    print("# Test the quality difference:")
    print("python quality_tts_demo.py")
    print()
    print("# Best quality for daily use:")
    print("python cli.py --tts-engine kitten --voice-profile natural")
    print()
    print("# Fast but still decent:")
    print("python cli.py --tts-engine kitten --voice-profile minimal")
    print()
    print("# Emergency speed (poor quality):")
    print("python cli.py --tts-engine espeak --voice-profile instant")

    print(f"\n📝 ABOUT PIPER TTS")
    print("-" * 18)
    print("Piper TTS is available but requires:")
    print("• Separate voice model downloads")
    print("• More complex setup")
    print("• Similar quality to KittenTTS")
    print("• Recommendation: Use KittenTTS instead (easier setup)")

    print(f"\n🎚️ NEW FEATURES: REVERB & REAL-TIME OPTIMIZATIONS")
    print("-" * 50)
    print("🎧 Reverb Effects:")
    print("• studio_chat: Perfect for real-time chatbots")
    print("• studio: Professional studio reverb")
    print("• hall: Spacious concert hall acoustics")
    print("• intimate: Warm, close room sound")
    print()
    print("⚡ Real-time Optimizations:")
    print("• Model pre-warming for faster first response")
    print("• Audio caching for instant phrase repetition")
    print("• Streaming synthesis for non-blocking conversation")
    print("• Test: python realtime_chatbot_demo.py")

    print(f"\n✅ Use KittenTTS for the best balance of quality and speed!")
    return 0


if __name__ == "__main__":
    try:
        exit_code = main()
        sys.exit(exit_code)
    except KeyboardInterrupt:
        print("\n👋 Guide interrupted")
        sys.exit(0)
    except Exception as e:
        print(f"\n❌ Guide failed: {e}")
        sys.exit(1)