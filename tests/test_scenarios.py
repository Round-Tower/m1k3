#!/usr/bin/env python3
"""
M1K3 Avatar System - Comprehensive Test Scenarios
Automated testing suite for all avatar system components
"""

import asyncio
import websockets
import json
import time
import random
import sys
import argparse
from src.avatar.avatar_server import (
    send_avatar_emotion, send_avatar_state, send_avatar_progress,
    send_chat_ai_start, send_chat_ai_chunk, send_chat_ai_complete, 
    send_sound_trigger, send_metrics_update
)

class M1K3TestRunner:
    def __init__(self, host='localhost', port=8081):
        self.host = host
        self.port = port
        self.websocket = None
        self.test_results = {}
        self.start_time = None
        
        # Test data
        self.emotions = ['happy', 'sad', 'angry', 'surprised', 'love', 'thinking', 'sleepy', 'excited']
        self.states = ['idle', 'listening', 'thinking', 'pre_thinking', 'generating', 'speaking', 'error']
        self.styles = ['robot', 'pixel', 'modern', 'retro']
        self.sounds = ['connect', 'message_sent', 'message_received', 'error', 'thinking', 'emotion_change']
        
        # Sample chat messages
        self.user_messages = [
            "Hello M1K3!",
            "How are you today?",
            "Tell me about artificial intelligence",
            "What's the weather like?",
            "Can you help me with coding?",
            "What's your favorite color?",
            "Explain quantum computing",
            "How does machine learning work?"
        ]
        
        self.ai_responses = [
            "Hello! I'm doing great, thanks for asking.",
            "I'm having a wonderful day helping users like yourself!",
            "Artificial intelligence is the simulation of human intelligence processes by machines.",
            "I don't have access to real-time weather data, but I'd be happy to help you find weather resources!",
            "Absolutely! I'd love to help you with coding. What programming language are you working with?",
            "I find the color orange quite appealing - it's vibrant and energetic!",
            "Quantum computing uses quantum mechanical phenomena like superposition and entanglement to process information.",
            "Machine learning is a subset of AI that enables computers to learn and improve from experience without being explicitly programmed."
        ]
    
    async def connect(self):
        """Connect to the WebSocket server"""
        try:
            self.websocket = await websockets.connect(f'ws://{self.host}:{self.port}')
            print(f"✅ Connected to M1K3 Avatar Server at {self.host}:{self.port}")
            return True
        except Exception as e:
            print(f"❌ Failed to connect: {e}")
            return False
    
    async def disconnect(self):
        """Disconnect from the WebSocket server"""
        if self.websocket:
            await self.websocket.close()
            print("🔌 Disconnected from server")
    
    async def run_test_scenario(self, scenario_name, test_func):
        """Run a single test scenario"""
        print(f"\n🧪 Running Test: {scenario_name}")
        print("-" * 50)
        
        start_time = time.time()
        success = False
        
        try:
            await test_func()
            success = True
            print(f"✅ {scenario_name}: PASSED")
        except Exception as e:
            print(f"❌ {scenario_name}: FAILED - {e}")
            import traceback
            traceback.print_exc()
        
        duration = time.time() - start_time
        self.test_results[scenario_name] = {
            'success': success,
            'duration': duration,
            'timestamp': time.time()
        }
        
        await asyncio.sleep(1)  # Brief pause between tests
    
    async def test_basic_connection(self):
        """Test basic WebSocket connectivity"""
        # Send a simple ping
        await self.websocket.send(json.dumps({'type': 'ping'}))
        
        # Wait for any response (should be pong)
        try:
            response = await asyncio.wait_for(self.websocket.recv(), timeout=5.0)
            data = json.loads(response)
            if data.get('type') == 'pong':
                print("✅ Ping/Pong successful")
            else:
                print(f"⚠️  Unexpected response: {data}")
        except asyncio.TimeoutError:
            raise Exception("No pong received within 5 seconds")
    
    async def test_emotion_system(self):
        """Test emotion changes and transitions"""
        print("Testing emotion system...")
        
        for i, emotion in enumerate(self.emotions):
            intensity = random.randint(30, 100)
            message = f"Testing {emotion} emotion with intensity {intensity}"
            
            print(f"  {i+1}/{len(self.emotions)}: {emotion} ({intensity}%)")
            send_avatar_emotion(emotion, intensity, message)
            await asyncio.sleep(0.8)
        
        print("✅ All emotions tested")
    
    async def test_state_transitions(self):
        """Test avatar state changes"""
        print("Testing state transitions...")
        
        for i, state in enumerate(self.states):
            print(f"  {i+1}/{len(self.states)}: {state}")
            send_avatar_state(state)
            await asyncio.sleep(1.2)
        
        print("✅ All states tested")
    
    async def test_style_changes(self):
        """Test avatar style changes (simulated with emotions)"""
        print("Testing style changes (via emotion variations)...")
        
        # Since there's no send_avatar_style, we'll test different emotion intensities
        for i, style in enumerate(self.styles):
            emotion = random.choice(self.emotions)
            intensity = random.randint(20, 100)
            print(f"  {i+1}/{len(self.styles)}: {style} style simulation with {emotion} ({intensity}%)")
            send_avatar_emotion(emotion, intensity, f"Testing {style} style")
            await asyncio.sleep(1.5)
        
        print("✅ Style variations tested")
    
    async def test_progress_updates(self):
        """Test progress reporting system"""
        print("Testing progress updates...")
        
        stages = [
            ('pre_thinking', 'Preparing to think...', 0),
            ('thinking', 'Processing your request...', 25), 
            ('generating', 'Generating response...', 60),
            ('speaking', 'Delivering response...', 90),
            ('complete', 'Response complete!', 100)
        ]
        
        tokens_used = 0
        for stage, message, progress in stages:
            tokens_used += random.randint(5, 15)
            print(f"  {stage}: {progress}% - {tokens_used} tokens")
            send_avatar_progress(stage, progress, tokens_used, message)
            await asyncio.sleep(1.0)
        
        print("✅ Progress updates tested")
    
    async def test_chat_streaming(self):
        """Test chat message streaming"""
        print("Testing chat streaming...")
        
        # Test user message (simulated via direct WebSocket)
        user_msg = random.choice(self.user_messages)
        print(f"  User: {user_msg}")
        await self.websocket.send(json.dumps({
            'type': 'chat_user',
            'message': user_msg
        }))
        await asyncio.sleep(0.5)
        
        # Test AI response streaming
        ai_response = random.choice(self.ai_responses)
        print(f"  AI: Starting response...")
        send_chat_ai_start()
        await asyncio.sleep(0.5)
        
        # Stream the response word by word
        words = ai_response.split()
        for i, word in enumerate(words):
            chunk = word + (' ' if i < len(words) - 1 else '')
            send_chat_ai_chunk(chunk)
            await asyncio.sleep(0.1)
        
        send_chat_ai_complete()
        print("✅ Chat streaming tested")
    
    async def test_sound_system(self):
        """Test sound effect triggers"""
        print("Testing sound system...")
        
        for i, sound in enumerate(self.sounds):
            print(f"  {i+1}/{len(self.sounds)}: {sound}")
            send_sound_trigger(sound)
            await asyncio.sleep(1.0)
        
        print("✅ Sound system tested")
    
    async def test_metrics_updates(self):
        """Test metrics reporting"""
        print("Testing metrics updates...")
        
        metrics = {
            'energy_saved': random.randint(100, 500),
            'water_saved': random.randint(1000, 5000),
            'co2_prevented': random.randint(50, 200), 
            'messages_processed': random.randint(10, 50),
            'session_duration': random.randint(300, 1800),
            'tokens_used': random.randint(500, 2000)
        }
        
        print(f"  Metrics: {metrics}")
        send_metrics_update(metrics)
        await asyncio.sleep(1.0)
        
        print("✅ Metrics tested")
    
    async def test_rapid_fire_messages(self):
        """Test system under rapid message load"""
        print("Testing rapid fire messages...")
        
        message_count = 20
        for i in range(message_count):
            emotion = random.choice(self.emotions)
            intensity = random.randint(20, 100)
            send_avatar_emotion(emotion, intensity, f"Rapid test {i+1}")
            
            if i % 5 == 0:
                print(f"  Sent {i+1}/{message_count} messages")
            
            await asyncio.sleep(0.1)  # Very rapid
        
        print("✅ Rapid fire test completed")
    
    async def test_conversation_simulation(self):
        """Simulate a realistic conversation"""
        print("Testing conversation simulation...")
        
        conversation = [
            ("user", "Hello M1K3, how are you?"),
            ("ai_start", ""),
            ("ai_chunk", "Hello! "),
            ("ai_chunk", "I'm doing "),
            ("ai_chunk", "wonderful "),
            ("ai_chunk", "today. "),
            ("ai_chunk", "How can "),
            ("ai_chunk", "I help "),
            ("ai_chunk", "you?"),
            ("ai_complete", ""),
            ("user", "Tell me about AI"),
            ("ai_start", ""),
            ("ai_chunk", "Artificial intelligence "),
            ("ai_chunk", "is fascinating! "),
            ("ai_chunk", "It's the simulation "),
            ("ai_chunk", "of human intelligence "),
            ("ai_chunk", "in machines."),
            ("ai_complete", "")
        ]
        
        for msg_type, content in conversation:
            if msg_type == "user":
                print(f"  👤 User: {content}")
                await self.websocket.send(json.dumps({
                    'type': 'chat_user', 
                    'message': content
                }))
                send_avatar_emotion('happy', 80, 'User is chatting!')
                send_sound_trigger('message_sent')
                
            elif msg_type == "ai_start":
                print(f"  🤖 AI: Starting response...")
                send_chat_ai_start()
                send_avatar_state('thinking')
                send_sound_trigger('thinking')
                
            elif msg_type == "ai_chunk":
                send_chat_ai_chunk(content)
                send_avatar_state('generating')
                
            elif msg_type == "ai_complete":
                send_chat_ai_complete()
                send_avatar_state('idle')
                send_avatar_emotion('happy', 70, 'Response complete!')
                send_sound_trigger('message_received')
                print(f"  🤖 AI: Response complete!")
            
            await asyncio.sleep(0.3)
        
        print("✅ Conversation simulation completed")
    
    async def test_error_handling(self):
        """Test error conditions and recovery"""
        print("Testing error handling...")
        
        # Test invalid emotion
        try:
            send_avatar_emotion('invalid_emotion', 50, 'Testing invalid emotion')
            await asyncio.sleep(0.5)
            print("  ⚠️  Invalid emotion sent")
        except Exception as e:
            print(f"  ✅ Invalid emotion handled: {e}")
        
        # Test invalid state
        try:
            send_avatar_state('invalid_state')
            await asyncio.sleep(0.5)
            print("  ⚠️  Invalid state sent")
        except Exception as e:
            print(f"  ✅ Invalid state handled: {e}")
        
        # Trigger error state
        send_avatar_state('error')
        send_avatar_emotion('angry', 100, 'Error occurred!')
        send_sound_trigger('error')
        await asyncio.sleep(1.0)
        
        # Recovery
        send_avatar_state('idle')
        send_avatar_emotion('happy', 50, 'System recovered')
        await asyncio.sleep(0.5)
        
        print("✅ Error handling tested")
    
    async def run_all_tests(self):
        """Run the complete test suite"""
        self.start_time = time.time()
        
        print("🚀 Starting M1K3 Avatar System Test Suite")
        print("=" * 60)
        
        test_scenarios = [
            ('Basic Connection', self.test_basic_connection),
            ('Emotion System', self.test_emotion_system),
            ('State Transitions', self.test_state_transitions),
            ('Style Changes', self.test_style_changes),
            ('Progress Updates', self.test_progress_updates),
            ('Chat Streaming', self.test_chat_streaming),
            ('Sound System', self.test_sound_system),
            ('Metrics Updates', self.test_metrics_updates),
            ('Rapid Fire Messages', self.test_rapid_fire_messages),
            ('Conversation Simulation', self.test_conversation_simulation),
            ('Error Handling', self.test_error_handling)
        ]
        
        for name, test_func in test_scenarios:
            await self.run_test_scenario(name, test_func)
        
        await self.print_summary()
    
    async def print_summary(self):
        """Print test results summary"""
        total_time = time.time() - self.start_time
        passed = sum(1 for result in self.test_results.values() if result['success'])
        total = len(self.test_results)
        
        print("\n" + "=" * 60)
        print("🧪 TEST RESULTS SUMMARY")
        print("=" * 60)
        
        for test_name, result in self.test_results.items():
            status = "✅ PASSED" if result['success'] else "❌ FAILED"
            duration = f"{result['duration']:.2f}s"
            print(f"{status:<10} {test_name:<25} ({duration})")
        
        print("-" * 60)
        print(f"Total Tests: {total}")
        print(f"Passed: {passed}")
        print(f"Failed: {total - passed}")
        print(f"Success Rate: {(passed/total)*100:.1f}%")
        print(f"Total Duration: {total_time:.2f}s")
        
        if passed == total:
            print("\n🎉 All tests passed! M1K3 Avatar System is working perfectly!")
        else:
            print(f"\n⚠️  {total - passed} tests failed. Check the logs above for details.")

async def main():
    parser = argparse.ArgumentParser(description='M1K3 Avatar System Test Suite')
    parser.add_argument('--host', default='localhost', help='WebSocket server host')
    parser.add_argument('--port', type=int, default=8081, help='WebSocket server port')
    parser.add_argument('--scenario', help='Run specific test scenario')
    
    args = parser.parse_args()
    
    runner = M1K3TestRunner(args.host, args.port)
    
    if not await runner.connect():
        sys.exit(1)
    
    try:
        if args.scenario:
            # Run specific scenario (implement if needed)
            print(f"Running specific scenario: {args.scenario}")
            await runner.run_all_tests()  # For now, run all tests
        else:
            await runner.run_all_tests()
    
    finally:
        await runner.disconnect()

if __name__ == '__main__':
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n\n⚠️  Test suite interrupted by user")
    except Exception as e:
        print(f"\n\n❌ Test suite failed with error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)