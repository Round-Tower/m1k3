#!/usr/bin/env python3
"""
Prosody Modification Engine for M1K3
Provides emotional voice characteristics through pitch, speed, and formant modifications
Designed for real-time TTS enhancement with mobile-compatible algorithms
"""

import numpy as np
from typing import Optional, Dict, Tuple
from enum import Enum

class Emotion(Enum):
    """Supported emotional states"""
    NEUTRAL = "neutral"
    HAPPY = "happy"
    SAD = "sad"
    ANGRY = "angry"
    EXCITED = "excited"
    CALM = "calm"
    FRIENDLY = "friendly"
    PROFESSIONAL = "professional"
    PLAYFUL = "playful"
    EMPATHETIC = "empathetic"


class ProsodyEngine:
    """
    Real-time prosody modification engine for emotional TTS

    Uses lightweight algorithms suitable for mobile deployment:
    - Phase vocoder for pitch shifting (PSOLA-inspired)
    - Simple time-stretching for pacing control
    - Spectral envelope modification for vocal quality
    """

    def __init__(self, sample_rate: int = 24000):
        self.sample_rate = sample_rate

        # Emotional prosody profiles (MINIMAL for realism - avoid any echo/artifacts)
        # Format: {emotion: (pitch_shift, speed_factor, energy_mult, formant_shift)}
        self.emotion_profiles = {
            Emotion.NEUTRAL: (0.0, 1.0, 1.0, 0.0),
            Emotion.HAPPY: (0.03, 1.02, 1.03, 0.0),  # Very subtle upbeat (minimal pitch change)
            Emotion.SAD: (-0.02, 0.96, 0.95, 0.0),  # Very subtle slower/softer
            Emotion.ANGRY: (0.02, 1.03, 1.08, 0.0),  # Mainly energy, minimal pitch
            Emotion.EXCITED: (0.04, 1.04, 1.05, 0.0),  # Subtle energy boost
            Emotion.CALM: (-0.01, 0.98, 0.97, 0.0),  # Minimal slowdown
            Emotion.FRIENDLY: (0.02, 1.01, 1.02, 0.0),  # Very subtle warmth
            Emotion.PROFESSIONAL: (0.0, 1.0, 1.0, 0.0),  # No change - already professional
            Emotion.PLAYFUL: (0.03, 1.03, 1.04, 0.0),  # Subtle variation
            Emotion.EMPATHETIC: (-0.02, 0.97, 0.96, 0.0)  # Gentle, minimal change
        }

    def apply_emotion(
        self,
        audio: np.ndarray,
        emotion: Emotion = Emotion.NEUTRAL,
        intensity: float = 1.0
    ) -> np.ndarray:
        """
        Apply emotional prosody modifications to audio

        Args:
            audio: Input audio (mono, float32, -1.0 to 1.0)
            emotion: Target emotion
            intensity: Emotion intensity (0.0 to 1.0, default 1.0)

        Returns:
            Modified audio with emotional characteristics
        """
        if audio is None or len(audio) == 0:
            return audio

        # Get prosody parameters for emotion
        pitch_shift, speed_factor, energy_mult, formant_shift = self.emotion_profiles[emotion]

        # Scale by intensity
        pitch_shift *= intensity
        speed_factor = 1.0 + (speed_factor - 1.0) * intensity
        energy_mult = 1.0 + (energy_mult - 1.0) * intensity
        formant_shift *= intensity

        # Apply modifications in order
        modified = audio.copy()

        # 1. Pitch shifting (if needed) - skip for very small changes to avoid artifacts
        if abs(pitch_shift) > 0.05:  # Only apply if shift is significant (>0.5 semitones)
            modified = self._pitch_shift(modified, pitch_shift)

        # 2. Time stretching/speed modification
        if abs(speed_factor - 1.0) > 0.01:
            modified = self._time_stretch(modified, speed_factor)

        # 3. Energy/amplitude modification
        if abs(energy_mult - 1.0) > 0.01:
            modified = self._modify_energy(modified, energy_mult)

        # 4. Formant shifting (spectral envelope)
        if abs(formant_shift) > 0.01:
            modified = self._formant_shift(modified, formant_shift)

        # Ensure output is normalized
        max_val = np.max(np.abs(modified))
        if max_val > 0.95:
            modified = modified * (0.95 / max_val)

        return modified.astype(np.float32)

    def _pitch_shift(self, audio: np.ndarray, semitones: float) -> np.ndarray:
        """
        Pitch shift using phase vocoder (simplified PSOLA approach)

        Args:
            audio: Input audio
            semitones: Pitch shift in semitones (positive = higher, negative = lower)

        Returns:
            Pitch-shifted audio
        """
        if len(audio) < 100:
            return audio

        # Convert semitones to pitch ratio
        pitch_ratio = 2.0 ** (semitones / 12.0)

        # For small shifts, use simple resampling (fast, good enough for real-time)
        if abs(semitones) < 0.5:
            # Very simple: resample and pad/trim to original length
            new_length = int(len(audio) / pitch_ratio)
            if new_length > 0:
                indices = np.linspace(0, len(audio) - 1, new_length)
                shifted = np.interp(indices, np.arange(len(audio)), audio)

                # Pad or trim to original length
                if len(shifted) < len(audio):
                    shifted = np.pad(shifted, (0, len(audio) - len(shifted)), mode='edge')
                elif len(shifted) > len(audio):
                    shifted = shifted[:len(audio)]

                return shifted

        # For larger shifts, use more sophisticated approach
        try:
            # Simple PSOLA-inspired algorithm
            # 1. Detect pitch periods (simplified: use autocorrelation)
            period = self._estimate_pitch_period(audio)

            # 2. Resample with overlap-add
            shifted = self._psola_shift(audio, pitch_ratio, period)

            return shifted

        except Exception as e:
            print(f"⚠️ Pitch shift fallback: {e}")
            # Fallback to simple resampling
            new_length = int(len(audio) / pitch_ratio)
            if new_length > 0:
                indices = np.linspace(0, len(audio) - 1, new_length)
                shifted = np.interp(indices, np.arange(len(audio)), audio)
                if len(shifted) != len(audio):
                    if len(shifted) < len(audio):
                        shifted = np.pad(shifted, (0, len(audio) - len(shifted)), mode='edge')
                    else:
                        shifted = shifted[:len(audio)]
                return shifted
            return audio

    def _estimate_pitch_period(self, audio: np.ndarray) -> int:
        """Estimate fundamental pitch period using autocorrelation"""
        # Autocorrelation method
        correlation = np.correlate(audio, audio, mode='full')
        correlation = correlation[len(correlation)//2:]

        # Find first peak after initial correlation
        min_period = int(self.sample_rate / 500)  # Max 500Hz
        max_period = int(self.sample_rate / 50)   # Min 50Hz

        if max_period >= len(correlation):
            max_period = len(correlation) - 1

        if min_period >= max_period:
            return 100  # Default fallback

        # Find maximum correlation in valid range
        peak_idx = min_period + np.argmax(correlation[min_period:max_period])

        return peak_idx if peak_idx > 0 else 100

    def _psola_shift(self, audio: np.ndarray, pitch_ratio: float, period: int) -> np.ndarray:
        """
        PSOLA-inspired pitch shifting with overlap-add

        Args:
            audio: Input audio
            pitch_ratio: Pitch multiplication factor
            period: Estimated pitch period in samples

        Returns:
            Pitch-shifted audio
        """
        # Window size based on pitch period
        window_size = period * 2
        hop_input = period
        hop_output = int(hop_input / pitch_ratio)

        if hop_output <= 0:
            hop_output = 1

        # Create Hann window
        window = np.hanning(window_size)

        # Output buffer with normalization tracker
        output_length = int(len(audio) * pitch_ratio)
        output = np.zeros(output_length + window_size)
        norm_buffer = np.zeros(output_length + window_size)  # Track overlap count

        # Overlap-add synthesis with proper normalization
        input_pos = 0
        output_pos = 0

        while input_pos + window_size < len(audio) and output_pos + window_size < len(output):
            # Extract and window input frame
            frame = audio[input_pos:input_pos + window_size] * window

            # Add to output
            output[output_pos:output_pos + window_size] += frame

            # Track overlap count for normalization
            norm_buffer[output_pos:output_pos + window_size] += window

            # Advance positions
            input_pos += hop_input
            output_pos += hop_output

        # Normalize by overlap count to prevent echo/buildup
        mask = norm_buffer > 0.01  # Avoid division by zero
        output[mask] = output[mask] / norm_buffer[mask]

        # Trim to approximately original length
        output = output[:len(audio)]

        # Final normalization
        max_val = np.max(np.abs(output))
        if max_val > 0:
            output = output / max_val * 0.95  # Slight headroom

        return output.astype(np.float32)

    def _time_stretch(self, audio: np.ndarray, factor: float) -> np.ndarray:
        """
        Time-stretch audio (change speed without affecting pitch)

        Args:
            audio: Input audio
            factor: Speed factor (>1.0 = faster, <1.0 = slower)

        Returns:
            Time-stretched audio at original length
        """
        if len(audio) < 100 or abs(factor - 1.0) < 0.01:
            return audio

        # Simple approach: resample then pitch-correct
        # For real-time, we just do simple linear interpolation
        new_length = int(len(audio) / factor)

        if new_length <= 0:
            return audio

        # Resample
        indices = np.linspace(0, len(audio) - 1, new_length)
        stretched = np.interp(indices, np.arange(len(audio)), audio)

        # Pad or trim to original length
        if len(stretched) < len(audio):
            stretched = np.pad(stretched, (0, len(audio) - len(stretched)), mode='edge')
        elif len(stretched) > len(audio):
            stretched = stretched[:len(audio)]

        return stretched.astype(np.float32)

    def _modify_energy(self, audio: np.ndarray, multiplier: float) -> np.ndarray:
        """
        Modify audio energy/amplitude with soft limiting

        Args:
            audio: Input audio
            multiplier: Energy multiplier

        Returns:
            Energy-modified audio
        """
        if len(audio) == 0:
            return audio

        # Apply energy modification with soft clipping
        modified = audio * multiplier

        # Soft clipping to prevent harsh distortion
        threshold = 0.9
        mask = np.abs(modified) > threshold
        if np.any(mask):
            # Soft clip values above threshold
            over = np.abs(modified[mask]) - threshold
            modified[mask] = np.sign(modified[mask]) * (threshold + np.tanh(over * 2.0) * 0.1)

        return modified.astype(np.float32)

    def _formant_shift(self, audio: np.ndarray, shift: float) -> np.ndarray:
        """
        Shift formants (spectral envelope) to change vocal quality

        Args:
            audio: Input audio
            shift: Formant shift factor (-0.1 to 0.1 typical range)
                  Positive = brighter, negative = darker

        Returns:
            Formant-shifted audio
        """
        if len(audio) < 100 or abs(shift) < 0.01:
            return audio

        try:
            # Simple spectral envelope modification using FFT
            # This is a lightweight approximation suitable for real-time

            # FFT
            spectrum = np.fft.rfft(audio)
            freqs = np.fft.rfftfreq(len(audio), 1.0 / self.sample_rate)

            # Shift spectral envelope
            # Positive shift = move formants up (brighter)
            # Negative shift = move formants down (darker)
            shift_factor = 1.0 + shift

            # Create shifted spectrum
            new_spectrum = np.zeros_like(spectrum)

            for i, freq in enumerate(freqs):
                if freq == 0:
                    new_spectrum[i] = spectrum[i]
                    continue

                # Map to new frequency
                new_freq = freq * shift_factor

                # Find closest index in original spectrum
                new_idx = int(new_freq / (self.sample_rate / 2) * len(freqs))

                if 0 <= new_idx < len(spectrum):
                    new_spectrum[i] = spectrum[new_idx]

            # IFFT back to time domain
            modified = np.fft.irfft(new_spectrum, len(audio))

            # Normalize
            max_val = np.max(np.abs(modified))
            if max_val > 0:
                modified = modified / max_val * np.max(np.abs(audio))

            return modified.astype(np.float32)

        except Exception as e:
            print(f"⚠️ Formant shift error: {e}")
            return audio

    def detect_emotion_from_text(self, text: str) -> Tuple[Emotion, float]:
        """
        Simple rule-based emotion detection from text

        Args:
            text: Input text

        Returns:
            (detected_emotion, confidence)
        """
        text_lower = text.lower()

        # Emotional keywords
        happy_words = ['happy', 'joy', 'excited', 'great', 'awesome', 'wonderful', 'love', '!', ':)']
        sad_words = ['sad', 'sorry', 'unfortunately', 'disappointed', 'miss', 'lost', ':(']
        angry_words = ['angry', 'mad', 'furious', 'hate', 'stupid', 'ridiculous', '!!']
        calm_words = ['calm', 'peaceful', 'relax', 'gentle', 'soft', 'quiet']
        excited_words = ['exciting', 'amazing', 'incredible', 'wow', '!!!']

        # Count matches
        scores = {
            Emotion.HAPPY: sum(word in text_lower for word in happy_words),
            Emotion.SAD: sum(word in text_lower for word in sad_words),
            Emotion.ANGRY: sum(word in text_lower for word in angry_words),
            Emotion.CALM: sum(word in text_lower for word in calm_words),
            Emotion.EXCITED: sum(word in text_lower for word in excited_words),
        }

        # Get highest scoring emotion
        max_score = max(scores.values())

        if max_score == 0:
            return Emotion.NEUTRAL, 0.5

        detected_emotion = max(scores, key=scores.get)
        confidence = min(max_score / 3.0, 1.0)  # Normalize confidence

        return detected_emotion, confidence


# Create singleton instance
prosody_engine = ProsodyEngine()


if __name__ == "__main__":
    print("🎭 Testing Prosody Engine")
    print("=" * 80)

    # Create test audio (simple sine wave)
    duration = 1.0
    sample_rate = 24000
    t = np.linspace(0, duration, int(sample_rate * duration))
    test_audio = np.sin(2 * np.pi * 200 * t).astype(np.float32)

    engine = ProsodyEngine(sample_rate)

    # Test each emotion
    emotions = [Emotion.HAPPY, Emotion.SAD, Emotion.ANGRY, Emotion.EXCITED, Emotion.CALM]

    for emotion in emotions:
        print(f"\n🎤 Testing {emotion.value} emotion...")
        modified = engine.apply_emotion(test_audio, emotion, intensity=1.0)
        print(f"   Input length: {len(test_audio)}, Output length: {len(modified)}")
        print(f"   Input RMS: {np.sqrt(np.mean(test_audio**2)):.4f}")
        print(f"   Output RMS: {np.sqrt(np.mean(modified**2)):.4f}")

    # Test emotion detection
    print("\n\n📝 Testing Emotion Detection:")
    test_texts = [
        "I'm so happy to help you today!",
        "I'm sorry to hear that, that's really sad.",
        "This is absolutely ridiculous! I'm furious!",
        "Let's take a moment to relax and breathe.",
        "WOW!!! This is amazing and exciting!!!"
    ]

    for text in test_texts:
        emotion, confidence = engine.detect_emotion_from_text(text)
        print(f"   '{text[:50]}...'")
        print(f"   → {emotion.value} (confidence: {confidence:.2f})")

    print("\n✅ Prosody Engine test complete!")
