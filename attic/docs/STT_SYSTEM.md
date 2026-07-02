# Speech-to-Text (STT) System Documentation

## Overview

M1K3's Speech-to-Text system provides conversational voice input capabilities with a robust multi-engine architecture, automatic fallbacks, and comprehensive error handling. The system prioritizes privacy, minimal footprint, and reliability.

## Architecture

### Multi-Engine Design

The STT system implements a priority-based multi-engine architecture with automatic fallbacks:

1. **Primary**: macOS Native STT (0MB footprint, on-device, private)
2. **Secondary**: Vosk (54MB footprint, offline, good accuracy)
3. **Tertiary**: Web Speech (0MB footprint, cloud-based)
4. **Optional**: Whisper (1GB+ footprint, excellent quality)

### Core Components

```
STT Manager (Coordinator)
├── macOS Native Engine (SFSpeechRecognizer)
├── Vosk Engine (Offline ML)  
├── Web Speech Engine (SpeechRecognition library)
└── Whisper Engine (OpenAI Whisper)
```

## Features

### 🎤 **Voice Input Integration**
- **CLI Integration**: Press ENTER for voice input in interactive mode
- **Avatar Feedback**: Visual listening indicators during voice capture
- **Real-time Processing**: Live audio level monitoring and feedback
- **Automatic Timeouts**: Smart timeout handling with fallback attempts

### 🔐 **Permission Management**
- **Automatic Authorization**: System prompts appear automatically when needed
- **Status Verification**: Real-time permission checking with clear guidance
- **Cross-Platform**: Handles macOS speech recognition permissions seamlessly
- **Diagnostic Tools**: Easy permission verification and troubleshooting

### 🔄 **Intelligent Fallbacks**
- **Automatic Switching**: Seamlessly tries backup engines when primary fails
- **State Management**: Clean resource cleanup between engine attempts
- **Performance Optimization**: Shorter timeouts for fallback engines
- **Recovery Logic**: Graceful error handling with actionable feedback

### 📊 **Comprehensive Diagnostics**
- **Audio Level Monitoring**: Real-time microphone input verification
- **Engine Testing**: Individual engine testing and validation
- **Permission Checking**: System permission status verification
- **Troubleshooting Guidance**: Step-by-step issue resolution help

## Usage

### Basic CLI Usage

```bash
# Start CLI with STT enabled
python cli.py

# Disable voice input
python cli.py --stt-engine none

# Use specific engine
python cli.py --stt-engine vosk
python cli.py --stt-engine native
```

### Voice Input Commands

```bash
# In CLI, press ENTER to activate voice input
💬 You (type or press ENTER for voice): [ENTER]
🎤 Listening... (speak now)
```

### STT Management Commands

```bash
# Check STT status
stt status

# Test specific engine
stt test

# Switch engines
stt engine vosk

# Show available engines
stt engines
```

### Diagnostic Tools

```bash
# Test microphone audio levels
python audio_level_test.py

# Comprehensive STT diagnostics
python stt_diagnostics.py

# Check speech recognition permissions
python check_speech_permissions.py

# Test individual engines
python test_macos_stt.py
```

## Engine Details

### macOS Native STT Engine

**Features:**
- Zero footprint (uses system frameworks)
- Complete privacy (on-device processing)
- High-quality recognition
- Real-time partial results
- Automatic permission handling

**Requirements:**
- macOS 10.15+ (Catalina or later)
- PyObjC frameworks: `pip install pyobjc-framework-Speech pyobjc-framework-AVFoundation`
- Speech Recognition permission granted

**Configuration:**
```python
# Optimized settings applied automatically
- Partial results enabled for real-time feedback
- Dictation mode for conversational input
- On-device recognition for privacy
- Automatic endpointing for natural speech
```

### Vosk Engine

**Features:**
- Offline operation (no internet required)
- Lightweight model (54MB)
- Good accuracy for general speech
- Voice activity detection
- Cross-platform compatibility

**Models:**
- Default: `vosk-model-small-en-us-0.15` (40MB, auto-downloaded)
- Alternative models available for other languages

**Configuration:**
```python
# Audio settings
sample_rate = 16000  # 16kHz sampling
channels = 1         # Mono input
silence_threshold = 0.01  # Voice detection threshold
```

### Web Speech Engine

**Features:**
- Zero local footprint
- Cloud-based processing (Google Speech API)
- Multiple backend options
- Automatic sensitivity adjustment
- Wide language support

**Backends:**
- Google (default, free tier available)
- Sphinx (offline fallback)
- Wit.ai (requires API key)
- Bing Speech (requires API key)

### Whisper Engine

**Features:**
- Excellent accuracy and quality
- Multiple model sizes (tiny to large)
- Multilingual support
- Robust noise handling
- Optional (heavy footprint)

**Models:**
- `tiny`: ~39MB, fastest, basic accuracy
- `base`: ~74MB, good balance
- `small`: ~244MB, better accuracy
- `medium`: ~769MB, high accuracy
- `large`: ~1550MB, best quality

## Configuration

### Engine Selection Priority

```python
# Default priority order (can be overridden)
1. macOS Native (0MB, private, fast)
2. Vosk (54MB, offline, reliable)  
3. Web Speech (0MB, cloud-based)
4. Whisper (optional, heavy, excellent)
```

### CLI Arguments

```bash
--stt-engine     # Engine selection: auto, native, vosk, web, whisper, none
--stt-model      # Model name (for Vosk/Whisper)
--stt-lang       # Language code (default: en-US)
```

### Environment Variables

```bash
M1K3_STT_ENGINE=vosk     # Force specific engine
M1K3_USE_WHISPER=true    # Enable Whisper engine
```

## Troubleshooting

### Common Issues

#### 1. **"No speech detected" - All Engines Fail**

**Symptoms:**
```
⚠️ No speech detected or recognition failed
⚠️ No audio recorded
Audio Level: 0.0000 (consistently)
```

**Root Cause:** System audio input not configured properly

**Solutions:**
1. **Check System Audio Settings:**
   - macOS: System Preferences → Sound → Input
   - Verify microphone input levels are not zero
   - Test with other apps (Voice Memos, etc.)

2. **Hardware Verification:**
   - Check microphone is connected and not muted
   - Try external microphone
   - Verify built-in microphone isn't physically blocked

3. **Use Diagnostic Tools:**
   ```bash
   python audio_level_test.py    # Real-time audio level monitoring
   python stt_diagnostics.py     # Comprehensive engine testing
   ```

#### 2. **"Speech recognition access denied"**

**Symptoms:**
```
❌ Speech recognition access denied
Authorization status: 1 (Denied)
```

**Solutions:**
1. **Grant Permissions:**
   - macOS: System Preferences → Privacy & Security → Speech Recognition
   - Enable access for Terminal and/or Python

2. **Reset Permissions:**
   ```bash
   tccutil reset Microphone
   tccutil reset SpeechRecognition
   ```

3. **Verify Permission Status:**
   ```bash
   python check_speech_permissions.py
   ```

#### 3. **"PyObjC Speech framework not available"**

**Symptoms:**
```
❌ PyObjC Speech framework not available
💡 Install with: pip install pyobjc-framework-Speech
```

**Solutions:**
```bash
pip install pyobjc-framework-Speech pyobjc-framework-AVFoundation
```

#### 4. **Vosk "No audio recorded"**

**Symptoms:**
```
⚠️ No audio recorded
🎤 Using audio device: MacBook Pro Microphone
```

**Solutions:**
1. **Install sounddevice:**
   ```bash
   pip install sounddevice
   ```

2. **Check Audio Device:**
   ```bash
   python -c "import sounddevice as sd; print(sd.query_devices())"
   ```

3. **Force Device Selection:**
   ```python
   # In vosk_stt_engine.py, modify:
   with sd.InputStream(device=1):  # Try different device indices
   ```

#### 5. **Web Speech Energy Threshold Issues**

**Symptoms:**
```
🔧 Energy threshold: 0.0
⚠️ Listening timeout - no speech detected
```

**Solutions:**
1. **Microphone Permissions:** Grant microphone access to Terminal
2. **Reinitialize Engine:** The system now automatically reinitializes between attempts
3. **Manual Calibration:**
   ```bash
   stt calibrate  # Recalibrate microphone sensitivity
   ```

### Performance Optimization

#### Memory Usage
- **Minimal**: Use `native` or `web` engines (0MB footprint)
- **Balanced**: Use `vosk` engine (54MB footprint)
- **Quality**: Use `whisper tiny` model (~40MB)

#### Latency Optimization
- **Fastest**: macOS Native (on-device processing)
- **Fast**: Vosk (local processing)
- **Moderate**: Web Speech (network dependent)

#### Accuracy vs Privacy
- **Maximum Privacy**: macOS Native (on-device) or Vosk (offline)
- **Maximum Accuracy**: Whisper large model
- **Balanced**: Vosk or Web Speech

## Development

### Adding New Engines

1. **Implement STTEngine Interface:**
```python
class CustomSTTEngine(STTEngine):
    def initialize(self) -> bool: pass
    def is_available(self) -> bool: pass
    def listen_once(self, timeout, phrase_timeout) -> Optional[STTResult]: pass
    def cleanup(self): pass
```

2. **Register in STT Manager:**
```python
# In stt_manager.py _initialize_engines()
custom_engine = CustomSTTEngine()
if custom_engine.initialize():
    self.engines["custom"] = custom_engine
```

3. **Add CLI Support:**
```python
# In cli.py argument parser
choices=["auto", "native", "vosk", "web", "whisper", "custom", "none"]
```

### Testing Framework

```bash
# Unit tests for individual engines
python -m pytest tests/test_stt_engines.py

# Integration tests
python -m pytest tests/test_stt_integration.py

# Manual testing
python stt_diagnostics.py
```

### Debug Mode

```python
# Enable verbose logging
import logging
logging.basicConfig(level=logging.DEBUG)

# Or set environment variable
export M1K3_DEBUG=1
```

## API Reference

### STTManager Class

```python
from src.engines.stt.stt_manager import STTManager

stt = STTManager()
result = stt.listen_once(timeout=10.0, phrase_timeout=2.0)

if result:
    print(f"Recognized: {result.text}")
    print(f"Confidence: {result.confidence}")
    print(f"Engine: {result.engine}")
```

### STTResult Class

```python
@dataclass
class STTResult:
    text: str                    # Recognized text
    confidence: float            # Confidence score (0.0-1.0)
    language: str               # Language code (e.g., "en-US")
    duration: float             # Recognition duration in seconds
    engine: str                 # Engine used for recognition
    raw_data: Dict[str, Any]    # Engine-specific metadata
```

### Engine Status

```python
from src.engines.stt.stt_manager import STTStatus

# Status values
STTStatus.DISABLED    # Engine not available
STTStatus.IDLE        # Ready for recognition
STTStatus.LISTENING   # Currently capturing audio
STTStatus.PROCESSING  # Processing captured audio
STTStatus.ERROR       # Error state
```

## Security Considerations

### Privacy
- **macOS Native**: Complete privacy, all processing on-device
- **Vosk**: Private, offline processing, no network requests
- **Web Speech**: Audio sent to cloud services (Google, etc.)
- **Whisper**: Private when run locally

### Permissions
- **Microphone Access**: Required for all engines
- **Speech Recognition**: Required for macOS Native engine
- **Network Access**: Required for Web Speech engine

### Data Handling
- Audio data is processed in memory only
- No persistent storage of voice recordings
- Recognition results are not logged by default
- Raw audio data is immediately discarded after processing

## Performance Metrics

### Typical Performance (MacBook Pro M1)

| Engine | Initialization | Recognition Time | Memory Usage | Accuracy |
|--------|---------------|------------------|--------------|----------|
| macOS Native | 0.1s | 1-3s | 0MB | Excellent |
| Vosk | 2-3s | 1-2s | 54MB | Good |
| Web Speech | 0.1s | 2-5s | 0MB | Very Good |
| Whisper Tiny | 1-2s | 3-8s | 40MB | Good |
| Whisper Base | 2-4s | 5-15s | 74MB | Excellent |

### Benchmarks
- **Cold Start**: First recognition attempt (includes model loading)
- **Warm Start**: Subsequent attempts (model already loaded)
- **Fallback Overhead**: ~0.5s delay when switching engines

## Changelog

### v2.0.0 - Enhanced Architecture (2025-08-26)
- ✅ Implemented multi-engine fallback system
- ✅ Added automatic permission prompting for macOS
- ✅ Enhanced audio pipeline with proper resource cleanup
- ✅ Added comprehensive diagnostic tools
- ✅ Implemented partial results support
- ✅ Added audio level monitoring and verification
- ✅ Improved error handling and recovery
- ✅ Fixed sounddevice timing issues in Vosk engine
- ✅ Added recognizer reinitialization for Web Speech

### v1.0.0 - Initial Implementation
- Basic STT integration with CLI
- Single-engine support
- Manual engine selection
- Basic error handling

## License

Part of M1K3 Local AI Assistant - See main project license.