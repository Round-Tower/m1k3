#!/usr/bin/env python3
"""
🔊 VibeVoice Demo Audio Player
Quick script to play all generated VibeVoice demo audio files
"""

import subprocess
import platform
from pathlib import Path
import time

def play_audio_file(audio_path: Path):
    """Play an audio file using system default player"""
    if not audio_path.exists():
        print(f"⚠️  Audio file not found: {audio_path}")
        return False
        
    try:
        print(f"🔊 Playing {audio_path.name}... (press Ctrl+C to skip)")
        
        # Cross-platform audio playback
        system = platform.system()
        
        if system == "Darwin":  # macOS
            subprocess.run(["afplay", str(audio_path)], check=True)
        elif system == "Windows":
            # Try different Windows audio players
            try:
                subprocess.run(["powershell", "-c", f"(New-Object Media.SoundPlayer '{audio_path}').PlaySync()"], check=True)
            except:
                # Fallback to start command
                subprocess.run(["start", str(audio_path)], shell=True, check=True)
        else:  # Linux
            # Try common Linux audio players
            players = ["paplay", "aplay", "play", "cvlc", "mpg123"]
            for player in players:
                try:
                    subprocess.run([player, str(audio_path)], check=True, 
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    break
                except (subprocess.CalledProcessError, FileNotFoundError):
                    continue
            else:
                print(f"⚠️  No audio player found for: {audio_path}")
                return False
        
        print(f"✅ Finished playing {audio_path.name}")
        return True
        
    except KeyboardInterrupt:
        print(f"\n⏸️  Skipped {audio_path.name}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"⚠️  Could not play audio: {e}")
        return False
    except Exception as e:
        print(f"⚠️  Audio playback error: {e}")
        return False

def main():
    """Main audio player function"""
    print("🔊 VibeVoice Demo Audio Player")
    print("=" * 40)
    
    demo_dir = Path("vibevoice_demos")
    
    if not demo_dir.exists():
        print("❌ No demo directory found. Run demos first:")
        print("   python vibevoice_showcase.py")
        return
    
    # Find all audio files
    audio_files = list(demo_dir.glob("*.wav"))
    
    if not audio_files:
        print("❌ No audio files found in demo directory")
        print(f"   Directory: {demo_dir}")
        return
    
    print(f"🎵 Found {len(audio_files)} audio files")
    print("─" * 40)
    
    # Sort files for logical playback order
    audio_files.sort()
    
    for i, audio_file in enumerate(audio_files, 1):
        print(f"\n🎵 Track {i}/{len(audio_files)}: {audio_file.name}")
        
        # Get file info
        size = audio_file.stat().st_size
        print(f"📊 File size: {size:,} bytes")
        
        # Play the file
        success = play_audio_file(audio_file)
        
        if not success:
            print(f"⚠️  Playback failed for {audio_file.name}")
        
        # Pause between files (except for the last one)
        if i < len(audio_files):
            try:
                time.sleep(1)  # Brief pause between tracks
            except KeyboardInterrupt:
                print("\n🛑 Playback interrupted by user")
                break
    
    print(f"\n🎉 Audio playback complete!")
    print(f"📁 Demo directory: {demo_dir}")

if __name__ == "__main__":
    main()