#!/usr/bin/env python3
"""
M1K3 Model Download Script
Downloads required AI models for the M1K3 system
"""

import os
import sys
from pathlib import Path
import subprocess
import json

def download_models():
    """Download required models for M1K3 system"""
    
    print("🚀 M1K3 Model Download Script")
    print("=" * 40)
    
    # Create models directory
    models_dir = Path("models")
    models_dir.mkdir(exist_ok=True)
    
    print(f"📁 Models directory: {models_dir.absolute()}")
    
    # Required models for M1K3
    models_to_download = [
        {
            "name": "TinyLlama-1.1B-Chat-v1.0",
            "source": "huggingface",
            "repo": "TinyLlama/TinyLlama-1.1B-Chat-v1.0",
            "description": "Primary chat model (1.1B parameters)",
            "required": True
        },
        {
            "name": "BAAI/bge-small-en-v1.5", 
            "source": "huggingface",
            "repo": "BAAI/bge-small-en-v1.5",
            "description": "RAG embedding model for semantic search",
            "required": True
        },
        {
            "name": "microsoft/DialoGPT-small",
            "source": "huggingface", 
            "repo": "microsoft/DialoGPT-small",
            "description": "Conversational AI model",
            "required": False
        },
        {
            "name": "TinyLlama GGUF",
            "source": "direct",
            "url": "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            "filename": "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            "description": "Quantized GGUF model for low-memory systems",
            "required": False
        }
    ]
    
    print("\n📦 Available Models:")
    for i, model in enumerate(models_to_download, 1):
        status = "Required" if model.get("required", False) else "Optional"
        print(f"   {i}. {model['name']}")
        print(f"      {model['description']}")
        print(f"      Status: {status}")
        print()
    
    # Check if we have the dependencies
    try:
        import huggingface_hub
        hf_available = True
    except ImportError:
        print("⚠️  huggingface_hub not available. Install with:")
        print("   pip install huggingface_hub")
        hf_available = False
    
    try:
        import transformers
        transformers_available = True
    except ImportError:
        print("⚠️  transformers not available. Install with:")
        print("   pip install transformers torch")
        transformers_available = False
    
    if not (hf_available and transformers_available):
        print("\n❌ Missing dependencies. Please install required packages first.")
        return False
    
    # Download models
    print("🔄 Starting model downloads...\n")
    
    downloaded = 0
    failed = 0
    
    for model in models_to_download:
        print(f"📥 Downloading {model['name']}...")
        
        try:
            if model["source"] == "huggingface":
                # Download using huggingface_hub
                from huggingface_hub import snapshot_download
                
                model_path = models_dir / model["name"]
                
                if model_path.exists():
                    print(f"   ✅ Already exists: {model_path}")
                    downloaded += 1
                    continue
                
                print(f"   🔄 Downloading from {model['repo']}...")
                
                snapshot_download(
                    repo_id=model["repo"],
                    local_dir=str(model_path),
                    local_dir_use_symlinks=False
                )
                
                print(f"   ✅ Downloaded: {model_path}")
                downloaded += 1
                
            elif model["source"] == "direct":
                # Direct download
                import urllib.request
                
                file_path = models_dir / model["filename"]
                
                if file_path.exists():
                    print(f"   ✅ Already exists: {file_path}")
                    downloaded += 1
                    continue
                
                print(f"   🔄 Downloading {model['filename']}...")
                
                urllib.request.urlretrieve(model["url"], str(file_path))
                
                print(f"   ✅ Downloaded: {file_path}")
                downloaded += 1
                
        except Exception as e:
            print(f"   ❌ Failed to download {model['name']}: {e}")
            if model.get("required", False):
                failed += 1
            continue
    
    # Create startup config
    startup_config = {
        "version": "1.0",
        "default_model": "TinyLlama/TinyLlama-1.1B-Chat-v1.0",
        "embedding_model": "BAAI/bge-small-en-v1.5",
        "models_directory": str(models_dir.absolute()),
        "downloaded_at": str(Path().cwd()),
        "download_status": {
            "downloaded": downloaded,
            "failed": failed,
            "total": len(models_to_download)
        }
    }
    
    config_path = models_dir / "startup_config.json"
    with open(config_path, 'w') as f:
        json.dump(startup_config, f, indent=2)
    
    print(f"\n📊 Download Summary:")
    print(f"   ✅ Downloaded: {downloaded}/{len(models_to_download)}")
    print(f"   ❌ Failed: {failed}")
    print(f"   📝 Config saved: {config_path}")
    
    if failed == 0:
        print("\n🎉 All models downloaded successfully!")
        print("✅ M1K3 is ready to use")
        print("\n🚀 Quick start:")
        print("   python m1k3.py")
        print("   python m1k3.py --rag  # With knowledge base")
        return True
    else:
        print(f"\n⚠️  {failed} required models failed to download")
        print("🔧 M1K3 may have limited functionality")
        return False


def check_models():
    """Check which models are already available"""
    
    print("🔍 Checking available models...")
    models_dir = Path("models")
    
    if not models_dir.exists():
        print("❌ Models directory not found")
        return False
    
    models = list(models_dir.iterdir())
    
    if not models:
        print("❌ No models found")
        return False
    
    print(f"\n📦 Found {len(models)} items in models directory:")
    
    total_size = 0
    for model_path in models:
        if model_path.is_dir():
            # Count files in directory
            files = list(model_path.rglob("*"))
            file_count = len([f for f in files if f.is_file()])
            dir_size = sum(f.stat().st_size for f in files if f.is_file())
            total_size += dir_size
            
            print(f"   📁 {model_path.name}/")
            print(f"      Files: {file_count}")
            print(f"      Size: {dir_size / (1024*1024):.1f} MB")
            
        else:
            file_size = model_path.stat().st_size
            total_size += file_size
            
            print(f"   📄 {model_path.name}")
            print(f"      Size: {file_size / (1024*1024):.1f} MB")
        
        print()
    
    print(f"💾 Total size: {total_size / (1024*1024):.1f} MB")
    
    # Check config
    config_path = models_dir / "startup_config.json"
    if config_path.exists():
        with open(config_path) as f:
            config = json.load(f)
        
        print(f"⚙️  Configuration found:")
        print(f"   Default model: {config.get('default_model', 'Not set')}")
        print(f"   Embedding model: {config.get('embedding_model', 'Not set')}")
    
    return True


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--check":
        check_models()
    else:
        success = download_models()
        if not success:
            sys.exit(1)