# M1K3 Voice Configuration

## Selected Voice Setup

M1K3 now uses **Kokoro Daniel** with **Radio Chat effects** as the default voice!

### Primary Voice (Python CLI + Mobile Download)
- **Engine:** Kokoro-82M (#1 TTS Arena)
- **Voice:** Daniel (bm_daniel) - British Male
- **Character:** Professional, precise, conversational
- **Speed:** 1.0x (natural pace)
- **Model Size:** 310MB (ONNX)
- **Effects Pipeline:**
  - Light Intercom (300-3400Hz bandpass)
  - Compression (threshold: 0.6, ratio: 0.3)
  - Normalization (level: 0.8)

### Mobile-Embedded Voice
- **Engine:** Piper TTS (Lessac Low)
- **Voice:** en_US-lessac-low - American Male
- **Character:** Professional, clear
- **Speed:** 1.4x (deliberate, conversational)
- **Model Size:** 60MB (ONNX) - 5.2x smaller!
- **Effects Pipeline:** Same as primary (Radio Chat)

## Voice Profiles

### Default Profile: `kokoro`
```python
voice_engine.set_profile("kokoro")
```
- Uses Kokoro Daniel
- Radio chat effects
- Natural British accent
- Best quality for CLI

### Mobile Profile: `mobile`
```python
voice_engine.set_profile("mobile")
```
- Uses Piper Lessac
- Radio chat effects
- 60MB model (mobile-optimized)
- Ultra-fast generation (RTF 0.05x)

## Performance Comparison

| Voice | Model Size | Generation RTF | Character | Use Case |
|-------|-----------|---------------|-----------|----------|
| **Kokoro Daniel** | 310MB | 0.59x | British, precise | CLI, downloadable |
| **Piper Lessac** | 60MB | 0.05x | American, clear | Mobile embedded |

## Mobile Strategy

### Embedded (Shipped with App)
- **Voice:** Piper Lessac (60MB)
- **Why:** Small enough to ship with app
- **Performance:** 20x faster than real-time
- **Quality:** Good, professional

### Optional Download
- **Voice:** Kokoro Daniel (310MB)
- **Why:** SOTA quality for users who want best voice
- **Performance:** 2x faster than real-time
- **Quality:** #1 TTS Arena ranking

## Audio Effects Pipeline

All voices use the **Radio Chat** effects stack:

1. **Intercom Effect**
   - Bandpass filter: 300-3400Hz
   - Creates classic assistant/radio voice character
   - Reduces background noise sensitivity

2. **Compression Effect**
   - Threshold: 0.6
   - Ratio: 0.3
   - Smooth, consistent volume

3. **Normalization Effect**
   - Level: 0.8
   - Consistent output levels

## Testing

Test the configuration:

```bash
# Test Kokoro Daniel (CLI default)
python test_daniel_normal_speed.py

# Test Piper Lessac (mobile default)
python test_m1k3_fine_tune_speed.py
```

## Usage in Code

```python
from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine

# CLI: Use Kokoro Daniel
voice_engine = UnifiedVoiceEngine()
voice_engine.set_profile("kokoro")
voice_engine.load_model()
audio = voice_engine.synthesize_and_play("Greetings Human, I'm M1K3!")

# Mobile: Use Piper Lessac
voice_engine = UnifiedVoiceEngine()
voice_engine.set_profile("mobile")
voice_engine.load_model()
audio = voice_engine.synthesize_and_play("Greetings Human, I'm M1K3!")
```

## M1K3's Introduction

Both voices use this introduction:

> "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, designed to save the planet by using the compute you have in your own very pocket. Eco, offline, forever!!!"

## Next Steps for Mobile

1. **KMP Integration:**
   - See `docs/KOKORO_MOBILE_ONNX.md` for Android/iOS setup
   - Piper has even better mobile support (smaller, faster)

2. **Model Management:**
   - Ship Piper Lessac (60MB) embedded
   - Offer Kokoro Daniel (310MB) as in-app download
   - Store models in app's document directory

3. **Runtime Selection:**
   - Default to Piper on first launch
   - Allow users to download Kokoro in settings
   - Persist voice preference

## Files

- **Kokoro Manager:** `src/tts/controllers/kokoro_tts_manager.py`
- **Piper Manager:** `src/tts/controllers/piper_tts_manager.py`
- **Voice Engine:** `src/engines/voice/unified_voice_engine.py`
- **Effects:** `src/tts/effects/audio_effects.py`
- **Setup Guide:** `docs/KOKORO_SETUP.md`
- **Mobile Guide:** `docs/KOKORO_MOBILE_ONNX.md`
