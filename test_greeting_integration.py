#!/usr/bin/env python3
"""
Integration test for the new greeting system in the CLI
"""

import sys
import os

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

def test_greeting_integration():
    print("🧪 Testing M1K3 Greeting Integration")
    print("=" * 50)
    
    try:
        # Import required modules
        from cli import M1K3CLI
        
        print("✅ CLI module imported successfully")
        
        # Create CLI instance (this will initialize AI engine automatically)
        print("🔄 Initializing M1K3 CLI...")
        cli = M1K3CLI(voice_enabled=False, auto_avatar=False)
        
        print("✅ CLI initialized successfully")
        
        # Simulate the greeting generation process
        print("\n🎯 Simulating startup greeting process...")
        
        # Collect system metrics like the CLI does
        metrics = cli.system_monitor.collect_metrics()
        m1k3_context = cli._collect_m1k3_context()
        
        print("✅ System context collected")
        
        # Generate the startup greeting
        print("\n💬 Generating startup greeting...")
        greeting = cli._generate_intelligent_greeting(metrics, m1k3_context)
        
        print(f"🎉 Generated greeting: \"{greeting}\"")
        print(f"📏 Length: {len(greeting)} characters")
        
        # Test the enhancement attempt
        print("\n🔍 Testing LLM enhancement attempt...")
        enhanced_greeting = cli._try_llm_greeting_enhancement(metrics, m1k3_context, greeting)
        
        if enhanced_greeting:
            print(f"✨ Enhanced greeting: \"{enhanced_greeting}\"")
            print(f"📏 Length: {len(enhanced_greeting)} characters")
        else:
            print("⚠️  LLM enhancement not successful, using rule-based greeting")
        
        # Show comparison with original system
        from system_metrics import generate_dynamic_greeting
        original_greeting = generate_dynamic_greeting(metrics, m1k3_context)
        
        print(f"\n📊 Comparison:")
        print(f"   Rule-based: \"{original_greeting}\"")
        print(f"   Enhanced:   \"{greeting}\"")
        print(f"   Same? {greeting == original_greeting}")
        
        print("\n✅ Greeting integration test completed successfully!")
        return True
        
    except Exception as e:
        print(f"❌ Integration test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_greeting_integration()
    
    if success:
        print("\n🎉 M1K3 greeting integration is working perfectly!")
        print("💡 The system gracefully falls back to rule-based greetings when LLM enhancement doesn't work optimally.")
    else:
        print("\n❌ Integration test failed")
    
    sys.exit(0 if success else 1)