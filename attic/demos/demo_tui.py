#!/usr/bin/env python3
"""
M1K3 TUI Demonstration Script
Shows the capabilities of both Textual and Rich full-screen interfaces
"""

import time
import subprocess
import sys
from rich.console import Console
from rich.panel import Panel
from rich.columns import Columns
from rich.text import Text

def main():
    console = Console()
    
    console.print("\n[bold blue]🚀 M1K3 Full-Screen Terminal Interface Demo[/bold blue]")
    console.print("=" * 60)
    
    # Show available interfaces
    interfaces = [
        Panel(
            Text(
                "Classic CLI Interface\n\n"
                "• Traditional terminal chat\n"
                "• Command-based interaction\n" 
                "• Lightweight and fast\n"
                "• Original M1K3 experience",
                justify="left"
            ),
            title="🖥️  Classic CLI",
            border_style="green"
        ),
        Panel(
            Text(
                "Textual TUI Interface\n\n"
                "• Modern full-screen UI\n"
                "• Multiple panels and tabs\n"
                "• Keyboard shortcuts\n"
                "• Mouse support",
                justify="left"
            ),
            title="✨ Textual TUI",
            border_style="blue"
        ),
        Panel(
            Text(
                "Rich Full-Screen Interface\n\n"
                "• Lightweight alternative\n"
                "• Real-time updates\n"
                "• Eco metrics display\n"
                "• Clean layout",
                justify="left"
            ),
            title="🎨 Rich TUI",
            border_style="yellow"
        )
    ]
    
    console.print(Columns(interfaces, equal=True))
    
    # Launch instructions
    console.print("\n[bold]🚀 Launch Commands:[/bold]")
    launch_commands = Panel(
        """[cyan]# Classic CLI (default)[/cyan]
python m1k3.py

[blue]# Textual TUI (modern, recommended)[/blue]
python m1k3.py --tui

[yellow]# Rich Full-Screen (lightweight)[/yellow]
python m1k3.py --fullscreen

[dim]# Additional options[/dim]
python m1k3.py --tui --no-voice      # TUI without voice
python m1k3.py --fullscreen --no-avatar  # Rich without avatar
python m1k3_tui.py                   # Direct TUI launch
python m1k3_rich_tui.py              # Direct Rich launch""",
        title="Command Examples",
        border_style="cyan"
    )
    console.print(launch_commands)
    
    # Feature comparison
    console.print("\n[bold]📊 Feature Comparison:[/bold]")
    
    from rich.table import Table
    
    table = Table(show_header=True, header_style="bold magenta")
    table.add_column("Feature", style="dim", width=20)
    table.add_column("Classic CLI", justify="center")
    table.add_column("Textual TUI", justify="center")
    table.add_column("Rich TUI", justify="center")
    
    table.add_row("Full-Screen Mode", "❌", "✅", "✅")
    table.add_row("Real-time Updates", "❌", "✅", "✅")
    table.add_row("Multiple Panels", "❌", "✅", "✅")
    table.add_row("Mouse Support", "❌", "✅", "❌")
    table.add_row("Keyboard Shortcuts", "Basic", "Advanced", "Basic")
    table.add_row("Avatar Integration", "✅", "✅", "✅")
    table.add_row("Voice Synthesis", "✅", "✅", "✅")
    table.add_row("Eco Metrics", "✅", "✅", "✅")
    table.add_row("Resource Usage", "Low", "Medium", "Low")
    table.add_row("Dependencies", "Rich", "Textual", "Rich")
    
    console.print(table)
    
    # Keyboard shortcuts
    console.print("\n[bold]⌨️  Keyboard Shortcuts:[/bold]")
    shortcuts = Panel(
        """[bold cyan]Textual TUI:[/bold cyan]
• Ctrl+Q - Quit application
• Ctrl+C - Clear chat history
• Ctrl+S - Show statistics tab
• Ctrl+A - Toggle avatar server
• Ctrl+V - Toggle voice synthesis
• Escape - Focus chat input
• F1 - Show help
• Tab - Switch between panels

[bold yellow]Rich TUI:[/bold yellow]
• Ctrl+C - Exit application
• Ctrl+L - Clear display
• Ctrl+S - Show detailed stats
• Enter - Send message
• Backspace - Delete character""",
        title="Shortcuts Reference",
        border_style="green"
    )
    console.print(shortcuts)
    
    console.print("\n[green]🎉 M1K3 now supports professional full-screen terminal interfaces![/green]")
    console.print("[dim]Choose the interface that best fits your workflow and preferences.[/dim]")

if __name__ == "__main__":
    main()