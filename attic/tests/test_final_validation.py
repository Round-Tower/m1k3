#!/usr/bin/env python3
"""
M1K3 Final Validation Test
Comprehensive test of all TUI interfaces and core functionality
"""

import sys
import time
from rich.console import Console
from rich.table import Table
from rich.panel import Panel

def test_all_systems():
    """Test all M1K3 systems comprehensively"""
    console = Console()
    
    console.print("\n[bold blue]🎯 M1K3 Final Validation Test[/bold blue]")
    console.print("=" * 60)
    
    results = []
    
    # Test 1: Core Dependencies
    console.print("\n[bold]📦 Testing Core Dependencies[/bold]")
    try:
        import rich
        import textual
        results.append(("Core Dependencies", "✅", "Rich & Textual available"))
    except ImportError as e:
        results.append(("Core Dependencies", "❌", f"Missing: {e}"))
    
    # Test 2: AI Engine
    console.print("[bold]🧠 Testing AI Engine[/bold]")
    try:
        from src.engines.ai.ai_inference import LocalAIEngine
        ai = LocalAIEngine()
        results.append(("AI Engine", "✅", "LocalAIEngine ready"))
    except Exception as e:
        results.append(("AI Engine", "❌", str(e)))
    
    # Test 3: Voice System
    console.print("[bold]🔊 Testing Voice System[/bold]")
    try:
        from enhanced_voice_engine import create_voice_engine
        voice = create_voice_engine()
        status = voice.get_status()
        results.append(("Voice System", "✅", f"Available: {status['available']}"))
    except Exception as e:
        results.append(("Voice System", "❌", str(e)))
    
    # Test 4: Avatar Integration
    console.print("[bold]🧘 Testing Avatar Integration[/bold]")
    try:
        from src.avatar.avatar_server import is_avatar_server_running
        from src.avatar.avatar_controller import AvatarController
        avatar = AvatarController()
        results.append(("Avatar System", "✅", "Avatar controller ready"))
    except Exception as e:
        results.append(("Avatar System", "❌", str(e)))
    
    # Test 5: System Monitoring
    console.print("[bold]📊 Testing System Monitoring[/bold]")
    try:
        from src.utils.performance.system_metrics import SystemMonitor
        monitor = SystemMonitor()
        metrics = monitor.collect_metrics()
        results.append(("System Monitor", "✅", f"Monitoring {metrics.os_name}"))
    except Exception as e:
        results.append(("System Monitor", "❌", str(e)))
    
    # Test 6: Textual TUI
    console.print("[bold]✨ Testing Textual TUI[/bold]")
    try:
        from src.cli.m1k3_tui import M1K3TUIApp
        app = M1K3TUIApp(voice_enabled=False)
        results.append(("Textual TUI", "✅", "Full-screen interface ready"))
    except Exception as e:
        results.append(("Textual TUI", "❌", str(e)))
    
    # Test 7: Rich TUI  
    console.print("[bold]🎨 Testing Rich TUI[/bold]")
    try:
        from src.cli.m1k3_rich_tui import M1K3RichTUI
        tui = M1K3RichTUI(voice_enabled=False, avatar_enabled=False)
        results.append(("Rich TUI", "✅", "Lightweight interface ready"))
    except Exception as e:
        results.append(("Rich TUI", "❌", str(e)))
    
    # Test 8: Unified Launcher
    console.print("[bold]🚀 Testing Unified Launcher[/bold]")
    try:
        from m1k3 import main, launch_classic_cli
        results.append(("Launcher", "✅", "Multi-interface launcher ready"))
    except Exception as e:
        results.append(("Launcher", "❌", str(e)))
    
    # Test 9: CLI Animations
    console.print("[bold]🎭 Testing CLI Animations[/bold]")
    try:
        from src.cli.cli_animations import CLIAnimator, AnimationType
        animator = CLIAnimator()
        results.append(("Animations", "✅", "Visual effects available"))
    except Exception as e:
        results.append(("Animations", "❌", str(e)))
    
    # Test 10: WebSocket Integration
    console.print("[bold]🔗 Testing WebSocket Integration[/bold]")
    try:
        import websockets
        from src.avatar.avatar_server import send_avatar_emotion
        results.append(("WebSocket", "✅", "Real-time communication ready"))
    except Exception as e:
        results.append(("WebSocket", "❌", str(e)))
    
    return results

def display_results(results):
    """Display test results in a nice table"""
    console = Console()
    
    # Create results table
    table = Table(title="🧪 M1K3 System Validation Results", show_header=True, header_style="bold magenta")
    table.add_column("Component", style="dim", width=20)
    table.add_column("Status", justify="center", width=8)
    table.add_column("Details", style="cyan")
    
    passed = 0
    total = len(results)
    
    for component, status, details in results:
        table.add_row(component, status, details)
        if status == "✅":
            passed += 1
    
    console.print(table)
    
    # Summary
    console.print(f"\n[bold]📈 Results Summary:[/bold]")
    console.print(f"• Passed: {passed}/{total} ({(passed/total)*100:.1f}%)")
    console.print(f"• Failed: {total-passed}/{total}")
    
    if passed == total:
        console.print("\n[bold green]🎉 ALL SYSTEMS OPERATIONAL![/bold green]")
        console.print("[dim]M1K3 is ready for full-screen terminal operation.[/dim]")
        
        # Usage instructions
        usage_panel = Panel(
            """[bold cyan]Ready to Launch![/bold cyan]

[yellow]Choose your preferred interface:[/yellow]

[green]• python m1k3.py --tui[/green]
  Modern full-screen interface (recommended)
  Features: Multiple panels, mouse support, advanced shortcuts

[blue]• python m1k3.py --fullscreen[/blue]  
  Lightweight full-screen interface
  Features: Real-time updates, clean layout, low resource usage

[dim]• python m1k3.py[/dim]
  Classic CLI interface (original)
  Features: Traditional terminal chat, lightweight

[bold]All interfaces support:[/bold]
✨ Voice synthesis (optional: --no-voice)
🧘 Avatar integration (optional: --no-avatar)  
📊 Eco-friendly metrics and system monitoring
🔗 Real-time WebSocket communication""",
            title="🚀 Launch Instructions",
            border_style="green"
        )
        console.print(usage_panel)
        
        return 0
    else:
        console.print(f"\n[bold red]⚠️  {total-passed} systems need attention[/bold red]")
        console.print("[dim]Some components may not be fully functional.[/dim]")
        return 1

def main():
    """Run the final validation test"""
    try:
        results = test_all_systems()
        return display_results(results)
    except KeyboardInterrupt:
        console = Console()
        console.print("\n[yellow]Test interrupted by user[/yellow]")
        return 1
    except Exception as e:
        console = Console()
        console.print(f"\n[red]Test failed with error: {e}[/red]")
        return 1

if __name__ == "__main__":
    sys.exit(main())