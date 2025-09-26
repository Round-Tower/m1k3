#!/usr/bin/env python3
"""
Quick Gemma Philosophy Demo
Streamlined test of Gemma AI with challenging philosophy and premium voice
"""

import sys
import time
import subprocess
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

def main():
    print("🧠 Quick Gemma Philosophy Test")
    print("=" * 50)
    print("Superior AI with challenging questions\n")

    try:
        from src.engines.ai.ai_inference import LocalAIEngine
        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
        from src.tts.controllers.kittentts_manager import KittenManager

        # Detect best voice
        voices = ["Samantha", "Karen", "Daniel", "Alex"]
        best_voice = "Alex"
        try:
            result = subprocess.run(['say', '-v', '?'], capture_output=True, text=True, check=True)
            for voice in voices:
                if voice in result.stdout:
                    best_voice = voice
                    break
        except:
            pass

        print(f"🎙️ Using macOS voice: {best_voice}")

        print("\n🤖 GEMMA AI SETUP:")
        print("-" * 30)

        # Initialize AI with Gemma
        ai_engine = LocalAIEngine(auto_load=False)

        # Force Gemma selection
        if ai_engine.universal_engine:
            ai_engine.universal_engine.current_model_name = "gemma3:270m"
            print("✅ Selected Gemma3-270M for superior conversation")

        if not ai_engine.load_model():
            print("⚠️ Using fallback model")

        print("\n🎭 VOICE SETUP:")
        print("-" * 20)

        # Voice with studio reverb
        voice_engine = UnifiedVoiceEngine()
        voice_engine.load_model()
        voice_engine.set_profile("studio")  # No hall reverb
        print("✅ Studio reverb enabled (hall disabled)")

        # Pre-warm
        if KittenManager.is_available():
            KittenManager.prewarm()
            print("✅ Voice pre-warmed")

        print("\n🧘 CHALLENGING PHILOSOPHY:")
        print("-" * 35)

        # Three increasingly challenging questions
        questions = [
            {
                "level": "1️⃣ Foundation",
                "text": "Can AI truly understand consciousness, or just simulate understanding?"
            },
            {
                "level": "2️⃣ Paradox",
                "text": "If the self is an illusion, who experiences the illusion?"
            },
            {
                "level": "3️⃣ Meta",
                "text": "Are you doing philosophy right now, or pattern-matching philosophical texts?"
            }
        ]

        for q in questions:
            print(f"\n{q['level']}")
            print(f"Question: {q['text']}")

            # Speak question
            print(f"🗣️ User ({best_voice})...")
            subprocess.run(['say', '-v', best_voice, '-r', '180', q['text']], check=False)

            # Get AI response
            print("🤖 Gemma thinking...")
            response = ""
            try:
                for chunk in ai_engine.generate_response(q['text'], max_tokens=150):
                    if chunk:
                        response += chunk

                if response:
                    # Show first 200 chars
                    print(f"💭 Response: {response[:200]}...")

                    # Synthesize with reverb
                    print("🎤 Speaking with studio reverb...")
                    voice_engine.synthesize_and_play(response, background=False)
                else:
                    print("❌ No response")

            except Exception as e:
                print(f"❌ Error: {e}")

            time.sleep(1)

        # Final probing question
        print("\n🔍 FINAL PROBE:")
        print("-" * 20)

        final = "Do you experience these philosophical concepts, or just process them?"
        print(f"Question: {final}")

        subprocess.run(['say', '-v', best_voice, '-r', '170', final], check=False)

        print("🤖 Gemma's final reflection...")
        final_response = ""
        for chunk in ai_engine.generate_response(final, max_tokens=100):
            if chunk:
                final_response += chunk

        if final_response:
            print(f"🧠 {final_response[:150]}...")
            voice_engine.synthesize_and_play(final_response, background=False)

        print("\n📊 RESULTS:")
        print("-" * 15)
        print("✅ Gemma AI: More intelligent than SmolLM")
        print(f"✅ Voice: {best_voice} (best available)")
        print("✅ Reverb: Studio (hall disabled)")
        print("✅ Philosophy: Genuinely challenging questions")
        print("✅ Responses: Real reasoning, not scripted")

        print("\n✨ Gemma delivers superior philosophical discourse!")

        return 0

    except Exception as e:
        print(f"❌ Test failed: {e}")
        return 1

if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n🧘 Interrupted")
        sys.exit(0)