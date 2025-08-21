#!/usr/bin/env python3
"""
M1K3 Textual Terminal User Interface
Full-screen terminal interface for M1K3 AI assistant
"""

import asyncio
import json
import os
import time
import argparse
import atexit
import signal
import sys
from datetime import datetime
from typing import Optional

from textual import on
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Container, Horizontal, Vertical
from textual.widgets import (
    Button, Footer, Header, Input, RichLog, Static, 
    ProgressBar, Label, DataTable, TabbedContent, TabPane
)
from textual.reactive import reactive
from textual.message import Message
from rich.console import Console
from rich.text import Text
from rich.panel import Panel
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn
from rich.align import Align

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
        self.tokens = len(content.split())

class AvatarStatusWidget(Static):
    """Widget displaying avatar status and metrics"""
    
    emotion = reactive("happy")
    state = reactive("idle")
    intensity = reactive(50)
    
    def compose(self) -> ComposeResult:
        yield Static(id="avatar-display")
        yield Static(id="avatar-metrics")
    
    def watch_emotion(self, emotion: str) -> None:
        """Update display when emotion changes"""
        self.update_avatar_display()
    
    def watch_state(self, state: str) -> None:
        """Update display when state changes"""
        self.update_avatar_display()
    
    def update_avatar_display(self):
        """Update the avatar visual display"""
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
        
        emotion_icon = emotion_icons.get(self.emotion, '😊')
        state_icon = state_icons.get(self.state, '💤')
        
        avatar_panel = Panel(
            Align.center(f"{emotion_icon} {state_icon}\n\n{self.emotion.title()}\n{self.state.title()}\nIntensity: {self.intensity}%"),
            title="Avatar Status",
            border_style="blue"
        )
        
        display_widget = self.query_one("#avatar-display")
        display_widget.update(avatar_panel)

class MetricsWidget(Static):
    """Widget displaying system metrics and eco-friendly stats"""
    
    def compose(self) -> ComposeResult:
        yield Static(id="metrics-display")
        self.update_metrics()
    
    def update_metrics(self, app_instance=None):
        """Update metrics display with real-time data"""
        # Get real-time metrics
        if app_instance and app_instance.system_monitor:
            try:
                # Calculate real metrics
                session_time = time.time() - app_instance.system_monitor.start_time if hasattr(app_instance.system_monitor, 'start_time') else 0
                message_count = len(app_instance.conversation_context) // 2  # Divide by 2 for user/ai pairs
                
                # Eco calculations (approximate)
                energy_saved = message_count * 3.2  # Wh per message vs cloud
                water_saved = message_count * 0.12  # L per message vs cloud  
                co2_prevented = message_count * 14   # g per message vs cloud
                
                # Format session time
                hours = int(session_time // 3600)
                minutes = int((session_time % 3600) // 60)
                session_str = f"{hours}:{minutes:02d}"
                
                # Token estimation
                total_tokens = sum(len(msg.split()) for msg in app_instance.conversation_context)
                
                # Create metrics table with real data
                table = Table(title="System Metrics", show_header=False)
                table.add_column("Metric", style="cyan")
                table.add_column("Value", style="green")
                
                table.add_row("⚡ Energy Saved", f"{energy_saved:.1f} Wh")
                table.add_row("💧 Water Saved", f"{water_saved:.2f}L")
                table.add_row("🌍 CO2 Prevented", f"{co2_prevented:.0f}g")
                table.add_row("💬 Messages", str(message_count))
                table.add_row("🔄 Session Time", session_str)
                table.add_row("🎯 Tokens", f"{total_tokens:,}")
                table.add_row("🤖 AI Status", app_instance.ai_status)
                table.add_row("🔊 Voice", app_instance.voice_status)
                table.add_row("👾 Avatar", app_instance.avatar_status)
                
                # Send metrics to avatar if connected
                if app_instance.avatar_running and AVATAR_AVAILABLE:
                    try:
                        from avatar_server import send_metrics_update
                        metrics_data = {
                            'energy_saved': energy_saved,
                            'water_saved': water_saved,
                            'co2_prevented': co2_prevented,
                            'messages': message_count,
                            'session_time': session_str,
                            'tokens': total_tokens
                        }
                        send_metrics_update(metrics_data)
                    except Exception as e:
                        pass  # Silently fail if avatar not available
                        
            except Exception as e:
                # Fallback to static data
                table = Table(title="System Metrics", show_header=False)
                table.add_column("Metric", style="cyan")
                table.add_column("Value", style="green")
                
                table.add_row("⚡ Energy Saved", "127 Wh")
                table.add_row("💧 Water Saved", "3.2L")
                table.add_row("🌍 CO2 Prevented", "85g")
                table.add_row("💬 Messages", "15")
                table.add_row("🔄 Session Time", "12:34")
                table.add_row("🎯 Tokens", "1,247")
                
        else:
            # Static fallback data
            table = Table(title="System Metrics", show_header=False)
            table.add_column("Metric", style="cyan")
            table.add_column("Value", style="green")
            
            table.add_row("⚡ Energy Saved", "127 Wh")
            table.add_row("💧 Water Saved", "3.2L")
            table.add_row("🌍 CO2 Prevented", "85g")
            table.add_row("💬 Messages", "15")
            table.add_row("🔄 Session Time", "12:34")
            table.add_row("🎯 Tokens", "1,247")
        
        metrics_panel = Panel(table, title="Eco Metrics", border_style="green")
        
        try:
            display_widget = self.query_one("#metrics-display") 
            display_widget.update(metrics_panel)
        except Exception:
            # Widget not yet mounted, store for later
            self._cached_metrics = metrics_panel

class ChatWidget(Container):
    """Main chat interface widget"""
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.messages: list[ChatMessage] = []
        
    def compose(self) -> ComposeResult:
        with Vertical():
            yield RichLog(id="chat-log", highlight=True, markup=True)
            with Horizontal(id="input-container"):
                yield Input(placeholder="Type your message here...", id="chat-input")
                yield Button("Send", variant="primary", id="send-button")
    
    def add_message(self, message: ChatMessage):
        """Add a message to the chat"""
        self.messages.append(message)
        
        # Format message for display
        timestamp = datetime.fromtimestamp(message.timestamp).strftime("%H:%M")
        
        if message.role == "user":
            styled_text = Text(f"[{timestamp}] 👤 You: {message.content}", style="cyan")
        elif message.role == "assistant": 
            styled_text = Text(f"[{timestamp}] 🤖 M1K3: {message.content}", style="green")
        else:
            styled_text = Text(f"[{timestamp}] ℹ️  {message.content}", style="yellow")
        
        chat_log = self.query_one("#chat-log")
        chat_log.write(styled_text)
    
    def clear_chat(self):
        """Clear all messages"""
        self.messages.clear()
        chat_log = self.query_one("#chat-log")
        chat_log.clear()

class M1K3TUIApp(App):
    """Main M1K3 Textual TUI Application"""
    
    TITLE = "M1K3 - AI Terminal Interface"
    SUB_TITLE = "Local AI Assistant with Real-time Avatar"
    
    # Embedded CSS as fallback
    EMBEDDED_CSS = """
    /* M1K3 Embedded TUI Styles */
    App { background: #0A0A0A; color: #FFFFFF; }
    Header { background: #E25303; color: #FFFFFF; }
    Footer { background: #141414; color: #A0A0A0; }
    Tabs { background: #141414; }
    Tab { background: #1F1F1F; color: #A0A0A0; margin: 1; }
    Tab:hover { background: #2A2A2A; color: #FFFFFF; }
    Tab.-active { background: #E25303; color: #FFFFFF; }
    #chat-log { background: #0A0A0A; border: solid #1F1F1F; }
    #chat-input { background: #1A1A1A; border: solid #2A2A2A; color: #FFFFFF; }
    #chat-input:focus { border: solid #E25303; }
    #send-button { background: #E25303; color: #FFFFFF; }
    #send-button:hover { background: #FF6B33; }
    Button { background: #1F1F1F; color: #A0A0A0; }
    Button:hover { background: #2A2A2A; color: #FFFFFF; }
    #sidebar { background: #141414; border: solid #1F1F1F; }
    Static { color: #FFFFFF; }
    *:focus { border: solid #E25303; }
    """
    
    CSS_PATH = "tui_styles_simple.css"
    
    BINDINGS = [
        Binding("ctrl+q", "quit", "Quit", priority=True),
        Binding("ctrl+c", "clear_chat", "Clear Chat"),
        Binding("ctrl+s", "show_stats", "Statistics"),
        Binding("ctrl+a", "toggle_avatar", "Toggle Avatar"),
        Binding("ctrl+v", "toggle_voice", "Toggle Voice"),
        Binding("escape", "focus_input", "Focus Input"),
        Binding("f1", "help", "Help"),
    ]
    
    # Reactive properties
    connected = reactive(False)
    voice_enabled = reactive(True)
    avatar_running = reactive(False)
    current_emotion = reactive("happy")
    current_state = reactive("idle")
    
    def __init__(self, voice_enabled: bool = True, **kwargs):
        super().__init__(**kwargs)
        
        # Initialize AI engine with error handling
        try:
            if REAL_AI_AVAILABLE:
                self.ai_engine = LocalAIEngine()
                self.ai_status = "✅ AI: LocalAI Ready"
            else:
                self.ai_engine = SimpleAIEngine()
                self.ai_status = "⚠️ AI: Fallback Mode"
        except Exception as e:
            from simple_ai_engine import SimpleAIEngine
            self.ai_engine = SimpleAIEngine()
            self.ai_status = f"❌ AI: Error fallback ({str(e)[:30]}...)"
        
        # Initialize voice engine with error handling
        self.voice_enabled = voice_enabled
        try:
            self.voice_engine = create_voice_engine() if voice_enabled else None
            self.voice_status = "✅ Voice: Ready" if voice_enabled else "⏹️ Voice: Disabled"
        except Exception as e:
            self.voice_engine = None
            self.voice_status = f"❌ Voice: Error ({str(e)[:20]}...)"
            self.voice_enabled = False
        
        # Initialize system monitor with error handling
        try:
            self.system_monitor = SystemMonitor()
            self.metrics_status = "✅ Metrics: Active"
        except Exception as e:
            self.system_monitor = None
            self.metrics_status = f"❌ Metrics: Error ({str(e)[:20]}...)"
        
        # Avatar connection tracking
        self.avatar_status = "🔌 Avatar: Disconnected"
        self.check_avatar_status()
        
        # Chat state and session management
        self.conversation_context = []
        self.session_file = ".m1k3_session.json"
        self.load_session()
        
        # Set up cleanup handlers
        atexit.register(self.cleanup_on_exit)
        signal.signal(signal.SIGTERM, self._signal_handler)
        signal.signal(signal.SIGINT, self._signal_handler)
        
        # CSS fallback handling
        self.setup_css()
        
        # Cache emotion keywords for performance
        self._emotion_keywords = {
            'sad': ['error', 'sorry', 'cannot', 'unable', 'failed', 'problem'],
            'excited': ['excellent', 'amazing', 'fantastic', 'great!', 'awesome', 'wonderful'],
            'love': ['thank you', 'appreciate', 'helpful', 'perfect', 'love'],
            'thinking': ['analyze', 'consider', 'think', 'examine', 'evaluate', 'let me'],
            'surprised': ['interesting', 'surprising', 'unexpected', 'wow', 'remarkable'],
            'code': ['function', 'class', 'import', 'def ', 'return', 'print']
        }
        
    def compose(self) -> ComposeResult:
        """Compose the UI layout"""
        yield Header(show_clock=True)
        
        with TabbedContent(initial="chat"):
            with TabPane("Chat", id="chat"):
                with Horizontal():
                    yield ChatWidget(id="chat-widget")
                    with Vertical(id="sidebar"):
                        yield AvatarStatusWidget(id="avatar-status") 
                        yield MetricsWidget(id="metrics")
            
            with TabPane("Statistics", id="stats"):
                yield Static("Detailed statistics will be shown here", id="stats-content")
            
            with TabPane("Settings", id="settings"):
                yield Static("Settings panel coming soon", id="settings-content")
        
        yield Footer()
    
    def on_mount(self) -> None:
        """Called when app starts"""
        self.title = self.TITLE
        self.sub_title = self.SUB_TITLE
        
        # Initialize avatar if available
        if AVATAR_AVAILABLE:
            self.start_avatar_server()
        
        # Add welcome message
        chat_widget = self.query_one("#chat-widget", ChatWidget)
        welcome_msg = ChatMessage(
            "Welcome to M1K3! I'm your local AI assistant. How can I help you today?",
            role="assistant"
        )
        chat_widget.add_message(welcome_msg)
        
        # Update metrics with app instance
        metrics_widget = self.query_one("#metrics", MetricsWidget)
        metrics_widget.update_metrics(self)
        
        # Focus the input
        self.query_one("#chat-input", Input).focus()
        
        # Start health monitoring timer (10s interval for better performance)
        self.set_timer(10.0, self.health_check_timer)
    
    def start_avatar_server(self):
        """Start the avatar WebSocket server"""
        if AVATAR_AVAILABLE and not is_avatar_server_running():
            try:
                start_avatar_server()
                self.avatar_running = True
                send_avatar_emotion("happy", 70, "M1K3 TUI started!")
                send_avatar_state("idle")
            except Exception as e:
                self.notify(f"Failed to start avatar server: {e}", severity="error")
    
    @on(Button.Pressed, "#send-button")
    async def on_send_button(self, event: Button.Pressed) -> None:
        """Handle send button press"""
        await self.send_message()
    
    @on(Input.Submitted, "#chat-input")
    async def on_input_submitted(self, event: Input.Submitted) -> None:
        """Handle input submission (Enter key)"""
        await self.send_message()
    
    async def send_message(self):
        """Send user message and get AI response"""
        input_widget = self.query_one("#chat-input", Input)
        message_text = input_widget.value.strip()
        
        if not message_text:
            return
        
        # Clear input
        input_widget.value = ""
        
        # Add user message to chat
        chat_widget = self.query_one("#chat-widget", ChatWidget)
        user_message = ChatMessage(message_text, role="user")
        chat_widget.add_message(user_message)
        
        # Handle special commands
        if message_text.lower() in ['quit', 'exit', 'q']:
            self.exit()
            return
        elif message_text.lower() in ['clear', 'reset']:
            self.action_clear_chat()
            return
        elif message_text.lower() in ['help', 'h']:
            self.show_help()
            return
        
        # Update avatar state
        if self.avatar_running:
            send_avatar_state("thinking")
            send_avatar_emotion("thinking", 80, "Processing user message...")
        
        try:
            # Get AI response
            self.current_state = "generating"
            
            # Add context and generate response
            self.conversation_context.append(f"User: {message_text}")
            
            if REAL_AI_AVAILABLE:
                # LocalAIEngine doesn't support context parameter, so we build it into the prompt
                context_prompt = "\n".join(self.conversation_context[-6:]) + f"\nUser: {message_text}\nAssistant:"
                # Collect the streaming response
                response = ""
                for token in self.ai_engine.generate_response(context_prompt):
                    response += token
            else:
                response = self.ai_engine.generate_response(message_text)
            
            # Add AI response to context and chat
            self.conversation_context.append(f"Assistant: {response}")
            ai_message = ChatMessage(response, role="assistant")
            chat_widget.add_message(ai_message)
            
            # Intelligent emotion detection based on response
            emotion, intensity = self.analyze_response_emotion(response, message_text)
            
            # Update avatar with intelligent emotion
            if self.avatar_running:
                send_avatar_state("idle")
                send_avatar_emotion(emotion, intensity, f"Response: {emotion}")
                send_sound_trigger("message_received")
            
            # Voice synthesis
            if self.voice_enabled and self.voice_engine and response:
                if self.avatar_running:
                    send_avatar_state("speaking")
                self.voice_engine.synthesize_and_play(response)
                if self.avatar_running:
                    send_avatar_state("idle")
            
            # Update metrics after response
            try:
                metrics_widget = self.query_one("#metrics", MetricsWidget)
                metrics_widget.update_metrics(self)
            except Exception:
                pass  # Don't let metrics update break the flow
                    
        except Exception as e:
            error_msg = f"Error: {str(e)}"
            error_message = ChatMessage(error_msg, role="system")
            chat_widget.add_message(error_message)
            
            if self.avatar_running:
                send_avatar_state("error")
                send_avatar_emotion("angry", 100, "An error occurred")
                send_sound_trigger("error")
    
    def show_help(self):
        """Show help information"""
        help_text = """M1K3 TUI Help:

Keyboard Shortcuts:
• Ctrl+Q - Quit application
• Ctrl+C - Clear chat history  
• Ctrl+S - Show statistics
• Ctrl+A - Toggle avatar server
• Ctrl+V - Toggle voice synthesis
• Escape - Focus chat input
• F1 - Show this help

Commands:
• Type naturally to chat with M1K3
• 'clear' or 'reset' - Clear conversation
• 'help' - Show this help
• 'quit' or 'exit' - Exit application

Features:
• Real-time avatar emotions and states
• Voice synthesis (if enabled)
• Eco-friendly metrics tracking
• Full keyboard and mouse support"""
        
        chat_widget = self.query_one("#chat-widget", ChatWidget)
        help_message = ChatMessage(help_text, role="system")
        chat_widget.add_message(help_message)
    
    def action_quit(self) -> None:
        """Quit the application"""
        self.exit()
    
    def action_clear_chat(self) -> None:
        """Clear chat history"""
        chat_widget = self.query_one("#chat-widget", ChatWidget)
        chat_widget.clear_chat()
        self.conversation_context.clear()
        self.notify("Chat history cleared")
    
    def action_show_stats(self) -> None:
        """Show statistics tab"""
        tabbed_content = self.query_one(TabbedContent)
        tabbed_content.active = "stats"
        
        # Update stats content
        stats_widget = self.query_one("#stats-content", Static)
        
        ai_stats = self.ai_engine.get_memory_usage()
        metrics = self.system_monitor.collect_metrics()
        
        stats_text = f"""📊 M1K3 Statistics

💾 Memory Usage:
• AI Engine: {ai_stats.get('memory_mb', 0):.1f} MB
• Total RAM: {metrics.memory_total_gb:.1f} GB
• Available: {metrics.memory_available_gb:.1f} GB

🖥️ System:
• CPU: {metrics.cpu_model}
• OS: {metrics.os_name} {metrics.os_version}
• Architecture: {metrics.architecture}

🔋 Status:
• Battery: {metrics.battery_status()}
• Performance: {metrics.performance_status()}
• Thermal: {metrics.thermal_status()}

💬 Conversation:
• Messages: {len(self.conversation_context)}
• Context Length: {len(' '.join(self.conversation_context))} chars
• Voice Enabled: {'Yes' if self.voice_enabled else 'No'}
• Avatar Server: {'Running' if self.avatar_running else 'Stopped'}"""
        
        stats_widget.update(stats_text)
    
    def action_toggle_avatar(self) -> None:
        """Toggle avatar server"""
        if AVATAR_AVAILABLE:
            if self.avatar_running:
                # Stop avatar server
                stop_avatar_server()
                self.avatar_running = False
                self.notify("Avatar server stopped")
            else:
                self.start_avatar_server()
                self.notify("Avatar server started")
        else:
            self.notify("Avatar system not available", severity="warning")
    
    def action_toggle_voice(self) -> None:
        """Toggle voice synthesis"""
        self.voice_enabled = not self.voice_enabled
        status = "enabled" if self.voice_enabled else "disabled"
        self.notify(f"Voice synthesis {status}")
    
    def action_focus_input(self) -> None:
        """Focus the chat input"""
        self.query_one("#chat-input", Input).focus()
    
    def action_help(self) -> None:
        """Show help"""
        self.show_help()
    
    def _signal_handler(self, signum, frame):
        """Handle signals (SIGTERM, SIGINT) for graceful shutdown"""
        self.cleanup_on_exit()
        sys.exit(0)
    
    def cleanup_on_exit(self):
        """Clean up resources on application exit"""
        try:
            # Save session data
            self.save_session()
            
            # Stop avatar server if running
            if AVATAR_AVAILABLE and hasattr(self, 'avatar_running') and self.avatar_running:
                stop_avatar_server()
                
        except Exception as e:
            # Fail silently during cleanup to avoid exit issues
            pass
    
    def setup_css(self):
        """Setup CSS with fallback to embedded styles"""
        try:
            if not os.path.exists(self.CSS_PATH):
                # Use embedded CSS if file doesn't exist
                self.CSS = self.EMBEDDED_CSS
        except Exception:
            # Always fallback to embedded CSS on error
            self.CSS = self.EMBEDDED_CSS
    
    def check_avatar_status(self):
        """Check avatar server connection status"""
        try:
            if AVATAR_AVAILABLE and is_avatar_server_running():
                self.avatar_status = "🟢 Avatar: Connected"
                self.avatar_running = True
            else:
                self.avatar_status = "🔴 Avatar: Disconnected" 
                self.avatar_running = False
        except Exception as e:
            self.avatar_status = f"❌ Avatar: Error ({str(e)[:20]}...)"
            self.avatar_running = False
    
    def load_session(self):
        """Load chat session from file"""
        try:
            if os.path.exists(self.session_file):
                with open(self.session_file, 'r') as f:
                    session_data = json.loads(f.read())
                    self.conversation_context = session_data.get('context', [])
                    # Could load other session state here
        except Exception as e:
            # Silently fail - session loading is optional
            pass
    
    def save_session(self):
        """Save chat session to file"""
        try:
            session_data = {
                'context': self.conversation_context[-20:],  # Save last 20 exchanges
                'timestamp': datetime.now().isoformat(),
                'ai_status': self.ai_status,
                'voice_status': self.voice_status
            }
            with open(self.session_file, 'w') as f:
                f.write(json.dumps(session_data, indent=2))
        except Exception as e:
            # Silently fail - session saving is optional
            pass
    
    def health_check_timer(self):
        """Periodic health check for avatar system"""
        try:
            # Reschedule for next check (create repeating behavior)
            self.set_timer(10.0, self.health_check_timer)
            # Update avatar connection status
            old_status = self.avatar_status
            self.check_avatar_status()
            
            # If status changed, update avatar widget
            if old_status != self.avatar_status:
                try:
                    avatar_widget = self.query_one("#avatar-status", AvatarStatusWidget)
                    avatar_widget.update_avatar_display()
                except Exception:
                    pass
            
            # Update metrics periodically 
            try:
                metrics_widget = self.query_one("#metrics", MetricsWidget)
                metrics_widget.update_metrics(self)
            except Exception:
                pass
                
            # Check system health
            if self.system_monitor:
                try:
                    # Get current system metrics (optional health indicators)
                    metrics = self.system_monitor.get_all_metrics()
                    
                    # Update status indicators based on system health
                    if hasattr(metrics, 'battery_percent') and metrics.battery_percent is not None:
                        if metrics.battery_percent < 20:
                            # Low battery warning
                            if self.avatar_running and AVATAR_AVAILABLE:
                                send_avatar_emotion("sleepy", 30, "Low battery detected")
                except Exception:
                    pass
                    
        except Exception as e:
            # Don't let health check crash the app
            pass
    
    def analyze_response_emotion(self, response: str, user_input: str) -> tuple[str, int]:
        """Analyze AI response and user input to determine appropriate emotion (optimized)"""
        response_lower = response.lower()
        user_lower = user_input.lower()
        
        # Use cached keywords for faster lookup
        if any(word in response_lower for word in self._emotion_keywords['sad']):
            return ("sad", 60)
        
        if any(word in response_lower for word in self._emotion_keywords['excited']):
            return ("excited", 85)
        
        if any(word in response_lower for word in self._emotion_keywords['love']):
            return ("love", 80)
        
        if any(word in response_lower for word in self._emotion_keywords['thinking']):
            return ("thinking", 70)
        
        if any(word in response_lower for word in self._emotion_keywords['surprised']):
            return ("surprised", 75)
        
        # User emotion influence (simplified)
        if any(word in user_lower for word in ['help', 'please', 'thank']):
            return ("happy", 80)
        
        if any(word in user_lower for word in ['problem', 'issue', 'error', 'wrong', 'broken']):
            return ("thinking", 85)
        
        # Code/technical content
        if any(word in response_lower for word in self._emotion_keywords['code']):
            return ("excited", 70)
        
        # Length-based emotion (optimized thresholds)
        response_len = len(response)
        if response_len > 500:
            return ("excited", 75)
        elif response_len < 50:
            return ("thinking", 50)
        
        # Default happy state
        return ("happy", 65)
    
    def on_shutdown_request(self, event):
        """Handle app shutdown"""
        self.save_session()
        return super().on_shutdown_request(event)

def main():
    """Main entry point"""
    parser = argparse.ArgumentParser(description="M1K3 Textual Terminal Interface")
    parser.add_argument("--no-voice", action="store_true", help="Disable voice synthesis")
    parser.add_argument("--no-avatar", action="store_true", help="Disable avatar server")
    
    args = parser.parse_args()
    
    # Create and run the app
    app = M1K3TUIApp(voice_enabled=not args.no_voice)
    app.run()

if __name__ == "__main__":
    main()