#!/usr/bin/env python3
"""
Minimal TUI test to isolate issues
"""

from textual.app import App, ComposeResult
from textual.containers import Container
from textual.widgets import Header, Footer, Static

class MinimalTUIApp(App):
    """Minimal test app"""
    
    TITLE = "M1K3 - Test"
    
    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        yield Static("Hello from M1K3 TUI!", id="content")
        yield Footer()

def main():
    app = MinimalTUIApp()
    app.run()

if __name__ == "__main__":
    main()