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
    
    # Speech-to-Text (STT) options
    stt_group = parser.add_argument_group('Speech Recognition Options')
    stt_group.add_argument("--stt-engine", 
                          choices=["auto", "native", "vosk", "web", "whisper", "none"],
                          default="auto",
                          help="Speech recognition engine: auto (smart default), native (macOS only), vosk (offline), web (cloud), whisper (heavy), none (disable)")
    stt_group.add_argument("--stt-model", 
                          default="vosk-model-small-en-us-0.15",
                          help="STT model name (for Vosk/Whisper engines)")
    stt_group.add_argument("--stt-lang", 
                          default="en-US",
                          help="Speech recognition language (default: en-US)")
    
    args = parser.parse_args()
    
    # Handle special modes
    if args.download_only:
        return handle_download_only(args.model)
    
    if args.test_voice:
        return handle_voice_test()
    
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
        stt_language=args.stt_lang
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