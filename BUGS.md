# M1K3 Known Issues & Bugs

This document tracks known bugs and issues in the M1K3 system.

## 🐛 Active Bugs

### Speech Cutoff Issue (HIGH PRIORITY)
**Status**: Open  
**Reported**: 2025-08-21  
**Severity**: High - Affects user experience  

**Description**:  
Speech synthesis cuts off at the end of sentences, losing the final words or syllables despite multiple attempted fixes.

**Symptoms**:
- Last words of sentences are truncated
- Issue persists across all voice profiles
- Occurs with both short and long text inputs
- Consistent across different audio hardware

**Investigation Summary**:
- Added 950ms total padding (100ms pre + 500ms main + 200ms post + 150ms fade)
- Implemented hardware-aware timing with latency detection
- Added smart fade-out that preserves speech content
- Debug analysis shows sufficient padding (0.70s) vs hardware latency (0.195s)
- KittenTTS raw output has high tail amplitude (0.117354) indicating active speech at end

**Root Cause Hypotheses**:
1. **KittenTTS Model Issue**: The underlying KittenTTS model may be generating truncated audio
2. **SoundDevice Buffer Issue**: `sd.wait()` may not wait for complete hardware playback
3. **Audio Pipeline Corruption**: Effects pipeline may be modifying/truncating the ending
4. **Platform-Specific Issue**: macOS/hardware-specific audio system behavior

**Attempted Fixes**:
- ❌ Increased padding from 300ms → 500ms → 950ms total
- ❌ Added hardware-aware timing calculations  
- ❌ Implemented robust playback with manual delays
- ❌ Smart fade-out that preserves speech content
- ❌ Double-padding (before + after effects)
- ❌ Post-effects final padding

**Files Affected**:
- `unified_voice_engine.py` - Primary audio processing
- `audio_effects.py` - Effects pipeline
- `voice_config.json` - Voice profile configurations

**Workarounds**:
- None currently available
- Issue affects all voice profiles and text lengths

**Next Steps**:
1. Test with alternative TTS engines (Coqui TTS) to isolate KittenTTS
2. Investigate raw KittenTTS output for inherent truncation
3. Test with different audio backends (PyAudio, system TTS)
4. Platform-specific audio system investigation

---

## 🔧 Resolved Issues

### SoundManager Missing Method Error
**Status**: Fixed (2025-08-21)  
**Issue**: `'SoundManager' object has no attribute 'play_system_event_sound'`  
**Fix**: Moved method from ContextualSoundManager to base SoundManager class  
**Files**: `sound_manager.py`

### Avatar Server Startup Issues  
**Status**: Fixed (2025-08-21)  
**Issue**: Avatar server not properly coordinating with voice engine  
**Fix**: Enhanced integration and error handling  
**Files**: `avatar_server.py`, `cli.py`

---

## 📝 Bug Reporting Guidelines

When reporting bugs, please include:
1. **Steps to reproduce**
2. **Expected vs actual behavior**  
3. **System information** (OS, Python version, hardware)
4. **Error messages or logs**
5. **Audio samples** (if audio-related)

## 🚀 Feature Requests

Currently no pending feature requests.

---

*Last Updated: 2025-08-21*