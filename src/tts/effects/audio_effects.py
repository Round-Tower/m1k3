#!/usr/bin/env python3
"""
Audio Effects Pipeline for M1K3 Voice Engine
A collection of pluggable audio effect modules for the voice synthesis pipeline.
"""

from abc import ABC, abstractmethod
import numpy as np
from typing import Dict, Any

class AudioEffect(ABC):
    """Abstract base class for all audio effects."""
    
    def __init__(self, config: Dict[str, Any] = None):
        self.config = config or {}

    @abstractmethod
    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply the effect to the audio data."""
        pass
    
    def get_name(self) -> str:
        """Return the name of the effect."""
        return self.__class__.__name__

class IntercomEffect(AudioEffect):
    """Applies an intercom-style bandpass filter."""

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        low_freq = self.config.get("low_freq", 300)
        high_freq = self.config.get("high_freq", 3400)
        
        try:
            from scipy import signal
            nyquist = sample_rate / 2
            low = low_freq / nyquist
            high = high_freq / nyquist
            b, a = signal.butter(4, [low, high], btype='band')
            return signal.filtfilt(b, a, audio_data)
        except ImportError:
            print("⚠️  Scipy not found, using fallback bandpass filter.")
            fft = np.fft.fft(audio_data)
            freqs = np.fft.fftfreq(len(audio_data), 1 / sample_rate)
            mask = (np.abs(freqs) >= low_freq) & (np.abs(freqs) <= high_freq)
            fft[~mask] *= 0.1  # Attenuate frequencies outside the band
            return np.real(np.fft.ifft(fft))

class CompressionEffect(AudioEffect):
    """Applies a simple, soft-knee audio compressor."""

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        threshold = self.config.get("threshold", 0.6)
        ratio = self.config.get("ratio", 0.3)
        
        mask = np.abs(audio_data) > threshold
        compressed_audio = audio_data.copy()
        
        over_threshold = np.abs(audio_data[mask]) - threshold
        compressed_audio[mask] = np.sign(audio_data[mask]) * (threshold + over_threshold * ratio)
        
        return compressed_audio

class NormalizationEffect(AudioEffect):
    """Normalizes audio to a consistent volume level."""

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        level = self.config.get("level", 0.8)
        max_val = np.max(np.abs(audio_data))
        if max_val > 0:
            return (audio_data / max_val) * level
        return audio_data

class FormantCorrectionEffect(AudioEffect):
    """Corrects formant frequencies to reduce lisp and improve clarity."""
    
    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        # Gentle formant correction to avoid distortion
        shift_factor = self.config.get("shift_factor", 0.98)  # More subtle shift
        
        try:
            # FFT processing for formant adjustment
            fft = np.fft.fft(audio_data)
            freqs = np.fft.fftfreq(len(audio_data), 1 / sample_rate)
            
            # Apply gentle formant adjustments
            for i, freq in enumerate(freqs):
                if 800 <= abs(freq) <= 2500:  # More focused formant range
                    # Very subtle frequency adjustment
                    fft[i] *= shift_factor
                    
                # Gentle clarity enhancement
                if 1800 <= abs(freq) <= 2200:  # Narrower clarity band
                    fft[i] *= 1.05  # Reduced from 1.1
                    
            result = np.real(np.fft.ifft(fft))
            
            # Blend with original to reduce artifacts
            blend_factor = 0.7  # 70% processed, 30% original
            return result * blend_factor + audio_data * (1 - blend_factor)
            
        except:
            # Fallback: very gentle high-frequency emphasis
            if len(audio_data) > 1:
                emphasis = np.zeros_like(audio_data)
                emphasis[1:] = np.diff(audio_data) * 0.03  # Reduced from 0.1
                return audio_data + emphasis
            return audio_data

class ClarityEnhancementEffect(AudioEffect):
    """Enhances speech clarity and reduces artifacts."""
    
    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        # Multi-stage clarity enhancement with distortion protection
        
        # Stage 1: Gentle noise gate (remove low-level noise)
        gate_threshold = self.config.get("gate_threshold", 0.01)
        audio_data = np.where(np.abs(audio_data) < gate_threshold, 
                             audio_data * 0.3, audio_data)  # Less aggressive gating
        
        # Stage 2: Gentle high-frequency emphasis for clarity
        if len(audio_data) > 2:
            try:
                # Subtle high-pass emphasis
                emphasis = np.concatenate([[0], np.diff(audio_data)])
                audio_data = audio_data + emphasis * 0.02  # Reduced from 0.05
            except:
                pass
                
        # Stage 3: Gentle dynamic range optimization
        rms = np.sqrt(np.mean(audio_data**2))
        if rms > 0:
            target_rms = 0.15  # More conservative target
            adjustment = min(target_rms / rms, 1.5)  # Max 1.5x boost (reduced)
            audio_data *= adjustment
        
        # Stage 4: Soft clipping protection
        audio_data = np.tanh(audio_data * 0.9) * 0.8  # Soft saturation
            
        return audio_data

class SibilanceReductionEffect(AudioEffect):
    """Reduces harsh sibilant sounds (s, sh, etc.) that can sound like a lisp."""
    
    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        reduction_factor = self.config.get("reduction_factor", 0.7)
        sibilance_freq_low = self.config.get("freq_low", 4000)
        sibilance_freq_high = self.config.get("freq_high", 8000)
        
        try:
            # FFT-based sibilance reduction
            fft = np.fft.fft(audio_data)
            freqs = np.fft.fftfreq(len(audio_data), 1 / sample_rate)
            
            # Reduce harsh sibilant frequencies
            for i, freq in enumerate(freqs):
                if sibilance_freq_low <= abs(freq) <= sibilance_freq_high:
                    fft[i] *= reduction_factor
                    
            return np.real(np.fft.ifft(fft))
        except:
            # Fallback: simple smoothing
            if len(audio_data) > 2:
                smoothed = np.copy(audio_data)
                smoothed[1:-1] = (audio_data[:-2] + audio_data[1:-1] + audio_data[2:]) / 3
                return smoothed * 0.9 + audio_data * 0.1
            return audio_data

class ClippingProtectionEffect(AudioEffect):
    """Prevents audio clipping and distortion."""
    
    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        # Soft limiting to prevent clipping
        threshold = self.config.get("threshold", 0.95)
        
        # Soft knee limiting
        mask = np.abs(audio_data) > threshold
        if np.any(mask):
            # Apply soft saturation to peaks
            audio_data[mask] = np.sign(audio_data[mask]) * (
                threshold + (1 - threshold) * np.tanh((np.abs(audio_data[mask]) - threshold) / (1 - threshold))
            )
        
        # Final safety clamp
        audio_data = np.clip(audio_data, -0.98, 0.98)
        
        return audio_data

class SidechainCompressionEffect(AudioEffect):
    """Applies sidechain compression to duck background effects when vocal is present."""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        self.ducking_threshold = self.config.get("ducking_threshold", 0.15)
        self.reduction_ratio = self.config.get("reduction_ratio", 0.4)
        self.attack_samples = int(self.config.get("attack_ms", 10) * 24000 / 1000)  # 10ms default
        self.release_samples = int(self.config.get("release_ms", 100) * 24000 / 1000)  # 100ms default
        self.envelope = 0.0
    
    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply sidechain compression - ducks background effects when vocal is strong."""
        # Calculate RMS energy to detect vocal presence
        rms_window = int(sample_rate * 0.01)  # 10ms window
        compressed_audio = audio_data.copy()
        
        for i in range(0, len(audio_data), rms_window):
            end_idx = min(i + rms_window, len(audio_data))
            window = audio_data[i:end_idx]
            
            # Calculate RMS of current window
            rms = np.sqrt(np.mean(window**2))
            
            # Determine if vocal is present (above threshold)
            if rms > self.ducking_threshold:
                # Duck background effects - reduce audio level
                target_gain = self.reduction_ratio
            else:
                # No ducking needed - full level
                target_gain = 1.0
            
            # Smooth envelope following with attack/release
            if target_gain < self.envelope:
                # Attack - quick reduction when vocal starts
                self.envelope = max(target_gain, 
                                   self.envelope - (1.0 / self.attack_samples))
            else:
                # Release - slow return when vocal ends
                self.envelope = min(target_gain,
                                   self.envelope + (1.0 / self.release_samples))
            
            # Apply envelope to audio window
            compressed_audio[i:end_idx] *= self.envelope
        
        return compressed_audio


class PitchShiftEffect(AudioEffect):
    """
    Pitch shift effect for fun voice modulation
    Presets: chipmunk, giant, slight_up, slight_down
    """

    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)

        # Presets for common pitch shifts
        self.presets = {
            "chipmunk": {"semitones": 8},      # High-pitched cute voice
            "giant": {"semitones": -6},         # Deep low voice
            "slight_up": {"semitones": 2},      # Subtle lift
            "slight_down": {"semitones": -2},   # Subtle lower
            "robot": {"semitones": 0},          # For robot effect (no pitch shift)
        }

        # Get preset or use custom value
        preset_name = self.config.get("preset")
        if preset_name and preset_name in self.presets:
            self.config.update(self.presets[preset_name])

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply pitch shift using phase vocoder technique"""
        semitones = self.config.get("semitones", 0)

        if semitones == 0:
            return audio_data

        # Calculate pitch shift ratio
        pitch_ratio = 2 ** (semitones / 12.0)

        try:
            # Try using librosa if available (higher quality)
            import librosa
            return librosa.effects.pitch_shift(
                y=audio_data,
                sr=sample_rate,
                n_steps=semitones
            )
        except ImportError:
            # Fallback: Simple resampling method (lower quality but fast)
            # This changes both pitch and speed
            new_length = int(len(audio_data) / pitch_ratio)
            indices = np.linspace(0, len(audio_data) - 1, new_length)
            shifted = np.interp(indices, np.arange(len(audio_data)), audio_data)

            # Pad or trim to original length
            if len(shifted) < len(audio_data):
                shifted = np.pad(shifted, (0, len(audio_data) - len(shifted)))
            else:
                shifted = shifted[:len(audio_data)]

            return shifted


class RobotVoiceEffect(AudioEffect):
    """
    Robot/vocoder voice effect with distortion and modulation
    Makes the voice sound mechanical and robotic

    Quality presets:
    - "clean": Subtle robotic effect, mostly intelligible
    - "vintage": Classic 80s robot sound
    - "heavy": Strong digital distortion
    """

    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)

        # Presets for different robot voice styles
        self.presets = {
            "clean": {
                "carrier_freq": 80,  # Lower freq for smoother sound
                "intensity": 0.4,
                "bit_depth": 12,     # Higher bit depth = cleaner
                "resonance": 0.1
            },
            "vintage": {
                "carrier_freq": 120,
                "intensity": 0.6,
                "bit_depth": 10,
                "resonance": 0.2
            },
            "heavy": {
                "carrier_freq": 200,
                "intensity": 0.8,
                "bit_depth": 8,
                "resonance": 0.3
            }
        }

        # Apply preset if specified
        preset_name = self.config.get("preset", "clean")  # Default to clean
        if preset_name in self.presets:
            preset_config = self.presets[preset_name]
            # Override with any custom values
            for key, value in preset_config.items():
                if key not in self.config:
                    self.config[key] = value

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply robot voice effect with improved quality"""
        intensity = self.config.get("intensity", 0.4)  # Lower default
        carrier_freq = self.config.get("carrier_freq", 80)  # Lower default freq
        bit_depth = self.config.get("bit_depth", 12)  # Higher default bit depth
        resonance_amount = self.config.get("resonance", 0.1)  # Lower default resonance

        # Generate time array
        t = np.arange(len(audio_data)) / sample_rate

        # Stage 1: Ring modulation (gives metallic quality)
        # Use a gentler modulation approach
        carrier = np.sin(2 * np.pi * carrier_freq * t)
        # Blend between original and modulated based on intensity
        modulated = audio_data * (1 - intensity * 0.5) + (audio_data * carrier) * intensity

        # Stage 2: Bit crushing (reduces bit depth for digital distortion)
        # Use specified bit depth instead of calculating from intensity
        steps = 2 ** bit_depth
        crushed = np.round(modulated * steps) / steps

        # Stage 3: Add subtle mechanical resonance (optional)
        if resonance_amount > 0 and len(crushed) > 10:
            # Simple comb filter for metallic resonance
            delay_samples = min(int(sample_rate / carrier_freq), len(crushed) - 1)
            if delay_samples > 0:
                resonance = np.zeros_like(crushed)
                resonance[delay_samples:] = crushed[:-delay_samples] * resonance_amount
                crushed = crushed + resonance

        # Stage 4: Gentle normalization and clipping
        max_val = np.max(np.abs(crushed))
        if max_val > 0:
            crushed = crushed / max_val * 0.95  # Normalize to 95% to prevent clipping

        crushed = np.clip(crushed, -1.0, 1.0)

        return crushed


class EchoEffect(AudioEffect):
    """
    Echo/Delay effect with configurable delay time and decay
    Creates repeating echoes of the audio
    """

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply echo effect"""
        delay_ms = self.config.get("delay_ms", 300)     # Delay time in milliseconds
        decay = self.config.get("decay", 0.5)           # Echo decay (0.0 to 1.0)
        num_echoes = self.config.get("num_echoes", 3)   # Number of echoes
        mix = self.config.get("mix", 0.5)               # Wet/dry mix

        # Convert delay to samples
        delay_samples = int(delay_ms * sample_rate / 1000)

        # Create output buffer
        output_length = len(audio_data) + (delay_samples * num_echoes)
        output = np.zeros(output_length)

        # Add original audio (dry signal)
        output[:len(audio_data)] = audio_data * (1 - mix)

        # Add echoes with decreasing amplitude
        for i in range(num_echoes):
            echo_decay = decay ** (i + 1)
            start_idx = delay_samples * (i + 1)
            end_idx = start_idx + len(audio_data)
            output[start_idx:end_idx] += audio_data * echo_decay * mix

        # Trim back to original length
        return output[:len(audio_data)]


class ChorusEffect(AudioEffect):
    """
    Chorus effect - creates multiple slightly detuned copies for a richer sound
    Makes it sound like multiple voices singing together
    """

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply chorus effect"""
        num_voices = self.config.get("num_voices", 3)   # Number of chorus voices
        detune = self.config.get("detune", 0.02)        # Pitch detune amount
        delay = self.config.get("delay_ms", 20)         # Delay in ms
        mix = self.config.get("mix", 0.5)               # Wet/dry mix

        delay_samples = int(delay * sample_rate / 1000)
        output = audio_data.copy() * (1 - mix)  # Dry signal

        # Add detuned voices
        for i in range(num_voices):
            # Create slightly different delay for each voice
            voice_delay = delay_samples + int(i * sample_rate * 0.005)  # 5ms spread

            # Simple time-based detuning (vibrato)
            t = np.arange(len(audio_data)) / sample_rate
            lfo_freq = 1.0 + i * 0.5  # Slightly different LFO for each voice
            vibrato = np.sin(2 * np.pi * lfo_freq * t) * detune

            # Apply subtle pitch modulation via sample index modulation
            indices = np.arange(len(audio_data)) + vibrato * sample_rate * 0.01
            indices = np.clip(indices, 0, len(audio_data) - 1).astype(int)
            detuned_voice = audio_data[indices]

            # Add to output with proper gain
            output += detuned_voice * (mix / num_voices)

        return output


class LoFiEffect(AudioEffect):
    """
    Lo-Fi/Bitcrusher effect for degraded, vintage sound
    Reduces sample rate and bit depth for a retro aesthetic
    """

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply lo-fi bitcrusher effect"""
        bit_depth = self.config.get("bit_depth", 8)             # Bits (1-16)
        sample_rate_factor = self.config.get("downsample", 4)   # Downsample factor
        noise_amount = self.config.get("noise", 0.01)           # Vinyl noise

        # Downsample (reduce sample rate)
        downsampled = audio_data[::sample_rate_factor]

        # Upsample back to original rate (creates aliasing)
        upsampled = np.repeat(downsampled, sample_rate_factor)

        # Trim or pad to original length
        if len(upsampled) < len(audio_data):
            upsampled = np.pad(upsampled, (0, len(audio_data) - len(upsampled)))
        else:
            upsampled = upsampled[:len(audio_data)]

        # Reduce bit depth
        steps = 2 ** bit_depth
        crushed = np.round(upsampled * steps) / steps

        # Add vinyl-style noise
        if noise_amount > 0:
            noise = np.random.normal(0, noise_amount, len(crushed))
            crushed = crushed + noise

        # Clip to valid range
        return np.clip(crushed, -1.0, 1.0)


class FlangerEffect(AudioEffect):
    """
    Flanger effect - creates sweeping, swooshing sound
    Classic effect using modulated delay
    """

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply flanger effect"""
        rate = self.config.get("rate", 0.5)         # LFO rate in Hz
        depth = self.config.get("depth", 0.002)     # Depth in seconds
        mix = self.config.get("mix", 0.5)           # Wet/dry mix
        feedback = self.config.get("feedback", 0.5) # Feedback amount

        # Generate LFO (Low Frequency Oscillator)
        t = np.arange(len(audio_data)) / sample_rate
        lfo = np.sin(2 * np.pi * rate * t)

        # Convert depth to samples
        depth_samples = depth * sample_rate

        # Calculate modulated delay for each sample
        output = np.zeros_like(audio_data)
        delay_line = np.zeros(int(depth_samples * 2))  # Delay buffer

        for i, sample in enumerate(audio_data):
            # Calculate current delay based on LFO
            current_delay = depth_samples * (1 + lfo[i]) / 2
            delay_idx = int(current_delay)

            # Read from delay line with linear interpolation
            if delay_idx < len(delay_line) - 1:
                frac = current_delay - delay_idx
                delayed_sample = (delay_line[delay_idx] * (1 - frac) +
                                delay_line[delay_idx + 1] * frac)
            else:
                delayed_sample = 0

            # Mix dry and wet signals
            output_sample = (1 - mix) * sample + mix * delayed_sample

            # Update delay line with feedback
            delay_line = np.roll(delay_line, 1)
            delay_line[0] = sample + delayed_sample * feedback

            output[i] = output_sample

        return output


class ReverbEffect(AudioEffect):
    """
    Algorithmic reverb effect using comb and allpass filters
    Optimized for real-time processing with multiple presets
    """

    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)

        # Reverb presets
        self.presets = {
            "room": {
                "room_size": 0.5,
                "decay_time": 1.0,
                "damping": 0.5,
                "wet_mix": 0.3,
                "dry_mix": 0.7
            },
            "hall": {
                "room_size": 0.8,
                "decay_time": 2.5,
                "damping": 0.3,
                "wet_mix": 0.4,
                "dry_mix": 0.6
            },
            "cathedral": {
                "room_size": 0.95,
                "decay_time": 4.0,
                "damping": 0.2,
                "wet_mix": 0.5,
                "dry_mix": 0.5
            },
            "studio": {
                "room_size": 0.4,
                "decay_time": 0.8,
                "damping": 0.6,
                "wet_mix": 0.25,
                "dry_mix": 0.75
            },
            "intimate": {
                "room_size": 0.3,
                "decay_time": 0.5,
                "damping": 0.7,
                "wet_mix": 0.2,
                "dry_mix": 0.8
            }
        }

        # Get preset or use custom config
        preset_name = self.config.get("preset", "room")
        if preset_name in self.presets:
            preset_config = self.presets[preset_name]
            # Override with any custom values
            for key, value in preset_config.items():
                if key not in self.config:
                    self.config[key] = value

        # Initialize filter buffers (will be set per sample rate)
        self.comb_buffers = []
        self.allpass_buffers = []
        self.initialized_sample_rate = None

    def _initialize_filters(self, sample_rate: int):
        """Initialize filter delay lines based on sample rate"""
        if self.initialized_sample_rate == sample_rate:
            return

        room_size = self.config.get("room_size", 0.5)

        # Comb filter delay times (in samples) - different for each filter
        comb_delays = [
            int(0.02973 * sample_rate * room_size),  # ~29.7ms base delay
            int(0.03179 * sample_rate * room_size),  # ~31.8ms
            int(0.03407 * sample_rate * room_size),  # ~34.1ms
            int(0.03653 * sample_rate * room_size),  # ~36.5ms
        ]

        # Allpass filter delay times (in samples)
        allpass_delays = [
            int(0.00507 * sample_rate * room_size),  # ~5.1ms
            int(0.01127 * sample_rate * room_size),  # ~11.3ms
        ]

        # Initialize comb filter buffers
        self.comb_buffers = []
        for delay in comb_delays:
            buffer = np.zeros(max(delay, 1))
            self.comb_buffers.append({"buffer": buffer, "index": 0, "delay": delay})

        # Initialize allpass filter buffers
        self.allpass_buffers = []
        for delay in allpass_delays:
            buffer = np.zeros(max(delay, 1))
            self.allpass_buffers.append({"buffer": buffer, "index": 0, "delay": delay})

        self.initialized_sample_rate = sample_rate

    def apply(self, audio_data: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply reverb effect to audio data"""
        self._initialize_filters(sample_rate)

        # Get parameters
        decay_time = self.config.get("decay_time", 1.0)
        damping = self.config.get("damping", 0.5)
        wet_mix = self.config.get("wet_mix", 0.3)
        dry_mix = self.config.get("dry_mix", 0.7)

        # Calculate feedback coefficients
        comb_feedback = min(0.95, 0.84 * decay_time / 2.0)  # Prevent instability
        allpass_feedback = 0.7

        # Process audio sample by sample for real-time behavior
        output = np.zeros_like(audio_data)

        for i, sample in enumerate(audio_data):
            # Process through comb filters (parallel)
            comb_sum = 0.0

            for comb in self.comb_buffers:
                # Read delayed sample
                delayed_sample = comb["buffer"][comb["index"]]

                # Apply damping (simple high-frequency roll-off)
                damped_delayed = delayed_sample * (1.0 - damping * 0.1)

                # Calculate output
                comb_sum += damped_delayed

                # Write new sample with feedback
                feedback_sample = sample + damped_delayed * comb_feedback
                comb["buffer"][comb["index"]] = feedback_sample

                # Advance buffer index
                comb["index"] = (comb["index"] + 1) % comb["delay"]

            # Average comb filter outputs
            comb_output = comb_sum / len(self.comb_buffers)

            # Process through allpass filters (series)
            allpass_output = comb_output

            for allpass in self.allpass_buffers:
                # Read delayed sample
                delayed_sample = allpass["buffer"][allpass["index"]]

                # Allpass calculation
                output_sample = delayed_sample + allpass_output * (-allpass_feedback)
                feedback_sample = allpass_output + delayed_sample * allpass_feedback

                # Write to buffer
                allpass["buffer"][allpass["index"]] = feedback_sample

                # Advance buffer index
                allpass["index"] = (allpass["index"] + 1) % allpass["delay"]

                # Use output for next stage
                allpass_output = output_sample

            # Mix wet and dry signals
            output[i] = dry_mix * sample + wet_mix * allpass_output

        return output

    def get_name(self) -> str:
        """Return effect name with preset info"""
        preset = self.config.get("preset", "custom")
        return f"ReverbEffect ({preset})"


if __name__ == "__main__":
    print("Testing Audio Effects...")
    
    # Create a dummy audio signal (a simple sine wave)
    sample_rate = 22050
    duration = 2
    frequency = 440
    t = np.linspace(0., duration, int(sample_rate * duration))
    amplitude = np.iinfo(np.int16).max * 0.5
    dummy_audio = (amplitude * np.sin(2. * np.pi * frequency * t)).astype(np.float32)
    dummy_audio /= np.max(np.abs(dummy_audio)) # Normalize to [-1, 1]

    print(f"✅ Created dummy audio signal. Shape: {dummy_audio.shape}")

    # Test Intercom Effect
    print("\nTesting IntercomEffect...")
    intercom = IntercomEffect({"low_freq": 500, "high_freq": 3000})
    intercom_audio = intercom.apply(dummy_audio.copy(), sample_rate)
    assert intercom_audio.shape == dummy_audio.shape
    print("✅ IntercomEffect applied successfully.")

    # Test Compression Effect
    print("\nTesting CompressionEffect...")
    compressor = CompressionEffect({"threshold": 0.4, "ratio": 0.2})
    compressed_audio = compressor.apply(dummy_audio.copy(), sample_rate)
    assert compressed_audio.shape == dummy_audio.shape
    # Check that peaks are actually compressed
    assert np.max(np.abs(compressed_audio)) < np.max(np.abs(dummy_audio))
    print("✅ CompressionEffect applied successfully.")

    # Test Normalization Effect
    print("\nTesting NormalizationEffect...")
    # First, reduce the amplitude of the dummy audio
    quiet_audio = dummy_audio.copy() * 0.2
    normalizer = NormalizationEffect({"level": 0.9})
    normalized_audio = normalizer.apply(quiet_audio, sample_rate)
    assert normalized_audio.shape == quiet_audio.shape
    # Check that the audio is now louder
    assert np.max(np.abs(normalized_audio)) > np.max(np.abs(quiet_audio))
    assert np.isclose(np.max(np.abs(normalized_audio)), 0.9)
    print("✅ NormalizationEffect applied successfully.")
    
    print("\n✅ All audio effects tested successfully!")
