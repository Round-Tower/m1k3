#!/usr/bin/env python3
"""
M1K3 Rich Full-Screen Terminal Interface
Lightweight full-screen terminal interface using Rich library
"""

import asyncio
import atexit
import threading
import time
import signal
import sys
from datetime import datetime
from typing import List, Optional

from rich.console import Console
from rich.layout import Layout
from rich.panel import Panel
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn
from rich.live import Live
from rich.text import Text
from rich.align import Align
from rich.columns import Columns
from rich.rule import Rule
from rich import box

# M1K3 imports
try:
    from ai_inference import LocalAIEngine
    REAL_AI_AVAILABLE = True
except ImportError:
    from simple_ai_engine import SimpleAIEngine
    REAL_AI_AVAILABLE = False

from enhanced_voice_engine import create_voice_engine
from system_metrics import SystemMonitor

try:
    from avatar_server import (
        start_avatar_server, stop_avatar_server, is_avatar_server_running, send_avatar_emotion,
        send_avatar_state, send_chat_ai_start, send_chat_ai_chunk, 
        send_chat_ai_complete, send_sound_trigger
    )
    AVATAR_AVAILABLE = True
except ImportError:
    AVATAR_AVAILABLE = False

class ChatMessage:
    """Represents a chat message with metadata"""
    def __init__(self, content: str, role: str = "user", timestamp: Optional[float] = None):
        self.content = content
        self.role = role  # "user", "assistant", "system"
        self.timestamp = timestamp or time.time()

class M1K3RichTUI:
    """Rich-based full-screen terminal interface for M1K3"""
    
    def __init__(self, voice_enabled: bool = True, avatar_enabled: bool = True, 
                 auto_avatar: bool = False, avatar_port: int = 8080):
        
        # Initialize console
        self.console = Console()
        
        # Initialize AI engine
        if REAL_AI_AVAILABLE:
            self.ai_engine = LocalAIEngine()
        else:
            self.ai_engine = SimpleAIEngine()
        
        # Initialize voice engine
        self.voice_engine = create_voice_engine() if voice_enabled else None
        self.voice_enabled = voice_enabled
        
        # Initialize system monitor
        self.system_monitor = SystemMonitor()
        
        # State
        self.running = True
        self.messages: List[ChatMessage] = []
        self.conversation_context = []
        self.current_input = ""
        self.input_thread = None
        
        # Avatar state
        self.avatar_enabled = avatar_enabled
        self.avatar_running = False
        self.current_emotion = "happy"
        self.current_state = "idle"
        self.emotion_intensity = 50
        
        # Metrics
        self.start_time = time.time()
        self.message_count = 0
        
        # Layout
        self.layout = Layout(name="root")
        self.setup_layout()
        
        # Start avatar server if requested
        if avatar_enabled and AVATAR_AVAILABLE:
            self.start_avatar_server()
        
        # Set up cleanup handlers
        atexit.register(self.cleanup_on_exit)
        signal.signal(signal.SIGTERM, self._signal_handler)
        signal.signal(signal.SIGINT, self._signal_handler)
    
    def setup_layout(self):
        """Setup the Rich layout structure"""
        self.layout.split(
            Layout(name="header", size=3),
            Layout(name="main", ratio=1),
            Layout(name="footer", size=3)
        )
        
        # Split main area
        self.layout["main"].split_row(
            Layout(name="chat", ratio=2),
            Layout(name="sidebar", size=35)
        )
        
        # Split sidebar
        self.layout["sidebar"].split(
            Layout(name="avatar", size=12),
            Layout(name="metrics", size=10),
            Layout(name="stats", ratio=1)
        )
    
    def start_avatar_server(self):
        """Start the avatar WebSocket server"""
        if AVATAR_AVAILABLE and not is_avatar_server_running():
            try:
                start_avatar_server()
                self.avatar_running = True
                send_avatar_emotion("happy", 70, "M1K3 Rich TUI started!")
                send_avatar_state("idle")
            except Exception:
                pass  # Silently fail if avatar can't start
    
    def update_header(self):
        """Update header panel"""
        current_time = datetime.now().strftime("%H:%M:%S")
        title = f"🤖 M1K3 - AI Terminal Interface  |  {current_time}"
        
        status_items = []
        if self.voice_enabled:
            status_items.append("[green]🔊 Voice[/green]")
        if self.avatar_running:
            status_items.append("[blue]🧘 Avatar[/blue]")
        
        status = " | ".join(status_items) if status_items else "[yellow]Basic Mode[/yellow]"
        
        header_table = Table.grid(padding=1)
        header_table.add_column(justify="left")
        header_table.add_column(justify="right")
        header_table.add_row(title, status)
        
        self.layout["header"].update(Panel(header_table, style="bold white on blue", border_style="blue"))
    
    def update_chat(self):
        """Update chat panel"""
        chat_content = []
        
        # Show last 20 messages
        recent_messages = self.messages[-20:] if len(self.messages) > 20 else self.messages
        
        for msg in recent_messages:
            timestamp = datetime.fromtimestamp(msg.timestamp).strftime("%H:%M")
            
            if msg.role == "user":
                chat_content.append(f"[cyan]{timestamp} 👤 You:[/cyan] {msg.content}")
            elif msg.role == "assistant":
                chat_content.append(f"[green]{timestamp} 🤖 M1K3:[/green] {msg.content}")
            else:
                chat_content.append(f"[yellow]{timestamp} ℹ️ :[/yellow] {msg.content}")
        
        if not chat_content:
            chat_content = ["[dim]Welcome to M1K3! Start typing to begin a conversation...[/dim]"]
        
        # Add current input if any
        if self.current_input:
            chat_content.append(f"[cyan bold]> {self.current_input}_[/cyan bold]")
        
        chat_text = "\n".join(chat_content)
        
        self.layout["chat"].update(
            Panel(
                chat_text,
                title="💬 Conversation",
                border_style="cyan",
                padding=(1, 2)
            )
        )
    
    def update_avatar(self):
        """Update avatar status panel"""
        # Emotion icons
        emotion_icons = {
            'happy': '😊', 'sad': '😢', 'angry': '😠', 'surprised': '😮',
            'love': '😍', 'thinking': '🤔', 'sleepy': '😴', 'excited': '🤩'
        }
        
        # State icons
        state_icons = {
            'idle': '💤', 'listening': '👂', 'thinking': '🧠',
            'pre_thinking': '💭', 'generating': '⚡', 'speaking': '🗣️', 'error': '❌'
        }
        
        emotion_icon = emotion_icons.get(self.current_emotion, '😊')
        state_icon = state_icons.get(self.current_state, '💤')
        
        avatar_content = Align.center(
            f"{emotion_icon} {state_icon}\n\n"
            f"[bold]{self.current_emotion.title()}[/bold]\n"
            f"[dim]{self.current_state.title()}[/dim]\n"
            f"Intensity: {self.emotion_intensity}%"
        )
        
        self.layout["avatar"].update(
            Panel(
                avatar_content,
                title="🧘 Avatar Status",
                border_style="blue" if self.avatar_running else "dim"
            )
        )
    
    def update_metrics(self):
        """Update metrics panel"""
        session_time = int(time.time() - self.start_time)
        minutes, seconds = divmod(session_time, 60)
        
        metrics_table = Table(show_header=False, box=None)
        metrics_table.add_column("Metric", style="dim")
        metrics_table.add_column("Value", style="green")
        
        metrics_table.add_row("⚡ Energy", "127 Wh")
        metrics_table.add_row("💧 Water", "3.2L") 
        metrics_table.add_row("🌍 CO2", "85g")
        metrics_table.add_row("💬 Messages", str(self.message_count))
        metrics_table.add_row("🕐 Session", f"{minutes:02d}:{seconds:02d}")
        metrics_table.add_row("🎯 Context", str(len(self.conversation_context)))
        
        self.layout["metrics"].update(
            Panel(
                metrics_table,
                title="📊 Eco Metrics",
                border_style="green"
            )
        )
    
    def update_stats(self):
        """Update system stats panel"""
        metrics = self.system_monitor.collect_metrics()
        ai_stats = self.ai_engine.get_memory_usage()
        
        stats_content = f"""[bold]System Info[/bold]
CPU: {(metrics.cpu_model or 'Unknown')[:25]}
RAM: {metrics.memory_percent or 0:.1f}% / {metrics.memory_total_gb or 0:.1f}GB
AI: {ai_stats.get('memory_mb', '0MB')}

[bold]Status[/bold]
Battery: {(metrics.battery_status() or 'Unknown')[:10]}
Performance: {(metrics.performance_status() or 'Unknown')[:10]}
Thermal: {(metrics.thermal_status() or 'Unknown')[:10]}"""
        
        self.layout["stats"].update(
            Panel(
                stats_content,
                title="🖥️ System",
                border_style="yellow"
            )
        )
    
    def update_footer(self):
        """Update footer panel"""
        shortcuts = [
            "Ctrl+C: Exit",
            "Ctrl+L: Clear",
            "Ctrl+S: Stats",
            "Enter: Send"
        ]
        
        footer_content = " | ".join(shortcuts)
        
        self.layout["footer"].update(
            Panel(
                Align.center(footer_content),
                style="white on black",
                border_style="dim"
            )
        )
    
    def update_display(self):
        """Update all display components"""
        self.update_header()
        self.update_chat()
        self.update_avatar()
        self.update_metrics()
        self.update_stats()
        self.update_footer()
    
    async def process_message(self, message: str):
        """Process user message and generate AI response"""
        # Add user message
        user_msg = ChatMessage(message, role="user")
        self.messages.append(user_msg)
        self.message_count += 1
        
        # Update display after new user message
        self.update_display()
        
        # Update avatar state
        if self.avatar_running:
            send_avatar_state("thinking")
            send_avatar_emotion("thinking", 80, "Processing user message...")
            self.current_state = "thinking"
            self.current_emotion = "thinking"
        
        try:
            # Generate AI response
            self.conversation_context.append(f"User: {message}")
            
            if REAL_AI_AVAILABLE:
                # LocalAIEngine doesn't support context parameter, so we build it into the prompt
                context_prompt = "\n".join(self.conversation_context[-6:]) + f"\nUser: {message}\nAssistant:"
                # Collect the streaming response
                response = ""
                for token in self.ai_engine.generate_response(context_prompt):
                    response += token
            else:
                response = self.ai_engine.generate_response(message)
            
            # Add AI response
            ai_msg = ChatMessage(response, role="assistant")
            self.messages.append(ai_msg)
            self.conversation_context.append(f"Assistant: {response}")
            
            # Update display after new message
            self.update_display()
            
            # Update avatar
            if self.avatar_running:
                send_avatar_state("idle")
                send_avatar_emotion("happy", 90, "Response complete!")
                send_sound_trigger("message_received")
                self.current_state = "idle"
                self.current_emotion = "happy"
            
            # Voice synthesis
            if self.voice_enabled and self.voice_engine and response:
                if self.avatar_running:
                    send_avatar_state("speaking")
                    self.current_state = "speaking"
                
                # Run voice synthesis in background thread
                def speak():
                    self.voice_engine.synthesize_and_play(response)
                    if self.avatar_running:
                        send_avatar_state("idle")
                        self.current_state = "idle"
                
                voice_thread = threading.Thread(target=speak, daemon=True)
                voice_thread.start()
                
        except Exception as e:
            error_msg = ChatMessage(f"Error: {str(e)}", role="system")
            self.messages.append(error_msg)
            
            if self.avatar_running:
                send_avatar_state("error")
                send_avatar_emotion("angry", 100, "An error occurred")
                self.current_state = "error"
                self.current_emotion = "angry"
    
    def handle_command(self, command: str):
        """Handle special commands"""
        cmd = command.lower().strip()
        
        if cmd in ['quit', 'exit', 'q']:
            self.running = False
            return True
        elif cmd in ['clear', 'reset']:
            self.messages.clear()
            self.conversation_context.clear()
            self.messages.append(ChatMessage("Chat cleared", role="system"))
            return True
        elif cmd in ['help', 'h']:
            help_msg = ChatMessage(
                "M1K3 Rich TUI Commands:\n"
                "• Type naturally to chat\n"
                "• 'clear' or 'reset' - Clear chat\n" 
                "• 'help' - Show this help\n"
                "• 'quit' or 'exit' - Exit\n"
                "• Ctrl+C - Force exit\n"
                "• Ctrl+L - Clear screen\n"
                "• Ctrl+S - Show detailed stats",
                role="system"
            )
            self.messages.append(help_msg)
            return True
        elif cmd in ['stats', 'status']:
            metrics = self.system_monitor.collect_metrics()
            ai_stats = self.ai_engine.get_memory_usage()
            
            stats_text = f"""System Statistics:
CPU: {metrics.cpu_model or 'Unknown'}
OS: {metrics.os_name or 'Unknown'} {metrics.os_version or ''}
Memory: {metrics.memory_percent or 0:.1f}% / {metrics.memory_total_gb or 0:.1f}GB
AI Memory: {ai_stats.get('memory_mb', '0MB')}
Messages: {len(self.messages)}
Voice: {'Enabled' if self.voice_enabled else 'Disabled'}
Avatar: {'Running' if self.avatar_running else 'Stopped'}"""
            
            stats_msg = ChatMessage(stats_text, role="system")
            self.messages.append(stats_msg)
            return True
        
        return False
    
    def input_handler(self):
        """Handle user input in a separate thread"""
        try:
            import select
            import tty
            import termios
            
            # Check if we're in a proper TTY environment
            if not sys.stdin.isatty():
                # Fallback for non-TTY environments (like when running in some IDEs)
                return
            
            old_settings = termios.tcgetattr(sys.stdin)
            try:
                tty.setraw(sys.stdin)
                
                while self.running:
                    if select.select([sys.stdin], [], [], 0.1)[0]:
                        char = sys.stdin.read(1)
                        
                        if ord(char) == 3:  # Ctrl+C
                            self.running = False
                            break
                        elif ord(char) == 12:  # Ctrl+L
                            self.console.clear()
                        elif ord(char) == 19:  # Ctrl+S
                            self.handle_command("stats")
                        elif ord(char) == 13:  # Enter
                            if self.current_input.strip():
                                message = self.current_input
                                self.current_input = ""
                                
                                if not self.handle_command(message):
                                    # Process as regular message
                                    asyncio.create_task(self.process_message(message))
                        elif ord(char) == 127:  # Backspace
                            if self.current_input:
                                self.current_input = self.current_input[:-1]
                        elif ord(char) >= 32:  # Printable character
                            self.current_input += char
            finally:
                try:
                    termios.tcsetattr(sys.stdin, termios.TCSADRAIN, old_settings)
                except:
                    pass
        except Exception as e:
            # Handle any terminal-related errors gracefully
            pass
    
    def _signal_handler(self, signum, frame):
        """Handle signals (SIGTERM, SIGINT) for graceful shutdown"""
        self.cleanup_on_exit()
        sys.exit(0)
    
    def cleanup_on_exit(self):
        """Clean up resources on application exit"""
        try:
            # Stop avatar server if running
            if AVATAR_AVAILABLE and self.avatar_running:
                stop_avatar_server()
                self.avatar_running = False
        except Exception as e:
            # Fail silently during cleanup to avoid exit issues
            pass
    
    def run(self):
        """Run the Rich TUI interface"""
        # Add welcome message
        welcome_msg = ChatMessage(
            "Welcome to M1K3 Rich TUI! Type your message and press Enter.",
            role="assistant"
        )
        self.messages.append(welcome_msg)
        
        # Setup signal handler
        def signal_handler(sig, frame):
            self.running = False
        
        signal.signal(signal.SIGINT, signal_handler)
        
        # Start input handler thread
        self.input_thread = threading.Thread(target=self.input_handler, daemon=True)
        self.input_thread.start()
        
        # Main display loop
        with self.console.screen():
            with Live(self.layout, console=self.console, refresh_per_second=8) as live:
                # Initial display update
                self.update_display()
                
                while self.running:
                    try:
                        # Let Live handle the automatic refresh
                        # Occasionally update dynamic content (time, metrics)
                        time.sleep(1.0)  # Update every second for time/metrics
                        self.update_header()  # Update time
                        self.update_metrics()  # Update dynamic metrics
                    except KeyboardInterrupt:
                        self.running = False
                        break
        
        self.console.print("\n[green]👋 Thanks for using M1K3![/green]")

def launch_rich_tui(voice_enabled: bool = True, avatar_enabled: bool = True,
                   auto_avatar: bool = False, avatar_port: int = 8080):
    """Launch the Rich TUI interface"""
    
    console = Console()
    console.print("[bold blue]🚀 Starting M1K3 Rich Terminal Interface...[/bold blue]")
    
    try:
        tui = M1K3RichTUI(
            voice_enabled=voice_enabled,
            avatar_enabled=avatar_enabled,
            auto_avatar=auto_avatar,
            avatar_port=avatar_port
        )
        tui.run()
    except Exception as e:
        console.print(f"[red]❌ Error: {e}[/red]")
        raise

if __name__ == "__main__":
    launch_rich_tui()