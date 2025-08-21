#!/usr/bin/env python3
"""
Test Enhanced M1K3 Avatar Dashboard
"""

import time
import threading
from avatar_server import start_avatar_server, stop_avatar_server, get_avatar_server_status, send_chat_ai_start, send_chat_ai_chunk, send_chat_ai_complete

def test_enhanced_dashboard():
    print("🧪 Testing Enhanced M1K3 Avatar Dashboard")
    print("=" * 50)
    
    # Test 1: Start server
    print("\n1. Starting avatar server...")
    if start_avatar_server():
        print("   ✅ Server started successfully")
        
        # Get status
        status = get_avatar_server_status()
        if status['running']:
            print(f"   📱 Dashboard available at:")
            for url in status.get('urls', [status['http_url']]):
                print(f"      {url}")
        
        # Test 2: Chat streaming simulation
        print("\n2. Testing chat streaming...")
        
        def simulate_ai_response():
            time.sleep(1)
            print("   Sending AI start...")
            send_chat_ai_start()
            
            time.sleep(0.5)
            message = "Hello! This is a test of the enhanced dashboard with real-time chat streaming."
            print("   Streaming response chunks...")
            
            for word in message.split():
                send_chat_ai_chunk(word + " ")
                time.sleep(0.2)
            
            print("   Sending completion...")
            send_chat_ai_complete()
            print("   ✅ Chat streaming test complete")
        
        # Run simulation in background
        threading.Thread(target=simulate_ai_response, daemon=True).start()
        
        # Test 3: Keep server running for manual testing
        print("\n3. Server running for manual testing...")
        print("   📱 Open the dashboard URLs above to test:")
        print("   - Real-time avatar emotions")
        print("   - Chat interface with speech-to-text")
        print("   - Sound effects system")
        print("   - Streaming AI responses")
        print("   - Eco-metrics display")
        print("\n   Press Ctrl+C to stop...")
        
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n\n4. Stopping server...")
            stop_avatar_server()
            print("   ✅ Server stopped successfully")
        
    else:
        print("   ❌ Failed to start server")
        return False
    
    print("\n✅ Enhanced dashboard test complete!")
    return True

if __name__ == "__main__":
    test_enhanced_dashboard()