#!/usr/bin/env python3
"""
Test script for M1K3 TUI interfaces
Verifies that all interfaces can be imported and initialized
"""

import sys
from rich.console import Console

def test_textual_tui():
    """Test Textual TUI interface"""
    console = Console()
    
    try:
        from m1k3_tui import M1K3TUIApp
        console.print("✅ Textual TUI imports successfully")
        
        # Test initialization
        app = M1K3TUIApp(voice_enabled=False)
        console.print("✅ Textual TUI initializes successfully")
        
        # Test CSS loading
        if app.CSS_PATH == "tui_styles.css":
            console.print("✅ CSS path configured correctly")
        
        return True
        
    except ImportError as e:
        console.print(f"❌ Textual TUI import failed: {e}")
        return False
    except Exception as e:
        console.print(f"❌ Textual TUI error: {e}")
        return False

def test_rich_tui():
    """Test Rich TUI interface"""
    console = Console()
    
    try:
        from m1k3_rich_tui import M1K3RichTUI, launch_rich_tui
        console.print("✅ Rich TUI imports successfully")
        
        # Test initialization
        tui = M1K3RichTUI(voice_enabled=False, avatar_enabled=False)
        console.print("✅ Rich TUI initializes successfully")
        
        # Test layout setup
        if tui.layout is not None:
            console.print("✅ Rich layout configured correctly")
        
        return True
        
    except ImportError as e:
        console.print(f"❌ Rich TUI import failed: {e}")
        return False
    except Exception as e:
        console.print(f"❌ Rich TUI error: {e}")
        return False

def test_launcher():
    """Test the main launcher"""
    console = Console()
    
    try:
        from m1k3 import main, launch_classic_cli
        console.print("✅ Launcher imports successfully")
        
        # Test argument parsing (without actually running)
        import argparse
        from unittest.mock import patch
        
        with patch('sys.argv', ['m1k3.py', '--help']):
            try:
                # This will exit with help, which is expected
                main()
            except SystemExit as e:
                if e.code == 0:  # Help exit code
                    console.print("✅ Launcher argument parsing works")
                else:
                    console.print(f"⚠️  Launcher exit code: {e.code}")
        
        return True
        
    except ImportError as e:
        console.print(f"❌ Launcher import failed: {e}")
        return False
    except SystemExit:
        console.print("✅ Launcher argument parsing works")
        return True
    except Exception as e:
        console.print(f"❌ Launcher error: {e}")
        return False

def test_dependencies():
    """Test required dependencies"""
    console = Console()
    
    dependencies = [
        ('textual', 'Textual TUI framework'),
        ('rich', 'Rich console library'),
        ('websockets', 'WebSocket client (optional)'),
    ]
    
    all_deps_available = True
    
    for dep_name, description in dependencies:
        try:
            __import__(dep_name)
            console.print(f"✅ {description} available")
        except ImportError:
            console.print(f"❌ {description} not available")
            if dep_name in ['textual', 'rich']:
                all_deps_available = False
    
    return all_deps_available

def main():
    """Run all tests"""
    console = Console()
    
    console.print("\n[bold blue]🧪 M1K3 TUI Interface Tests[/bold blue]")
    console.print("=" * 50)
    
    # Test dependencies
    console.print("\n[bold]📦 Testing Dependencies[/bold]")
    deps_ok = test_dependencies()
    
    # Test interfaces
    console.print("\n[bold]🖥️ Testing Interfaces[/bold]")
    textual_ok = test_textual_tui()
    rich_ok = test_rich_tui()
    launcher_ok = test_launcher()
    
    # Summary
    console.print("\n[bold]📊 Test Results Summary[/bold]")
    console.print("-" * 30)
    
    results = [
        ("Dependencies", deps_ok),
        ("Textual TUI", textual_ok),
        ("Rich TUI", rich_ok),
        ("Launcher", launcher_ok),
    ]
    
    all_passed = True
    for name, passed in results:
        status = "✅ PASS" if passed else "❌ FAIL"
        console.print(f"{status:<8} {name}")
        if not passed:
            all_passed = False
    
    console.print("-" * 30)
    
    if all_passed:
        console.print("[bold green]🎉 All tests passed![/bold green]")
        console.print("\n[dim]You can now use:[/dim]")
        console.print("• [cyan]python m1k3.py --tui[/cyan] (Textual interface)")
        console.print("• [yellow]python m1k3.py --fullscreen[/yellow] (Rich interface)")
        console.print("• [green]python m1k3.py[/green] (Classic CLI)")
    else:
        console.print("[bold red]❌ Some tests failed![/bold red]")
        console.print("\n[dim]Try installing missing dependencies:[/dim]")
        console.print("• [cyan]pip install textual[/cyan]")
        console.print("• [yellow]pip install rich[/yellow]")
    
    return 0 if all_passed else 1

if __name__ == "__main__":
    sys.exit(main())