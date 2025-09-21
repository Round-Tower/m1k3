#!/usr/bin/env python3
"""
M1K3 CLI Application - Refactored Modular Version
Command-line interface with modular architecture, proper logging, and clean separation of concerns
"""

import sys
import argparse
from pathlib import Path

# Add src directory to path for imports
sys.path.insert(0, str(Path(__file__).parent / "src"))

# Import the refactored CLI core
from src.cli.cli_core import M1K3CLICore
from src.cli.cli_logging import setup_cli_logging, log_info, log_error


def main():
    """Main entry point for M1K3 CLI"""
    # Setup logging first
    setup_cli_logging()
    
    # Parse command line arguments
    parser = argparse.ArgumentParser(description="M1K3 Local AI CLI with Voice")
    parser.add_argument("--query", "-q", help="Single query mode")
    parser.add_argument("--download-only", action="store_true", help="Download model and exit")
    parser.add_argument("--model", default="SmolLM-135M-Q4_K_M", help="Model to download")
    parser.add_argument("--no-voice", action="store_true", help="Disable voice synthesis")
    parser.add_argument("--test-voice", action="store_true", help="Test voice synthesis only")
    parser.add_argument("--no-avatar", action="store_true", help="Disable auto-start of avatar server")
    parser.add_argument("--avatar-port", type=int, default=8080, help="Avatar server HTTP port (default: 8080)")
    parser.add_argument("--no-browser", action="store_true", help="Don't auto-open browser when starting avatar server")
    parser.add_argument("--transparency", choices=["off", "basic", "detailed", "full", "debug"], 
                       default="basic", help="Set model transparency level")
    parser.add_argument("--rag", action="store_true", 
                       help="Enable RAG (Retrieval-Augmented Generation) with comprehensive knowledge base")
    
    # VibeVoice TTS options
    tts_group = parser.add_argument_group('VibeVoice TTS Options')
    tts_group.add_argument("--tts-engine", 
                          choices=["auto", "vibevoice", "kitten", "fallback"],
                          default="auto",
                          help="Text-to-Speech engine: auto (smart default), vibevoice (Microsoft's frontier TTS), kitten (lightweight), fallback (system)")
    tts_group.add_argument("--no-vibevoice", action="store_true",
                          help="Disable VibeVoice entirely for faster startup (use KittenTTS instead)")
    tts_group.add_argument("--vibevoice-model",
                          choices=["1.5B", "7B"],
                          default="1.5B",
                          help="VibeVoice model variant: 1.5B (64K context, 90min), 7B (32K context, 45min)")
    tts_group.add_argument("--multi-speaker", 
                          action="store_true",
                          help="Enable multi-speaker conversation mode (up to 4 speakers)")
    tts_group.add_argument("--continuous-mode",
                          action="store_true", 
                          help="Enable continuous synthesis mode (up to 90 minutes)")
    tts_group.add_argument("--speakers",
                          nargs="+",
                          default=["Alice"],
                          help="Specify speaker names for multi-speaker mode (e.g., --speakers Alice Bob)")
    tts_group.add_argument("--voice-profile",
                          choices=["natural", "assistant", "broadcast", "terminal", "debug", "minimal", 
                                  "conversational", "narrative", "assistant_duo"],
                          default="natural",
                          help="Voice profile: natural/assistant/broadcast/terminal/debug/minimal (KittenTTS) or conversational/narrative/assistant_duo (VibeVoice)")
    tts_group.add_argument("--vibevoice-quality",
                          choices=["fast", "balanced", "quality"],
                          default="balanced", 
                          help="VibeVoice generation quality: fast (5 steps), balanced (7 steps), quality (10 steps)")
    
    # Speech-to-Text (STT) options
    stt_group = parser.add_argument_group('Speech Recognition Options')
    stt_group.add_argument("--stt-engine", 
                          choices=["auto", "native", "vosk", "web", "whisper", "none"],
                          default="web",
                          help="Speech recognition engine: auto (smart default), native (macOS only), vosk (offline), web (cloud), whisper (heavy), none (disable)")
    stt_group.add_argument("--stt-model", 
                          default="vosk-model-small-en-us-0.15",
                          help="STT model name (for Vosk/Whisper engines)")
    stt_group.add_argument("--stt-lang", 
                          default="en-US",
                          help="Speech recognition language (default: en-US)")
    stt_group.add_argument("--voice-first", 
                          action="store_true",
                          help="Enable voice-first input mode (voice by default, text on typing)")
    
    # Streaming and conversation flow options
    streaming_group = parser.add_argument_group('Streaming & Conversation Options')
    streaming_group.add_argument("--streaming", action="store_true",
                                help="Enable streaming TTS/STT for natural conversation flow")
    streaming_group.add_argument("--conversation-mode", action="store_true",
                                help="Enable natural conversation flow with turn-taking")
    streaming_group.add_argument("--chunk-size", type=int, default=20,
                                help="TTS chunk size for streaming (words per chunk, default: 20)")
    streaming_group.add_argument("--chunk-timeout", type=float, default=0.5,
                                help="TTS chunk timeout in seconds (default: 0.5)")
    streaming_group.add_argument("--response-delay", type=float, default=0.2,
                                help="Delay before AI response in conversation mode (default: 0.2)")
    streaming_group.add_argument("--enable-interruptions", action="store_true", default=True,
                                help="Allow user to interrupt AI speech (default: True)")
    
    # Diagnostic options
    diagnostic_group = parser.add_argument_group('Diagnostic Options')
    diagnostic_group.add_argument("--mic-test", action="store_true",
                                 help="Run microphone diagnostic test")
    diagnostic_group.add_argument("--enhanced-mic-test", action="store_true", 
                                 help="Run enhanced microphone diagnostic with visual feedback")
    diagnostic_group.add_argument("--streaming-test", action="store_true",
                                 help="Run streaming TTS/STT test")
    diagnostic_group.add_argument("--audio-diagnostic", action="store_true",
                                 help="Run comprehensive audio device diagnostic for Bluetooth issues")
    
    # Audio device options
    audio_group = parser.add_argument_group('Audio Device Options')
    audio_group.add_argument("--force-internal-mic", action="store_true",
                            help="Force use of internal microphone (workaround for Bluetooth issues)")
    audio_group.add_argument("--detect-audio-devices", action="store_true",
                            help="Detect and display available audio devices")
    
    args = parser.parse_args()
    
    # Handle special modes
    if args.download_only:
        return handle_download_only(args.model)
    
    if args.test_voice:
        return handle_voice_test()
    
    if args.mic_test:
        return handle_microphone_test()
    
    if args.enhanced_mic_test:
        return handle_enhanced_microphone_test()
    
    if args.streaming_test:
        return handle_streaming_test()
    
    if args.audio_diagnostic:
        return handle_audio_diagnostic()
    
    if args.detect_audio_devices:
        return handle_detect_audio_devices()
    
    # Create CLI instance with configuration
    cli = M1K3CLICore(
        voice_enabled=not args.no_voice,
        auto_avatar=not args.no_avatar,
        avatar_port=args.avatar_port,
        open_browser=not args.no_browser,
        transparency_level=args.transparency,
        rag_enabled=args.rag,
        stt_engine=args.stt_engine,
        stt_model=args.stt_model,
        stt_language=args.stt_lang,
        voice_first=args.voice_first,
        force_internal_mic=args.force_internal_mic,
        # New streaming options
        streaming_enabled=args.streaming,
        conversation_mode=args.conversation_mode,
        chunk_size=args.chunk_size,
        chunk_timeout=args.chunk_timeout,
        response_delay=args.response_delay,
        enable_interruptions=args.enable_interruptions,
        # TTS/VibeVoice options
        tts_engine=args.tts_engine,
        vibevoice_model=args.vibevoice_model,
        vibevoice_quality=args.vibevoice_quality,
        voice_profile=args.voice_profile,
        speakers=args.speakers,
        multi_speaker=args.multi_speaker
    )
    
    # Run in appropriate mode
    if args.query:
        log_info(f"Running single query mode: {args.query}")
        return cli.run_single_query(args.query)
    else:
        log_info("Starting interactive mode")
        return cli.run_interactive()


def handle_download_only(model_name: str) -> int:
    """Handle download-only mode"""
    try:
        from src.models.loaders.download_model import download_model
        
        log_info(f"Downloading {model_name}...")
        print(f"Downloading {model_name}...")
        
        model_path = download_model(model_name)
        if model_path:
            print(f"Model downloaded successfully: {model_path}")
            log_info(f"Model downloaded: {model_path}")
            return 0
        else:
            print("Failed to download model")
            log_error("Model download failed")
            return 1
            
    except ImportError as e:
        print(f"❌ Download functionality not available: {e}")
        log_error(f"Download functionality not available: {e}")
        return 1
    except Exception as e:
        print(f"❌ Download failed: {e}")
        log_error(f"Download failed: {e}")
        return 1


def handle_voice_test() -> int:
    """Handle voice test mode"""
    try:
        from src.engines.voice.voice_engine import create_voice_engine
        
        print("🔊 Testing voice synthesis...")
        log_info("Starting voice synthesis test")
        
        engine = create_voice_engine()
        if engine and engine.load_model():
            engine.synthesize_and_play("Voice synthesis test successful! M1K3 is ready to speak.", background=False)
            print("✅ Voice synthesis test completed successfully")
            log_info("Voice synthesis test completed successfully")
            return 0
        else:
            print("❌ Failed to load voice model")
            log_error("Failed to load voice model")
            return 1
            
    except ImportError as e:
        print(f"❌ Voice engine not available: {e}")
        log_error(f"Voice engine not available: {e}")
        return 1
    except Exception as e:
        print(f"❌ Voice test failed: {e}")
        log_error(f"Voice test failed: {e}")
        return 1


def handle_microphone_test() -> int:
    """Handle microphone diagnostic test"""
    try:
        log_info("Running microphone diagnostic test...")
        
        from microphone_doctor import MicrophoneDoctor
        
        doctor = MicrophoneDoctor()
        is_healthy = doctor.run_full_diagnostic()
        
        return 0 if is_healthy else 1
        
    except Exception as e:
        print(f"Microphone test failed: {e}")
        log_error(f"Microphone test failed: {e}")
        return 1


def handle_enhanced_microphone_test() -> int:
    """Handle enhanced microphone test with visual feedback"""
    try:
        log_info("Running enhanced microphone test...")
        
        from enhanced_audio_level_test import run_comprehensive_audio_test
        
        success = run_comprehensive_audio_test()
        return 0 if success else 1
        
    except Exception as e:
        print(f"Enhanced microphone test failed: {e}")
        log_error(f"Enhanced microphone test failed: {e}")
        return 1


def handle_streaming_test() -> int:
    """Handle streaming TTS/STT test"""
    try:
        log_info("Running streaming test...")
        print("🧪 Testing Streaming TTS/STT System")
        print("=" * 50)
        
        # Test streaming TTS engine
        from src.engines.tts.streaming_tts_engine import create_streaming_tts_engine
        
        streaming_tts = create_streaming_tts_engine(chunk_size=10, chunk_timeout=1.0)
        
        def mock_ai_response():
            """Simulate AI response tokens"""
            response = "This is a comprehensive test of the streaming text-to-speech system. It demonstrates how AI responses can be synthesized and played in real-time as tokens arrive, creating a much more natural conversation experience."
            
            for word in response.split():
                yield word
                import time
                time.sleep(0.1)  # Simulate AI generation delay
        
        print("🎤 Testing streaming TTS with mock AI response...")
        
        chunks = list(streaming_tts.process_token_stream(mock_ai_response()))
        
        print(f"\n✅ Streaming test completed successfully!")
        print(f"   Chunks generated: {len(chunks)}")
        
        stats = streaming_tts.get_stats()
        print("📊 Streaming Stats:")
        for key, value in stats.items():
            print(f"   {key}: {value}")
        
        streaming_tts.cleanup()
        
        return 0
        
    except Exception as e:
        print(f"Streaming test failed: {e}")
        log_error(f"Streaming test failed: {e}")
        return 1


def handle_audio_diagnostic() -> int:
    """Handle comprehensive audio device diagnostic"""
    try:
        log_info("Running comprehensive audio device diagnostic...")
        
        print("🎧 M1K3 Audio Device Diagnostic")
        print("=" * 50)
        
        # Test macOS STT engine with detailed diagnostics
        try:
            from src.engines.stt.macos_stt_engine import MacOSSTTEngine
            
            print("📱 Testing macOS Native STT Engine:")
            print("-" * 30)
            
            macos_engine = MacOSSTTEngine()
            
            if macos_engine.initialize():
                print("✅ macOS STT engine initialized successfully")
                print(f"   📊 Sample rate: {macos_engine.sample_rate}Hz")
                print(f"   📊 Device type: {macos_engine.device_type}")
                print(f"   📊 Detected rate: {macos_engine.detected_sample_rate}Hz")
                
                # Test sample rate detection
                print("\n🔍 Testing sample rate detection...")
                macos_engine._detect_hardware_sample_rate()
                
                # Test permissions
                print("\n🔐 Testing microphone permissions...")
                has_perms = macos_engine._check_microphone_permissions()
                print(f"   {'✅' if has_perms else '❌'} Microphone permissions: {has_perms}")
                
                # Test hardware
                print("\n🎤 Testing microphone hardware...")
                hw_works = macos_engine._test_microphone_hardware()
                print(f"   {'✅' if hw_works else '❌'} Hardware test: {hw_works}")
                
                macos_engine.cleanup()
            else:
                print("❌ macOS STT engine initialization failed")
                print("   💡 This may indicate Bluetooth audio issues")
        
        except Exception as e:
            print(f"❌ macOS STT diagnostic failed: {e}")
        
        # Test Vosk engine as fallback
        print(f"\n🤖 Testing Vosk STT Engine (fallback):")
        print("-" * 30)
        
        try:
            from src.engines.stt.vosk_stt_engine import VoskSTTEngine
            
            vosk_engine = VoskSTTEngine()
            if vosk_engine.initialize():
                print("✅ Vosk STT engine available as fallback")
                print(f"   📊 Sample rate: {vosk_engine.sample_rate}Hz")
                vosk_engine.cleanup()
            else:
                print("❌ Vosk STT engine not available")
        except Exception as e:
            print(f"❌ Vosk STT diagnostic failed: {e}")
        
        print(f"\n💡 Recommendations:")
        print("- If macOS STT fails with 'format.sampleRate' errors, use --stt-engine vosk")
        print("- For Bluetooth issues, try --force-internal-mic")
        print("- Check System Preferences > Sound for device settings")
        
        return 0
        
    except Exception as e:
        print(f"Audio diagnostic failed: {e}")
        log_error(f"Audio diagnostic failed: {e}")
        return 1


def handle_detect_audio_devices() -> int:
    """Handle audio device detection and display"""
    try:
        log_info("Detecting audio devices...")
        
        print("🎧 M1K3 Audio Device Detection")
        print("=" * 40)
        
        # Try to detect devices using macOS APIs
        try:
            import sys
            sys.path.insert(0, str(Path(__file__).parent / "src"))
            
            from src.engines.stt.macos_stt_engine import PYOBJC_AVAILABLE
            
            if PYOBJC_AVAILABLE:
                from AVFoundation import AVAudioEngine
                
                print("🎤 Analyzing current audio input device:")
                
                # Create temporary audio engine to detect format
                test_engine = AVAudioEngine.alloc().init()
                input_node = test_engine.inputNode()
                
                if input_node:
                    input_format = input_node.outputFormatForBus_(0)
                    sample_rate = float(input_format.sampleRate())
                    channels = int(input_format.channelCount())
                    
                    print(f"   📊 Sample rate: {sample_rate}Hz")
                    print(f"   📊 Channels: {channels}")
                    
                    # Analyze device type
                    if sample_rate <= 8000:
                        device_type = "Bluetooth (low quality)"
                        quality = "⚠️ POOR"
                    elif sample_rate <= 16000:
                        device_type = "Bluetooth (standard) or Internal (low power)"
                        quality = "⚠️ LIMITED" 
                    elif sample_rate >= 44100:
                        device_type = "Internal microphone or External interface"
                        quality = "✅ GOOD"
                    else:
                        device_type = "Unknown"
                        quality = "❓ UNKNOWN"
                    
                    print(f"   🎧 Device type: {device_type}")
                    print(f"   📈 Quality: {quality}")
                    
                    if sample_rate <= 16000:
                        print(f"\n💡 Low sample rate detected:")
                        print(f"   - This may cause speech recognition issues")
                        print(f"   - Try switching to internal microphone")
                        print(f"   - Or use: --stt-engine vosk for better compatibility")
                else:
                    print("❌ No input device detected")
            else:
                print("❌ PyObjC not available - cannot detect audio devices")
                print("💡 Install with: pip install pyobjc-framework-AVFoundation")
        
        except Exception as e:
            print(f"❌ Device detection failed: {e}")
        
        print(f"\n🔧 Audio Settings Tips:")
        print("- Open System Preferences > Sound > Input to change microphones") 
        print("- Use Audio MIDI Setup.app for advanced device configuration")
        print("- Disconnect/reconnect Bluetooth devices to reset audio mode")
        
        return 0
        
    except Exception as e:
        print(f"Device detection failed: {e}")
        log_error(f"Device detection failed: {e}")
        return 1


if __name__ == "__main__":
    try:
        exit_code = main()
        sys.exit(exit_code)
    except KeyboardInterrupt:
        log_info("Application interrupted by user")
        print("\n👋 Goodbye!")
        sys.exit(0)
    except Exception as e:
        log_error(f"Unexpected error in main: {e}")
        print(f"❌ Unexpected error: {e}")
        sys.exit(1)