#!/usr/bin/env python3
"""
Quick test to verify the voice profile system works.
"""
import sys
from pathlib import Path

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from src.tts.profiles import profile_registry


def test_profile_loading():
    """Test that profiles load from JSON."""
    print("Testing profile loading...")

    # Test profile registry
    profiles = profile_registry.list_all()
    print(f"\n✅ Loaded {len(profiles)} profiles from JSON")

    # Show some profiles
    print("\nSample profiles:")
    for i, (profile_id, description) in enumerate(list(profiles.items())[:5]):
        print(f"  {i+1}. {profile_id}: {description}")

    return len(profiles) > 0


def test_profile_discovery():
    """Test profile discovery API."""
    print("\n\nTesting profile discovery...")

    # Test search
    results = profile_registry.search_profiles("kokoro")
    print(f"\n✅ Search 'kokoro' found {len(results)} profiles:")
    for profile_id, desc in results.items():
        print(f"  - {profile_id}: {desc}")

    # Test list by engine
    kokoro_profiles = profile_registry.list_by_engine("kokoro")
    print(f"\n✅ Kokoro engine has {len(kokoro_profiles)} profiles:")
    for profile_id in kokoro_profiles:
        print(f"  - {profile_id}")

    return len(results) > 0


def test_profile_validation():
    """Test profile validation."""
    print("\n\nTesting profile validation...")

    from src.tts.profiles import VoiceProfile

    # Get a profile and validate
    profile = profile_registry.get_profile("kokoro")
    if profile:
        is_valid = profile.validate()
        print(f"\n✅ Kokoro profile validation: {is_valid}")
        print(f"   Engine: {profile.preferred_engine}")
        print(f"   Effects: {profile.effects}")
        print(f"   Optimization: {profile.optimization}")
        return is_valid
    else:
        print("❌ Kokoro profile not found")
        return False


def test_profile_conversion():
    """Test profile dict conversion."""
    print("\n\nTesting profile conversion...")

    # Get profiles as dict (for backward compatibility)
    profiles_dict = profile_registry.get_profiles_dict()
    print(f"\n✅ Converted {len(profiles_dict)} profiles to dict format")

    # Verify key profiles exist
    key_profiles = ["kokoro", "realtime", "mobile", "chat"]
    missing = [p for p in key_profiles if p not in profiles_dict]

    if missing:
        print(f"❌ Missing profiles: {missing}")
        return False
    else:
        print(f"✅ All key profiles present: {key_profiles}")
        # Show sample profile structure
        sample = profiles_dict["kokoro"]
        print(f"\n   Sample profile 'kokoro':")
        print(f"   - description: {sample.get('description', 'N/A')[:60]}...")
        print(f"   - preferred_engine: {sample.get('preferred_engine', 'N/A')}")
        print(f"   - effects: {sample.get('effects', [])}")
        return True


if __name__ == "__main__":
    print("=" * 60)
    print("Voice Profile System Test")
    print("=" * 60)

    tests = [
        ("Profile Loading", test_profile_loading),
        ("Profile Discovery", test_profile_discovery),
        ("Profile Validation", test_profile_validation),
        ("Profile Conversion", test_profile_conversion),
    ]

    results = []
    for test_name, test_func in tests:
        try:
            result = test_func()
            results.append((test_name, result, None))
        except Exception as e:
            results.append((test_name, False, str(e)))

    # Summary
    print("\n" + "=" * 60)
    print("TEST SUMMARY")
    print("=" * 60)

    passed = 0
    for test_name, result, error in results:
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"{status}: {test_name}")
        if error:
            print(f"        Error: {error}")
        if result:
            passed += 1

    print(f"\nTotal: {passed}/{len(tests)} tests passed")

    sys.exit(0 if passed == len(tests) else 1)
