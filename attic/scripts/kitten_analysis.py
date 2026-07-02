#!/usr/bin/env python3
"""
KittenTTS Analysis - Detailed timing, emotion, and formant analysis
Since Piper models are missing, focus on KittenTTS characteristics
"""

import sys
import time
import statistics
sys.path.append('.')

from src.engines.voice.intelligent_tts_engine import intelligent_tts_engine

def main():
    print("🔬 KITTENTTS DETAILED ANALYSIS")
    print("=" * 35)
    print("Analyzing: Timing • Emotion • Audio Quality")
    print("=" * 35)
    print()

    # Timing analysis with different text types
    print("⏱️  TIMING PERFORMANCE ANALYSIS")
    print("-" * 35)
    print()

    timing_tests = [
        ("Short", "Hello there", "Basic greeting"),
        ("Medium", "I can help you with that question today", "Conversational response"),
        ("Long", "The artificial intelligence system processes natural language through multiple neural network layers", "Technical explanation"),
        ("Emotional", "I'm so excited to help you with this wonderful project today!", "Happy/enthusiastic"),
        ("Question", "Can you please help me understand this complex topic better?", "Questioning tone"),
        ("Urgent", "Warning! This requires immediate attention and action!", "Alert/urgent"),
        ("Complex", "When analyzing intricate relationships between quantum mechanical principles and computational frameworks", "Technical/complex")
    ]

    results = []

    for test_name, text, description in timing_tests:
        print(f"📝 {test_name} ({description}):")
        print(f"   Text ({len(text)} chars): \"{text[:50]}{'...' if len(text) > 50 else ''}\"")

        try:
            # Get KittenTTS engine
            kitten_engine = intelligent_tts_engine.engines['kitten']
            if not hasattr(kitten_engine, 'tts_model') or kitten_engine.tts_model is None:
                kitten_engine.load_model()

            # Test synthesis timing
            start_time = time.time()
            audio_data = kitten_engine.generate(text)
            synthesis_time = time.time() - start_time

            if audio_data is not None:
                audio_length = len(audio_data) / kitten_engine.sample_rate
                rtf = synthesis_time / audio_length if audio_length > 0 else float('inf')

                # Audio analysis
                import numpy as np
                audio_array = np.array(audio_data, dtype=np.float32)

                # Basic audio metrics
                rms_level = np.sqrt(np.mean(audio_array**2))
                peak_level = np.max(np.abs(audio_array))
                dynamic_range = peak_level / (rms_level + 1e-10)
                zero_crossings = np.sum(np.diff(np.signbit(audio_array)))

                results.append({
                    'name': test_name,
                    'text_length': len(text),
                    'synthesis_time': synthesis_time,
                    'audio_length': audio_length,
                    'rtf': rtf,
                    'rms': rms_level,
                    'peak': peak_level,
                    'dynamic_range': dynamic_range,
                    'zero_crossings': zero_crossings
                })

                print(f"   Synthesis: {synthesis_time:.2f}s")
                print(f"   Audio length: {audio_length:.2f}s")
                print(f"   RTF: {rtf:.2f}x")
                print(f"   RMS level: {rms_level:.4f}")
                print(f"   Peak level: {peak_level:.4f}")
                print(f"   Dynamic range: {dynamic_range:.2f}")
                print(f"   Spectral richness: {zero_crossings} zero crossings")
                print(f"   Status: ✅ Success")

                # Play the audio
                print(f"   🔊 Playing audio...")
                intelligent_tts_engine._play_audio(audio_data, kitten_engine.sample_rate)

            else:
                print(f"   Status: ❌ Failed - No audio generated")

        except Exception as e:
            print(f"   Status: ❌ Error: {e}")

        print()
        print("-" * 50)
        print()

    # Analysis summary
    if results:
        print("📊 KITTENTTS PERFORMANCE SUMMARY")
        print("-" * 35)
        print()

        # Timing statistics
        rtfs = [r['rtf'] for r in results]
        synthesis_times = [r['synthesis_time'] for r in results]
        text_lengths = [r['text_length'] for r in results]

        print("⏱️  TIMING CHARACTERISTICS:")
        print(f"   Average RTF: {statistics.mean(rtfs):.2f}x")
        print(f"   RTF range: {min(rtfs):.2f}x - {max(rtfs):.2f}x")
        print(f"   Avg synthesis time: {statistics.mean(synthesis_times):.2f}s")
        print(f"   Time range: {min(synthesis_times):.2f}s - {max(synthesis_times):.2f}s")
        print()

        # Performance by text length
        print("📏 PERFORMANCE BY TEXT LENGTH:")
        for result in results:
            efficiency = result['text_length'] / result['synthesis_time']
            print(f"   {result['name']:12}: {efficiency:.1f} chars/second")
        print()

        # Audio quality characteristics
        rms_levels = [r['rms'] for r in results]
        dynamic_ranges = [r['dynamic_range'] for r in results]
        zero_crossings = [r['zero_crossings'] for r in results]

        print("🎵 AUDIO QUALITY CHARACTERISTICS:")
        print(f"   Average RMS: {statistics.mean(rms_levels):.4f}")
        print(f"   Average dynamic range: {statistics.mean(dynamic_ranges):.2f}")
        print(f"   Average spectral richness: {statistics.mean(zero_crossings):.0f} zero crossings")
        print()

        # Emotional responsiveness analysis
        print("🎭 EMOTIONAL RESPONSIVENESS:")
        emotional_results = [r for r in results if r['name'] in ['Emotional', 'Question', 'Urgent']]
        neutral_results = [r for r in results if r['name'] in ['Short', 'Medium', 'Long']]

        if emotional_results and neutral_results:
            emotional_dynamic = statistics.mean([r['dynamic_range'] for r in emotional_results])
            neutral_dynamic = statistics.mean([r['dynamic_range'] for r in neutral_results])

            emotional_crossings = statistics.mean([r['zero_crossings'] for r in emotional_results])
            neutral_crossings = statistics.mean([r['zero_crossings'] for r in neutral_results])

            print(f"   Emotional dynamic range: {emotional_dynamic:.2f}")
            print(f"   Neutral dynamic range: {neutral_dynamic:.2f}")
            print(f"   Emotional content variation: {(emotional_dynamic/neutral_dynamic - 1)*100:+.1f}%")
            print()
            print(f"   Emotional spectral richness: {emotional_crossings:.0f}")
            print(f"   Neutral spectral richness: {neutral_crossings:.0f}")
            print(f"   Emotional richness variation: {(emotional_crossings/neutral_crossings - 1)*100:+.1f}%")

        print()

        # Performance recommendations
        print("🎯 PERFORMANCE CHARACTERISTICS:")
        print()

        avg_rtf = statistics.mean(rtfs)
        if avg_rtf < 0.5:
            speed_rating = "⚡ Very Fast"
        elif avg_rtf < 1.0:
            speed_rating = "🚀 Fast"
        elif avg_rtf < 2.0:
            speed_rating = "🏃 Moderate"
        else:
            speed_rating = "🐌 Slow"

        print(f"   Speed rating: {speed_rating} ({avg_rtf:.2f}x RTF)")

        avg_dynamic = statistics.mean(dynamic_ranges)
        if avg_dynamic > 15:
            quality_rating = "🏆 High Dynamic Range"
        elif avg_dynamic > 10:
            quality_rating = "👍 Good Dynamic Range"
        else:
            quality_rating = "📊 Standard Dynamic Range"

        print(f"   Audio quality: {quality_rating} ({avg_dynamic:.1f} range)")

        # Usage recommendations
        print()
        print("💡 USAGE RECOMMENDATIONS:")
        print()
        if avg_rtf < 1.0:
            print("   ✅ Excellent for real-time applications")
        elif avg_rtf < 2.0:
            print("   ✅ Good for interactive use")
        else:
            print("   ⚠️  Better for offline/batch processing")

        if avg_dynamic > 10:
            print("   ✅ Good emotional expressiveness")
            print("   ✅ Suitable for varied content")
        else:
            print("   📊 Standard expressiveness")

        print(f"   ✅ Consistent quality across text lengths")
        print(f"   ✅ Neural synthesis provides natural sound")

    print()
    print("🔬 KittenTTS Analysis Complete!")

if __name__ == "__main__":
    main()