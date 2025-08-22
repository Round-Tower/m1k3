#!/usr/bin/env python3
"""
Test CLI-Avatar Integration to Reproduce Error
"""

import sys
import time
from contextlib import redirect_stderr
import io

# Import M1K3 components
try:
    from cli import M1K3CLI
    from avatar_controller import AvatarController
    from avatar_server import send_avatar_emotion, send_classification_update, is_avatar_server_running, start_avatar_server, stop_avatar_server
    print("✅ All imports successful")
except Exception as e:
    print(f"❌ Import error: {e}")
    sys.exit(1)

def test_avatar_integration():
    """Test the CLI-Avatar integration that's causing errors"""
    
    print("🚀 Testing CLI-Avatar Integration")
    print("=" * 60)
    
    # Start avatar server
    print("\n1️⃣ Starting avatar server...")
    try:
        success = start_avatar_server()
        if success:
            print("✅ Avatar server started successfully")
        else:
            print("❌ Failed to start avatar server")
            return
    except Exception as e:
        print(f"❌ Error starting avatar server: {e}")
        return
    
    # Wait for server to be ready
    time.sleep(2)
    
    if not is_avatar_server_running():
        print("❌ Avatar server not responding")
        return
    
    # Test CLI initialization
    print("\n2️⃣ Initializing CLI...")
    try:
        cli = M1K3CLI(
            voice_enabled=False,  # Disable voice for testing
            auto_avatar=False,  # Already started
            avatar_port=8080,
            open_browser=False
        )
        print("✅ CLI initialized successfully")
    except Exception as e:
        print(f"❌ CLI initialization error: {e}")
        return
    
    # Test avatar controller creation
    print("\n3️⃣ Testing avatar controller...")
    try:
        if cli.avatar_controller:
            print("✅ Avatar controller available")
        else:
            print("⚠️ Avatar controller not available, creating one...")
            cli.avatar_controller = AvatarController()
            print("✅ Avatar controller created")
    except Exception as e:
        print(f"❌ Avatar controller error: {e}")
        return
    
    # Test the problematic send_avatar_update method
    print("\n4️⃣ Testing send_avatar_update with classification data...")
    
    # Create test classification metadata in the format that comes from enhanced_adaptive_model_config
    test_classification_data = {
        'intent': 'mathematical_calculation',
        'confidence': 1.0,
        'response_strategy': 'deterministic',
        'context_factors': {'query_complexity': 'low'},
        'reasoning': 'Intent: mathematical_calculation (1.000) -> Strategy: deterministic',
        'classification_engine': 'context_aware',
        'system_version': 'enhanced_v2.0',
        'generation_timestamp': '2025-08-22T15:50:00'
    }
    
    try:
        print(f"   Classification data type: {type(test_classification_data)}")
        print(f"   Classification data keys: {list(test_classification_data.keys())}")
        
        # This is the call that should be causing the error
        cli.send_avatar_update("What is 25 * 17?", "generating", test_classification_data)
        
        print("✅ send_avatar_update completed successfully")
        
    except Exception as e:
        print(f"❌ send_avatar_update error: {e}")
        import traceback
        print("Full traceback:")
        traceback.print_exc()
    
    # Test with simulated AI engine response
    print("\n5️⃣ Testing with simulated AI engine...")
    try:
        # Simulate what happens in handle_user_input
        user_input = "What is 25 * 17?"
        
        # Get adaptive generation params (this might be where the error occurs)
        if hasattr(cli.ai_engine, '_get_adaptive_generation_params'):
            print("   AI engine has _get_adaptive_generation_params method")
            params = cli.ai_engine._get_adaptive_generation_params(100, user_input)
            print(f"   Params type: {type(params)}")
            print(f"   Params repr: {repr(params)[:200]}...")
            
            if isinstance(params, dict) and '_metadata' in params:
                classification_metadata = params['_metadata']
                print(f"   Metadata type: {type(classification_metadata)}")
                print(f"   Metadata repr: {repr(classification_metadata)[:200]}...")
                
                # This is where the error likely occurs
                cli.send_avatar_update(user_input, "generating", classification_metadata)
                print("✅ Simulated AI engine test successful")
            else:
                print("⚠️ No _metadata in params or params not dict")
        else:
            print("⚠️ AI engine missing _get_adaptive_generation_params method")
            
    except Exception as e:
        print(f"❌ Simulated AI engine test error: {e}")
        import traceback
        print("Full traceback:")
        traceback.print_exc()
    
    # Clean up
    print("\n6️⃣ Cleaning up...")
    try:
        stop_avatar_server()
        print("✅ Avatar server stopped")
    except Exception as e:
        print(f"⚠️ Cleanup warning: {e}")

if __name__ == "__main__":
    test_avatar_integration()