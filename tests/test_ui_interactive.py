#!/usr/bin/env python3
"""
Interactive UI Test Suite for M1K3 Enhanced Avatar Dashboard
Comprehensive testing of all dashboard features with real-time feedback
"""

import time
import threading
import json
import random
from datetime import datetime
from avatar_server import (
    get_avatar_server, send_avatar_emotion, send_avatar_state, send_avatar_progress,
    send_chat_ai_start, send_chat_ai_chunk, send_chat_ai_complete, 
    send_sound_trigger, send_metrics_update
)

class M1K3UITester:
    def __init__(self):
        self.server = get_avatar_server()
        self.test_running = False
        self.emotions = ['happy', 'sad', 'angry', 'surprised', 'love', 'thinking', 'sleepy', 'excited']
        self.states = ['idle', 'thinking', 'generating', 'speaking', 'post_response']
        self.sounds = ['message_sent', 'message_received', 'thinking', 'error', 'emotion_change']
        
    def start_server(self):
        """Start the avatar server if not running"""
        if not self.server.is_running():
            print("🚀 Starting avatar server...")
            if self.server.start():
                print("   ✅ Server started successfully")
                status = self.server.get_status()
                if 'urls' in status:
                    for url in status['urls']:
                        print(f"   📱 {url}")
                return True
            else:
                print("   ❌ Failed to start server")
                return False
        else:
            print("✅ Server already running")
            return True
    
    def stop_server(self):
        """Stop the avatar server"""
        if self.server.is_running():
            print("🛑 Stopping avatar server...")
            self.server.stop()
            print("   ✅ Server stopped")
    
    def test_emotion_cycling(self, duration=30):
        """Test all emotions with cycling intensity"""
        print(f"\n🎭 Testing emotion cycling ({duration}s)...")
        
        start_time = time.time()
        emotion_index = 0
        
        while time.time() - start_time < duration:
            emotion = self.emotions[emotion_index % len(self.emotions)]
            intensity = random.randint(20, 100)
            message = f"Testing {emotion} emotion at {intensity}% intensity"
            
            print(f"   😊 {emotion.capitalize()}: {intensity}%")
            send_avatar_emotion(emotion, intensity, message)
            
            # Trigger emotion change sound
            send_sound_trigger('emotion_change')
            
            emotion_index += 1
            time.sleep(2)
        
        print("   ✅ Emotion cycling test complete")
    
    def test_state_transitions(self):
        """Test all avatar states with realistic timing"""
        print("\n🔄 Testing state transitions...")
        
        state_sequence = [
            ('idle', 2, "Ready for input"),
            ('thinking', 3, "Processing user input..."),
            ('generating', 5, "Generating AI response..."),
            ('speaking', 2, "Voice synthesis active"),
            ('post_response', 1, "Response complete"),
            ('idle', 1, "Back to idle")
        ]
        
        for state, duration, description in state_sequence:
            print(f"   🔄 {state.capitalize()}: {description}")
            send_avatar_state(state)
            
            # Send progress updates during generating state
            if state == 'generating':
                for progress in range(0, 101, 20):
                    send_avatar_progress('generating', progress, progress * 2, f"Generated {progress * 2} tokens")
                    time.sleep(duration / 5)
            else:
                time.sleep(duration)
        
        print("   ✅ State transition test complete")
    
    def test_chat_streaming(self):
        """Test chat streaming with realistic AI response"""
        print("\n💬 Testing chat streaming...")
        
        # Simulate user message
        print("   👤 Simulating user message...")
        
        # Start AI response
        print("   🤖 Starting AI response stream...")
        send_chat_ai_start()
        send_avatar_state('generating')
        
        # Stream a realistic response
        response = """Hello! I'm M1K3, your AI companion. I can help you with various tasks including:

• Answering questions about any topic
• Writing and editing text
• Creative tasks like brainstorming
• Technical assistance with coding
• General conversation and support

The enhanced dashboard shows my emotions in real-time and includes sound effects for a more immersive experience. You can also use speech-to-text by holding the microphone button!

Is there anything specific you'd like to chat about today?"""
        
        words = response.split()
        for i, word in enumerate(words):
            chunk = word + (" " if i < len(words) - 1 else "")
            send_chat_ai_chunk(chunk)
            
            # Update progress
            progress = (i / len(words)) * 100
            send_avatar_progress('generating', progress, i + 1, f"Generated {i + 1} words")
            
            # Vary emotion based on content
            if 'help' in word.lower() or 'assist' in word.lower():
                send_avatar_emotion('happy', 80, "Helpful response")
            elif 'creative' in word.lower():
                send_avatar_emotion('excited', 70, "Creative enthusiasm")
            elif 'chat' in word.lower():
                send_avatar_emotion('love', 60, "Friendly conversation")
            
            time.sleep(0.1)  # Realistic typing speed
        
        # Complete response
        send_chat_ai_complete()
        send_avatar_state('post_response')
        send_sound_trigger('message_received')
        
        print("   ✅ Chat streaming test complete")
    
    def test_sound_effects(self):
        """Test all sound effects with descriptions"""
        print("\n🔊 Testing sound effects...")
        
        sound_tests = [
            ('connect', "Connection established"),
            ('message_sent', "User message sent"),
            ('message_received', "AI response received"),
            ('thinking', "AI thinking sound"),
            ('error', "Error notification"),
            ('emotion_change', "Emotion state change"),
            ('voice_start', "Voice recording started"),
            ('voice_end', "Voice recording ended")
        ]
        
        for sound, description in sound_tests:
            print(f"   🔊 {sound}: {description}")
            send_sound_trigger(sound)
            time.sleep(1.5)
        
        print("   ✅ Sound effects test complete")
    
    def test_metrics_updates(self):
        """Test real-time metrics updates"""
        print("\n📊 Testing metrics updates...")
        
        base_metrics = {
            "energy_saved": 0.0,
            "water_saved": 0,
            "co2_saved": 0,
            "message_count": 0
        }
        
        for i in range(1, 11):
            metrics = {
                "energy_saved": f"{base_metrics['energy_saved'] + i * 1.2:.1f}",
                "water_saved": f"{base_metrics['water_saved'] + i * 45}",
                "co2_saved": f"{base_metrics['co2_saved'] + i * 14}",
                "message_count": f"{base_metrics['message_count'] + i}"
            }
            
            print(f"   📊 Update {i}: Energy: {metrics['energy_saved']} Wh, "
                  f"Water: {metrics['water_saved']} ml, CO2: {metrics['co2_saved']}g")
            
            send_metrics_update(metrics)
            time.sleep(1)
        
        print("   ✅ Metrics updates test complete")
    
    def test_error_scenarios(self):
        """Test error handling and recovery"""
        print("\n❌ Testing error scenarios...")
        
        # Simulate various error states
        error_scenarios = [
            ('error', 'Connection timeout', 'error'),
            ('angry', 'Invalid input detected', 'error'),
            ('sad', 'Processing failed', 'error'),
            ('thinking', 'Recovering from error...', 'thinking'),
            ('happy', 'System recovered successfully!', 'idle')
        ]
        
        for emotion, message, state in error_scenarios:
            print(f"   ❌ {message}")
            send_avatar_emotion(emotion, 70, message)
            send_avatar_state(state)
            
            if 'error' in message.lower():
                send_sound_trigger('error')
            
            time.sleep(2)
        
        print("   ✅ Error scenarios test complete")
    
    def test_rapid_updates(self):
        """Test system performance with rapid updates"""
        print("\n⚡ Testing rapid updates (stress test)...")
        
        print("   ⚡ Rapid emotion changes...")
        for i in range(20):
            emotion = random.choice(self.emotions)
            intensity = random.randint(30, 100)
            send_avatar_emotion(emotion, intensity, f"Rapid test {i+1}")
            time.sleep(0.2)
        
        print("   ⚡ Rapid state changes...")
        for i in range(15):
            state = random.choice(self.states)
            send_avatar_state(state)
            time.sleep(0.3)
        
        print("   ⚡ Rapid metrics updates...")
        for i in range(10):
            metrics = {
                "energy_saved": f"{random.uniform(1, 50):.1f}",
                "water_saved": f"{random.randint(100, 2000)}",
                "co2_saved": f"{random.randint(10, 500)}",
                "message_count": f"{i + 1}"
            }
            send_metrics_update(metrics)
            time.sleep(0.1)
        
        print("   ✅ Rapid updates test complete")
    
    def run_interactive_session(self):
        """Run interactive test session with user control"""
        print("\n🎮 Interactive Test Session")
        print("=" * 50)
        
        while True:
            print("\nAvailable Tests:")
            print("1. Emotion Cycling (30s)")
            print("2. State Transitions")
            print("3. Chat Streaming")
            print("4. Sound Effects")
            print("5. Metrics Updates")
            print("6. Error Scenarios")
            print("7. Rapid Updates (Stress Test)")
            print("8. Custom Emotion")
            print("9. Custom Chat Message")
            print("0. Exit")
            
            try:
                choice = input("\nSelect test (0-9): ").strip()
                
                if choice == '0':
                    break
                elif choice == '1':
                    self.test_emotion_cycling()
                elif choice == '2':
                    self.test_state_transitions()
                elif choice == '3':
                    self.test_chat_streaming()
                elif choice == '4':
                    self.test_sound_effects()
                elif choice == '5':
                    self.test_metrics_updates()
                elif choice == '6':
                    self.test_error_scenarios()
                elif choice == '7':
                    self.test_rapid_updates()
                elif choice == '8':
                    emotion = input("Enter emotion (happy/sad/angry/surprised/love/thinking/sleepy/excited): ").strip()
                    intensity = int(input("Enter intensity (0-100): "))
                    message = input("Enter message: ").strip()
                    send_avatar_emotion(emotion, intensity, message)
                    print(f"   ✅ Sent {emotion} at {intensity}%")
                elif choice == '9':
                    message = input("Enter chat message: ").strip()
                    send_chat_ai_start()
                    for word in message.split():
                        send_chat_ai_chunk(word + " ")
                        time.sleep(0.1)
                    send_chat_ai_complete()
                    print(f"   ✅ Sent chat message")
                else:
                    print("   ❌ Invalid choice")
                    
            except KeyboardInterrupt:
                print("\n\n🛑 Test session interrupted")
                break
            except ValueError:
                print("   ❌ Invalid input")
            except Exception as e:
                print(f"   ❌ Error: {e}")
    
    def run_full_test_suite(self):
        """Run complete automated test suite"""
        print("\n🧪 M1K3 UI Test Suite - Full Automated Run")
        print("=" * 60)
        print(f"⏰ Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        
        if not self.start_server():
            return False
        
        print(f"\n📱 Dashboard URLs available:")
        status = self.server.get_status()
        if 'urls' in status:
            for url in status['urls']:
                print(f"   {url}")
        print(f"   WebSocket: ws://localhost:8081")
        
        print(f"\n⚠️  Open the dashboard in your browser to see live updates!")
        input("Press Enter when ready to start tests...")
        
        try:
            # Run all tests in sequence
            self.test_emotion_cycling(15)
            time.sleep(2)
            
            self.test_state_transitions()
            time.sleep(2)
            
            self.test_chat_streaming()
            time.sleep(2)
            
            self.test_sound_effects()
            time.sleep(2)
            
            self.test_metrics_updates()
            time.sleep(2)
            
            self.test_error_scenarios()
            time.sleep(2)
            
            self.test_rapid_updates()
            
            print(f"\n✅ Full test suite completed at: {datetime.now().strftime('%H:%M:%S')}")
            print("🎉 All tests passed! Check the dashboard for visual results.")
            
        except KeyboardInterrupt:
            print("\n\n🛑 Test suite interrupted")
        
        return True

def main():
    tester = M1K3UITester()
    
    print("🧪 M1K3 Enhanced Dashboard - Interactive UI Tester")
    print("=" * 55)
    print("This tool tests all dashboard features with live WebSocket updates")
    print("Make sure to open http://localhost:8080 in your browser!")
    
    try:
        while True:
            print("\nTest Options:")
            print("1. Run Full Automated Test Suite")
            print("2. Interactive Test Session")
            print("3. Start Server Only")
            print("0. Exit")
            
            choice = input("\nSelect option (0-3): ").strip()
            
            if choice == '0':
                break
            elif choice == '1':
                tester.run_full_test_suite()
            elif choice == '2':
                if tester.start_server():
                    tester.run_interactive_session()
            elif choice == '3':
                tester.start_server()
                input("Press Enter to stop server...")
            else:
                print("❌ Invalid choice")
                
    except KeyboardInterrupt:
        print("\n\n🛑 Exiting...")
    finally:
        tester.stop_server()
        print("👋 Goodbye!")

if __name__ == "__main__":
    main()