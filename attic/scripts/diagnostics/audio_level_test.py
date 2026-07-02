#!/usr/bin/env python3
"""
Audio Level Monitor - Test microphone input levels
"""
import time
import numpy as np

def test_audio_levels():
    """Test microphone audio levels in real-time"""
    print("🎤 Audio Level Monitor")
    print("=" * 40)
    print("This tool will show real-time audio levels from your microphone")
    print("Speak or make noise to see if your microphone is working")
    print("Press Ctrl+C to stop")
    print("=" * 40)
    
    try:
        import sounddevice as sd
        
        # Get device info
        try:
            device_info = sd.query_devices(kind='input')
            print(f"🎤 Input Device: {device_info['name']}")
            print(f"📊 Sample Rate: {device_info['default_samplerate']}Hz")
            print(f"📡 Channels: {device_info['max_input_channels']}")
        except Exception as e:
            print(f"⚠️ Device info error: {e}")
        
        print("\n🔊 Audio Level Monitor Active:")
        print("   ████████████████████████████████████████████████ (loud)")
        print("   ██████████████████████ (moderate)")  
        print("   ██████ (quiet)")
        print("   │ (silence)")
        print()
        
        def audio_callback(indata, frames, time, status):
            """Real-time audio level callback"""
            if status:
                print(f"⚠️ Audio status: {status}")
            
            # Calculate RMS volume
            volume_norm = np.linalg.norm(indata) * 10
            volume_rms = np.sqrt(np.mean(indata ** 2))
            
            # Create visual bar
            bar_length = int(min(50, volume_norm * 100))
            bar = "█" * bar_length + "│"
            
            # Print level with overwrite
            print(f"\r🎵 Level: {volume_rms:.4f} {bar:<50}", end="", flush=True)
        
        # Start audio stream
        with sd.InputStream(
            callback=audio_callback,
            channels=1,
            samplerate=16000,
            blocksize=1024
        ):
            print("🎤 Monitoring... (Ctrl+C to stop)")
            while True:
                sd.sleep(100)
                
    except ImportError:
        print("❌ sounddevice not available, trying speech_recognition...")
        try:
            test_speech_recognition_levels()
        except ImportError:
            print("❌ No audio libraries available")
            return False
    except KeyboardInterrupt:
        print("\n\n✅ Audio level test stopped")
        return True
    except Exception as e:
        print(f"\n❌ Audio level test failed: {e}")
        return False

def test_speech_recognition_levels():
    """Test audio levels using speech_recognition library"""
    import speech_recognition as sr
    
    r = sr.Recognizer()
    m = sr.Microphone()
    
    print("🎤 Testing with SpeechRecognition library...")
    
    with m as source:
        print("🔧 Calibrating...")
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

if __name__ == "__main__":
    print("🧪 Audio Input Verification Tool")
    success = test_audio_levels()
    
    if not success:
        print("\n💡 Troubleshooting:")
        print("   1. Check microphone is connected and not muted")
        print("   2. Verify microphone permissions for Terminal")
        print("   3. Test microphone with other applications")
        print("   4. Check System Preferences > Sound > Input")
    
    exit(0 if success else 1)