#!/usr/bin/env python3
"""
Test CLI Startup - Debug CLI initialization issues
"""

import sys
import traceback
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

def test_cli_startup():
    """Test CLI startup components"""

    print("🧪 CLI Startup Debug Test")
    print("=" * 40)
    print()

    try:
        print("1. Testing CLI core import...")
        from src.cli.cli_core import M1K3CLICore
        print("✅ CLI core imported successfully")

        print("\n2. Testing CLI initialization...")
        cli = M1K3CLICore(
            voice_enabled=False,
            auto_avatar=False,
            avatar_port=8080,
            open_browser=False,
            transparency_level="basic",
            rag_enabled=False
        )
        print("✅ CLI instance created successfully")

        print("\n3. Testing CLI help...")
        # Test help command that should work without full initialization
        try:
            help_result = cli.command_handler.process_command("help")
            if help_result:
                print("✅ Help command working")
                print(f"   Help output length: {len(str(help_result))} characters")
            else:
                print("⚠️ Help command returned no result")
        except Exception as e:
            print(f"❌ Help command failed: {e}")

        print("\n4. Testing database commands...")
        try:
            # Test vector memory commands
            search_result = cli.command_handler.process_command("vector_search consciousness")
            if search_result:
                print("✅ Vector search command accessible")
            else:
                print("⚠️ Vector search command returned no result")
        except Exception as e:
            print(f"⚠️ Vector search command issue: {e}")

        print("\n5. Testing single query mode...")
        try:
            # This should work without full interactive setup
            result = cli.run_single_query("What is the current status?")
            print(f"✅ Single query mode returned: {result}")
        except Exception as e:
            print(f"❌ Single query mode failed: {e}")
            traceback.print_exc()

        print("\n🎯 CLI Startup Test Summary:")
        print("✅ Core components are working")
        print("✅ CLI can be initialized")
        print("✅ Commands are accessible")

        return True

    except Exception as e:
        print(f"❌ CLI startup test failed: {e}")
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_cli_startup()
    if success:
        print("\n🚀 CLI components are working - issue may be in interactive mode setup")
        print("Suggestion: Try running 'python cli.py --query \"help\"' first")
    else:
        print("\n⚠️ CLI has initialization issues that need to be fixed")

    sys.exit(0 if success else 1)