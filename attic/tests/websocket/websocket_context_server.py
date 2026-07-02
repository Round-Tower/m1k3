#!/usr/bin/env python3
"""
WebSocket Context Data Server
Provides real-time context data to enrich AI classification and generation
"""

import asyncio
import json
import logging
import websockets
from typing import Dict, List, Any, Optional
from dataclasses import dataclass, asdict
from datetime import datetime
import threading

@dataclass
class ContextData:
    """Structured context data from various sources"""
    timestamp: float
    source: str  # 'user_activity', 'system_metrics', 'app_state', 'external_api'
    data_type: str  # 'typing_speed', 'battery_level', 'current_app', 'weather', etc.
    value: Any
    confidence: float = 1.0
    metadata: Dict[str, Any] = None
    
    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

class ContextDataManager:
    """Manages real-time context data collection and distribution"""
    
    def __init__(self):
        self.context_buffer: List[ContextData] = []
        self.max_buffer_size = 1000
        self.subscribers: List[websockets.WebSocketServerProtocol] = []
        self.lock = threading.Lock()
        
    def add_context_data(self, context: ContextData) -> None:
        """Add new context data to the buffer"""
        with self.lock:
            self.context_buffer.append(context)
            
            # Maintain buffer size
            if len(self.context_buffer) > self.max_buffer_size:
                self.context_buffer = self.context_buffer[-self.max_buffer_size:]
                
        # Broadcast to subscribers (only if we're in an async context)
        try:
            loop = asyncio.get_running_loop()
            asyncio.create_task(self._broadcast_context(context))
        except RuntimeError:
            # No event loop running, skip broadcast (sync context)
            pass
    
    def get_recent_context(self, seconds: int = 30) -> List[ContextData]:
        """Get context data from the last N seconds"""
        cutoff_time = datetime.now().timestamp() - seconds
        with self.lock:
            return [ctx for ctx in self.context_buffer if ctx.timestamp >= cutoff_time]
    
    def get_context_by_type(self, data_type: str, seconds: int = 30) -> List[ContextData]:
        """Get context data of a specific type from recent history"""
        recent_context = self.get_recent_context(seconds)
        return [ctx for ctx in recent_context if ctx.data_type == data_type]
    
    def get_context_summary(self, seconds: int = 30) -> Dict[str, Any]:
        """Get a summary of recent context data for classification"""
        recent_context = self.get_recent_context(seconds)
        
        summary = {
            'total_data_points': len(recent_context),
            'data_types': {},
            'sources': {},
            'latest_values': {},
            'time_range': {
                'start': min([ctx.timestamp for ctx in recent_context]) if recent_context else None,
                'end': max([ctx.timestamp for ctx in recent_context]) if recent_context else None
            }
        }
        
        for ctx in recent_context:
            # Count by data type
            summary['data_types'][ctx.data_type] = summary['data_types'].get(ctx.data_type, 0) + 1
            
            # Count by source
            summary['sources'][ctx.source] = summary['sources'].get(ctx.source, 0) + 1
            
            # Store latest values
            summary['latest_values'][ctx.data_type] = ctx.value
            
        return summary
    
    async def _broadcast_context(self, context: ContextData) -> None:
        """Broadcast context data to all WebSocket subscribers"""
        if not self.subscribers:
            return
            
        message = {
            'type': 'context_data',
            'data': context.to_dict()
        }
        
        # Send to all subscribers (remove disconnected ones)
        disconnected = []
        for subscriber in self.subscribers:
            try:
                await subscriber.send(json.dumps(message))
            except websockets.exceptions.ConnectionClosed:
                disconnected.append(subscriber)
        
        # Clean up disconnected subscribers
        for sub in disconnected:
            self.subscribers.remove(sub)

class WebSocketContextServer:
    """WebSocket server for real-time context data collection"""
    
    def __init__(self, host: str = "localhost", port: int = 8082):
        self.host = host
        self.port = port
        self.context_manager = ContextDataManager()
        self.server = None
        self.logger = logging.getLogger(__name__)
        
    async def handle_client(self, websocket):
        """Handle WebSocket client connections"""
        self.logger.info(f"🔌 New WebSocket client connected from {websocket.remote_address}")
        
        try:
            # Add to subscribers for context data broadcasts
            self.context_manager.subscribers.append(websocket)
            
            # Send current context summary to new client
            summary = self.context_manager.get_context_summary()
            welcome_message = {
                'type': 'welcome',
                'context_summary': summary,
                'server_info': {
                    'buffer_size': len(self.context_manager.context_buffer),
                    'active_subscribers': len(self.context_manager.subscribers)
                }
            }
            await websocket.send(json.dumps(welcome_message))
            
            # Listen for incoming context data
            async for message in websocket:
                try:
                    data = json.loads(message)
                    await self._handle_message(data, websocket)
                except json.JSONDecodeError:
                    await websocket.send(json.dumps({
                        'type': 'error',
                        'message': 'Invalid JSON format'
                    }))
                except Exception as e:
                    self.logger.error(f"Error handling message: {e}")
                    await websocket.send(json.dumps({
                        'type': 'error', 
                        'message': str(e)
                    }))
                    
        except websockets.exceptions.ConnectionClosed:
            self.logger.info("🔌 WebSocket client disconnected")
        finally:
            # Remove from subscribers
            if websocket in self.context_manager.subscribers:
                self.context_manager.subscribers.remove(websocket)
    
    async def _handle_message(self, data: Dict[str, Any], websocket) -> None:
        """Handle incoming WebSocket messages"""
        message_type = data.get('type', 'unknown')
        
        if message_type == 'context_data':
            # Add context data to manager
            context_data = ContextData(
                timestamp=data.get('timestamp', datetime.now().timestamp()),
                source=data.get('source', 'unknown'),
                data_type=data.get('data_type', 'unknown'),
                value=data.get('value'),
                confidence=data.get('confidence', 1.0),
                metadata=data.get('metadata', {})
            )
            self.context_manager.add_context_data(context_data)
            
            # Acknowledge receipt
            await websocket.send(json.dumps({
                'type': 'ack',
                'message': 'Context data received'
            }))
            
        elif message_type == 'get_context':
            # Client requesting context data
            seconds = data.get('seconds', 30)
            data_type = data.get('data_type')
            
            if data_type:
                context = self.context_manager.get_context_by_type(data_type, seconds)
            else:
                context = self.context_manager.get_recent_context(seconds)
            
            response = {
                'type': 'context_response',
                'context': [ctx.to_dict() for ctx in context],
                'count': len(context)
            }
            await websocket.send(json.dumps(response))
            
        elif message_type == 'get_summary':
            # Client requesting context summary
            seconds = data.get('seconds', 30)
            summary = self.context_manager.get_context_summary(seconds)
            
            response = {
                'type': 'summary_response',
                'summary': summary
            }
            await websocket.send(json.dumps(response))
            
        else:
            await websocket.send(json.dumps({
                'type': 'error',
                'message': f'Unknown message type: {message_type}'
            }))
    
    async def start_server(self) -> None:
        """Start the WebSocket server"""
        self.logger.info(f"🌐 Starting WebSocket context server on {self.host}:{self.port}")
        
        self.server = await websockets.serve(
            lambda websocket: self.handle_client(websocket),
            self.host,
            self.port
        )
        
        self.logger.info(f"✅ WebSocket context server running on ws://{self.host}:{self.port}")
    
    async def stop_server(self) -> None:
        """Stop the WebSocket server"""
        if self.server:
            self.server.close()
            await self.server.wait_closed()
            self.logger.info("🔌 WebSocket context server stopped")
    
    def add_sample_context_data(self) -> None:
        """Add some sample context data for testing"""
        import random
        
        # Sample system metrics
        battery_level = ContextData(
            timestamp=datetime.now().timestamp(),
            source='system_metrics',
            data_type='battery_level',
            value=random.randint(20, 100),
            confidence=1.0,
            metadata={'unit': 'percentage'}
        )
        self.context_manager.add_context_data(battery_level)
        
        # Sample user activity
        typing_speed = ContextData(
            timestamp=datetime.now().timestamp(),
            source='user_activity',
            data_type='typing_speed',
            value=random.randint(20, 80),
            confidence=0.8,
            metadata={'unit': 'wpm', 'sample_window': '10s'}
        )
        self.context_manager.add_context_data(typing_speed)
        
        # Sample app state
        current_app = ContextData(
            timestamp=datetime.now().timestamp(),
            source='app_state',
            data_type='active_application',
            value='Terminal',
            confidence=1.0,
            metadata={'process_name': 'Terminal.app'}
        )
        self.context_manager.add_context_data(current_app)

# Testing and demo functionality
async def demo_server():
    """Demo the WebSocket context server"""
    logging.basicConfig(level=logging.INFO)
    
    server = WebSocketContextServer()
    
    # Add sample data
    server.add_sample_context_data()
    
    # Start server
    await server.start_server()
    
    # Keep running
    try:
        print("🌐 WebSocket context server is running...")
        print(f"📡 Connect to: ws://{server.host}:{server.port}")
        print("🔌 Press Ctrl+C to stop")
        
        # Keep adding sample data periodically
        while True:
            await asyncio.sleep(10)
            server.add_sample_context_data()
            
    except KeyboardInterrupt:
        print("\n🛑 Stopping server...")
        await server.stop_server()

if __name__ == "__main__":
    asyncio.run(demo_server())