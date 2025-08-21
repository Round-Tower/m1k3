#!/usr/bin/env python3
"""
Debug Speech Cutoff Issue
Analyze exactly where and why speech is getting cut off
"""

import sys
import time
import numpy as np
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def debug_cutoff_analysis():
    """Debug the speech cutoff issue step by step"""
    print("\n" + "="*80)
    print("🔍 DEBUG: SPEECH CUTOFF ANALYSIS")
    print("="*80)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        from kittentts_manager import KittenTTSManager
        
        print("🚀 Loading components for analysis...")
        engine = create_voice_engine()
        kitten_mgr = KittenTTSManager()
        
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return 1
            
        print("✅ Components loaded!")
        
        # Test phrase that commonly gets cut off
        test_phrase = "This is a test sentence that should play completely to the end."
        print(f"\n📝 Test phrase: \"{test_phrase}\"")
        print(f"   Length: {len(test_phrase)} characters")
        
        print(f"\n🔍 STEP 1: TEXT CHUNKING ANALYSIS")
        print("-" * 60)
        from text_processors import smart_text_chunking
        chunks = smart_text_chunking(test_phrase, chunk_size=300)
        print(f"   Chunks created: {len(chunks)}")
        for i, chunk in enumerate(chunks):
            print(f"   Chunk {i+1}: \"{chunk}\" ({len(chunk)} chars)")
        
        print(f"\n🔍 STEP 2: RAW TTS GENERATION ANALYSIS")  
        print("-" * 60)
        raw_audio_chunks = []
        for i, chunk in enumerate(chunks):
            print(f"   Generating audio for chunk {i+1}...")
            raw_audio = kitten_mgr.generate(chunk, voice="expr-voice-2-m")
            if raw_audio is not None:
                raw_audio_chunks.append(raw_audio)
                print(f"   ✅ Chunk {i+1}: {len(raw_audio)} samples ({len(raw_audio)/24000:.2f}s)")
                # Check if there's any trailing silence or abrupt ending
                tail_samples = raw_audio[-1000:]  # Last 1000 samples
                avg_tail_amplitude = np.mean(np.abs(tail_samples))
                print(f"      Tail amplitude: {avg_tail_amplitude:.6f}")
            else:
                print(f"   ❌ Chunk {i+1}: Generation failed")
        
        if not raw_audio_chunks:
            print("❌ No audio generated, cannot continue analysis")
            return 1
        
        print(f"\n🔍 STEP 3: AUDIO COMBINATION ANALYSIS")
        print("-" * 60)
        
        # Combine chunks (same as unified_voice_engine.py)
        silence = np.zeros(int(0.03 * 24000), dtype=np.float32)  # 30ms silence
        if len(raw_audio_chunks) > 1:
            combined_audio = np.concatenate([np.concatenate([chunk, silence]) for chunk in raw_audio_chunks[:-1]] + [raw_audio_chunks[-1]])
        else:
            combined_audio = raw_audio_chunks[0]
            
        print(f"   Combined audio: {len(combined_audio)} samples ({len(combined_audio)/24000:.2f}s)")
        tail_samples = combined_audio[-1000:]
        avg_tail_amplitude = np.mean(np.abs(tail_samples))
        print(f"   Combined tail amplitude: {avg_tail_amplitude:.6f}")
        
        print(f"\n🔍 STEP 4: PADDING APPLICATION ANALYSIS")
        print("-" * 60)
        
        # Pre-effects padding
        pre_padding = np.zeros(int(0.1 * 24000), dtype=np.float32)  # 100ms
        padded_audio = np.concatenate([pre_padding, combined_audio])
        print(f"   After pre-padding: {len(padded_audio)} samples ({len(padded_audio)/24000:.2f}s)")
        
        # Fade-out
        fade_length = int(0.15 * 24000)  # 150ms
        if len(padded_audio) > fade_length:
            fade_curve = np.linspace(1.0, 0.0, fade_length)
            padded_audio[-fade_length:] *= fade_curve
        print(f"   After fade-out: {len(padded_audio)} samples")
        
        # End padding
        end_padding = np.zeros(int(0.5 * 24000), dtype=np.float32)  # 500ms
        padded_audio = np.concatenate([padded_audio, end_padding])
        print(f"   After end padding: {len(padded_audio)} samples ({len(padded_audio)/24000:.2f}s)")
        
        print(f"\n🔍 STEP 5: EFFECTS PIPELINE ANALYSIS")
        print("-" * 60)
        
        processed_audio = padded_audio.copy()
        print(f"   Before effects: {len(processed_audio)} samples")
        
        for i, effect in enumerate(engine.engine.effects_pipeline):
            before_len = len(processed_audio)
            processed_audio = effect.apply(processed_audio, 24000)
            after_len = len(processed_audio)
            print(f"   Effect {i+1} ({effect.get_name()}): {before_len} → {after_len} samples")
            
            if after_len != before_len:
                print(f"   ⚠️  Effect {effect.get_name()} changed audio length!")
                
        # Post-effects padding
        final_padding = np.zeros(int(0.2 * 24000), dtype=np.float32)  # 200ms
        processed_audio = np.concatenate([processed_audio, final_padding])
        print(f"   After final padding: {len(processed_audio)} samples ({len(processed_audio)/24000:.2f}s)")
        
        print(f"\n🔍 STEP 6: PLAYBACK ANALYSIS")
        print("-" * 60)
        
        # Check final audio characteristics
        total_duration = len(processed_audio) / 24000
        print(f"   Total audio duration: {total_duration:.2f}s")
        
        # Analyze the ending
        ending_samples = processed_audio[-5000:]  # Last ~200ms
        non_zero_ending = ending_samples[np.abs(ending_samples) > 0.001]
        print(f"   Non-zero samples in ending: {len(non_zero_ending)}")
        print(f"   Last significant sample index: {len(processed_audio) - len(ending_samples) + len(non_zero_ending)}")
        
        actual_content_end = len(processed_audio)
        for i in range(len(processed_audio)-1, -1, -1):
            if abs(processed_audio[i]) > 0.001:  # Found last significant sample
                actual_content_end = i + 1
                break
                
        actual_content_duration = actual_content_end / 24000
        padding_duration = total_duration - actual_content_duration
        
        print(f"   Actual content ends at: {actual_content_duration:.2f}s")
        print(f"   Total padding duration: {padding_duration:.2f}s")
        
        print(f"\n🔍 STEP 7: HARDWARE TIMING ANALYSIS")
        print("-" * 60)
        
        try:
            import sounddevice as sd
            device_info = sd.query_devices(sd.default.device['output'])
            latency = device_info.get('default_high_output_latency', 0.1)
            print(f"   Hardware latency: {latency:.3f}s")
            print(f"   Buffer size: {device_info.get('max_output_channels', 'unknown')}")
            print(f"   Sample rate: {device_info.get('default_samplerate', 'unknown')}")
        except Exception as e:
            print(f"   ⚠️  Could not get hardware info: {e}")
        
        print(f"\n🔍 STEP 8: PLAYBACK TEST")
        print("-" * 60)
        print("   Playing processed audio with debug timing...")
        
        start_time = time.time()
        try:
            import sounddevice as sd
            sd.play(processed_audio, samplerate=24000)
            sd.wait()
            actual_playback_time = time.time() - start_time
            print(f"   SD playback time: {actual_playback_time:.2f}s")
            
            # Additional hardware delay
            time.sleep(0.2)  # 200ms extra
            total_time = time.time() - start_time
            print(f"   Total elapsed time: {total_time:.2f}s")
            
        except Exception as e:
            print(f"   ❌ Playback failed: {e}")
        
        print(f"\n📊 CUTOFF ANALYSIS SUMMARY:")
        print("="*80)
        print(f"Expected audio duration: {total_duration:.2f}s")
        print(f"Actual content duration: {actual_content_duration:.2f}s") 
        print(f"Padding provided: {padding_duration:.2f}s")
        print(f"Hardware latency: {latency:.3f}s")
        
        if padding_duration < latency + 0.1:
            print("⚠️  POTENTIAL ISSUE: Insufficient padding for hardware latency!")
        else:
            print("✅ Padding should be sufficient for hardware")
            
        return 0
        
    except Exception as e:
        print(f"❌ Error during debug analysis: {e}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    sys.exit(debug_cutoff_analysis())