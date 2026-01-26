#!/usr/bin/env python3
"""Test that --voice-profile override works"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

# Parse CLI args to verify profile
sys.argv = ["test", "--voice-profile", "realtime", "--no-voice"]

from cli import parse_args

args = parse_args()

print("✅ CLI Argument Parsing Test")
print(f"   Voice profile: {args.voice_profile}")
print(f"   Expected: realtime")

if args.voice_profile == "realtime":
    print("\n✅ Manual override works!")
    print("   Users can still use --voice-profile realtime to override Kokoro default")
else:
    print(f"\n❌ Override failed! Got: {args.voice_profile}")
    sys.exit(1)

# Test default (no args)
sys.argv = ["test", "--no-voice"]
args = parse_args()

print(f"\n✅ Default Voice Profile Test")
print(f"   Voice profile: {args.voice_profile}")
print(f"   Expected: kokoro")

if args.voice_profile == "kokoro":
    print("\n✅ Default is now Kokoro!")
    print("   M1K3 will use Daniel (British Male, Radio Chat) by default")
else:
    print(f"\n❌ Default wrong! Got: {args.voice_profile}")
    sys.exit(1)
