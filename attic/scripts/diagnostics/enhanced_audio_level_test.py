#!/usr/bin/env python3
"""
Enhanced Audio Level Monitor - Test microphone input levels with visual feedback
Addresses the 0.0000 input levels bug with detailed diagnostics
"""
import time
import sys
import numpy as np
from typing import Dict, List, Optional

def test_audio_levels():
    """Test microphone audio levels in real-time with enhanced diagnostics"""
    print("🎤 Enhanced Audio Level Monitor v2.0")
    print("=" * 60)
    print("Advanced microphone diagnostic tool to detect 0.0000 levels bug")
    print("Provides real-time visual feedback and detailed analysis")
    print("Press Ctrl+C to stop monitoring")
    print("=" * 60)
    
    try:
        import sounddevice as sd
        
        # Enhanced device enumeration
        print("🔍 Detecting audio devices...")
        devices = sd.query_devices()
        input_devices = [d for d in devices if d['max_input_channels'] > 0]
        
        print(f"📊 Found {len(input_devices)} input device(s):")
        for i, device in enumerate(input_devices):
            print(f"   {i}: {device['name']} ({device['max_input_channels']} ch, {device['default_samplerate']:.0f}Hz)")
        
        # Get default input device info
        try:
            default_device = sd.query_devices(kind='input')
            print(f"\n🎤 Using Default Input: {default_device['name']}")
            print(f"📊 Sample Rate: {default_device['default_samplerate']:.0f}Hz")
            print(f"📡 Channels: {default_device['max_input_channels']}")
            print(f"🔧 Host API: {sd.query_hostapis(default_device['hostapi'])['name']}")
        except Exception as e:
            print(f"⚠️ Device info error: {e}")
            return False
        
        # Enhanced visual guide
        print("\n🎨 Enhanced Visual Audio Level Guide:")
        print("   🔴 ████████████████████████████████████████████████ HIGH (>0.1)")
        print("   🟡 █████████████████████████ MODERATE (>0.01)")  
        print("   🟢 ██████████ NORMAL (>0.001)")
        print("   🔵 ███ QUIET (>0.0001)")
        print("   ⚫ │ SILENCE/BUG (≤0.0001) ← 0.0000 LEVELS BUG")
        print()
        
        # Statistics tracking
        stats = {
            'total_samples': 0,
            'zero_samples': 0,
            'low_samples': 0,
            'normal_samples': 0,
            'high_samples': 0,
            'max_level': 0.0,
            'avg_level': 0.0,
            'level_history': []
        }
        
        def enhanced_audio_callback(indata, frames, time, status):
            """Enhanced real-time audio level callback with bug detection"""
            if status:
                print(f"\n⚠️ Audio status: {status}")
            
            # Enhanced level calculations
            volume_rms = np.sqrt(np.mean(indata ** 2))
            volume_peak = np.max(np.abs(indata))
            volume_mean = np.mean(np.abs(indata))
            
            # Update statistics
            stats['total_samples'] += 1
            stats['max_level'] = max(stats['max_level'], volume_rms)
            stats['level_history'].append(volume_rms)
            
            # Keep only last 50 samples for average
            if len(stats['level_history']) > 50:
                stats['level_history'].pop(0)
            stats['avg_level'] = np.mean(stats['level_history'])
            
            # Categorize levels for bug detection
            if volume_rms <= 0.0001:
                stats['zero_samples'] += 1
                level_emoji = "⚫"
                level_color = "SILENCE/BUG"
                bar_char = "│"
                bar_length = 0
            elif volume_rms <= 0.001:
                stats['low_samples'] += 1
                level_emoji = "🔵"
                level_color = "QUIET"
                bar_char = "▌"
                bar_length = max(1, int(volume_rms * 10000))
            elif volume_rms <= 0.01:
                stats['normal_samples'] += 1
                level_emoji = "🟢"
                level_color = "NORMAL"
                bar_char = "█"
                bar_length = max(1, int(volume_rms * 1000))
            elif volume_rms <= 0.1:
                stats['high_samples'] += 1
                level_emoji = "🟡"
                level_color = "MODERATE"
                bar_char = "█"
                bar_length = min(30, max(1, int(volume_rms * 100)))
            else:
                stats['high_samples'] += 1
                level_emoji = "🔴"
                level_color = "HIGH"
                bar_char = "█"
                bar_length = min(50, max(1, int(volume_rms * 50)))
            
            # Create enhanced visual bar
            bar = bar_char * bar_length
            
            # Calculate percentages
            total = max(1, stats['total_samples'])  # Prevent division by zero
            zero_pct = (stats['zero_samples'] / total) * 100
            
            # Enhanced output with multiple metrics
            status_line = (
                f"\r{level_emoji} RMS:{volume_rms:.6f} "
                f"PEAK:{volume_peak:.4f} "
                f"AVG:{stats['avg_level']:.6f} "
                f"MAX:{stats['max_level']:.6f} "
                f"│{bar:<50}│ "
                f"{level_color} "
            )
            
            # Add bug warning for excessive zero levels
            if zero_pct > 50 and stats['total_samples'] > 10:
                status_line += f"🚨 ZERO:{zero_pct:.0f}%"
            elif zero_pct > 20 and stats['total_samples'] > 5:
                status_line += f"⚠️ ZERO:{zero_pct:.0f}%"
            
            print(status_line, end="", flush=True)
            
            # Periodic detailed analysis
            if stats['total_samples'] % 100 == 0:  # Every ~10 seconds at 10Hz
                print(f"\n📊 Analysis Update (Sample #{stats['total_samples']}):")
                print(f"   Zero levels: {stats['zero_samples']}/{stats['total_samples']} ({zero_pct:.1f}%)")
                print(f"   Average level: {stats['avg_level']:.6f}")
                print(f"   Peak level: {stats['max_level']:.6f}")
                
                # Bug detection warning
                if zero_pct > 70:
                    print("   🚨 CRITICAL: Very high zero-level percentage!")
                    print("   💡 This strongly indicates the 0.0000 input levels bug")
                    print("   💡 Check microphone permissions and hardware connection")
                elif zero_pct > 30:
                    print("   ⚠️ WARNING: High zero-level percentage detected")
                    print("   💡 Possible microphone or driver issues")
        
        # Start enhanced audio stream
        print("🎤 Starting enhanced audio monitoring...")
        print("💡 Speak clearly or tap the microphone to test levels")
        print()
        
        with sd.InputStream(
            callback=enhanced_audio_callback,
            channels=1,
            samplerate=16000,
            blocksize=1024,
            dtype=np.float32
        ):
            print("🔄 Enhanced monitoring active... (Ctrl+C to stop)")
            try:
                while True:
                    sd.sleep(100)
            except KeyboardInterrupt:
                print("\n\n📋 Final Analysis Report:")
                print("=" * 50)
                
                total = stats['total_samples']
                if total > 0:
                    zero_pct = (stats['zero_samples'] / total) * 100
                    low_pct = (stats['low_samples'] / total) * 100
                    normal_pct = (stats['normal_samples'] / total) * 100
                    high_pct = (stats['high_samples'] / total) * 100
                    
                    print(f"📊 Sample Analysis ({total} total samples):")
                    print(f"   ⚫ Zero/Silence: {stats['zero_samples']} ({zero_pct:.1f}%)")
                    print(f"   🔵 Quiet: {stats['low_samples']} ({low_pct:.1f}%)")
                    print(f"   🟢 Normal: {stats['normal_samples']} ({normal_pct:.1f}%)")
                    print(f"   🟡🔴 High: {stats['high_samples']} ({high_pct:.1f}%)")
                    print()
                    print(f"📈 Level Statistics:")
                    print(f"   Maximum RMS: {stats['max_level']:.6f}")
                    print(f"   Average RMS: {stats['avg_level']:.6f}")
                    print()
                    
                    # Final diagnosis
                    if zero_pct > 80:
                        print("🚨 DIAGNOSIS: CRITICAL - 0.0000 Input Levels Bug Detected!")
                        print("💡 Microphone hardware detected but no audio data received")
                        print("💡 Required fixes:")
                        print("   1. Check System Preferences > Sound > Input volume")
                        print("   2. Grant microphone access in Privacy & Security settings")
                        print("   3. Try different microphone or restart audio system")
                        return False
                    elif zero_pct > 50:
                        print("⚠️ DIAGNOSIS: WARNING - High zero-level rate detected")
                        print("💡 Possible intermittent microphone issues")
                        print("💡 Check microphone connection and settings")
                        return False
                    elif stats['max_level'] > 0.001:
                        print("✅ DIAGNOSIS: GOOD - Microphone appears to be working")
                        print("💡 Audio levels detected successfully")
                        return True
                    else:
                        print("⚠️ DIAGNOSIS: POOR - Very low audio levels throughout test")
                        print("💡 Microphone may be working but levels are too low")
                        print("💡 Increase input volume in System Preferences > Sound")
                        return False
                else:
                    print("❌ No samples collected")
                    return False
                
    except ImportError:
        print("❌ sounddevice not available, trying speech_recognition...")
        try:
            test_speech_recognition_levels()
            return True
        except ImportError:
            print("❌ No audio libraries available")
            print("💡 Install with: pip install sounddevice numpy")
            return False
    except KeyboardInterrupt:
        print("\n\n✅ Enhanced audio level test stopped")
        return True
    except Exception as e:
        print(f"\n❌ Enhanced audio level test failed: {e}")
        return False

def test_speech_recognition_levels():
    """Test audio levels using speech_recognition library as fallback"""
    import speech_recognition as sr
    
    r = sr.Recognizer()
    m = sr.Microphone()
    
    print("🎤 Testing with SpeechRecognition library (fallback mode)...")
    
    with m as source:
        print("🔧 Calibrating ambient noise...")
        r.adjust_for_ambient_noise(source, duration=1)
        print(f"📊 Energy threshold: {r.energy_threshold:.1f}")
        
        print("🎤 Say something for 5 seconds...")
        
        try:
            # Try to capture audio
            audio = r.listen(source, timeout=5, phrase_time_limit=3)
            print("✅ Audio captured successfully!")
            
            # Try recognition
            try:
                text = r.recognize_google(audio)
                print(f"🎉 Recognized: '{text}'")
            except sr.UnknownValueError:
                print("⚠️ Audio captured but speech not understood")
            except sr.RequestError as e:
                print(f"⚠️ Recognition service error: {e}")
                
        except sr.WaitTimeoutError:
            print("⏰ No audio detected - check microphone levels")

def run_comprehensive_audio_test():
    """Run comprehensive audio testing suite"""
    print("🧪 Comprehensive Audio System Test Suite")
    print("=" * 60)
    
    success = test_audio_levels()
    
    if not success:
        print("\n💡 Enhanced Troubleshooting Guide:")
        print("   1. Check microphone is connected and not muted")
        print("   2. Verify microphone permissions for Terminal/Python")
        print("   3. Test microphone with other applications (Voice Memos, etc.)")
        print("   4. Check System Preferences > Sound > Input volume levels")
        print("   5. Try running: python microphone_doctor.py")
        print("   6. For 0.0000 levels bug, restart Core Audio:")
        print("      sudo killall coreaudiod")
    
    return success

if __name__ == "__main__":
    print("🧪 Enhanced Audio Input Verification Tool")
    success = run_comprehensive_audio_test()
    exit(0 if success else 1)