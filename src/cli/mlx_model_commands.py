#!/usr/bin/env python3
"""
MLX Model Management for M1K3 CLI
Adds MLX-LM backend support for Apple Silicon model switching
"""

import requests
import subprocess
from typing import List, Dict, Optional, Any
from dataclasses import dataclass


@dataclass
class MLXModel:
    """Represents an MLX model"""
    id: str
    object: str
    created: int

    @property
    def display_name(self) -> str:
        """Clean model name for display"""
        # Remove mlx-community/ prefix if present
        return self.id.replace('mlx-community/', '')

    @property
    def size_estimate(self) -> str:
        """Estimate model size from name"""
        name_lower = self.id.lower()
        if '135m' in name_lower or '0.5b' in name_lower:
            return "~500MB"
        elif '360m' in name_lower or '0.6b' in name_lower:
            return "~1.5GB"
        elif '1.5b' in name_lower or '1b' in name_lower:
            return "~3GB"
        elif '3b' in name_lower:
            return "~6GB"
        elif '7b' in name_lower:
            return "~14GB"
        elif 'coder-next' in name_lower or '80b' in name_lower:
            return "~46GB (80B MoE 4-bit)"
        else:
            return "Unknown"


class MLXModelManager:
    """Manages MLX-LM models via HTTP API"""

    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.timeout = 5.0

    def is_available(self) -> bool:
        """Check if MLX server is running"""
        try:
            response = self.session.get(f"{self.base_url}/v1/models")
            return response.status_code == 200
        except:
            return False

    def list_models(self) -> List[MLXModel]:
        """List available MLX models"""
        try:
            response = self.session.get(f"{self.base_url}/v1/models")
            response.raise_for_status()
            data = response.json()

            models = []
            for model_data in data.get('data', []):
                models.append(MLXModel(
                    id=model_data['id'],
                    object=model_data['object'],
                    created=model_data.get('created', 0)
                ))
            return models
        except Exception as e:
            print(f"❌ Failed to list MLX models: {e}")
            return []

    def get_current_model(self) -> Optional[str]:
        """Get the currently loaded MLX model"""
        # MLX server loads one model at a time
        # Check which process is running
        try:
            result = subprocess.run(
                ['ps', 'aux'],
                capture_output=True,
                text=True
            )
            for line in result.stdout.split('\n'):
                if 'mlx_lm.server' in line and '--model' in line:
                    parts = line.split('--model')
                    if len(parts) > 1:
                        model_name = parts[1].split()[0]
                        return model_name
        except:
            pass
        return None

    def switch_model(self, model_name: str) -> bool:
        """Switch to a different MLX model by restarting the server"""
        print(f"🔄 Switching to MLX model: {model_name}")

        # Stop current server
        try:
            subprocess.run(['pkill', '-f', 'mlx_lm.server'], check=False)
            print("⏸️  Stopped current MLX server")
        except:
            pass

        # Start new server with desired model
        try:
            cmd = [
                'mlx-env/bin/mlx_lm.server',
                '--model', model_name,
                '--port', '8080',
                '--trust-remote-code'
            ]

            # Start in background
            subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                start_new_session=True
            )

            print(f"🚀 Starting MLX server with {model_name}")
            print("⏳ Server will be ready in 5-10 seconds...")
            return True
        except Exception as e:
            print(f"❌ Failed to switch model: {e}")
            return False

    def test_inference(self, prompt: str = "Hello, how are you?") -> Optional[str]:
        """Test inference with current model"""
        try:
            response = self.session.post(
                f"{self.base_url}/v1/chat/completions",
                json={
                    "messages": [{"role": "user", "content": prompt}],
                    "max_tokens": 50,
                    "temperature": 0.7
                },
                timeout=30.0
            )
            response.raise_for_status()
            data = response.json()
            return data['choices'][0]['message']['content']
        except Exception as e:
            print(f"❌ Inference test failed: {e}")
            return None


class MLXCLICommands:
    """MLX-specific CLI commands"""

    def __init__(self):
        self.manager = MLXModelManager()

    def cmd_mlx_status(self) -> None:
        """Show MLX backend status"""
        print("\n🍎 MLX-LM Backend Status")
        print("=" * 60)

        if not self.manager.is_available():
            print("❌ Status: Not running")
            print("💡 Start with: mlx-env/bin/mlx_lm.server --model <model> --port 8080")
            print("📋 Available models:")
            print("   • mlx-community/SmolLM2-135M-Instruct (~500MB)")
            print("   • mlx-community/Qwen3-0.6B-4bit (~1.5GB)")
            print("   • mlx-community/Qwen3-Coder-Next-4bit (~46GB, 80B MoE)")
            return

        print("✅ Status: Running")

        # Show current model
        current = self.manager.get_current_model()
        if current:
            print(f"🎯 Active Model: {current}")

        # List available models
        models = self.manager.list_models()
        if models:
            print(f"\n📦 Available Models ({len(models)}):")
            for model in models:
                marker = "👉" if current and model.id in current else "  "
                print(f"  {marker} {model.display_name} ({model.size_estimate})")

        print(f"\n🔗 API Endpoint: {self.manager.base_url}")
        print("📖 Format: OpenAI-compatible")

    def cmd_mlx_list(self) -> None:
        """List MLX models"""
        if not self.manager.is_available():
            print("❌ MLX server not running")
            print("💡 Start it first: mlx-env/bin/mlx_lm.server --model <model> --port 8080")
            return

        models = self.manager.list_models()
        if not models:
            print("❌ No MLX models found")
            return

        print(f"\n📦 MLX Models ({len(models)}):")
        print("=" * 60)

        current = self.manager.get_current_model()

        for model in models:
            is_active = current and model.id in current
            marker = "✅" if is_active else "  "
            status = " (ACTIVE)" if is_active else ""

            print(f"{marker} {model.display_name}{status}")
            print(f"   💾 Size: {model.size_estimate}")
            print(f"   🏷️  Full ID: {model.id}")
            print()

    def cmd_mlx_switch(self, model_name: str) -> None:
        """Switch MLX model"""
        if not model_name:
            print("❌ Model name required")
            print("💡 Usage: mlx switch <model-name>")
            return

        # If model name doesn't have a namespace, add mlx-community
        if '/' not in model_name:
            model_name = f"mlx-community/{model_name}"

        success = self.manager.switch_model(model_name)
        if success:
            print(f"✅ Switched to {model_name}")
            print("💡 Wait 5-10 seconds for the model to load")
            print("🧪 Test with: mlx test")

    def cmd_mlx_test(self) -> None:
        """Test MLX inference"""
        if not self.manager.is_available():
            print("❌ MLX server not running")
            return

        current = self.manager.get_current_model()
        print(f"🧪 Testing inference with {current or 'current model'}...")

        response = self.manager.test_inference("Write a haiku about AI on Apple Silicon.")
        if response:
            print(f"\n✅ Response:\n{response}\n")
        else:
            print("❌ Inference test failed")


def handle_mlx_command(args: List[str]) -> bool:
    """Handle /mlx command"""
    cli = MLXCLICommands()

    if not args or args[0] == "status":
        cli.cmd_mlx_status()
        return True

    command = args[0].lower()
    command_args = args[1:] if len(args) > 1 else []

    if command == "list":
        cli.cmd_mlx_list()
    elif command == "switch" and command_args:
        cli.cmd_mlx_switch(command_args[0])
    elif command == "test":
        cli.cmd_mlx_test()
    else:
        print("❌ Unknown MLX command")
        print("📋 Available: status, list, switch <model>, test")

    return True


if __name__ == "__main__":
    # Test the MLX commands
    import sys
    if len(sys.argv) > 1:
        handle_mlx_command(sys.argv[1:])
    else:
        cli = MLXCLICommands()
        cli.cmd_mlx_status()
