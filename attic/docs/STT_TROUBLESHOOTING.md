# STT System Troubleshooting Guide

## Quick Diagnosis

**Is your STT system not working?** Run this quick diagnostic:

```bash
python audio_level_test.py
```

- **If audio levels show 0.0000**: You have a system audio configuration issue (see [Audio Input Issues](#audio-input-issues))
- **If audio levels show activity**: You have an STT engine configuration issue (see [Engine-Specific Issues](#engine-specific-issues))

## Audio Input Issues

### Problem: "No Speech Detected" Across All Engines

**Symptoms:**
```
🎵 Audio: 150 buffers, 1 channels, 4410 frames  [Audio buffers captured]
⚠️ No recognition result received                [But no speech detected]
🎵 Level: 0.0000 │                              [Zero audio input level]
```

**Root Cause:** System audio input is not properly configured - no actual audio data is reaching the applications.

### System-Level Solutions

#### macOS Audio Configuration

1. **Check Input Device Settings:**
   ```bash
   # Open System Preferences
   System Preferences → Sound → Input
   ```
   - Verify correct microphone is selected
   - Check input level meter shows activity when speaking
   - Adjust input volume if too low

2. **Test with Built-in Apps:**
   ```bash
   # Test microphone with Voice Memos or QuickTime
   Applications → Voice Memos → Record
   ```
   - If this doesn't work, it's a hardware/system issue
   - If it works, it's an application permissions issue

3. **Reset Audio System:**
   ```bash
   sudo killall coreaudiod  # Restart Core Audio daemon
   ```

4. **Check Audio MIDI Setup:**
   ```bash
   Applications → Utilities → Audio MIDI Setup
   ```
   - Verify input device format (44.1kHz recommended)
   - Check device is not muted or disabled

#### Hardware Verification

1. **Built-in Microphone:**
   - Check nothing is blocking microphone holes
   - Verify microphone not physically damaged
   - Test with external microphone to isolate hardware issues

2. **External Microphone:**
   - Ensure proper connection (USB/3.5mm)
   - Try different ports/adapters
   - Check microphone works on other devices

3. **Audio Interface Issues:**
   - Disconnect/reconnect audio interfaces
   - Check driver software is up to date
   - Verify interface isn't set to "monitor only" mode

#### Permission Issues

1. **Microphone Permissions:**
   ```bash
   System Preferences → Privacy & Security → Microphone
   ```
   - Enable for Terminal
   - Enable for Python (if listed)
   - Enable for your IDE if running from there

2. **Speech Recognition Permissions:**
   ```bash
   System Preferences → Privacy & Security → Speech Recognition  
   ```
   - Enable for Terminal
   - Enable for Python (if listed)

3. **Reset Permissions:**
   ```bash
   tccutil reset Microphone
   tccutil reset SpeechRecognition
   # Restart Terminal and try again
   ```

#### Advanced Diagnostics

1. **Check Audio Devices Programmatically:**
   ```bash
   python -c "
   import sounddevice as sd
   print('Input Devices:')
   devices = sd.query_devices()
   for i, device in enumerate(devices):
       if device['max_input_channels'] > 0:
           print(f'{i}: {device[\"name\"]} - {device[\"max_input_channels\"]} channels')
   "
   ```

2. **Test Specific Audio Device:**
   ```bash
   python -c "
   import sounddevice as sd
   import numpy as np
   
   device_id = 1  # Try different device IDs
   print(f'Testing device {device_id}...')
   
   def callback(indata, frames, time, status):
       volume = np.linalg.norm(indata) * 10
       print(f'Volume: {volume:.4f}', end='\r')
   
   with sd.InputStream(device=device_id, callback=callback):
       sd.sleep(5000)
   "
   ```

3. **Check Core Audio Logs:**
   ```bash
   # Monitor Core Audio system logs
   log stream --predicate 'subsystem == "com.apple.coreaudio"' --level debug
   ```

## Engine-Specific Issues

### macOS Native STT Issues

#### Permission Problems

**Symptoms:**
```
❌ Speech recognition access denied
🔐 Speech recognition authorization status: 1
```

**Solutions:**
1. **Manual Permission Grant:**
   - System Preferences → Privacy & Security → Speech Recognition
   - Add Terminal and enable

2. **Reset and Re-request:**
   ```bash
   tccutil reset SpeechRecognition
   python test_macos_stt.py  # Will trigger permission prompt
   ```

#### PyObjC Framework Issues

**Symptoms:**
```
❌ PyObjC Speech framework not available
ImportError: No module named 'Speech'
```

**Solutions:**
```bash
pip install pyobjc-framework-Speech pyobjc-framework-AVFoundation
# If using conda:
conda install pyobjc-framework-Speech pyobjc-framework-AVFoundation
```

#### Audio Engine State Issues

**Symptoms:**
```
⏱️ Recognition wait completed after 0.1s  [Immediate completion]
🔍 Delegate finished: True                 [But no result]
```

**Solutions:**
- **Fixed in v2.0.0**: Enhanced resource cleanup between attempts
- Update to latest version of STT system

### Vosk Engine Issues

#### sounddevice Installation

**Symptoms:**
```
⚠️ No audio recorded
ImportError: No module named 'sounddevice'
```

**Solutions:**
```bash
pip install sounddevice

# macOS additional requirements:
brew install portaudio  # If needed

# Linux additional requirements:
sudo apt-get install portaudio19-dev  # Ubuntu/Debian
```

#### Audio Device Selection

**Symptoms:**
```
🎤 Using audio device: Wrong Device Name
⚠️ No audio recorded
```

**Solutions:**
1. **List Available Devices:**
   ```bash
   python -c "import sounddevice as sd; print(sd.query_devices())"
   ```

2. **Force Specific Device:**
   ```python
   # In vosk_stt_engine.py, modify InputStream call:
   with sd.InputStream(
       device=1,  # Try different device indices: 0, 1, 2, etc.
       samplerate=self.sample_rate,
       ...
   ):
   ```

#### Model Download Issues

**Symptoms:**
```
❌ Vosk model not found: vosk-model-small-en-us-0.15
```

**Solutions:**
```bash
# Manual model download:
mkdir -p ~/.m1k3/vosk_models
cd ~/.m1k3/vosk_models
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
```

### Web Speech Engine Issues

#### SpeechRecognition Library

**Symptoms:**
```
ImportError: No module named 'speech_recognition'
```

**Solutions:**
```bash
pip install SpeechRecognition

# Additional dependencies for different backends:
pip install pyaudio          # For microphone access
pip install pocketsphinx     # For offline sphinx backend
```

#### Energy Threshold Issues

**Symptoms:**
```
🔧 Energy threshold: 0.0
⚠️ Listening timeout - no speech detected
```

**Solutions:**
- **Fixed in v2.0.0**: Automatic recognizer reinitialization
- The system now recreates recognizer objects between attempts

#### Google API Rate Limits

**Symptoms:**
```
❌ Google Speech Recognition error: quota exceeded
```

**Solutions:**
1. **Switch to offline backend:**
   ```python
   # Use sphinx backend instead of google
   engine = WebSpeechEngine("sphinx")
   ```

2. **Use API key:**
   ```python
   # Set Google API key (if available)
   engine.set_api_key("google", "YOUR_API_KEY")
   ```

### Whisper Engine Issues

#### Model Download

**Symptoms:**
```
❌ Whisper model 'base' not found
```

**Solutions:**
```bash
# Whisper models download automatically on first use
# Ensure internet connection for initial download
# Models cached in ~/.cache/whisper/
```

#### Memory Issues

**Symptoms:**
```
RuntimeError: CUDA out of memory
MemoryError: Unable to allocate array
```

**Solutions:**
1. **Use smaller model:**
   ```bash
   python cli.py --stt-engine whisper --stt-model tiny
   ```

2. **CPU-only inference:**
   ```python
   # Disable GPU acceleration
   import os
   os.environ["CUDA_VISIBLE_DEVICES"] = "-1"
   ```

## Network and Dependencies

### Internet Connectivity Issues

**Affects:** Web Speech, Whisper (first run)

**Solutions:**
1. **Use offline engines:**
   ```bash
   python cli.py --stt-engine vosk    # Fully offline
   python cli.py --stt-engine native  # On-device (macOS)
   ```

2. **Pre-download models:**
   ```bash
   python -c "import whisper; whisper.load_model('base')"
   ```

### Package Installation Issues

#### pip Installation Problems

**Common Error:**
```
ERROR: Could not install packages due to an EnvironmentError
```

**Solutions:**
```bash
# Try with user flag:
pip install --user package_name

# Or use conda:
conda install package_name

# Update pip first:
pip install --upgrade pip
```

#### Conflicting Dependencies

**Symptoms:**
```
ImportError: cannot import name 'X' from 'Y'
```

**Solutions:**
```bash
# Create clean environment:
python -m venv stt_env
source stt_env/bin/activate  # Unix
# or: stt_env\Scripts\activate  # Windows
pip install -r requirements.txt
```

## Performance Issues

### High Latency

**Symptoms:**
- Long delays before recognition starts
- Slow response times

**Solutions:**
1. **Use faster engines:**
   ```bash
   python cli.py --stt-engine native  # Fastest (macOS)
   python cli.py --stt-engine vosk    # Good balance
   ```

2. **Optimize timeouts:**
   ```python
   # Shorter timeouts for faster response
   result = stt.listen_once(timeout=5.0, phrase_timeout=1.0)
   ```

3. **Pre-load models:**
   ```python
   # Initialize STT system at startup
   stt = STTManager()  # Models loaded once
   ```

### High Memory Usage

**Solutions:**
1. **Use lightweight engines:**
   ```bash
   python cli.py --stt-engine native  # 0MB
   python cli.py --stt-engine web     # 0MB (cloud)
   ```

2. **Smaller Whisper models:**
   ```bash
   python cli.py --stt-engine whisper --stt-model tiny  # 39MB vs 1.5GB
   ```

## Emergency Debugging

### Last Resort Diagnostics

If all else fails, run comprehensive diagnostics:

```bash
# 1. Test system audio
python audio_level_test.py

# 2. Check permissions
python check_speech_permissions.py  

# 3. Test all engines individually  
python stt_diagnostics.py

# 4. Test with verbose logging
M1K3_DEBUG=1 python test_macos_stt.py

# 5. Check system logs
log stream --predicate 'process == "python"' --level debug
```

### Generate Debug Report

```bash
#!/bin/bash
echo "=== M1K3 STT Debug Report ===" > debug_report.txt
echo "Date: $(date)" >> debug_report.txt
echo "Platform: $(uname -a)" >> debug_report.txt
echo "" >> debug_report.txt

echo "=== Python Environment ===" >> debug_report.txt
python --version >> debug_report.txt
pip list | grep -E "(speech|audio|vosk|whisper)" >> debug_report.txt
echo "" >> debug_report.txt

echo "=== Audio Devices ===" >> debug_report.txt
python -c "import sounddevice as sd; print(sd.query_devices())" >> debug_report.txt 2>&1
echo "" >> debug_report.txt

echo "=== Permission Check ===" >> debug_report.txt
python check_speech_permissions.py >> debug_report.txt 2>&1
echo "" >> debug_report.txt

echo "=== STT Diagnostics ===" >> debug_report.txt  
python stt_diagnostics.py >> debug_report.txt 2>&1

echo "Debug report saved to: debug_report.txt"
```

## Getting Help

### Community Support
- GitHub Issues: Include debug report and specific error messages
- Discussions: For general questions and optimization tips

### Before Reporting Issues
1. Run `python audio_level_test.py` - include output
2. Run `python stt_diagnostics.py` - include output  
3. Check `debug_report.txt` for system information
4. Specify which engine(s) are failing
5. Include exact error messages and steps to reproduce

### Known Working Configurations

**Tested Configurations:**
- macOS 12+ with PyObjC: ✅ Native STT working
- macOS 12+ with Vosk 0.3.45: ✅ Offline STT working
- Python 3.8-3.11 with SpeechRecognition: ✅ Web Speech working
- Ubuntu 20.04+ with sounddevice: ✅ Vosk working

**Common Working Setups:**
```bash
# Minimal setup (macOS):
pip install pyobjc-framework-Speech pyobjc-framework-AVFoundation

# Full setup (cross-platform):
pip install vosk sounddevice SpeechRecognition pyaudio

# Development setup:
pip install -r requirements.txt  # Includes all engines
```