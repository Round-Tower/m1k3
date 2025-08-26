#!/usr/bin/env python3
"""
CLI Model Commands for M1K3
Integrates dynamic model monitoring into the CLI interface
"""

import sys
import json
import subprocess
from pathlib import Path
from typing import Optional, List, Dict, Any
import argparse
from datetime import datetime

from src.models.managers.dynamic_model_monitor import DynamicModelMonitor, ModelMetrics, ModelHealth


class ModelCLI:
    """CLI interface for model management commands"""
    
    def __init__(self, models_dir: Path = None):
        self.monitor = DynamicModelMonitor(models_dir)
        self.models_dir = models_dir or Path("models")
    
    def handle_command(self, args: List[str]) -> bool:
        """Handle command from CLI with string arguments"""
        if not args:
            print("❌ No model command specified")
            print("\n🤖 Available Model Commands:")
            print("  • list                     - Show all available models (Ollama, HuggingFace, GGUF)")
            print("  • recommend                - Get model recommendations for your device")
            print("  • health                   - Check health and integrity of installed models")
            print("  • info <model>             - Get detailed information about a specific model")
            print("  • cleanup                  - Remove broken or duplicate model files")
            print("  • download <model>         - Download a new model")
            print("\n💡 Examples: 'model list', 'model recommend', 'model info llama3.2:1b'")
            return True
        
        command = args[0].lower()
        command_args = args[1:] if len(args) > 1 else []
        
        command_map = {
            'list': self.cmd_models_list,
            'recommend': self.cmd_models_recommend, 
            'health': self.cmd_models_health,
            'cleanup': self.cmd_models_cleanup,
        }
        
        if command in command_map:
            try:
                command_map[command](command_args)
                return True
            except Exception as e:
                print(f"❌ Error executing model command '{command}': {e}")
                return True
        elif command == 'info':
            if command_args:
                self.cmd_models_info(command_args[0])
            else:
                print("❌ Model name required for info command")
                print("💡 Use: model info <model_name>")
            return True
        elif command == 'download':
            if command_args:
                self.cmd_models_download(command_args[0])
            else:
                print("❌ Model name required for download command") 
                print("💡 Use: model download <model_name>")
            return True
        else:
            print(f"❌ Unknown model command: {command}")
            print("📋 Available commands: list, recommend, health, info, cleanup, download")
            return True
        
    def cmd_models_list(self, args=None) -> None:
        """List all available models with status"""
        print("🔍 Refreshing model inventory...")
        self.monitor.scan_all_models(silent=False, cleanup=False)
        
        if not self.monitor.monitor_data:
            print("❌ No models found")
            print("💡 Try: models download <model_name> or python download_models.py")
            return
        
        print("\n" + "="*80)
        print("📦 M1K3 MODEL INVENTORY")
        print("="*80)
        
        # Group by type
        by_type = {}
        for name, metrics in self.monitor.monitor_data.items():
            model_type = metrics.type
            if model_type not in by_type:
                by_type[model_type] = []
            by_type[model_type].append((name, metrics))
        
        # Display by type with color coding
        type_emojis = {"ollama": "🦙", "huggingface": "🤗", "gguf": "🔧"}
        tier_emojis = {"ultra-fast": "🚀", "fast": "⚡", "balanced": "⚖️", "powerful": "🧠"}
        health_emojis = {"healthy": "💚", "degraded": "💛", "failed": "❤️", "unknown": "⚪"}
        
        for model_type, models in by_type.items():
            models.sort(key=lambda x: x[1].compatibility_score, reverse=True)
            
            print(f"\n{type_emojis.get(model_type, '📦')} {model_type.upper()} MODELS:")
            
            for model_name, metrics in models:
                health_emoji = health_emojis.get(metrics.health.status, "⚪")
                tier_emoji = tier_emojis.get(metrics.performance_tier, "📦")
                
                # Compatibility bar
                compat_score = int(metrics.compatibility_score * 10)
                compat_bar = "█" * compat_score + "░" * (10 - compat_score)
                
                print(f"  {health_emoji} {tier_emoji} {model_name}")
                print(f"    💾 {metrics.size_mb:.0f}MB | 🧠 {metrics.ram_required_mb//1024:.1f}GB")
                print(f"    🎯 {compat_bar} {metrics.compatibility_score:.1f} | 🏃 {metrics.performance_tier}")
                
                if metrics.health.error_message:
                    print(f"    ❌ {metrics.health.error_message}")
                
                if metrics.health.response_time_ms:
                    print(f"    ⏱️ {metrics.health.response_time_ms:.0f}ms response time")
                
                print()
        
        # Show totals
        total_size = sum(m.size_mb for m in self.monitor.monitor_data.values())
        healthy_count = sum(1 for m in self.monitor.monitor_data.values() if m.health.status == "healthy")
        
        print(f"📊 SUMMARY: {len(self.monitor.monitor_data)} models | {total_size:.0f}MB total | {healthy_count} healthy")
    
    def cmd_models_recommend(self, args=None) -> None:
        """Get model recommendations for current system"""
        print("🎯 Analyzing system and generating recommendations...")
        
        recommendations = self.monitor.get_recommendations()
        
        if not recommendations:
            print("❌ No suitable models found for your system")
            print("💡 Try downloading a lightweight model like gemma3:270m")
            return
        
        print("\n" + "="*60)
        print("🎯 MODEL RECOMMENDATIONS FOR YOUR SYSTEM")
        print("="*60)
        
        # Show system info
        import psutil
        memory = psutil.virtual_memory()
        print(f"💻 Available RAM: {memory.available / (1024**3):.1f}GB")
        print(f"🔧 Platform: {self._get_platform_info()}")
        
        print(f"\n🥇 TOP RECOMMENDATIONS:")
        
        for i, model_name in enumerate(recommendations[:3], 1):
            if model_name in self.monitor.monitor_data:
                metrics = self.monitor.monitor_data[model_name]
                tier_emoji = {"ultra-fast": "🚀", "fast": "⚡", "balanced": "⚖️", "powerful": "🧠"}[metrics.performance_tier]
                
                print(f"\n  {i}. {tier_emoji} {model_name} ({metrics.type})")
                print(f"     💾 Size: {metrics.size_mb:.0f}MB")
                print(f"     🧠 RAM: {metrics.ram_required_mb//1024:.1f}GB required")
                print(f"     🎯 Compatibility: {metrics.compatibility_score:.1f}/1.0")
                print(f"     🏃 Performance: {metrics.performance_tier}")
                
                if metrics.type == "ollama":
                    print(f"     💡 Ready to use: ollama run {model_name}")
                elif metrics.health.status == "healthy":
                    print(f"     ✅ Status: Ready to use")
                else:
                    print(f"     ⚠️ Status: {metrics.health.status}")
        
        if len(recommendations) > 3:
            print(f"\n📋 {len(recommendations)-3} additional compatible models available")
    
    def cmd_models_health(self, args=None) -> None:
        """Show detailed health status for all models"""
        print("🏥 Running comprehensive health checks...")
        
        # Force health check
        self.monitor.run_health_checks()
        
        if not self.monitor.monitor_data:
            print("❌ No models found to check")
            return
        
        print("\n" + "="*70)
        print("🏥 MODEL HEALTH REPORT")
        print("="*70)
        
        # Group by health status
        by_health = {"healthy": [], "degraded": [], "failed": [], "unknown": []}
        
        for name, metrics in self.monitor.monitor_data.items():
            status = metrics.health.status
            by_health[status].append((name, metrics))
        
        # Display by health status
        health_labels = {
            "healthy": "💚 HEALTHY MODELS",
            "degraded": "💛 DEGRADED MODELS", 
            "failed": "❤️ FAILED MODELS",
            "unknown": "⚪ UNKNOWN STATUS"
        }
        
        for status, models in by_health.items():
            if not models:
                continue
                
            print(f"\n{health_labels[status]} ({len(models)}):")
            
            for model_name, metrics in sorted(models):
                print(f"  📦 {model_name}")
                print(f"    🏷️ Type: {metrics.type}")
                print(f"    📊 Availability: {metrics.health.availability_score:.1f}/1.0")
                
                if metrics.health.response_time_ms:
                    print(f"    ⏱️ Response time: {metrics.health.response_time_ms:.0f}ms")
                
                if metrics.health.error_message:
                    print(f"    ❌ Error: {metrics.health.error_message}")
                
                last_check = datetime.fromtimestamp(metrics.health.last_check) if metrics.health.last_check else None
                if last_check:
                    print(f"    🕐 Last checked: {last_check.strftime('%H:%M:%S')}")
                
                print()
        
        # Summary
        healthy_count = len(by_health["healthy"])
        total_count = len(self.monitor.monitor_data)
        health_percentage = (healthy_count / total_count * 100) if total_count > 0 else 0
        
        print(f"📊 HEALTH SUMMARY: {healthy_count}/{total_count} models healthy ({health_percentage:.0f}%)")
    
    def cmd_models_info(self, model_name: str) -> None:
        """Show detailed information about a specific model"""
        if not model_name:
            print("❌ Please specify a model name")
            print("💡 Usage: models info <model_name>")
            return
        
        self.monitor.scan_all_models(silent=True, cleanup=False)
        
        if model_name not in self.monitor.monitor_data:
            print(f"❌ Model '{model_name}' not found")
            print("💡 Use 'models list' to see available models")
            return
        
        metrics = self.monitor.monitor_data[model_name]
        
        print("\n" + "="*70)
        print(f"📋 MODEL INFORMATION: {model_name}")
        print("="*70)
        
        # Basic info
        tier_emoji = {"ultra-fast": "🚀", "fast": "⚡", "balanced": "⚖️", "powerful": "🧠"}[metrics.performance_tier]
        health_emoji = {"healthy": "💚", "degraded": "💛", "failed": "❤️", "unknown": "⚪"}[metrics.health.status]
        
        print(f"🏷️  Name: {metrics.name}")
        print(f"📦 Type: {metrics.type}")
        print(f"💾 Size: {metrics.size_mb:.1f}MB")
        print(f"🧠 RAM Required: {metrics.ram_required_mb//1024:.1f}GB")
        print(f"🏃 Performance Tier: {tier_emoji} {metrics.performance_tier}")
        print(f"🎯 Compatibility Score: {metrics.compatibility_score:.2f}/1.0")
        
        # Health info
        print(f"\n🏥 HEALTH STATUS:")
        print(f"  {health_emoji} Status: {metrics.health.status}")
        print(f"  📊 Availability: {metrics.health.availability_score:.1f}/1.0")
        
        if metrics.health.response_time_ms:
            print(f"  ⏱️  Response Time: {metrics.health.response_time_ms:.0f}ms")
        
        if metrics.health.error_message:
            print(f"  ❌ Error: {metrics.health.error_message}")
        
        last_check = datetime.fromtimestamp(metrics.health.last_check) if metrics.health.last_check else None
        if last_check:
            print(f"  🕐 Last Health Check: {last_check.strftime('%Y-%m-%d %H:%M:%S')}")
        
        # Usage info
        print(f"\n💡 USAGE:")
        if metrics.type == "ollama":
            print(f"  🦙 Ollama: ollama run {model_name}")
            print(f"  🔧 Check status: ollama list")
        elif metrics.type == "huggingface":
            print(f"  🤗 HuggingFace: Available for transformers library")
            print(f"  📁 Path: {getattr(metrics, 'path', 'Auto-detected')}")
        elif metrics.type == "gguf":
            print(f"  🔧 GGUF: Available for llama.cpp or ctransformers")
        
        # Show metadata if available (for Ollama models)
        metadata_file = self.models_dir / f"{model_name.replace(':', '_')}.json"
        if metadata_file.exists():
            print(f"\n📄 DETAILED METADATA:")
            try:
                with open(metadata_file) as f:
                    metadata = json.load(f)
                
                model_info = metadata.get('model_info', {})
                if 'architecture' in model_info:
                    print(f"  🏗️  Architecture: {model_info['architecture']}")
                if 'parameter_count' in model_info:
                    params = model_info['parameter_count']
                    if isinstance(params, (int, float)):
                        print(f"  📊 Parameters: {params:,.0f}")
                if 'context_length' in model_info:
                    print(f"  📏 Context Length: {model_info['context_length']}")
                
                system_prompt = metadata.get('prompts', {}).get('system')
                if system_prompt:
                    print(f"  🤖 System Prompt: {system_prompt[:100]}{'...' if len(system_prompt) > 100 else ''}")
                    
            except Exception as e:
                print(f"  ⚠️  Could not load metadata: {e}")
    
    def cmd_models_cleanup(self, args=None) -> None:
        """Clean up broken or incomplete model downloads"""
        print("🧹 Scanning for cleanup opportunities...")
        
        # Scan with cleanup enabled to show cleanup candidates
        self.monitor.scan_all_models(silent=False, cleanup=True)
        
        print("\n💡 Cleanup candidates identified above")
        print("🔧 To manually clean up:")
        print("  - Remove incomplete HuggingFace downloads from ~/.cache/huggingface/hub/")
        print("  - Use 'ollama rm <model>' to remove unwanted Ollama models")
        print("  - Check models/ directory for orphaned files")
        
        # Could add interactive cleanup here
        print("\n⚠️  Automatic cleanup not implemented yet - manual review recommended")
    
    def cmd_models_download(self, model_name: str) -> None:
        """Download a new model (tries Ollama first)"""
        if not model_name:
            print("❌ Please specify a model name")
            print("💡 Usage: models download <model_name>")
            print("📋 Examples:")
            print("  models download gemma3:270m")
            print("  models download llama3.2:1b")
            return
        
        print(f"📥 Downloading model: {model_name}")
        print("🦙 Trying Ollama first (recommended)...")
        
        try:
            # Try Ollama first
            result = subprocess.run(
                ['ollama', 'pull', model_name],
                capture_output=False,  # Show progress
                text=True,
                check=True
            )
            
            print(f"✅ Successfully downloaded {model_name} via Ollama")
            print("🔄 Refreshing model inventory...")
            self.monitor.scan_all_models(silent=False, cleanup=False)
            
            # Show info about the new model
            if model_name in self.monitor.monitor_data:
                print(f"\n📋 Model ready:")
                self.cmd_models_info(model_name)
            
        except subprocess.CalledProcessError as e:
            print(f"❌ Ollama download failed: {e}")
            print("🤗 Falling back to HuggingFace...")
            self._download_huggingface_model(model_name)
            
        except FileNotFoundError:
            print("❌ Ollama not found")
            print("💡 Install from: https://ollama.com")
            print("🤗 Falling back to HuggingFace...")
            self._download_huggingface_model(model_name)
    
    def _download_huggingface_model(self, model_name: str) -> None:
        """Fallback to HuggingFace download"""
        try:
            from huggingface_hub import snapshot_download
            
            print(f"📥 Downloading {model_name} from HuggingFace...")
            
            model_path = self.models_dir / model_name.replace("/", "_")
            
            snapshot_download(
                repo_id=model_name,
                local_dir=str(model_path),
                local_dir_use_symlinks=False
            )
            
            print(f"✅ Downloaded to {model_path}")
            
        except ImportError:
            print("❌ HuggingFace hub not available")
            print("💡 Install with: pip install huggingface_hub")
        except Exception as e:
            print(f"❌ HuggingFace download failed: {e}")
    
    def _get_platform_info(self) -> str:
        """Get platform information"""
        import platform
        return f"{platform.system()} {platform.machine()}"


def setup_model_commands(parser: argparse.ArgumentParser) -> None:
    """Set up model management commands in the argument parser"""
    
    # Create models subcommand
    models_parser = parser.add_subparser('models', help='Model management commands')
    models_subparsers = models_parser.add_subparsers(dest='models_cmd', help='Model commands')
    
    # models list
    models_subparsers.add_parser('list', help='List all available models with status')
    
    # models recommend  
    models_subparsers.add_parser('recommend', help='Get model recommendations for current system')
    
    # models health
    models_subparsers.add_parser('health', help='Show detailed health status for all models')
    
    # models info <model_name>
    info_parser = models_subparsers.add_parser('info', help='Show detailed information about a specific model')
    info_parser.add_argument('model_name', help='Name of the model to show info for')
    
    # models cleanup
    models_subparsers.add_parser('cleanup', help='Clean up broken or incomplete model downloads')
    
    # models download <model_name>
    download_parser = models_subparsers.add_parser('download', help='Download a new model (tries Ollama first)')
    download_parser.add_argument('model_name', help='Name of the model to download')


def handle_model_commands(args, cli: ModelCLI) -> None:
    """Handle model management commands"""
    
    if not hasattr(args, 'models_cmd') or not args.models_cmd:
        print("❌ No model command specified")
        print("💡 Use: models <command>")
        print("📋 Available commands: list, recommend, health, info, cleanup, download")
        return
    
    command_map = {
        'list': cli.cmd_models_list,
        'recommend': cli.cmd_models_recommend, 
        'health': cli.cmd_models_health,
        'cleanup': cli.cmd_models_cleanup,
    }
    
    if args.models_cmd in command_map:
        command_map[args.models_cmd](args)
    elif args.models_cmd == 'info':
        cli.cmd_models_info(args.model_name)
    elif args.models_cmd == 'download':
        cli.cmd_models_download(args.model_name)
    else:
        print(f"❌ Unknown models command: {args.models_cmd}")


if __name__ == "__main__":
    # Simple test interface
    cli = ModelCLI()
    
    if len(sys.argv) > 1:
        command = sys.argv[1]
        if command == "list":
            cli.cmd_models_list()
        elif command == "recommend":
            cli.cmd_models_recommend()
        elif command == "health":
            cli.cmd_models_health()
        elif command == "info" and len(sys.argv) > 2:
            cli.cmd_models_info(sys.argv[2])
        elif command == "download" and len(sys.argv) > 2:
            cli.cmd_models_download(sys.argv[2])
        else:
            print("Usage: python cli_model_commands.py <list|recommend|health|info|download> [args]")
    else:
        cli.cmd_models_list()