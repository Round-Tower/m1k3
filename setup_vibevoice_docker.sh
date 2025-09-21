#!/bin/bash
"""
VibeVoice Docker Setup Script for M1K3
Sets up NVIDIA PyTorch Container environment for VibeVoice integration
"""

set -e  # Exit on any error

echo "🚀 Setting up VibeVoice Docker Environment for M1K3"
echo "=" * 50

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if NVIDIA Docker runtime is available
if ! docker run --rm --gpus all nvidia/cuda:11.0-base-ubuntu20.04 nvidia-smi &> /dev/null; then
    echo "⚠️  NVIDIA Docker runtime not available."
    echo "   VibeVoice will run on CPU (very slow)"
    echo "   To enable GPU: install nvidia-docker2 and restart Docker"
    GPU_SUPPORT="false"
else
    echo "✅ NVIDIA GPU support detected"
    GPU_SUPPORT="true"
fi

# Create VibeVoice directory structure
echo "📁 Creating VibeVoice directory structure..."
mkdir -p ~/VibeVoice
mkdir -p ~/VibeVoice/models
mkdir -p ~/VibeVoice/output

# Check if VibeVoice repository exists
if [ ! -d ~/VibeVoice/.git ]; then
    echo "📥 Cloning VibeVoice repository..."
    cd ~/
    git clone https://github.com/microsoft/VibeVoice.git
    cd VibeVoice
    echo "✅ VibeVoice repository cloned"
else
    echo "✅ VibeVoice repository already exists"
    cd ~/VibeVoice
    git pull origin main  # Update to latest
fi

# Create Docker run script
echo "🐳 Creating Docker run script..."
cat > run_vibevoice_docker.sh << 'EOF'
#!/bin/bash

# VibeVoice Docker Run Script
echo "🐳 Starting VibeVoice Docker Container..."

# Determine GPU flags
if docker run --rm --gpus all nvidia/cuda:11.0-base-ubuntu20.04 nvidia-smi &> /dev/null; then
    GPU_FLAGS="--gpus all"
    echo "✅ Using GPU acceleration"
else
    GPU_FLAGS=""
    echo "⚠️  Running on CPU (GPU not available)"
fi

# Run NVIDIA PyTorch Container with VibeVoice setup
docker run $GPU_FLAGS \
    --privileged --net=host --ipc=host \
    --ulimit memlock=-1:-1 --ulimit stack=-1:-1 \
    --rm -it \
    --name vibevoice-m1k3 \
    -v $(pwd):/workspace \
    -v ~/VibeVoice:/opt/VibeVoice \
    -v ~/.cache/huggingface:/root/.cache/huggingface \
    -w /workspace \
    nvcr.io/nvidia/pytorch:24.07-py3 \
    bash -c "
        echo '🔧 Setting up VibeVoice environment...'
        
        # Install additional dependencies
        pip install diffusers>=0.21.0 accelerate>=0.20.0 librosa>=0.10.0 gradio>=4.0.0
        
        # Install M1K3 dependencies
        pip install -e .
        
        # Add VibeVoice to Python path
        export PYTHONPATH=/opt/VibeVoice:\$PYTHONPATH
        
        # Test VibeVoice availability
        echo '🧪 Testing VibeVoice setup...'
        python test_vibevoice.py
        
        # Start interactive shell
        echo '🎤 VibeVoice environment ready!'
        echo 'Try: python cli.py --tts-engine vibevoice --voice-profile narrative'
        bash
    "
EOF

chmod +x run_vibevoice_docker.sh

# Create quick test script
echo "🧪 Creating quick test script..."
cat > test_vibevoice_docker.py << 'EOF'
#!/usr/bin/env python3
"""
Quick VibeVoice Docker Test
Tests VibeVoice integration in Docker environment
"""

import sys
import os
sys.path.insert(0, '/opt/VibeVoice')

def main():
    print("🧪 VibeVoice Docker Environment Test")
    print("=" * 40)
    
    # Test M1K3 imports
    try:
        from src.tts.controllers.vibevoice_manager import VibeVoiceManager
        print("✅ VibeVoice manager import successful")
        
        # Test availability
        manager = VibeVoiceManager
        info = manager.get_availability_info()
        
        print(f"📊 Availability Info:")
        for key, value in info.items():
            status = "✅" if value else "❌"
            print(f"   {status} {key}: {value}")
            
        # Test model loading (if available)
        if info['available']:
            print("🎤 Testing model loading...")
            if manager.load_model():
                print("✅ Model loaded successfully!")
                
                # Quick generation test
                audio = manager.generate("Hello from VibeVoice in Docker!")
                if audio is not None:
                    print("✅ Audio generation test passed!")
                else:
                    print("⚠️  Audio generation returned None")
            else:
                print("❌ Model loading failed")
        
    except ImportError as e:
        print(f"❌ Import error: {e}")
    except Exception as e:
        print(f"❌ Test error: {e}")
    
    print("\n🎯 Quick Start Commands:")
    print("   python cli.py --tts-engine vibevoice")
    print("   python cli.py --tts-engine vibevoice --multi-speaker --speakers Alice Bob")
    print("   python cli.py --tts-engine vibevoice --continuous-mode --voice-profile narrative")

if __name__ == "__main__":
    main()
EOF

# Create documentation
echo "📚 Creating setup documentation..."
cat > VIBEVOICE_SETUP.md << 'EOF'
# VibeVoice Integration Setup

## Quick Start

1. **Run the setup script:**
   ```bash
   ./setup_vibevoice_docker.sh
   ```

2. **Start the Docker environment:**
   ```bash
   ./run_vibevoice_docker.sh
   ```

3. **Test VibeVoice integration:**
   ```bash
   python test_vibevoice_docker.py
   ```

4. **Use VibeVoice with M1K3:**
   ```bash
   # Basic VibeVoice usage
   python cli.py --tts-engine vibevoice
   
   # Multi-speaker conversation
   python cli.py --tts-engine vibevoice --multi-speaker --speakers Alice Bob
   
   # Long-form narrative (up to 90 minutes)
   python cli.py --tts-engine vibevoice --continuous-mode --voice-profile narrative
   ```

## Requirements

- Docker with NVIDIA runtime (optional but recommended)
- NVIDIA GPU with 4GB+ VRAM for optimal performance
- ~10GB disk space for models and containers

## Models

- **VibeVoice-1.5B**: Default model, 64K context, up to 90 minutes
- **VibeVoice-7B**: Larger model, 32K context, up to 45 minutes, requires 16GB+ RAM

## Voice Profiles

### KittenTTS Profiles (lightweight, fast)
- `natural`: Default conversational voice
- `assistant`: Professional AI assistant
- `broadcast`: Clear announcer style
- `terminal`: Technical system voice
- `debug`: Minimal processing for speed
- `minimal`: Basic synthesis only

### VibeVoice Profiles (advanced, high-quality)
- `conversational`: Multi-speaker dialogue (up to 4 speakers)
- `narrative`: Long-form storytelling (up to 90 minutes)
- `assistant_duo`: AI + user voice simulation

## Troubleshooting

1. **GPU not detected:**
   - Install nvidia-docker2: `sudo apt install nvidia-docker2`
   - Restart Docker: `sudo systemctl restart docker`

2. **Model download fails:**
   - Check internet connection
   - Ensure sufficient disk space (~4GB for 1.5B model)
   - Try manual download: `huggingface-cli download microsoft/VibeVoice-1.5B`

3. **Memory errors:**
   - Use 1.5B model instead of 7B
   - Reduce chunk size: `--chunk-size 50`
   - Enable CPU fallback: remove `--gpus all` flag

## Performance Notes

- **GPU (Recommended)**: Real-time generation, supports long-form synthesis
- **CPU (Fallback)**: Very slow generation, limited to short text
- **Memory Usage**: 1.5B model ~4GB RAM, 7B model ~16GB RAM
EOF

echo "✅ VibeVoice Docker setup complete!"
echo ""
echo "🚀 Next steps:"
echo "1. Run: ./run_vibevoice_docker.sh"
echo "2. Test: python test_vibevoice_docker.py"
echo "3. Use: python cli.py --tts-engine vibevoice"
echo ""
echo "📚 See VIBEVOICE_SETUP.md for detailed documentation"