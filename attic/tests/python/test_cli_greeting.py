#!/usr/bin/env python3
"""
Test CLI greeting generation
"""

import sys
import os

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

def test_cli_greeting():
    print("🧪 Testing CLI Greeting Generation")
    print("=" * 50)
    
    try:
        from cli import M1K3CLI
        
        # Create CLI instance with minimal setup
        cli = M1K3CLI(voice_enabled=False, auto_avatar=False)
        
        # Check if AI engine is available
        print("🔄 Checking AI engine...")
        if not hasattr(cli, 'ai_engine') or not cli.ai_engine:
            print("❌ AI engine not available")
            return False
        
        print("✅ AI engine available")
        
        # Test greeting generation
        print("\n🎯 Testing greeting generation...")
        
        # Collect system metrics
        metrics = cli.system_monitor.collect_metrics()
        m1k3_context = cli._collect_m1k3_context()
        
        print("✅ System metrics collected")
        
        # Generate multiple greetings to show variety
        print("\n💬 Generated Greetings:")
        print("-" * 30)
        
        for i in range(5):
            greeting = cli._generate_intelligent_greeting(metrics, m1k3_context)
            print(f"{i+1}. {greeting}")
            print(f"   Length: {len(greeting)} characters")
        
        print("\n✅ CLI greeting system working!")
        return True
        
    except Exception as e:
        print(f"❌ Error testing CLI greeting: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_cli_greeting()
    
    if success:
        print("\n🎉 CLI greeting system is ready!")
    else:
        print("\n⚠️  CLI greeting needs debugging")
    
    sys.exit(0 if success else 1)