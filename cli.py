#!/usr/bin/env python3
"""
M1K3 CLI Application - Refactored Modular Version
Command-line interface with modular architecture, proper logging, and clean separation of concerns
"""

import sys
import time
import argparse
import subprocess
import atexit
from pathlib import Path

# Add src directory to path for imports
sys.path.insert(0, str(Path(__file__).parent / "src"))

# Import the refactored CLI core
from src.cli.cli_core import M1K3CLICore
from src.cli.cli_logging import setup_cli_logging, log_info, log_error

# Global variable to hold the server process
mcp_server_process = None

def cleanup_mcp_server():
    """Ensure the MCP server process is terminated on exit."""
    global mcp_server_process
    if mcp_server_process:
        log_info("Terminating MCP server...")
        mcp_server_process.terminate()
        mcp_server_process.wait()
        print("\nMCP server terminated.")

atexit.register(cleanup_mcp_server)

def main():
    """Main entry point for M1K3 CLI"""
    global mcp_server_process
    # Setup logging first
    setup_cli_logging()
    
    # Parse command line arguments
    parser = argparse.ArgumentParser(description="M1K3 Local AI CLI with Voice")
    parser.add_argument("--query", "-q", help="Single query mode")
    parser.add_argument("--download-only", action="store_true", help="Download model and exit")
    parser.add_argument("--model", default="SmolLM-135M-Q4_K_M", help="Model to download")
    parser.add_argument("--no-voice", action="store_true", help="Disable voice synthesis")
    parser.add_argument("--test-voice", action="store_true", help="Test voice synthesis only")
    parser.add_argument("--test-realtime-tts", action="store_true", help="Test real-time TTS engines with audio demo")
    parser.add_argument("--no-avatar", action="store_true", help="Disable auto-start of avatar server")
    parser.add_argument("--avatar-port", type=int, default=8080, help="Avatar server HTTP port (default: 8080)")
    parser.add_argument("--no-browser", action="store_true", help="Don't auto-open browser when starting avatar server")
    parser.add_argument("--transparency", choices=["off", "basic", "detailed", "full", "debug"], 
                       default="basic", help="Set model transparency level")
    parser.add_argument("--rag", action="store_true", 
                       help="Enable RAG (Retrieval-Augmented Generation) with comprehensive knowledge base")
    parser.add_argument("--with-mcp-server", action="store_true",
                       help="Launch the Model Context Protocol server alongside the CLI.")
    
    # Real-time TTS options (Updated with new offline engines)
    tts_group = parser.add_argument_group('Real-Time TTS Options')
    tts_group.add_argument("--tts-engine",
                          choices=["auto", "piper", "espeak", "vibevoice", "kitten", "fallback"],
                          default="auto",
                          help="Text-to-Speech engine: auto (smart default), piper (ultra-fast neural, sub-50ms), espeak (instant formant, sub-10ms), vibevoice (long-form), kitten (lightweight), fallback (system)")
    tts_group.add_argument("--realtime-mode", action="store_true",
                          help="Enable ultra-fast real-time TTS optimized for chat (uses Piper by default)")
    tts_group.add_argument("--instant-mode", action="store_true",
                          help="Enable instant TTS for maximum speed (uses eSpeak, sub-10ms latency)")

    # Piper TTS options (NEW)
    piper_group = parser.add_argument_group('Piper TTS Options (Neural Real-Time)')
    piper_group.add_argument("--piper-voice",
                            choices=["en_US-lessac-medium", "en_US-lessac-low", "en_US-amy-medium", "en_US-ryan-medium"],
                            default="en_US-lessac-medium",
                            help="Piper voice selection: lessac-medium (balanced), lessac-low (fastest), amy-medium, ryan-medium")
    piper_group.add_argument("--piper-speed", type=float, default=1.0,
                            help="Piper synthesis speed multiplier (0.5-2.0, default: 1.0)")

    # eSpeak TTS options (NEW)
    espeak_group = parser.add_argument_group('eSpeak TTS Options (Ultra-Fast)')
    espeak_group.add_argument("--espeak-voice",
                             choices=["en", "en+f3", "en+m3", "en+f4", "en-us", "en-gb"],
                             default="en",
                             help="eSpeak voice selection: en (default male), en+f3 (female), en+m3 (male variant), etc.")
    espeak_group.add_argument("--espeak-speed", type=int, default=175,
                             help="eSpeak speed in words per minute (80-450, default: 175)")
    espeak_group.add_argument("--espeak-profile",
                             choices=["ultra_fast", "fast", "balanced", "clear"],
                             default="fast",
                             help="eSpeak performance profile: ultra_fast (maximum speed), fast (default), balanced, clear (slower but clearer)")

    # VibeVoice TTS options (Long-form)
    vibevoice_group = parser.add_argument_group('VibeVoice TTS Options (Long-Form)')
    vibevoice_group.add_argument("--no-vibevoice", action="store_true",
                                help="Disable VibeVoice entirely for faster startup")
    vibevoice_group.add_argument("--vibevoice-model",
                                choices=["1.5B", "7B"],
                                default="1.5B",
                                help="VibeVoice model variant: 1.5B (64K context, 90min), 7B (32K context, 45min)")
    vibevoice_group.add_argument("--multi-speaker",
                                action="store_true",
                                help="Enable multi-speaker conversation mode (up to 4 speakers)")
    vibevoice_group.add_argument("--continuous-mode",
                                action="store_true",
                                help="Enable continuous synthesis mode (up to 90 minutes)")
    vibevoice_group.add_argument("--speakers",
                                nargs="+",
                                default=["Alice"],
                                help="Specify speaker names for multi-speaker mode (e.g., --speakers Alice Bob)")
    vibevoice_group.add_argument("--vibevoice-quality",
                                choices=["fast", "balanced", "quality"],
                                default="balanced",
                                help="VibeVoice generation quality: fast (5 steps), balanced (7 steps), quality (10 steps)")

    # Reverb options (NEW)
    reverb_group = parser.add_argument_group('Reverb Effects Options')
    reverb_group.add_argument("--reverb-type",
                             choices=["room", "hall", "cathedral", "studio", "intimate"],
                             help="Reverb type for studio/hall/intimate profiles")
    reverb_group.add_argument("--reverb-intensity", type=float, default=0.3,
                             help="Reverb intensity (0.0-1.0, default: 0.3)")

    # Voice profiles (Updated with new real-time profiles and reverb)
    tts_group.add_argument("--voice-profile",
                          choices=["realtime", "instant", "chat", "natural", "assistant", "broadcast", "terminal", "debug", "minimal",
                                  "kitten_natural", "kitten_fast", "conversational", "narrative", "assistant_duo",
                                  "studio", "hall", "intimate", "realtime_chat", "studio_chat", "intimate_chat"],
                          default="realtime",
                          help="Voice profile: realtime (Piper, sub-50ms), instant (eSpeak, sub-10ms), chat (conversational AI), natural/assistant/broadcast/terminal (traditional), debug/minimal (fast), studio/hall/intimate (reverb effects), realtime_chat/studio_chat/intimate_chat (optimized chat with reverb)")
    
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

    # Launch MCP server if requested
    if args.with_mcp_server:
        log_info("Launching MCP server in the background...")
        try:
            mcp_server_process = subprocess.Popen(["./launch_mcp_server.sh"])
            print("MCP server started in the background.")
        except Exception as e:
            log_error(f"Failed to launch MCP server: {e}")
            print(f"❌ Failed to launch MCP server: {e}")
    
    # Handle special modes
    if args.download_only:
        return handle_download_only(args.model)
    
    if args.test_voice:
        return handle_voice_test()

    if args.test_realtime_tts:
        return handle_realtime_tts_test(args)
    
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
        # Real-time TTS options (Updated)
        tts_engine=args.tts_engine,
        voice_profile=args.voice_profile,
        realtime_mode=getattr(args, 'realtime_mode', False),
        instant_mode=getattr(args, 'instant_mode', False),
        # Piper TTS options
        piper_voice=getattr(args, 'piper_voice', 'en_US-lessac-medium'),
        piper_speed=getattr(args, 'piper_speed', 1.0),
        # eSpeak TTS options
        espeak_voice=getattr(args, 'espeak_voice', 'en'),
        espeak_speed=getattr(args, 'espeak_speed', 175),
        espeak_profile=getattr(args, 'espeak_profile', 'fast'),
        # VibeVoice options (Long-form)
        vibevoice_model=getattr(args, 'vibevoice_model', '1.5B'),
        vibevoice_quality=getattr(args, 'vibevoice_quality', 'balanced'),
        speakers=getattr(args, 'speakers', ['Alice']),
        multi_speaker=getattr(args, 'multi_speaker', False),
        # Reverb options (NEW)
        reverb_type=getattr(args, 'reverb_type', None),
        reverb_intensity=getattr(args, 'reverb_intensity', 0.3)
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


def handle_realtime_tts_test(args) -> int:
    """Handle real-time TTS test with audio demo"""
    try:
        log_info("Running real-time TTS test...")
        print("🎤 M1K3 Real-Time TTS Demo")
        print("=" * 50)
        print("This will test the new ultra-fast offline TTS engines with actual audio.")
        print("Make sure your speakers are on and volume is set appropriately.\n")

        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine

        # Create voice engine
        engine = UnifiedVoiceEngine()

        # Set engine preference if specified
        if hasattr(args, 'tts_engine') and args.tts_engine != "auto":
            engine.set_engine_preference(args.tts_engine)

        # Load engine
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            log_error("Failed to load voice engine")
            return 1

        print(f"✅ Voice engine loaded: {engine.preferred_engine}")

        # Set voice profile
        profile = getattr(args, 'voice_profile', 'realtime')
        if engine.set_profile(profile):
            profile_info = engine.get_current_profile()
            print(f"🎭 Voice profile: {profile}")
            print(f"   Engine: {profile_info['preferred_engine']}")
            print(f"   Description: {profile_info['description']}")

        # Configure engine-specific settings
        if engine.preferred_engine == "espeak" and hasattr(args, 'espeak_speed'):
            engine.espeak_manager.set_speed(args.espeak_speed)
        elif engine.preferred_engine == "piper" and hasattr(args, 'piper_speed'):
            engine.piper_manager.set_speed(args.piper_speed)

        # Demo texts
        demo_texts = [
            "Welcome to M1K3's real-time text-to-speech system.",
            "This demonstrates ultra-fast offline voice synthesis.",
            "Perfect for natural conversation with AI assistants.",
            "The system prioritizes speed while maintaining quality."
        ]

        print(f"\n🎵 Playing {len(demo_texts)} audio demonstrations...")

        for i, text in enumerate(demo_texts, 1):
            print(f"\n📢 Demo {i}/{len(demo_texts)}: \"{text[:50]}{'...' if len(text) > 50 else ''}\"")

            start_time = time.time()
            success = engine.synthesize_and_play(text, background=False)
            total_time = time.time() - start_time

            if success:
                print(f"✅ Played in {total_time:.2f}s using {engine.preferred_engine}")
            else:
                print(f"⚠️ Demo {i} had playback issues but synthesis likely worked")

            if i < len(demo_texts):
                print("⏸️ Pausing...")
                time.sleep(1.5)

        # Show performance summary
        print(f"\n📊 PERFORMANCE SUMMARY")
        print(f"   Active engine: {engine.preferred_engine}")
        print(f"   Voice profile: {profile}")
        print(f"   Audio effects: {len(engine.effects_pipeline)} effects")

        engine_status = engine.get_status()
        if "current_engine_info" in engine_status:
            info = engine_status["current_engine_info"]
            if isinstance(info, dict):
                for key, value in info.items():
                    if key in ["optimization", "engine_type", "sample_rate"]:
                        print(f"   {key}: {value}")

        print(f"\n🎯 CLI Integration Examples:")
        print(f"   # Start with this engine:")
        print(f"   python cli.py --tts-engine {engine.preferred_engine} --voice-profile {profile}")
        print(f"   # Ultra-fast mode:")
        print(f"   python cli.py --instant-mode")
        print(f"   # Real-time neural:")
        print(f"   python cli.py --realtime-mode")

        print(f"\n✅ Real-time TTS test completed successfully!")
        log_info("Real-time TTS test completed successfully")
        return 0

    except ImportError as e:
        print(f"❌ TTS engine not available: {e}")
        log_error(f"TTS engine not available: {e}")
        return 1
    except Exception as e:
        print(f"❌ Real-time TTS test failed: {e}")
        log_error(f"Real-time TTS test failed: {e}")
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