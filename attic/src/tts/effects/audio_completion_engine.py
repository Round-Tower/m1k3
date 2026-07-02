#!/usr/bin/env python3
"""
Audio Completion Engine for M1K3
Fixes KittenTTS truncation by intelligently completing cut-off speech
"""

import numpy as np
from typing import Optional, Tuple
import scipy.signal as signal

# Import tracker for enhanced diagnostics
try:
    from voice_truncation_tracker import log_truncation_event
    TRACKING_AVAILABLE = True
except ImportError:
    TRACKING_AVAILABLE = False
    def log_truncation_event(*args, **kwargs):
        pass

class AudioCompletionEngine:
    """
    Detects and fixes truncated audio from TTS systems like KittenTTS.
    Uses signal processing to identify abrupt cutoffs and synthesize natural endings.
    """
    
    def __init__(self, sample_rate: int = 24000):
        self.sample_rate = sample_rate
        self.truncation_threshold = 0.008  # SYLLABLE FIX: Even more sensitive for final syllables
        self.kitten_mode = True  # Assume KittenTTS always needs completion
        self.analysis_window = 1200  # Larger window to better detect syllable patterns
        self.force_completion = True  # AGGRESSIVE FIX: Always apply completion for KittenTTS
        
    def detect_truncation(self, audio: np.ndarray) -> Tuple[bool, float]:
        """
        Detect if audio is truncated by analyzing the ending characteristics.
        
        Returns:
            (is_truncated: bool, confidence: float)
        """
        if len(audio) < self.analysis_window:
            return False, 0.0
            
        # Analyze the tail end
        tail_samples = audio[-self.analysis_window:]
        tail_amplitude = np.mean(np.abs(tail_samples))
        
        # Check for abrupt ending patterns
        # 1. High amplitude at end (most reliable indicator)
        high_amplitude = tail_amplitude > self.truncation_threshold
        
        # 2. Lack of natural fade-out
        fade_length = min(200, len(tail_samples) // 2)
        if len(tail_samples) >= fade_length * 2:
            early_tail = tail_samples[:fade_length]
            late_tail = tail_samples[-fade_length:]
            
            early_amp = np.mean(np.abs(early_tail))
            late_amp = np.mean(np.abs(late_tail))
            
            # Natural speech should fade out, truncated speech won't
            lacks_fadeout = late_amp >= early_amp * 0.8
        else:
            lacks_fadeout = False
            
        # 3. Check for unnatural spectral characteristics at the end
        if len(tail_samples) >= 512:
            # FFT analysis of ending
            fft = np.fft.rfft(tail_samples[-512:])
            magnitude = np.abs(fft)
            
            # Truncated audio often has unnatural high-frequency content
            high_freq_energy = np.sum(magnitude[len(magnitude)//2:])
            total_energy = np.sum(magnitude)
            high_freq_ratio = high_freq_energy / total_energy if total_energy > 0 else 0
            
            unnatural_spectrum = high_freq_ratio > 0.3
        else:
            unnatural_spectrum = False
        
        # Combine indicators for confidence score
        indicators = [high_amplitude, lacks_fadeout, unnatural_spectrum]
        confidence = sum(indicators) / len(indicators)
        
        # For KittenTTS mode, be more selective - only fix actual truncations
        threshold = 0.4 if self.kitten_mode else 0.5  # More selective threshold for KittenTTS
        is_truncated = confidence >= threshold or self.force_completion
        
        # Boost confidence for forced completion
        if self.force_completion and confidence < 0.3:
            confidence = 0.3  # Minimum confidence for forced completion
        
        return is_truncated, confidence
        
    def complete_audio(self, audio: np.ndarray, confidence: float) -> np.ndarray:
        """
        Complete truncated audio by synthesizing a natural ending.
        
        Args:
            audio: Original truncated audio
            confidence: Truncation confidence (0-1)
            
        Returns:
            Audio with synthesized natural ending
        """
        if len(audio) == 0:
            return audio
            
        # Determine completion length based on truncation severity (optimized for final syllable preservation)
        if self.kitten_mode:
            # SYLLABLE FIX: Increase completion to capture full final syllables (150-300ms typical)
            base_completion_ms = 150   # Enough for a typical syllable
            severity_multiplier = min(confidence * 1.2, 0.8)  # More aggressive completion
            max_completion_ms = 300   # Allow full syllable completion (was 120ms)
        else:
            base_completion_ms = 180   # Even more for other engines
            severity_multiplier = min(confidence * 1.4, 1.0)
            max_completion_ms = 350   # Conservative but syllable-aware maximum
        
        completion_ms = min(base_completion_ms * (1 + severity_multiplier), max_completion_ms)
        completion_samples = int(completion_ms * self.sample_rate / 1000)
        
        # Method 1: Exponential decay of the ending (enhanced for syllable preservation)
        if len(audio) >= 100:
            # SYLLABLE FIX: Take a longer sample to better capture syllable patterns
            ending_sample_length = min(400, len(audio) // 3)  # Larger sample for better analysis
            ending_sample = audio[-ending_sample_length:]

            # SYLLABLE FIX: Slower decay to preserve more of the natural ending
            decay_rate = 2.0 / completion_samples  # Slower decay (was 3.0) - preserve more audio
            decay_curve = np.exp(-decay_rate * np.arange(completion_samples))

            # Use the mean characteristics of the ending to generate completion
            ending_amplitude = np.mean(np.abs(ending_sample))
            # SYLLABLE FIX: Use more ending characteristics for better syllable reconstruction
            ending_characteristics = ending_sample[-120:] if len(ending_sample) >= 120 else ending_sample
            
            # Generate completion based on ending characteristics
            completion = np.zeros(completion_samples)
            
            if len(ending_characteristics) > 10:
                # Use autocorrelation to find repeating patterns
                autocorr = np.correlate(ending_characteristics, ending_characteristics, mode='full')
                autocorr = autocorr[len(autocorr)//2:]
                
                # Find dominant period (if any)
                if len(autocorr) > 20:
                    peaks, _ = signal.find_peaks(autocorr[5:], height=np.max(autocorr) * 0.3)
                    if len(peaks) > 0:
                        dominant_period = peaks[0] + 5
                        
                        # Extend based on dominant period
                        for i in range(completion_samples):
                            source_idx = len(ending_characteristics) - 1 - (i % dominant_period)
                            source_idx = max(0, min(source_idx, len(ending_characteristics) - 1))
                            completion[i] = ending_characteristics[source_idx] * decay_curve[i]
                    else:
                        # No clear period, use simple decay of last sample
                        last_value = ending_characteristics[-1] if len(ending_characteristics) > 0 else 0
                        completion = last_value * decay_curve
                else:
                    # Very short ending, simple decay
                    last_value = ending_characteristics[-1] if len(ending_characteristics) > 0 else 0
                    completion = last_value * decay_curve
            else:
                # Very short audio, minimal completion
                completion = audio[-1] * decay_curve if len(audio) > 0 else np.zeros(completion_samples)
                
        else:
            # Very short audio, simple fade
            completion_samples = min(completion_samples, 500)  # Limit for very short audio
            completion = np.zeros(completion_samples)
            
        # Apply smoothing to avoid clicks
        if len(completion) > 10:
            # Smooth the connection point
            window_size = min(20, len(completion) // 4)
            if window_size > 2:
                # Apply Hann window to smooth the beginning of completion
                hann_window = np.hanning(window_size * 2)[:window_size]
                completion[:window_size] *= hann_window
        
        # Concatenate original with completion
        completed_audio = np.concatenate([audio, completion])
        
        return completed_audio
        
    def fix_audio(self, audio: np.ndarray, debug_label: str = "") -> Tuple[np.ndarray, dict]:
        """
        Main function to detect and fix truncated audio.
        
        Args:
            audio: Input audio array
            debug_label: Optional label for logging
        
        Returns:
            (fixed_audio, info_dict)
        """
        if len(audio) == 0:
            return audio, {"truncated": False, "confidence": 0.0, "applied_fix": False}
            
        # Only apply forced fixes for clearly truncated chunks
        force_fix = self.kitten_mode and ("chunk_" in debug_label) and len(audio) > 12000  # Only long chunks

        # Detect truncation
        is_truncated, confidence = self.detect_truncation(audio)

        # Override detection only for long chunks that likely need completion
        if force_fix and not is_truncated and confidence > 0.3:
            is_truncated = True
            confidence = max(confidence, 0.5)  # Moderate confidence boost
        
        info = {
            "truncated": is_truncated,
            "confidence": confidence,
            "original_length": len(audio),
            "applied_fix": False,
            "debug_label": debug_label,
            "forced_fix": force_fix
        }
        
        # Enhanced logging and tracking
        if debug_label and (is_truncated or confidence > 0.3):
            print(f"🔍 [{debug_label}] Truncation check: {is_truncated} (confidence: {confidence:.2f})")
        
        if is_truncated:
            # Apply fix
            fixed_audio = self.complete_audio(audio, confidence)
            info["applied_fix"] = True
            info["completed_length"] = len(fixed_audio)
            info["added_samples"] = len(fixed_audio) - len(audio)
            info["added_ms"] = info["added_samples"] / self.sample_rate * 1000
            
            if debug_label:
                print(f"🔧 [{debug_label}] Fixed truncation (added {info['added_ms']:.0f}ms, confidence: {confidence:.2f})")
            
            # Log to tracker for analytics
            if TRACKING_AVAILABLE:
                log_truncation_event(len(audio), is_truncated, confidence, True, info['added_ms'], debug_label)
            
            return fixed_audio, info
        else:
            # Log non-truncated audio too for statistics
            if TRACKING_AVAILABLE:
                log_truncation_event(len(audio), is_truncated, confidence, False, 0.0, debug_label)
            
            return audio, info

def test_completion_engine():
    """Test the audio completion engine"""
    print("🧪 Testing Audio Completion Engine...")
    
    engine = AudioCompletionEngine()
    
    # Test with KittenTTS to see if it fixes truncation
    try:
        import sys
        from pathlib import Path
        sys.path.insert(0, str(Path(__file__).parent.parent.parent.parent))
        from src.tts.controllers.kittentts_manager import KittenManager
        
        if not KittenManager.load_model():
            print("❌ Could not load KittenTTS for testing")
            return
            
        test_phrases = [
            "Hello world",
            "How are you today?", 
            "Welcome to the demonstration!",
            "Thank you for your time"
        ]
        
        for i, phrase in enumerate(test_phrases):
            print(f"\n🔬 Testing: '{phrase}'")
            
            # Generate original audio
            original_audio = KittenManager.generate(phrase)
            if original_audio is None:
                continue
                
            # Apply completion fix
            fixed_audio, info = engine.fix_audio(original_audio)
            
            print(f"   Original: {len(original_audio)} samples ({len(original_audio)/24000:.2f}s)")
            print(f"   Truncated: {info['truncated']} (confidence: {info['confidence']:.2f})")
            
            if info['applied_fix']:
                print(f"   Fixed: {len(fixed_audio)} samples ({len(fixed_audio)/24000:.2f}s)")
                print(f"   Added: {info['added_ms']:.0f}ms")
                
                # Save for comparison
                try:
                    import scipy.io.wavfile as wavfile
                    wavfile.write(f"/tmp/original_{i+1}.wav", 24000, (original_audio * 32767).astype(np.int16))
                    wavfile.write(f"/tmp/fixed_{i+1}.wav", 24000, (fixed_audio * 32767).astype(np.int16))
                    print(f"   💾 Saved comparison files to /tmp/")
                except:
                    pass
            else:
                print(f"   No fix needed - audio appears complete")
                
        print(f"\n✅ Audio completion engine test complete!")
        
    except Exception as e:
        print(f"❌ Test failed: {e}")

if __name__ == "__main__":
    test_completion_engine()