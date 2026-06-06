#!/usr/bin/env python3
"""
Test Enhanced Syllable Completion
Tests the improved audio completion engine specifically for final syllable preservation
"""

import sys
import time
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

def test_syllable_ending_phrases():
    """Test audio completion with phrases that end in specific syllables"""

    print("🔧 Testing Enhanced Syllable Completion for Final Syllable Preservation")
    print("=" * 70)

    try:
        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
        from src.tts.controllers.kittentts_manager import KittenManager

        # Test phrases that commonly lose their final syllables
        syllable_test_cases = [
            # Single-syllable endings that get cut off
            ("Hello there", "THERE ending (single syllable)"),
            ("Thank you", "YOU ending (single syllable)"),
            ("Good morning", "MORN-ING ending (two syllables)"),
            ("Ready to go", "GO ending (single syllable)"),
            ("See you later", "LA-TER ending (two syllables)"),

            # Multi-syllable endings
            ("That's incredible", "IN-CRED-I-BLE ending (four syllables)"),
            ("Welcome to the system", "SYS-TEM ending (two syllables)"),
            ("Processing complete", "COM-PLETE ending (two syllables)"),
            ("Have a wonderful day", "WON-DER-FUL DAY ending (three syllables)"),
            ("Everything is working perfectly", "PER-FECT-LY ending (three syllables)"),

            # Tricky consonant endings
            ("That sounds right", "RIGHT ending (consonant cluster)"),
            ("Please wait just a moment", "MO-MENT ending (nasal + stop)"),
            ("The task is almost finished", "FIN-ISHED ending (fricative + stop)"),
            ("Configuration successful", "SUC-CESS-FUL ending (complex cluster)"),
        ]

        if not KittenManager.is_available():
            print("❌ KittenTTS not available - cannot test syllable completion")
            return

        print("🎤 Loading voice engine with enhanced syllable completion...")
        voice_engine = UnifiedVoiceEngine()
        voice_engine.voice_enabled = True

        if not voice_engine.load_model():
            print("❌ Failed to load voice engine")
            return

        print("✅ Voice engine loaded with enhanced completion settings")
        print(f"   - Completion duration: 150-300ms (was 50-120ms)")
        print(f"   - Truncation threshold: 0.008 (was 0.015)")
        print(f"   - Analysis window: 1200 samples (was 800)")
        print(f"   - Ending sample: 120 samples (was 50)")
        print()

        print("Testing syllable endings that commonly get truncated:")
        print("-" * 70)

        success_count = 0
        total_tests = len(syllable_test_cases)

        for i, (test_text, syllable_description) in enumerate(syllable_test_cases, 1):
            print(f"\n🎵 Test {i}/{total_tests}: '{test_text}'")
            print(f"   Target: {syllable_description}")
            print(f"   Length: {len(test_text)} characters")

            try:
                # Test the synthesis with enhanced syllable completion
                start_time = time.time()
                success = voice_engine.synthesize_and_play(test_text, background=False)
                duration = time.time() - start_time

                if success:
                    print(f"   Result: ✅ Success ({duration:.2f}s)")
                    print(f"   Expected: Final syllable should be preserved with 150-300ms completion")
                    success_count += 1
                else:
                    print(f"   Result: ❌ Synthesis failed")

            except Exception as e:
                print(f"   Result: ❌ Exception: {e}")

            # Brief pause between tests to allow listening
            time.sleep(1.0)

        print("\n" + "=" * 70)
        print("🎯 Enhanced Syllable Completion Test Complete!")
        print(f"   Success Rate: {success_count}/{total_tests} ({success_count/total_tests*100:.1f}%)")
        print()
        print("Key enhancements made:")
        print("✅ Completion duration increased: 150-300ms (was 50-120ms)")
        print("✅ More sensitive truncation detection: 0.008 threshold (was 0.015)")
        print("✅ Larger analysis window: 1200 samples (was 800)")
        print("✅ Extended ending sample: 120 samples (was 50)")
        print("✅ Slower decay rate: 2.0 (was 3.0) for better preservation")
        print()
        print("🎧 Listen carefully - final syllables should now be complete!")
        print("   If you still hear cutoffs, the TTS engine itself may need adjustment.")

    except Exception as e:
        print(f"❌ Syllable completion test failed: {e}")
        import traceback
        traceback.print_exc()

def test_completion_engine_directly():
    """Test the completion engine parameters directly"""
    print("\n🔧 Testing Enhanced Audio Completion Engine Parameters")
    print("-" * 50)

    try:
        from src.tts.effects.audio_completion_engine import AudioCompletionEngine
        from src.tts.controllers.kittentts_manager import KittenManager
        import numpy as np

        if not KittenManager.is_available():
            print("❌ KittenTTS not available")
            return

        if not KittenManager.load_model():
            print("❌ Failed to load KittenTTS")
            return

        completion_engine = AudioCompletionEngine(sample_rate=24000)

        print(f"🔧 Enhanced completion parameters:")
        print(f"   Truncation threshold: {completion_engine.truncation_threshold}")
        print(f"   Analysis window: {completion_engine.analysis_window}")
        print(f"   Force completion: {completion_engine.force_completion}")

        # Test with a phrase that typically loses its ending
        test_phrase = "Configuration successful"
        print(f"\n🎵 Testing: '{test_phrase}' (ends in 'SUC-CESS-FUL')")

        raw_audio = KittenManager.generate(test_phrase)
        if raw_audio is None:
            print("❌ Failed to generate raw audio")
            return

        print(f"✅ Raw audio generated: {raw_audio.shape} samples ({len(raw_audio)/24000:.3f}s)")

        # Test enhanced completion
        is_truncated, confidence = completion_engine.detect_truncation(raw_audio)
        print(f"🔍 Truncation detected: {is_truncated} (confidence: {confidence:.3f})")

        completed_audio, fix_info = completion_engine.fix_audio(raw_audio, "syllable_test")

        print(f"🔧 Enhanced completion applied:")
        print(f"   Applied fix: {fix_info['applied_fix']}")
        print(f"   Original length: {len(raw_audio)} samples ({len(raw_audio)/24000:.3f}s)")
        print(f"   Completed length: {len(completed_audio)} samples ({len(completed_audio)/24000:.3f}s)")

        if fix_info['applied_fix']:
            print(f"   Added duration: {fix_info['added_ms']:.1f}ms")
            if fix_info['added_ms'] >= 150:
                print(f"   ✅ Sufficient completion for syllable preservation (≥150ms)")
            else:
                print(f"   ⚠️ May be insufficient for full syllable (expected ≥150ms)")

    except Exception as e:
        print(f"❌ Direct completion test failed: {e}")

if __name__ == "__main__":
    test_syllable_ending_phrases()
    test_completion_engine_directly()