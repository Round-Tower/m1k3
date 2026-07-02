#!/usr/bin/env python3
"""
Piper vs KittenTTS Analysis
Comprehensive comparison of timing, emotional response, and formant characteristics
"""

import sys
import time
import statistics
sys.path.append('.')

from src.engines.voice.intelligent_tts_engine import intelligent_tts_engine

def test_header():
    """Display test header"""
    print("🔬 PIPER vs KITTENTTS ANALYSIS")
    print("=" * 40)
    print("Testing: Timing • Emotion • Formants")
    print("=" * 40)
    print()

def timing_analysis():
    """Comprehensive timing analysis"""
    print("⏱️  TIMING PERFORMANCE ANALYSIS")
    print("-" * 35)
    print()

    # Test phrases of different lengths
    timing_tests = [
        ("Short", "Hello there"),
        ("Medium", "I can help you with that question today"),
        ("Long", "The artificial intelligence system processes natural language through multiple neural network layers to generate coherent responses"),
        ("Complex", "When analyzing the intricate relationships between quantum mechanical principles and their applications in modern computational frameworks, we must consider the fundamental underlying mathematical structures")
    ]

    results = {"kitten": [], "piper": []}

    for test_name, text in timing_tests:
        print(f"📝 {test_name} Text ({len(text)} chars): \"{text[:40]}{'...' if len(text) > 40 else ''}\"")
        print()

        # Test KittenTTS
        try:
            print("🎯 Testing KittenTTS:")
            start_time = time.time()

            # Force KittenTTS selection
            kitten_engine = intelligent_tts_engine.engines['kitten']
            if not hasattr(kitten_engine, 'tts_model') or kitten_engine.tts_model is None:
                kitten_engine.load_model()

            audio_data = kitten_engine.generate(text)
            synthesis_time = time.time() - start_time

            if audio_data is not None:
                audio_length = len(audio_data) / kitten_engine.sample_rate
                rtf = synthesis_time / audio_length if audio_length > 0 else float('inf')
                results["kitten"].append((len(text), synthesis_time, audio_length, rtf))

                print(f"   Synthesis time: {synthesis_time:.2f}s")
                print(f"   Audio length: {audio_length:.2f}s")
                print(f"   RTF (Real-Time Factor): {rtf:.2f}x")
                print(f"   Status: ✅ Success")
            else:
                print(f"   Status: ❌ Failed")

        except Exception as e:
            print(f"   Status: ❌ Error: {e}")

        print()

        # Test Piper
        try:
            print("⚡ Testing Piper:")

            # Check if Piper is available
            if 'piper' in intelligent_tts_engine.engines:
                start_time = time.time()

                piper_engine = intelligent_tts_engine.engines['piper']
                if hasattr(piper_engine, 'load_model') and not getattr(piper_engine, 'tts_model', None):
                    piper_engine.load_model()

                audio_data = piper_engine.generate(text)
                synthesis_time = time.time() - start_time

                if audio_data is not None:
                    # Estimate audio length (Piper typically uses 22050 Hz)
                    sample_rate = getattr(piper_engine, 'sample_rate', 22050)
                    audio_length = len(audio_data) / sample_rate
                    rtf = synthesis_time / audio_length if audio_length > 0 else float('inf')
                    results["piper"].append((len(text), synthesis_time, audio_length, rtf))

                    print(f"   Synthesis time: {synthesis_time:.2f}s")
                    print(f"   Audio length: {audio_length:.2f}s")
                    print(f"   RTF (Real-Time Factor): {rtf:.2f}x")
                    print(f"   Status: ✅ Success")
                else:
                    print(f"   Status: ❌ Failed - No audio generated")
            else:
                print(f"   Status: ❌ Piper not available")

        except Exception as e:
            print(f"   Status: ❌ Error: {e}")

        print()
        print("-" * 50)
        print()

    # Timing summary
    print("📊 TIMING SUMMARY:")
    print()

    if results["kitten"]:
        kitten_rtfs = [result[3] for result in results["kitten"]]
        kitten_avg_rtf = statistics.mean(kitten_rtfs)
        print(f"🎯 KittenTTS Average RTF: {kitten_avg_rtf:.2f}x")
        print(f"   Range: {min(kitten_rtfs):.2f}x - {max(kitten_rtfs):.2f}x")

    if results["piper"]:
        piper_rtfs = [result[3] for result in results["piper"]]
        piper_avg_rtf = statistics.mean(piper_rtfs)
        print(f"⚡ Piper Average RTF: {piper_avg_rtf:.2f}x")
        print(f"   Range: {min(piper_rtfs):.2f}x - {max(piper_rtfs):.2f}x")

    if results["kitten"] and results["piper"]:
        speed_ratio = kitten_avg_rtf / piper_avg_rtf
        if speed_ratio > 1:
            print(f"🏆 Piper is {speed_ratio:.1f}x faster than KittenTTS")
        else:
            print(f"🏆 KittenTTS is {1/speed_ratio:.1f}x faster than Piper")

    print()
    return results

def emotional_analysis():
    """Test emotional expressiveness"""
    print("🎭 EMOTIONAL EXPRESSION ANALYSIS")
    print("-" * 36)
    print()

    # Emotional test phrases
    emotional_tests = [
        ("Neutral", "The weather is nice today"),
        ("Happy", "I'm so excited to help you with this wonderful project!"),
        ("Sad", "I'm sorry to hear that you're having difficulties"),
        ("Urgent", "Warning! This requires immediate attention!"),
        ("Question", "Can you please help me understand this better?"),
        ("Emphatic", "This is absolutely the most important thing to remember!"),
        ("Calm", "Let's take a moment to think through this carefully and methodically")
    ]

    print("Testing emotional expressiveness across different sentence types:")
    print()

    for emotion, text in emotional_tests:
        print(f"😊 {emotion} Expression:")
        print(f"   Text: \"{text}\"")
        print()

        # Test with KittenTTS
        print("   🎯 KittenTTS rendering:")
        try:
            start_time = time.time()
            success = False

            # Use the intelligent engine but force KittenTTS
            engine_name, engine = intelligent_tts_engine.select_best_engine(text)
            if engine_name == 'kitten' or 'kitten' in intelligent_tts_engine.engines:
                success = intelligent_tts_engine._synthesize_with_completion(
                    intelligent_tts_engine.engines['kitten'], text
                )

            duration = time.time() - start_time
            print(f"   Status: {'✅ Played' if success else '❌ Failed'} ({duration:.1f}s)")

        except Exception as e:
            print(f"   Status: ❌ Error: {e}")

        print()

        # Test with Piper
        print("   ⚡ Piper rendering:")
        try:
            if 'piper' in intelligent_tts_engine.engines:
                start_time = time.time()
                piper_engine = intelligent_tts_engine.engines['piper']

                audio_data = piper_engine.generate(text)
                if audio_data is not None:
                    success = intelligent_tts_engine._play_audio(
                        audio_data, getattr(piper_engine, 'sample_rate', 22050)
                    )
                else:
                    success = False

                duration = time.time() - start_time
                print(f"   Status: {'✅ Played' if success else '❌ Failed'} ({duration:.1f}s)")
            else:
                print(f"   Status: ❌ Piper not available")

        except Exception as e:
            print(f"   Status: ❌ Error: {e}")

        print()
        print("-" * 50)
        print()

def formant_analysis():
    """Analyze audio characteristics and formant-like properties"""
    print("🌊 AUDIO CHARACTERISTICS ANALYSIS")
    print("-" * 37)
    print()

    # Test phrases designed to reveal formant differences
    formant_tests = [
        ("Vowel Rich", "The eagle soars over the ocean waves"),
        ("Consonant Heavy", "The crisp crackling of the crackers"),
        ("Mixed Dynamics", "Whisper softly, then shout loudly!"),
        ("Technical Terms", "Frequency modulation and amplitude synthesis"),
        ("Natural Speech", "Hello, how are you doing today my friend?")
    ]

    print("Testing audio characteristics that reveal formant and spectral differences:")
    print()

    results = {"kitten": {}, "piper": {}}

    for test_name, text in formant_tests:
        print(f"🎵 {test_name}:")
        print(f"   Text: \"{text}\"")
        print()

        # KittenTTS analysis
        print("   🎯 KittenTTS characteristics:")
        try:
            kitten_engine = intelligent_tts_engine.engines['kitten']
            if not hasattr(kitten_engine, 'tts_model') or kitten_engine.tts_model is None:
                kitten_engine.load_model()

            start_time = time.time()
            audio_data = kitten_engine.generate(text)

            if audio_data is not None:
                synthesis_time = time.time() - start_time

                # Basic audio analysis
                import numpy as np
                audio_array = np.array(audio_data, dtype=np.float32)

                # Audio metrics
                rms_level = np.sqrt(np.mean(audio_array**2))
                peak_level = np.max(np.abs(audio_array))
                dynamic_range = peak_level / (rms_level + 1e-10)
                zero_crossings = np.sum(np.diff(np.signbit(audio_array)))

                print(f"      Synthesis time: {synthesis_time:.2f}s")
                print(f"      Audio length: {len(audio_array)} samples")
                print(f"      RMS level: {rms_level:.4f}")
                print(f"      Peak level: {peak_level:.4f}")
                print(f"      Dynamic range: {dynamic_range:.2f}")
                print(f"      Zero crossings: {zero_crossings}")
                print(f"      Estimated richness: {'High' if zero_crossings > 1000 else 'Medium' if zero_crossings > 500 else 'Low'}")

                results["kitten"][test_name] = {
                    "rms": rms_level,
                    "peak": peak_level,
                    "dynamic_range": dynamic_range,
                    "zero_crossings": zero_crossings
                }

            else:
                print("      Status: ❌ No audio generated")

        except Exception as e:
            print(f"      Status: ❌ Error: {e}")

        print()

        # Piper analysis
        print("   ⚡ Piper characteristics:")
        try:
            if 'piper' in intelligent_tts_engine.engines:
                piper_engine = intelligent_tts_engine.engines['piper']

                start_time = time.time()
                audio_data = piper_engine.generate(text)

                if audio_data is not None:
                    synthesis_time = time.time() - start_time

                    # Basic audio analysis
                    import numpy as np
                    audio_array = np.array(audio_data, dtype=np.float32)

                    # Audio metrics
                    rms_level = np.sqrt(np.mean(audio_array**2))
                    peak_level = np.max(np.abs(audio_array))
                    dynamic_range = peak_level / (rms_level + 1e-10)
                    zero_crossings = np.sum(np.diff(np.signbit(audio_array)))

                    print(f"      Synthesis time: {synthesis_time:.2f}s")
                    print(f"      Audio length: {len(audio_array)} samples")
                    print(f"      RMS level: {rms_level:.4f}")
                    print(f"      Peak level: {peak_level:.4f}")
                    print(f"      Dynamic range: {dynamic_range:.2f}")
                    print(f"      Zero crossings: {zero_crossings}")
                    print(f"      Estimated richness: {'High' if zero_crossings > 1000 else 'Medium' if zero_crossings > 500 else 'Low'}")

                    results["piper"][test_name] = {
                        "rms": rms_level,
                        "peak": peak_level,
                        "dynamic_range": dynamic_range,
                        "zero_crossings": zero_crossings
                    }

                else:
                    print("      Status: ❌ No audio generated")
            else:
                print("      Status: ❌ Piper not available")

        except Exception as e:
            print(f"      Status: ❌ Error: {e}")

        print()
        print("-" * 50)
        print()

    return results

def comparative_summary(timing_results, formant_results):
    """Generate comparative summary"""
    print("📋 COMPARATIVE SUMMARY")
    print("-" * 25)
    print()

    print("🏁 PERFORMANCE COMPARISON:")
    print()

    # Timing comparison
    if timing_results["kitten"] and timing_results["piper"]:
        kitten_avg_rtf = statistics.mean([r[3] for r in timing_results["kitten"]])
        piper_avg_rtf = statistics.mean([r[3] for r in timing_results["piper"]])

        print("⏱️  SPEED:")
        print(f"   KittenTTS: {kitten_avg_rtf:.2f}x RTF")
        print(f"   Piper: {piper_avg_rtf:.2f}x RTF")

        if piper_avg_rtf < kitten_avg_rtf:
            speed_advantage = kitten_avg_rtf / piper_avg_rtf
            print(f"   🏆 Winner: Piper ({speed_advantage:.1f}x faster)")
        else:
            speed_advantage = piper_avg_rtf / kitten_avg_rtf
            print(f"   🏆 Winner: KittenTTS ({speed_advantage:.1f}x faster)")

    print()

    # Audio quality comparison
    if formant_results["kitten"] and formant_results["piper"]:
        print("🎵 AUDIO CHARACTERISTICS:")

        # Average dynamic range
        kitten_dynamic = statistics.mean([r["dynamic_range"] for r in formant_results["kitten"].values()])
        piper_dynamic = statistics.mean([r["dynamic_range"] for r in formant_results["piper"].values()])

        print(f"   KittenTTS Dynamic Range: {kitten_dynamic:.2f}")
        print(f"   Piper Dynamic Range: {piper_dynamic:.2f}")

        # Average spectral richness (zero crossings)
        kitten_richness = statistics.mean([r["zero_crossings"] for r in formant_results["kitten"].values()])
        piper_richness = statistics.mean([r["zero_crossings"] for r in formant_results["piper"].values()])

        print(f"   KittenTTS Spectral Richness: {kitten_richness:.0f} zero crossings")
        print(f"   Piper Spectral Richness: {piper_richness:.0f} zero crossings")

    print()

    print("🎯 RECOMMENDATIONS:")
    print()
    print("📊 Use Cases:")
    print("   ⚡ Piper: Fast responses, real-time interaction")
    print("   🎯 KittenTTS: Conversational quality, natural flow")
    print("   🛡️ SimpleVoice: Emergency fallback, basic needs")

def main():
    """Run the complete Piper vs KittenTTS analysis"""
    import argparse

    parser = argparse.ArgumentParser(description="Piper vs KittenTTS Analysis")
    parser.add_argument("--timing-only", action="store_true", help="Run timing tests only")
    parser.add_argument("--emotion-only", action="store_true", help="Run emotion tests only")
    parser.add_argument("--formant-only", action="store_true", help="Run formant analysis only")
    args = parser.parse_args()

    test_header()

    # Initialize system
    if not intelligent_tts_engine.engines:
        print("❌ No TTS engines available!")
        return

    print(f"✅ TTS system initialized")
    print(f"   Available engines: {list(intelligent_tts_engine.engines.keys())}")
    print()

    timing_results = {}
    formant_results = {}

    # Run selected tests
    if not any([args.timing_only, args.emotion_only, args.formant_only]):
        # Run all tests
        timing_results = timing_analysis()
        emotional_analysis()
        formant_results = formant_analysis()
        comparative_summary(timing_results, formant_results)
    else:
        if args.timing_only:
            timing_results = timing_analysis()
        if args.emotion_only:
            emotional_analysis()
        if args.formant_only:
            formant_results = formant_analysis()

    print("🔬 Analysis Complete!")

if __name__ == "__main__":
    main()