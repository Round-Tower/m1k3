#!/usr/bin/env python3
"""
Test WebSocket Context Server
"""

import asyncio
import json
import websockets
import time
from datetime import datetime

async def test_websocket_client():
    """Test the WebSocket context server"""
    
    print("🔌 Testing WebSocket Context Server...")
    
    try:
        # Connect to the server
        uri = "ws://localhost:8082"
        async with websockets.connect(uri) as websocket:
            print(f"✅ Connected to {uri}")
            
            # Listen for welcome message
            welcome = await websocket.recv()
            welcome_data = json.loads(welcome)
            print(f"📨 Welcome message: {welcome_data['type']}")
            print(f"📊 Context summary: {welcome_data.get('context_summary', {})}")
            
            # Send some test context data
            test_contexts = [
                {
                    'type': 'context_data',
                    'timestamp': time.time(),
                    'source': 'test_client',
                    'data_type': 'user_mood',
                    'value': 'focused',
                    'confidence': 0.85,
                    'metadata': {'detected_via': 'typing_pattern'}
                },
                {
                    'type': 'context_data',
                    'timestamp': time.time(),
                    'source': 'test_client', 
                    'data_type': 'task_urgency',
                    'value': 'high',
                    'confidence': 0.9,
                    'metadata': {'keywords': ['urgent', 'deadline', 'ASAP']}
                },
                {
                    'type': 'context_data',
                    'timestamp': time.time(),
                    'source': 'test_client',
                    'data_type': 'conversation_topic',
                    'value': 'technical_debugging',
                    'confidence': 0.95,
                    'metadata': {'keywords': ['error', 'fix', 'debug', 'test']}
                }
            ]
            
            # Send test data
            for context in test_contexts:
                await websocket.send(json.dumps(context))
                response = await websocket.recv()
                response_data = json.loads(response)
                print(f"📤 Sent {context['data_type']}: {context['value']}")
                print(f"📥 Response: {response_data.get('message', 'No message')}")
            
            # Request context summary
            print("\n📊 Requesting context summary...")
            summary_request = {
                'type': 'get_summary',
                'seconds': 60
            }
            await websocket.send(json.dumps(summary_request))
            summary_response = await websocket.recv()
            summary_data = json.loads(summary_response)
            
            print("📋 Context Summary:")
            summary = summary_data.get('summary', {})
            print(f"   Total data points: {summary.get('total_data_points', 0)}")
            print(f"   Data types: {summary.get('data_types', {})}")
            print(f"   Sources: {summary.get('sources', {})}")
            print(f"   Latest values: {summary.get('latest_values', {})}")
            
            # Request specific context type
            print("\n🔍 Requesting specific context type...")
            context_request = {
                'type': 'get_context',
                'data_type': 'user_mood',
                'seconds': 60
            }
            await websocket.send(json.dumps(context_request))
            context_response = await websocket.recv()
            context_data = json.loads(context_response)
            
            print(f"📋 Context for 'user_mood': {context_data.get('count', 0)} entries")
            for ctx in context_data.get('context', []):
                print(f"   {ctx['data_type']}: {ctx['value']} (confidence: {ctx['confidence']})")
            
            print("\n✅ WebSocket context server test completed successfully!")
            
    except ConnectionRefusedError:
        print("❌ Failed to connect to WebSocket server.")
        print("💡 Make sure the server is running: python websocket_context_server.py")
    except Exception as e:
        print(f"❌ Test failed: {e}")

async def stress_test_websocket():
    """Stress test the WebSocket context server"""
    print("🔥 Running stress test...")
    
    try:
        uri = "ws://localhost:8082"
        async with websockets.connect(uri) as websocket:
            print("✅ Connected for stress test")
            
            # Receive welcome message
            await websocket.recv()
            
            # Send lots of context data rapidly
            for i in range(50):
                context = {
                    'type': 'context_data',
                    'timestamp': time.time(),
                    'source': 'stress_test',
                    'data_type': f'metric_{i % 5}',  # Rotate between 5 types
                    'value': f'test_value_{i}',
                    'confidence': 0.5 + (i % 5) * 0.1
                }
                await websocket.send(json.dumps(context))
                
                # Every 10th message, wait for ack
                if i % 10 == 0:
                    ack = await websocket.recv()
                    print(f"📥 Batch {i//10 + 1} acknowledged")
            
            # Get final summary
            summary_request = {'type': 'get_summary', 'seconds': 60}
            await websocket.send(json.dumps(summary_request))
            summary_response = await websocket.recv()
            summary = json.loads(summary_response)['summary']
            
            print(f"🎯 Stress test results:")
            print(f"   Total data points: {summary.get('total_data_points', 0)}")
            print(f"   Data types: {len(summary.get('data_types', {}))}")
            print("✅ Stress test completed!")
            
    except Exception as e:
        print(f"❌ Stress test failed: {e}")

async def run_all_tests():
    """Run all WebSocket tests"""
    print("🧪 WebSocket Context Server Test Suite")
    print("=" * 50)
    
    await test_websocket_client()
    print("\n" + "=" * 50)
    await stress_test_websocket()

if __name__ == "__main__":
    asyncio.run(run_all_tests())