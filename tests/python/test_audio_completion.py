#!/usr/bin/env python3
"""
Test Audio Completion Fix
Tests the enhanced audio completion engine to prevent cutoff at end of synthesis
"""

import sys
import time
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

def test_audio_completion():
    """Test audio completion engine with various text lengths and types"""

    print("🔧 Testing Audio Completion Fix for TTS Cutoff Issues")
    print("=" * 60)

    try:
        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
        from src.tts.controllers.kittentts_manager import KittenManager

        # Test texts that commonly get cut off
        test_cases = [
            # Short phrases (fast path)
            "Hello world!",
            "How are you today?",
            "This is a test.",
            "Thank you very much.",
            "Ready to proceed.",

            # Medium phrases (might be chunked)
            "Welcome to the M1K3 voice synthesis system with audio completion.",
            "The quick brown fox jumps over the lazy dog in the forest.",
            "Please let me know if you have any questions or concerns about this.",

            # Longer text (definitely chunked)
            "This is a comprehensive test of the audio completion engine that should prevent any cutoff issues at the end of speech synthesis. The system should now apply completion to all chunks, not just the final ones.",
        ]

        if not KittenManager.is_available():
            print("❌ KittenTTS not available - cannot test audio completion")
            return

        print("🎤 Loading voice engine...")
        voice_engine = UnifiedVoiceEngine()
        voice_engine.voice_enabled = True

        if not voice_engine.load_model():
            print("❌ Failed to load voice engine")
            return

        print("✅ Voice engine loaded successfully\n")

        print("Testing various text lengths that commonly get truncated:")
        print("-" * 50)

        for i, test_text in enumerate(test_cases, 1):
            print(f"\n🎵 Test {i}: {test_text}")
            print(f"   Length: {len(test_text)} characters")

            if len(test_text) < 100:
                print(f"   Path: Fast path (should use audio completion now)")
            else:
                print(f"   Path: Chunked path (should complete all chunks)")

            try:
                # Test the synthesis with our fixes
                start_time = time.time()
                success = voice_engine.synthesize_and_play(test_text, background=False)
                duration = time.time() - start_time

                if success:
                    print(f"   Result: ✅ Success ({duration:.2f}s) - Listen for complete audio")
                else:
                    print(f"   Result: ❌ Failed")

            except Exception as e:
                print(f"   Result: ❌ Exception: {e}")

            # Small delay between tests
            time.sleep(0.5)

        print("\n" + "=" * 60)
        print("🎯 Audio Completion Test Complete!")
        print("\nKey fixes applied:")
        print("✅ Audio completion now applied to fast path (< 100 chars)")
        print("✅ Audio completion applied to ALL chunks, not just final")
        print("✅ Force completion enabled for KittenTTS truncation issues")
        print("✅ More sensitive truncation detection threshold")
        print("\nListen carefully to the audio - endings should be complete now!")

    except Exception as e:
        print(f"❌ Audio completion test failed: {e}")
        import traceback
        traceback.print_exc()

def test_direct_completion_engine():
    """Test the audio completion engine directly"""
    print("\n🔧 Testing Audio Completion Engine Directly")
    print("-" * 40)

    try:
        from src.tts.effects.audio_completion_engine import AudioCompletionEngine
        from src.tts.controllers.kittentts_manager import KittenManager
        import numpy as np

        if not KittenManager.is_available():
            print("❌ KittenTTS not available")
            return

        # Load KittenTTS and generate raw audio
        if not KittenManager.load_model():
            print("❌ Failed to load KittenTTS")
            return

        completion_engine = AudioCompletionEngine(sample_rate=24000)

        test_text = "This might get cut off at the end"
        print(f"🎵 Generating raw audio: '{test_text}'")

        raw_audio = KittenManager.generate(test_text)
        if raw_audio is None:
            print("❌ Failed to generate raw audio")
            return

        print(f"✅ Raw audio generated: {raw_audio.shape} samples")

        # Test truncation detection
        is_truncated, confidence = completion_engine.detect_truncation(raw_audio)
        print(f"🔍 Truncation detected: {is_truncated} (confidence: {confidence:.3f})")

        # Test completion
        if is_truncated or completion_engine.force_completion:
            completed_audio, fix_info = completion_engine.fix_audio(raw_audio, "test")
            print(f"🔧 Audio completion applied: {fix_info}")
            print(f"   Original length: {len(raw_audio)} samples")
            print(f"   Completed length: {len(completed_audio)} samples")
            print(f"   Added: {len(completed_audio) - len(raw_audio)} samples")
        else:
            print("ℹ️  No completion needed")

    except Exception as e:
        print(f"❌ Direct completion test failed: {e}")

if __name__ == "__main__":
    test_audio_completion()
    test_direct_completion_engine()