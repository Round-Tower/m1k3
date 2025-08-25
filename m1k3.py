#!/usr/bin/env python3
"""
M1K3 - AI Assistant Launcher
Supports multiple interfaces: CLI, Textual TUI, and Rich full-screen
"""

import os
import sys
import argparse
import warnings

# Suppress compatibility warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)
os.environ['PYTHONWARNINGS'] = 'ignore'

# Enable HuggingFace tokenizers parallelism for better performance
os.environ['TOKENIZERS_PARALLELISM'] = 'true'

# Suppress specific tokenizer fork warnings while keeping performance benefits
import logging
logging.getLogger("transformers.tokenization_utils_base").setLevel(logging.ERROR)

def main():
    """Main launcher with interface selection"""
    parser = argparse.ArgumentParser(
        description="M1K3 - Local AI Assistant",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Interface Options:
  (default)          Classic CLI interface
  --tui             Modern Textual full-screen interface (recommended)
  --fullscreen      Rich-based full-screen interface (lightweight)

Examples:
  python m1k3.py                    # Classic CLI
  python m1k3.py --tui             # Full-screen TUI (modern)
  python m1k3.py --fullscreen      # Full-screen (lightweight)
  python m1k3.py --tui --no-voice  # TUI without voice
        """
    )
    
    # Interface selection
    interface_group = parser.add_mutually_exclusive_group()
    interface_group.add_argument(
        "--tui", 
        action="store_true", 
        help="Launch Textual full-screen terminal interface"
    )
    interface_group.add_argument(
        "--fullscreen", 
        action="store_true", 
        help="Launch Rich full-screen interface"
    )
    
    # Common options
    parser.add_argument(
        "--no-voice", 
        action="store_true", 
        help="Disable voice synthesis"
    )
    parser.add_argument(
        "--no-avatar", 
        action="store_true", 
        help="Disable avatar server"
    )
    parser.add_argument(
        "--auto-avatar", 
        action="store_true", 
        help="Automatically start avatar server with browser"
    )
    parser.add_argument(
        "--avatar-port", 
        type=int, 
        default=8080, 
        help="Avatar server port (default: 8080)"
    )
    parser.add_argument(
        "--rag", 
        action="store_true", 
        help="Enable RAG (Retrieval-Augmented Generation) with comprehensive knowledge base"
    )
    parser.add_argument(
        "--query", "-q",
        type=str,
        help="Single query mode - ask a question and exit"
    )
    
    args = parser.parse_args()
    
    # Launch appropriate interface
    if args.tui:
        # Launch Textual TUI
        try:
            from src.cli.m1k3_tui import M1K3TUIApp
            app = M1K3TUIApp(voice_enabled=not args.no_voice, rag_enabled=args.rag)
            app.run()
        except ImportError as e:
            print(f"❌ Textual TUI not available: {e}")
            print("💡 Install with: pip install textual")
            print("🔄 Falling back to classic CLI...")
            launch_classic_cli(args)
        except Exception as e:
            print(f"❌ TUI failed to start: {e}")
            print("🔄 Falling back to classic CLI...")
            launch_classic_cli(args)
            
    elif args.fullscreen:
        # Launch Rich full-screen interface
        try:
            from src.cli.m1k3_rich_tui import launch_rich_tui
            launch_rich_tui(
                voice_enabled=not args.no_voice,
                avatar_enabled=not args.no_avatar,
                auto_avatar=args.auto_avatar,
                avatar_port=args.avatar_port
            )
        except ImportError as e:
            print(f"❌ Rich full-screen interface not available: {e}")
            print("🔄 Falling back to classic CLI...")
            launch_classic_cli(args)
        except Exception as e:
            print(f"❌ Full-screen interface failed to start: {e}")
            print("🔄 Falling back to classic CLI...")
            launch_classic_cli(args)
    else:
        # Launch classic CLI (default)
        launch_classic_cli(args)

def launch_classic_cli(args):
    """Launch the classic CLI interface"""
    try:
        from cli import main as cli_main
        
        # Convert args to classic CLI format
        cli_args = []
        if args.no_voice:
            cli_args.append("--no-voice")
        if args.auto_avatar:
            cli_args.append("--auto-avatar")
        if args.avatar_port != 8080:
            cli_args.extend(["--avatar-port", str(args.avatar_port)])
        if args.rag:
            cli_args.append("--rag")
        if args.query:
            cli_args.extend(["--query", args.query])
            
        # Override sys.argv for the CLI
        original_argv = sys.argv
        sys.argv = ["m1k3.py"] + cli_args
        
        try:
            return cli_main()
        finally:
            sys.argv = original_argv
            
    except Exception as e:
        print(f"❌ Failed to start M1K3: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(main())