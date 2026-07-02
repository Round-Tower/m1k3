#!/usr/bin/env python3
"""
🎭 VibeVoice Showcase Demo for M1K3
Comprehensive demonstration of Microsoft VibeVoice integration capabilities
Features: Multi-speaker conversations, long-form synthesis, voice profiles, and streaming demos
"""

import sys
import time
import os
import json
from pathlib import Path
from typing import List, Dict, Any
import random
import subprocess
import platform

# Add src to path for imports
sys.path.insert(0, str(Path(__file__).parent / "src"))

from src.tts.controllers.vibevoice_manager import VibeVoiceManager
from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
from src.engines.tts.streaming_tts_engine import StreamingTTSEngine

class VibeVoiceShowcase:
    """Comprehensive VibeVoice demonstration system"""
    
    def __init__(self):
        self.vibevoice = VibeVoiceManager()
        self.voice_engine = UnifiedVoiceEngine()
        self.streaming_engine = None
        self.demo_output_dir = Path("vibevoice_demos")
        self.demo_output_dir.mkdir(exist_ok=True)
        
        # Audio playback settings
        self.auto_play_audio = True  # Set to True to automatically play generated audio
        
        # Demo content library
        self.demo_content = {
            "greetings": [
                "Hello! Welcome to the VibeVoice demonstration.",
                "Good day! I'm excited to show you what VibeVoice can do.",
                "Hi there! Ready to explore frontier text-to-speech technology?"
            ],
            "conversations": {
                "alice_bob_intro": {
                    "Alice": "Hi Bob! Have you heard about this amazing new VibeVoice technology?",
                    "Bob": "Alice! Yes, I have! It can generate up to 90 minutes of continuous speech with multiple speakers like us!",
                    "Alice": "That's incredible! And it uses Microsoft's frontier VALL-E architecture with diffusion models.",
                    "Bob": "Exactly! Plus it's completely local - no cloud dependencies at all. Perfect for privacy-focused applications."
                },
                "technical_discussion": {
                    "Alice": "The compression ratio is remarkable - 3200x at only 7.5 tokens per second!",
                    "Bob": "And the model sizes are reasonable too. The 1.5B model needs about 4GB RAM.",
                    "Alice": "While the 7B model is more powerful but requires 16GB. Great scalability options!",
                    "Bob": "Plus it integrates seamlessly with existing M1K3 architecture and voice profiles."
                }
            },
            "narratives": {
                "sci_fi_story": """In the year 2045, artificial intelligence had evolved beyond mere computation into something truly remarkable. 
                The breakthrough came not from raw processing power, but from understanding the nuances of human communication.
                
                VibeVoice represented this evolution - a system that could speak not just words, but emotions, context, and meaning.
                Unlike the robotic voices of the past, it could sustain conversations for hours, switching between speakers naturally.
                
                The technology used advanced diffusion models, much like those used for image generation, but applied to audio synthesis.
                Each word was crafted not in isolation, but as part of a flowing narrative that could span entire audiobooks.
                
                What made it truly special was its efficiency - generating speech 3200 times faster than real-time, while maintaining
                unprecedented quality. This wasn't just text-to-speech; it was the beginning of truly natural human-AI interaction.""",
                
                "technical_overview": """VibeVoice represents a paradigm shift in text-to-speech synthesis. Built on Microsoft's VALL-E 
                architecture, it treats speech generation as a language modeling task rather than traditional signal processing.
                
                The system generates discrete audio codec tokens using a transformer-based approach, then applies diffusion models
                to create natural-sounding speech. This allows for capabilities impossible with traditional TTS systems.
                
                Key innovations include support for 90-minute continuous generation, multi-speaker conversations with up to four
                simultaneous voices, and ultra-efficient processing at 7.5 tokens per second with 3200x compression.
                
                The integration with M1K3 maintains the project's privacy-first philosophy - all processing happens locally
                with no cloud dependencies, while providing seamless fallbacks to existing TTS engines."""
            }
        }
        
    def display_header(self):
        """Display fancy header"""
        header = """
╭─────────────────────────────────────────────────────────────────╮
│                                                                 │
│  🎭 VibeVoice Showcase for M1K3                                │
│  Microsoft's Frontier Text-to-Speech Integration               │
│                                                                 │
│  ✨ 90-minute continuous synthesis                             │
│  🗣️  Multi-speaker conversations (up to 4 speakers)            │
│  🎯 3200x compression at 7.5 tokens/second                     │
│  🔒 100% local processing - zero cloud dependencies            │
│  🤖 Seamless M1K3 integration with voice profiles             │
│                                                                 │
╰─────────────────────────────────────────────────────────────────╯
        """
        print(header)
    
    def play_audio_file(self, audio_path: Path, description: str = "audio"):
        """Play an audio file using system default player"""
        if not self.auto_play_audio:
            return
            
        if not audio_path.exists():
            print(f"⚠️  Audio file not found: {audio_path}")
            return
            
        try:
            print(f"🔊 Playing {description}... (press Ctrl+C to skip)")
            
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
                    print(f"⚠️  No audio player found. File saved to: {audio_path}")
                    return
            
            print(f"✅ Finished playing {description}")
            
        except KeyboardInterrupt:
            print(f"\n⏸️  Skipped {description}")
        except subprocess.CalledProcessError as e:
            print(f"⚠️  Could not play audio: {e}")
            print(f"📁 Audio file saved to: {audio_path}")
        except Exception as e:
            print(f"⚠️  Audio playback error: {e}")
            print(f"📁 Audio file saved to: {audio_path}")
    
    def toggle_audio_playback(self):
        """Toggle automatic audio playback"""
        self.auto_play_audio = not self.auto_play_audio
        status = "enabled" if self.auto_play_audio else "disabled"
        print(f"🔊 Audio playback {status}")
    
    def check_system_status(self):
        """Check and display system capabilities"""
        print("\n🔍 System Status Check")
        print("=" * 50)
        
        # Check VibeVoice availability
        info = self.vibevoice.get_availability_info()
        
        for key, value in info.items():
            if isinstance(value, bool):
                status = "✅" if value else "❌"
                print(f"{status} {key.replace('_', ' ').title()}: {value}")
            else:
                if key == "diffusers_error":
                    print(f"⚠️  Diffusers: Version compatibility issue (see note below)")
                elif key == "diffusers_note":
                    print(f"💡 Note: {value}")
                else:
                    print(f"📋 {key.replace('_', ' ').title()}: {value}")
        
        # System recommendations
        print("\n💡 Performance Notes:")
        if info.get("cuda_available"):
            print("✅ CUDA GPU detected - optimal performance available")
        else:
            print("⚠️  CPU mode - slower generation but fully functional")
        
        return info.get("available", False)
    
    def demo_basic_synthesis(self):
        """Demo 1: Basic synthesis capabilities"""
        print("\n" + "="*60)
        print("🎤 DEMO 1: Basic VibeVoice Synthesis")
        print("="*60)
        
        if not self.vibevoice.load_model():
            print("❌ Could not load VibeVoice model")
            return False
            
        test_phrases = [
            "Welcome to VibeVoice - Microsoft's frontier text-to-speech technology!",
            "This system can generate up to 90 minutes of continuous, natural-sounding speech.",
            "Let's explore what makes this technology so revolutionary."
        ]
        
        for i, phrase in enumerate(test_phrases, 1):
            print(f"\n🗣️  Generating sample {i}: '{phrase[:50]}...'")
            audio = self.vibevoice.generate(phrase)
            
            if audio is not None:
                filename = self.demo_output_dir / f"basic_sample_{i}.wav"
                if self.vibevoice.save_audio(audio, str(filename)):
                    duration = len(audio) / self.vibevoice.sample_rate
                    print(f"✅ Generated {duration:.2f}s audio → {filename}")
                    
                    # Play the audio!
                    self.play_audio_file(filename, f"Sample {i}")
                    
                else:
                    print(f"⚠️  Could not save audio file")
            else:
                print(f"❌ Generation failed for sample {i}")
        
        return True
    
    def demo_multi_speaker(self):
        """Demo 2: Multi-speaker conversations"""
        print("\n" + "="*60)
        print("🗣️  DEMO 2: Multi-Speaker Conversations")  
        print("="*60)
        
        # Set up multi-speaker conversation
        conversation = self.demo_content["conversations"]["alice_bob_intro"]
        speakers = ["Alice", "Bob"]
        
        print(f"👥 Setting up conversation with speakers: {speakers}")
        self.vibevoice.set_speakers(speakers)
        
        conversation_audio = []
        conversation_text = []
        
        for speaker, text in conversation.items():
            print(f"\n🎭 {speaker}: '{text[:60]}...'")
            
            # Generate audio for this speaker's line
            audio = self.vibevoice.generate(text, speakers=[speaker])
            
            if audio is not None:
                conversation_audio.append(audio)
                conversation_text.append(f"{speaker}: {text}")
                
                # Save individual line
                filename = self.demo_output_dir / f"conversation_{speaker.lower()}.wav"
                self.vibevoice.save_audio(audio, str(filename))
                
                duration = len(audio) / self.vibevoice.sample_rate
                print(f"✅ Generated {duration:.2f}s → {filename}")
                
                # Play the audio!
                self.play_audio_file(filename, f"{speaker}'s line")
            else:
                print(f"❌ Failed to generate audio for {speaker}")
        
        # Create conversation summary
        full_conversation = "\n".join(conversation_text)
        summary_file = self.demo_output_dir / "conversation_script.txt"
        summary_file.write_text(full_conversation)
        print(f"\n📝 Conversation script saved → {summary_file}")
        
        return len(conversation_audio) > 0
    
    def demo_long_form_narrative(self):
        """Demo 3: Long-form narrative synthesis"""
        print("\n" + "="*60)
        print("📚 DEMO 3: Long-Form Narrative Synthesis")
        print("="*60)
        
        narrative = self.demo_content["narratives"]["sci_fi_story"]
        
        print(f"📖 Generating long-form narrative ({len(narrative)} characters)")
        print("🎬 This demonstrates VibeVoice's ability to maintain coherence across extended content...")
        
        # Generate long-form content
        audio_chunks = self.vibevoice.generate_long_form(narrative, speakers=["Alice"])
        
        if audio_chunks:
            print(f"✅ Generated {len(audio_chunks)} audio chunks")
            
            total_duration = 0
            for i, chunk in enumerate(audio_chunks):
                duration = len(chunk) / self.vibevoice.sample_rate
                total_duration += duration
                
                filename = self.demo_output_dir / f"narrative_chunk_{i+1}.wav"
                self.vibevoice.save_audio(chunk, str(filename))
                print(f"📄 Chunk {i+1}: {duration:.1f}s → {filename}")
            
            print(f"\n📊 Total narrative duration: {total_duration:.1f} seconds")
            
            # Save narrative text
            text_file = self.demo_output_dir / "narrative_text.txt"
            text_file.write_text(narrative)
            print(f"📝 Narrative text saved → {text_file}")
            
            return True
        else:
            print("❌ Long-form generation failed")
            return False
    
    def demo_voice_profiles(self):
        """Demo 4: Voice profile comparison"""
        print("\n" + "="*60)
        print("🎭 DEMO 4: Voice Profile Showcase")
        print("="*60)
        
        # Set up voice engine with different profiles
        self.voice_engine.set_engine_preference("vibevoice")
        self.voice_engine.load_model()
        
        test_text = "This is a demonstration of different voice profiles available in M1K3's VibeVoice integration."
        
        vibevoice_profiles = ["conversational", "narrative", "assistant_duo"]
        
        print("🎨 Testing VibeVoice-specific profiles:")
        
        for profile in vibevoice_profiles:
            print(f"\n🔄 Testing profile: {profile}")
            
            if self.voice_engine.set_profile(profile):
                profile_info = self.voice_engine.get_current_profile()
                print(f"📋 Profile: {profile_info['description']}")
                print(f"🎤 Speakers: {profile_info.get('speakers', ['Default'])}")
                print(f"🎚️  Effects: {profile_info.get('effects', [])}")
                
                # For demo purposes, we'll use the VibeVoice manager directly
                speakers = profile_info.get('speakers', ['Alice'])
                audio = self.vibevoice.generate(test_text, speakers=speakers)
                
                if audio is not None:
                    filename = self.demo_output_dir / f"profile_{profile}.wav"
                    self.vibevoice.save_audio(audio, str(filename))
                    duration = len(audio) / self.vibevoice.sample_rate
                    print(f"✅ Generated {duration:.2f}s → {filename}")
                else:
                    print(f"❌ Generation failed for {profile}")
            else:
                print(f"❌ Could not set profile: {profile}")
        
        return True
    
    def demo_streaming_engine(self):
        """Demo 5: Streaming TTS engine capabilities"""
        print("\n" + "="*60)
        print("🚀 DEMO 5: Streaming TTS Engine with VibeVoice")
        print("="*60)
        
        # Create streaming engine
        self.streaming_engine = StreamingTTSEngine(voice_engine=self.voice_engine)
        
        # Enable VibeVoice mode
        self.streaming_engine.enable_vibevoice_mode(continuous=True)
        
        status = self.streaming_engine.get_vibevoice_status()
        print("📊 Streaming Engine Status:")
        for key, value in status.items():
            print(f"  {key}: {value}")
        
        # Demonstrate long-form processing
        long_content = self.demo_content["narratives"]["technical_overview"]
        
        print(f"\n🎬 Processing long-form content ({len(long_content)} characters)")
        print("📡 This demonstrates streaming synthesis with VibeVoice continuous mode...")
        
        # Set up callback to track progress
        chunks_processed = []
        
        def on_chunk_ready(chunk):
            chunks_processed.append(chunk)
            print(f"📦 Chunk {chunk.chunk_id}: '{chunk.text}' ({'Complete' if chunk.is_complete else 'Partial'})")
        
        self.streaming_engine.on_chunk_ready = on_chunk_ready
        
        # Process the content
        success = self.streaming_engine.process_long_form_content(long_content, speakers=["Alice"])
        
        if success:
            print(f"✅ Processed {len(chunks_processed)} chunks successfully")
            
            # Save chunk information
            chunk_info = {
                "total_chunks": len(chunks_processed),
                "content_length": len(long_content),
                "processing_mode": "continuous_vibevoice",
                "chunks": [
                    {
                        "id": chunk.chunk_id,
                        "text_preview": chunk.text[:100] + "..." if len(chunk.text) > 100 else chunk.text,
                        "is_complete": chunk.is_complete,
                        "timestamp": chunk.timestamp
                    } for chunk in chunks_processed
                ]
            }
            
            info_file = self.demo_output_dir / "streaming_info.json"
            info_file.write_text(json.dumps(chunk_info, indent=2))
            print(f"📊 Streaming info saved → {info_file}")
            
        else:
            print("❌ Streaming processing failed")
            
        return success
    
    def demo_technical_capabilities(self):
        """Demo 6: Technical capabilities and performance metrics"""
        print("\n" + "="*60)
        print("⚡ DEMO 6: Technical Capabilities & Performance")
        print("="*60)
        
        # Model information
        model_info = self.vibevoice.get_model_info()
        print("🤖 Model Information:")
        for key, value in model_info.items():
            print(f"  {key}: {value}")
        
        # Performance test
        print("\n🏁 Performance Testing:")
        test_texts = [
            "Short test phrase for timing.",
            "This is a medium-length sentence to test synthesis speed and quality with VibeVoice.",
            "This is a longer paragraph that will help us measure the performance characteristics of VibeVoice when processing more substantial amounts of text, including multiple sentences and complex linguistic structures."
        ]
        
        results = []
        
        for i, text in enumerate(test_texts, 1):
            print(f"\n⏱️  Test {i} ({len(text)} chars): '{text[:50]}...'")
            
            start_time = time.time()
            audio = self.vibevoice.generate(text)
            generation_time = time.time() - start_time
            
            if audio is not None:
                audio_duration = len(audio) / self.vibevoice.sample_rate
                realtime_factor = audio_duration / generation_time if generation_time > 0 else 0
                
                result = {
                    "test": i,
                    "text_length": len(text),
                    "generation_time": generation_time,
                    "audio_duration": audio_duration,
                    "realtime_factor": realtime_factor,
                    "chars_per_second": len(text) / generation_time if generation_time > 0 else 0
                }
                
                results.append(result)
                
                print(f"  ✅ Generated: {audio_duration:.2f}s audio in {generation_time:.3f}s")
                print(f"  📊 Real-time factor: {realtime_factor:.1f}x")
                print(f"  🚀 Processing speed: {result['chars_per_second']:.0f} chars/second")
                
                # Save audio
                filename = self.demo_output_dir / f"performance_test_{i}.wav"
                self.vibevoice.save_audio(audio, str(filename))
                
            else:
                print(f"  ❌ Generation failed")
        
        # Save performance results
        if results:
            perf_file = self.demo_output_dir / "performance_results.json"
            perf_file.write_text(json.dumps(results, indent=2))
            print(f"\n📈 Performance results saved → {perf_file}")
            
            avg_rtf = sum(r['realtime_factor'] for r in results) / len(results)
            avg_cps = sum(r['chars_per_second'] for r in results) / len(results)
            
            print(f"\n📊 Performance Summary:")
            print(f"  Average real-time factor: {avg_rtf:.1f}x")
            print(f"  Average processing speed: {avg_cps:.0f} chars/second")
        
        return len(results) > 0
    
    def interactive_demo_menu(self):
        """Interactive demo menu"""
        while True:
            print("\n" + "="*60)
            print("🎭 VibeVoice Interactive Demo Menu")
            print("="*60)
            print("1. 🎤 Basic Synthesis Demo")
            print("2. 👥 Multi-Speaker Conversations") 
            print("3. 📚 Long-Form Narratives")
            print("4. 🎨 Voice Profile Showcase")
            print("5. 🚀 Streaming Engine Demo")
            print("6. ⚡ Technical Performance Test")
            print("7. 🎯 Run All Demos (Full Showcase)")
            print("8. 📁 Open Demo Output Folder")
            print("9. 🧹 Clean Demo Files")
            print("A. 🔊 Toggle Audio Playback (Currently: " + ("ON" if self.auto_play_audio else "OFF") + ")")
            print("0. 🚪 Exit")
            
            choice = input("\n🎯 Select demo (0-9, A): ").strip().upper()
            
            if choice == "0":
                print("\n👋 Thanks for exploring VibeVoice! Goodbye!")
                break
            elif choice == "1":
                self.demo_basic_synthesis()
            elif choice == "2":
                self.demo_multi_speaker()
            elif choice == "3":
                self.demo_long_form_narrative()
            elif choice == "4":
                self.demo_voice_profiles()
            elif choice == "5":
                self.demo_streaming_engine()
            elif choice == "6":
                self.demo_technical_capabilities()
            elif choice == "7":
                self.run_full_showcase()
            elif choice == "8":
                self.open_output_folder()
            elif choice == "9":
                self.clean_demo_files()
            elif choice == "A":
                self.toggle_audio_playback()
            else:
                print("❌ Invalid choice. Please select 0-9 or A.")
                
            input("\n⏸️  Press Enter to continue...")
    
    def run_full_showcase(self):
        """Run all demos in sequence"""
        print("\n" + "🌟"*20)
        print("🚀 RUNNING FULL VIBEVOICE SHOWCASE")
        print("🌟"*20)
        
        demos = [
            ("Basic Synthesis", self.demo_basic_synthesis),
            ("Multi-Speaker Conversations", self.demo_multi_speaker), 
            ("Long-Form Narratives", self.demo_long_form_narrative),
            ("Voice Profile Showcase", self.demo_voice_profiles),
            ("Streaming Engine", self.demo_streaming_engine),
            ("Technical Performance", self.demo_technical_capabilities)
        ]
        
        results = {}
        
        for name, demo_func in demos:
            print(f"\n🎬 Starting: {name}")
            try:
                success = demo_func()
                results[name] = "✅ Success" if success else "❌ Failed"
                print(f"✅ Completed: {name}")
            except Exception as e:
                results[name] = f"❌ Error: {str(e)}"
                print(f"❌ Error in {name}: {e}")
                
            time.sleep(1)  # Brief pause between demos
        
        # Show final results
        print("\n" + "🎉"*20)
        print("📊 SHOWCASE RESULTS SUMMARY")
        print("🎉"*20)
        
        for demo, result in results.items():
            print(f"{result} {demo}")
        
        success_count = sum(1 for r in results.values() if "✅" in r)
        total_count = len(results)
        
        print(f"\n🏆 Success Rate: {success_count}/{total_count} demos completed successfully")
        print(f"📁 All demo files saved to: {self.demo_output_dir}")
        
        return success_count == total_count
    
    def open_output_folder(self):
        """Open the demo output folder"""
        import subprocess
        import platform
        
        try:
            if platform.system() == "Darwin":  # macOS
                subprocess.run(["open", str(self.demo_output_dir)])
            elif platform.system() == "Windows":
                subprocess.run(["explorer", str(self.demo_output_dir)])
            else:  # Linux
                subprocess.run(["xdg-open", str(self.demo_output_dir)])
            
            print(f"📁 Opened demo output folder: {self.demo_output_dir}")
        except Exception as e:
            print(f"❌ Could not open folder: {e}")
            print(f"📁 Demo files are located at: {self.demo_output_dir}")
    
    def clean_demo_files(self):
        """Clean up demo files"""
        import shutil
        
        if self.demo_output_dir.exists():
            try:
                shutil.rmtree(self.demo_output_dir)
                self.demo_output_dir.mkdir(exist_ok=True)
                print("🧹 Demo files cleaned successfully")
            except Exception as e:
                print(f"❌ Error cleaning files: {e}")
        else:
            print("📁 No demo files to clean")

def main():
    """Main showcase function"""
    showcase = VibeVoiceShowcase()
    
    # Display header
    showcase.display_header()
    
    # Check system status
    if not showcase.check_system_status():
        print("\n❌ VibeVoice is not available on this system.")
        print("Please check the installation requirements and try again.")
        return
    
    print("\n✅ VibeVoice is ready! Starting interactive demo...")
    time.sleep(2)
    
    # Run interactive demo menu
    showcase.interactive_demo_menu()

if __name__ == "__main__":
    main()