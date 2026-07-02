# STT Quick Start Guide

## 🎤 Voice Input Usage

### Basic Voice Input
```bash
# Start M1K3 CLI
python cli.py

# In CLI, press ENTER for voice input
💬 You (type or press ENTER for voice): [ENTER]
🎤 Listening... (speak now)
```

### STT Engine Selection
```bash
python cli.py --stt-engine native     # macOS Native (0MB, private)
python cli.py --stt-engine vosk       # Vosk Offline (54MB)
python cli.py --stt-engine web        # Web Speech (0MB, cloud)
python cli.py --stt-engine none       # Disable voice input
```

## 🔧 Troubleshooting

### Quick Diagnostic (30 seconds)
```bash
python audio_level_test.py
```
- **Shows 0.0000**: System audio issue (see [Fix Audio Input](#fix-audio-input))
- **Shows activity**: STT engine issue (see [Engine Issues](#engine-issues))

### Comprehensive Testing (2 minutes)
```bash
python stt_diagnostics.py
```

### Permission Check (10 seconds)
```bash
python check_speech_permissions.py
```

## 🛠️ Fix Audio Input

### macOS Systems
1. **System Preferences → Sound → Input**
   - Check microphone is selected and not muted
   - Verify input levels show activity when speaking

2. **Test with Voice Memos app**
   - If this doesn't work → hardware issue
   - If this works → permission issue

3. **Grant Permissions**
   - System Preferences → Privacy & Security → Microphone → Enable Terminal
   - System Preferences → Privacy & Security → Speech Recognition → Enable Terminal

### Quick Fixes
```bash
# Reset Core Audio
sudo killall coreaudiod

# Reset permissions  
tccutil reset Microphone
tccutil reset SpeechRecognition
```

## 🚀 Engine Issues

### macOS Native STT
```bash
# Install PyObjC frameworks
pip install pyobjc-framework-Speech pyobjc-framework-AVFoundation

# Test directly
python test_macos_stt.py
```

### Vosk STT
```bash  
# Install sounddevice
pip install vosk sounddevice

# Test directly (will auto-download 40MB model)
python -c "from src.engines.stt.vosk_stt_engine import VoskSTTEngine; e=VoskSTTEngine(); e.initialize(); print('✅ Vosk ready')"
```

### Web Speech STT
```bash
# Install SpeechRecognition
pip install SpeechRecognition pyaudio

# Test microphone access
python -c "import speech_recognition as sr; r=sr.Recognizer(); m=sr.Microphone(); print('✅ Microphone accessible')"
```

## 📊 Performance Comparison

| Engine | Footprint | Privacy | Speed | Accuracy | Internet |
|--------|-----------|---------|-------|----------|----------|
| macOS Native | 0MB | 🟢 Private | 🟢 Fast | 🟢 Excellent | ❌ No |
| Vosk | 54MB | 🟢 Private | 🟢 Fast | 🟡 Good | ❌ No |
| Web Speech | 0MB | ❌ Cloud | 🟡 Moderate | 🟢 Very Good | ✅ Yes |
| Whisper | 1GB+ | 🟢 Private | 🔴 Slow | 🟢 Excellent | ❌ No |

## 🎯 Recommended Setup

### For Privacy (macOS users):
```bash
pip install pyobjc-framework-Speech pyobjc-framework-AVFoundation
python cli.py --stt-engine native
```

### For Cross-Platform:
```bash
pip install vosk sounddevice
python cli.py --stt-engine vosk
```

### For Zero Footprint:
```bash
pip install SpeechRecognition pyaudio  
python cli.py --stt-engine web
```

## ❓ Getting Help

### Before Reporting Issues:
1. Run `python audio_level_test.py` 
2. Run `python stt_diagnostics.py`
3. Include outputs in issue report

### Documentation:
- **Full Guide**: `docs/STT_SYSTEM.md`
- **Troubleshooting**: `docs/STT_TROUBLESHOOTING.md`
- **GitHub Issues**: Include diagnostic outputs

### Working Test:
```bash
# This should work if audio input is configured properly:
python -c "
import speech_recognition as sr
r = sr.Recognizer()  
m = sr.Microphone()
with m as source:
    print('🎤 Say something...')
    audio = r.listen(source, timeout=3)
    print('✅ Audio captured!')
"
```

If the above fails, it's a system audio configuration issue, not an M1K3 code issue.