#!/usr/bin/env python3
"""
VibeVoice Manager for M1K3
A singleton manager for Microsoft's VibeVoice frontier TTS model
Provides long-form (90 minutes) and multi-speaker conversation synthesis
"""

import time
import numpy as np
import warnings
import sys
import os
from pathlib import Path
from typing import Optional, List, Dict, Union, Any
import tempfile
import json

# Fix tokenizer parallelism warning
os.environ["TOKENIZERS_PARALLELISM"] = "false"

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)
# Suppress specific tokenizer warning
warnings.filterwarnings("ignore", message="The tokenizer class you load from this checkpoint")

# Try to import VibeVoice dependencies
try:
    import torch
    TORCH_AVAILABLE = True
    TORCH_VERSION = torch.__version__
except ImportError:
    TORCH_AVAILABLE = False
    TORCH_VERSION = None

try:
    from transformers import AutoTokenizer, AutoModelForCausalLM
    TRANSFORMERS_AVAILABLE = True
except ImportError:
    TRANSFORMERS_AVAILABLE = False

try:
    import librosa
    import soundfile as sf
    AUDIO_LIBS_AVAILABLE = True
except ImportError:
    AUDIO_LIBS_AVAILABLE = False

# Try to locate VibeVoice repository
VIBEVOICE_PATHS = [
    Path.home() / "VibeVoice",
    Path.cwd() / "VibeVoice", 
    Path("/opt/VibeVoice"),
    Path("./external/VibeVoice")
]

# Try to import real VibeVoice components
try:
    # Add VibeVoice to Python path
    vibevoice_path = None
    for path in VIBEVOICE_PATHS:
        if path.exists():
            vibevoice_path = path
            break
    
    if vibevoice_path and str(vibevoice_path) not in sys.path:
        sys.path.insert(0, str(vibevoice_path))
    
    from vibevoice.modular.modeling_vibevoice_inference import VibeVoiceForConditionalGenerationInference
    from vibevoice.processor.vibevoice_processor import VibeVoiceProcessor
    REAL_VIBEVOICE_AVAILABLE = True
except Exception as e:
    REAL_VIBEVOICE_AVAILABLE = False
    VIBEVOICE_IMPORT_ERROR = str(e)

# Diffusers is optional for basic functionality
try:
    from diffusers import DiffusionPipeline
    DIFFUSERS_AVAILABLE = True
except Exception as e:
    DIFFUSERS_AVAILABLE = False
    DIFFUSERS_ERROR = str(e)

# Overall availability check
VIBEVOICE_DEPS_AVAILABLE = all([
    TORCH_AVAILABLE,
    TRANSFORMERS_AVAILABLE, 
    AUDIO_LIBS_AVAILABLE
])

if not VIBEVOICE_DEPS_AVAILABLE:
    if not TORCH_AVAILABLE:
        IMPORT_ERROR = "PyTorch not available"
    elif not TRANSFORMERS_AVAILABLE:
        IMPORT_ERROR = "Transformers not available"
    elif not AUDIO_LIBS_AVAILABLE:
        IMPORT_ERROR = "Audio libraries (librosa/soundfile) not available"
    else:
        IMPORT_ERROR = "Unknown dependency issue"

if not REAL_VIBEVOICE_AVAILABLE:
    REAL_VIBEVOICE_ERROR = f"Real VibeVoice not available: {VIBEVOICE_IMPORT_ERROR if 'VIBEVOICE_IMPORT_ERROR' in globals() else 'Unknown error'}"

VIBEVOICE_PATH = None
for path in VIBEVOICE_PATHS:
    if path.exists() and (path / "demo").exists():
        VIBEVOICE_PATH = path
        break

# VibeVoice is available if we have basic deps and the repository (diffusers optional for now)
VIBEVOICE_AVAILABLE = VIBEVOICE_DEPS_AVAILABLE and VIBEVOICE_PATH is not None

class VibeVoiceManager:
    """
    Singleton manager for VibeVoice TTS model
    Supports long-form generation (up to 90 minutes) and multi-speaker conversations
    """
    _instance = None
    
    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(VibeVoiceManager, cls).__new__(cls)
        return cls._instance

    def __init__(self, model_name: str = "microsoft/VibeVoice-1.5b"):
        if not hasattr(self, 'initialized'):  # Ensure __init__ runs only once
            self.model_name = model_name
            self.model: Optional[Any] = None
            self.processor: Optional[Any] = None
            self.tokenizer: Optional[Any] = None
            self.pipeline: Optional[Any] = None
            self.loading = False
            
            # Multi-speaker configuration
            self.current_speakers = ["Alice"]  # Default single speaker
            self.max_speakers = 4
            self.available_speakers = ["Alice", "Bob", "Carol", "Dave"]
            
            # Generation settings
            self.sample_rate = 24000  # VibeVoice native sample rate
            self.max_length_minutes = 90  # Maximum generation length
            self.chunk_length_seconds = 30  # Default chunking for streaming
            
            # Performance settings
            self.generation_quality = "balanced"  # fast, balanced, quality
            self.ddmp_steps = {"fast": 5, "balanced": 7, "quality": 10}
            self.cfg_scales = {"fast": 1.0, "balanced": 1.3, "quality": 1.5}
            
            # Device configuration
            if torch.cuda.is_available():
                self.device = "cuda"
            elif hasattr(torch.backends, 'mps') and torch.backends.mps.is_available():
                self.device = "mps"
            else:
                self.device = "cpu"
            
            # Model variants
            self.model_variants = {
                "1.5B": {
                    "name": "microsoft/VibeVoice-1.5b",
                    "context_length": 64000,  # 64K tokens
                    "max_minutes": 90,
                    "memory_requirement": "4GB"
                },
                "7B": {
                    "name": "microsoft/VibeVoice-7B-Preview", 
                    "context_length": 32000,  # 32K tokens
                    "max_minutes": 45,
                    "memory_requirement": "16GB"
                }
            }
            
            self.initialized = True

    def is_available(self) -> bool:
        """Check if VibeVoice is available for use"""
        if not VIBEVOICE_AVAILABLE:
            return False
            
        # Check device availability for optimal performance
        if torch.cuda.is_available():
            pass  # Optimal performance
        elif hasattr(torch.backends, 'mps') and torch.backends.mps.is_available():
            print("⚡ VibeVoice: Using Apple Silicon MPS acceleration")
        else:
            print("⚠️  VibeVoice: Using CPU (very slow performance)")
            
        return True

    def get_availability_info(self) -> Dict[str, Any]:
        """Get detailed availability information"""
        info = {
            "available": self.is_available(),
            "repository_found": VIBEVOICE_PATH is not None,
            "repository_path": str(VIBEVOICE_PATH) if VIBEVOICE_PATH else None,
            "torch_available": TORCH_AVAILABLE,
            "torch_version": TORCH_VERSION,
            "transformers_available": TRANSFORMERS_AVAILABLE,
            "diffusers_available": DIFFUSERS_AVAILABLE,
            "audio_libs_available": AUDIO_LIBS_AVAILABLE,
            "dependencies_installed": VIBEVOICE_DEPS_AVAILABLE,
            "cuda_available": torch.cuda.is_available() if TORCH_AVAILABLE else False,
            "mps_available": (hasattr(torch.backends, 'mps') and torch.backends.mps.is_available()) if TORCH_AVAILABLE else False
        }
        
        if not VIBEVOICE_DEPS_AVAILABLE:
            info["error"] = IMPORT_ERROR
            
        if not DIFFUSERS_AVAILABLE and 'DIFFUSERS_ERROR' in globals():
            info["diffusers_error"] = DIFFUSERS_ERROR
            info["diffusers_note"] = "Diffusers compatibility issue - may need PyTorch upgrade or version downgrade"
            
        return info

    def load_model(self, variant: str = "1.5B") -> bool:
        """Load the VibeVoice model"""
        if not self.is_available():
            if not VIBEVOICE_DEPS_AVAILABLE:
                print(f"❌ VibeVoice dependencies not available: {IMPORT_ERROR}")
            if not VIBEVOICE_PATH:
                print("❌ VibeVoice repository not found")
                print("   Clone with: git clone https://github.com/microsoft/VibeVoice.git ~/VibeVoice")
            return False
            
        if self.model is not None:
            return True  # Already loaded

        if variant not in self.model_variants:
            print(f"❌ Unknown model variant: {variant}")
            return False

        model_info = self.model_variants[variant]
        self.model_name = model_info["name"]
        
        print(f"🎤 Loading VibeVoice {variant} model...")
        print(f"   Model: {self.model_name}")
        print(f"   Device: {self.device}")
        print(f"   Max length: {model_info['max_minutes']} minutes")
        
        self.loading = True
        start_time = time.time()
        
        try:
            # Check if real VibeVoice is available
            if not REAL_VIBEVOICE_AVAILABLE:
                print(f"❌ Real VibeVoice not available: {REAL_VIBEVOICE_ERROR}")
                return False
            
            # Load VibeVoice processor
            print("   Loading processor...")
            
            # Try to use local preprocessor config if available
            local_config_path = Path(__file__).parent.parent / "configs" / "preprocessor_config.json"
            if local_config_path.exists():
                print(f"   Using local preprocessor config: {local_config_path}")
            
            self.processor = VibeVoiceProcessor.from_pretrained(
                self.model_name,
                language_model_pretrained_name=self.model_name, # Force it to use its own tokenizer
                trust_remote_code=True
            )
            
            # Load VibeVoice model
            print("   Loading model...")
            
            # Use optimized settings based on device capabilities
            if torch.cuda.is_available():
                dtype = torch.bfloat16
                device_map = 'cuda'
                attn_impl = 'flash_attention_2'
                load_kwargs = {
                    'torch_dtype': dtype,
                    'device_map': device_map,
                    'attn_implementation': attn_impl,
                    'low_cpu_mem_usage': True,
                    'use_safetensors': True
                }
            elif hasattr(torch.backends, 'mps') and torch.backends.mps.is_available():
                dtype = torch.float16  # MPS doesn't support bfloat16
                device_map = 'mps'
                attn_impl = 'sdpa'
                load_kwargs = {
                    'torch_dtype': dtype,
                    'device_map': device_map,
                    'attn_implementation': attn_impl,
                    'low_cpu_mem_usage': True,
                    'use_safetensors': True
                }
                # Enable MPS optimizations
                torch.backends.mps.allow_tf32 = True
            else:
                dtype = torch.float32  # CPU fallback
                device_map = 'cpu'
                attn_impl = 'sdpa'
                load_kwargs = {
                    'torch_dtype': dtype,
                    'device_map': device_map,
                    'attn_implementation': attn_impl,
                    'low_cpu_mem_usage': True
                }
            
            print(f"   Using dtype: {dtype}, device: {device_map}")
            
            self.model = VibeVoiceForConditionalGenerationInference.from_pretrained(
                self.model_name,
                **load_kwargs
            )
            
            # Set model to evaluation mode
            self.model.eval()
            
            # Set initial DDPM steps based on quality setting
            initial_steps = self.ddmp_steps[self.generation_quality]
            self.model.set_ddpm_inference_steps(num_steps=initial_steps)
            print(f"   DDPM inference steps: {initial_steps} ({self.generation_quality} mode)")
            
            # MPS optimizations if available
            if device_map == 'mps':
                print("   Applying MPS optimizations...")
                # Enable MPS fallback for better compatibility
                torch.backends.mps.enabled = True
            
            load_time = time.time() - start_time
            print(f"✅ VibeVoice {variant} loaded in {load_time:.2f} seconds")
            self.loading = False
            return True
            
        except Exception as e:
            print(f"❌ Failed to load VibeVoice model: {e}")
            self.model = None
            self.loading = False
            return False

    def _create_model_placeholder(self, model_info: Dict) -> Dict:
        """Create a model placeholder until we can test the actual VibeVoice API"""
        return {
            "name": model_info["name"],
            "context_length": model_info["context_length"],
            "sample_rate": self.sample_rate,
            "device": self.device,
            "loaded": True
        }

    def set_generation_quality(self, quality: str) -> bool:
        """Set generation quality: 'fast', 'balanced', or 'quality'"""
        if quality not in self.ddmp_steps:
            print(f"❌ Invalid quality setting: {quality}. Use: fast, balanced, quality")
            return False
            
        self.generation_quality = quality
        if self.model:
            steps = self.ddmp_steps[quality]
            self.model.set_ddpm_inference_steps(num_steps=steps)
            print(f"🎯 Generation quality set to '{quality}' ({steps} DDPM steps)")
        return True
    
    def generate(self, text: str, speakers: Optional[List[str]] = None, 
                 chunk_length: Optional[int] = None, quality: Optional[str] = None) -> Optional[np.ndarray]:
        """
        Generate speech from text using VibeVoice
        
        Args:
            text: Text to synthesize
            speakers: List of speaker names for multi-speaker generation
            chunk_length: Length of each chunk in seconds (for streaming)
            quality: Generation quality override ('fast', 'balanced', 'quality')
            
        Returns:
            Generated audio as numpy array
        """
        if not self.model:
            print("⚠️  VibeVoice model not loaded. Cannot generate audio.")
            return None
            
        if not speakers:
            speakers = self.current_speakers
            
        # Validate speakers
        if len(speakers) > self.max_speakers:
            print(f"⚠️  Too many speakers ({len(speakers)}), using first {self.max_speakers}")
            speakers = speakers[:self.max_speakers]
        
        # Apply quality setting if provided
        current_quality = quality or self.generation_quality
        if quality and quality != self.generation_quality:
            self.set_generation_quality(quality)
            
        try:
            print(f"🗣️  Generating speech with VibeVoice ({current_quality} quality)...")
            print(f"   Text length: {len(text)} characters")
            print(f"   Speakers: {speakers}")
            print(f"   DDPM steps: {self.ddmp_steps[current_quality]}")
            
            # Get voice samples for the speakers
            voice_samples = self._get_voice_samples(speakers)
            if not voice_samples:
                print("❌ Could not find voice samples for speakers")
                return None
            
            # Validate voice samples count matches speakers count
            if len(voice_samples) != len(speakers):
                print(f"⚠️  Voice samples count ({len(voice_samples)}) doesn't match speakers count ({len(speakers)})")
                # Adjust speakers to match available voice samples
                speakers = speakers[:len(voice_samples)]
                print(f"🔄 Adjusted speakers to: {speakers}")
            
            # Format text with speaker labels (VibeVoice expects specific format)
            formatted_text = self._format_text_with_speakers(text, speakers)
            
            # Prepare inputs for the model
            inputs = self.processor(
                text=[formatted_text],
                voice_samples=[voice_samples],
                padding=True,
                return_tensors="pt",
                return_attention_mask=True,
            )
            
            print("   Running VibeVoice inference...")
            start_time = time.time()
            
            # Generate audio using VibeVoice
            cfg_scale = self.cfg_scales[current_quality]
            
            # Move inputs to device to avoid meta tensor issues
            device_inputs = {}
            for key, value in inputs.items():
                if hasattr(value, 'to'):
                    device_inputs[key] = value.to(self.device)
                else:
                    device_inputs[key] = value
            
            outputs = self.model.generate(
                **device_inputs,
                max_new_tokens=None,
                cfg_scale=cfg_scale,
                tokenizer=self.processor.tokenizer,
                generation_config={'do_sample': False},
                verbose=False,
            )
            
            generation_time = time.time() - start_time
            
            if outputs.speech_outputs and outputs.speech_outputs[0] is not None:
                audio_tensor = outputs.speech_outputs[0]
                print(f"   Debug - Audio tensor shape: {audio_tensor.shape}")
                print(f"   Debug - Audio tensor dtype: {audio_tensor.dtype}")
                
                audio_data = audio_tensor.cpu().numpy()
                print(f"   Debug - Audio numpy shape: {audio_data.shape}")
                
                # Handle different tensor shapes
                if len(audio_data.shape) == 2:
                    # If stereo or batch dimension, take first channel
                    audio_data = audio_data[0] if audio_data.shape[0] < audio_data.shape[1] else audio_data[:, 0]
                
                duration_seconds = len(audio_data) / self.sample_rate
                rtf = generation_time / duration_seconds if duration_seconds > 0 else 0
                print(f"✅ Generated {duration_seconds:.1f}s audio in {generation_time:.2f}s (RTF: {rtf:.1f}x)")
                return audio_data.astype(np.float32)
            else:
                print("❌ No audio output generated")
                return None
            
        except Exception as e:
            print(f"❌ VibeVoice generation failed: {e}")
            return None

    def _get_voice_samples(self, speakers: List[str]) -> Optional[List[str]]:
        """Map speaker names to voice sample file paths"""
        voices_dir = Path(__file__).parent.parent / "voices"
        voice_samples = []
        
        # Voice mapping from speaker names to files
        voice_mapping = {
            "Alice": "en-Alice_woman.wav",
            "Bob": "en-Carter_man.wav", 
            "Carol": "en-Maya_woman.wav",
            "Dave": "en-Frank_man.wav",
            "Andrew": "en-Carter_man.wav",
            "Maya": "en-Maya_woman.wav",
            "Carter": "en-Carter_man.wav",
            "Frank": "en-Frank_man.wav",
            "Mary": "en-Mary_woman_bgm.wav",
            "Samuel": "in-Samuel_man.wav"
        }
        
        for speaker in speakers:
            # Try exact match first
            if speaker in voice_mapping:
                voice_file = voices_dir / voice_mapping[speaker]
            else:
                # Default to first available voice
                voice_file = voices_dir / "en-Alice_woman.wav"
                print(f"⚠️  No voice mapping for '{speaker}', using Alice")
            
            if voice_file.exists():
                voice_samples.append(str(voice_file))
            else:
                print(f"❌ Voice file not found: {voice_file}")
                return None
                
        return voice_samples
    
    def _format_text_with_speakers(self, text: str, speakers: List[str]) -> str:
        """Format text for VibeVoice with speaker labels"""
        # If text already has Speaker labels, return as-is
        if "Speaker " in text and ":" in text:
            return text
            
        # Ensure we don't exceed the maximum number of speakers
        max_speakers = min(len(speakers), self.max_speakers)
        if len(speakers) > max_speakers:
            speakers = speakers[:max_speakers]
            print(f"⚠️  Limited to {max_speakers} speakers (max supported)")
            
        # For single speaker, add Speaker 1 label
        if len(speakers) == 1:
            return f"Speaker 1: {text}"
        
        # For multi-speaker, we need to intelligently split the text
        # This is a simple heuristic - in practice you'd want more sophisticated parsing
        sentences = text.split('. ')
        formatted_parts = []
        
        for i, sentence in enumerate(sentences):
            if sentence.strip():
                # Ensure speaker_num doesn't exceed available speakers
                speaker_num = (i % len(speakers)) + 1
                if speaker_num <= len(speakers):
                    formatted_parts.append(f"Speaker {speaker_num}: {sentence.strip()}")
                else:
                    # Fallback to Speaker 1 if we exceed available speakers
                    formatted_parts.append(f"Speaker 1: {sentence.strip()}")
        
        return '. '.join(formatted_parts)

    def get_performance_info(self) -> Dict[str, Any]:
        """Get current performance settings"""
        return {
            "generation_quality": self.generation_quality,
            "ddpm_steps": self.ddmp_steps[self.generation_quality],
            "cfg_scale": self.cfg_scales[self.generation_quality],
            "device": self.device,
            "available_qualities": list(self.ddmp_steps.keys()),
            "model_loaded": self.model is not None
        }

    def generate_long_form(self, text: str, speakers: Optional[List[str]] = None, 
                          max_minutes: Optional[int] = None) -> Optional[List[np.ndarray]]:
        """
        Generate long-form speech (up to 90 minutes) by chunking text
        
        Args:
            text: Long text to synthesize
            speakers: Speakers for the conversation
            max_minutes: Maximum length in minutes
            
        Returns:
            List of audio chunks
        """
        if not self.model:
            print("⚠️  VibeVoice model not loaded.")
            return None
            
        if not max_minutes:
            max_minutes = self.model_variants.get("1.5B", {}).get("max_minutes", 90)
            
        print(f"🎬 Generating long-form speech (max {max_minutes} minutes)")
        
        # Split text into manageable chunks
        chunks = self._split_text_for_long_form(text, max_minutes)
        audio_chunks = []
        
        for i, chunk in enumerate(chunks):
            print(f"   Processing chunk {i+1}/{len(chunks)}")
            audio = self.generate(chunk, speakers)
            if audio is not None:
                audio_chunks.append(audio)
            else:
                print(f"⚠️  Failed to generate chunk {i+1}")
                
        return audio_chunks if audio_chunks else None

    def _split_text_for_long_form(self, text: str, max_minutes: int) -> List[str]:
        """Split text into chunks suitable for long-form generation"""
        # Simple sentence-based splitting
        sentences = text.split('. ')
        chunks = []
        current_chunk = ""
        
        # Rough estimate: 150 words per minute, 5 characters per word
        chars_per_minute = 150 * 5
        max_chars_per_chunk = chars_per_minute * (max_minutes // 10)  # Divide into ~10 chunks
        
        for sentence in sentences:
            if len(current_chunk + sentence) > max_chars_per_chunk and current_chunk:
                chunks.append(current_chunk.strip())
                current_chunk = sentence
            else:
                current_chunk += sentence + ". "
                
        if current_chunk.strip():
            chunks.append(current_chunk.strip())
            
        return chunks

    def set_speakers(self, speakers: List[str]) -> bool:
        """Set the current speakers for generation"""
        if len(speakers) > self.max_speakers:
            print(f"⚠️  Too many speakers, maximum is {self.max_speakers}")
            return False
            
        self.current_speakers = speakers[:self.max_speakers]
        print(f"🎭 Set speakers: {self.current_speakers}")
        return True

    def get_model_info(self) -> Dict[str, Any]:
        """Get information about the loaded model"""
        if not self.model:
            return {"loaded": False}
            
        return {
            "loaded": True,
            "model_name": self.model_name,
            "device": self.device,
            "current_speakers": self.current_speakers,
            "max_speakers": self.max_speakers,
            "sample_rate": self.sample_rate,
            "max_length_minutes": self.max_length_minutes
        }

    def save_audio(self, audio_data: np.ndarray, filename: str) -> bool:
        """Save audio data to file"""
        try:
            sf.write(filename, audio_data, self.sample_rate)
            print(f"💾 Audio saved: {filename}")
            return True
        except Exception as e:
            print(f"❌ Failed to save audio: {e}")
            return False

# Create singleton instance
vibevoice_manager = VibeVoiceManager()