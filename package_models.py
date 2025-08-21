#!/usr/bin/env python3
"""
M1K3 Model Packaging System
Package and distribute AI models with M1K3
"""

import shutil
import json
import hashlib
from pathlib import Path
from typing import Dict, List, Any, Optional
import tarfile
import time

class ModelPackager:
    """Package and manage AI models for M1K3 distribution"""
    
    def __init__(self, models_dir: str = "models"):
        self.models_dir = Path(models_dir)
        self.models_dir.mkdir(exist_ok=True)
        self.cache_dir = Path.home() / '.cache' / 'huggingface' / 'hub'
        self.package_manifest = self.models_dir / "model_manifest.json"
        
    def find_cached_models(self) -> Dict[str, Dict[str, Any]]:
        """Find all cached HuggingFace models"""
        cached_models = {}
        
        if not self.cache_dir.exists():
            return cached_models
        
        for model_folder in self.cache_dir.glob("models--*"):
            model_name = model_folder.name.replace("models--", "").replace("--", "/")
            
            # Get model size
            total_size = sum(f.stat().st_size for f in model_folder.rglob('*') if f.is_file())
            size_mb = total_size / (1024 * 1024)
            
            # Check for key files
            has_config = any(model_folder.rglob("config.json"))
            has_model = any(model_folder.rglob("*.safetensors")) or any(model_folder.rglob("pytorch_model.bin"))
            has_tokenizer = any(model_folder.rglob("tokenizer.json"))
            
            cached_models[model_name] = {
                "path": str(model_folder),
                "size_mb": round(size_mb, 1),
                "has_config": has_config,
                "has_model": has_model,
                "has_tokenizer": has_tokenizer,
                "complete": has_config and has_model and has_tokenizer
            }
        
        return cached_models
    
    def package_model(self, model_name: str, output_name: str = None) -> Optional[Path]:
        """Package a specific model for distribution"""
        cached_models = self.find_cached_models()
        
        if model_name not in cached_models:
            print(f"❌ Model {model_name} not found in cache")
            return None
        
        model_info = cached_models[model_name]
        if not model_info["complete"]:
            print(f"⚠️  Model {model_name} appears incomplete")
            return None
        
        # Create package name
        if not output_name:
            output_name = model_name.replace("/", "_").replace("-", "_")
        
        package_path = self.models_dir / f"{output_name}.tar.gz"
        source_path = Path(model_info["path"])
        
        print(f"📦 Packaging {model_name} ({model_info['size_mb']}MB)...")
        
        try:
            with tarfile.open(package_path, "w:gz") as tar:
                # Add all model files
                for file_path in source_path.rglob("*"):
                    if file_path.is_file():
                        # Create archive path without cache directory structure
                        archive_path = file_path.relative_to(source_path)
                        tar.add(file_path, arcname=archive_path)
            
            # Create package metadata
            package_info = {
                "model_name": model_name,
                "package_name": output_name,
                "original_size_mb": model_info["size_mb"],
                "package_size_mb": round(package_path.stat().st_size / (1024 * 1024), 1),
                "created": time.time(),
                "checksum": self._calculate_checksum(package_path)
            }
            
            # Update manifest
            self._update_manifest(package_info)
            
            print(f"✅ Package created: {package_path}")
            print(f"   Size: {package_info['package_size_mb']}MB")
            
            return package_path
            
        except Exception as e:
            print(f"❌ Packaging failed: {e}")
            return None
    
    def extract_model_package(self, package_path: Path, model_name: str) -> bool:
        """Extract a packaged model to the models directory"""
        if not package_path.exists():
            print(f"❌ Package not found: {package_path}")
            return False
        
        # Create extraction directory
        extract_dir = self.models_dir / model_name.replace("/", "_")
        extract_dir.mkdir(exist_ok=True)
        
        print(f"📂 Extracting {package_path.name}...")
        
        try:
            with tarfile.open(package_path, "r:gz") as tar:
                tar.extractall(extract_dir)
            
            print(f"✅ Model extracted to: {extract_dir}")
            return True
            
        except Exception as e:
            print(f"❌ Extraction failed: {e}")
            return False
    
    def create_model_symlinks(self) -> Dict[str, bool]:
        """Create symlinks from models directory to HuggingFace cache"""
        results = {}
        cached_models = self.find_cached_models()
        
        for model_name, model_info in cached_models.items():
            if not model_info["complete"]:
                continue
            
            # Create safe directory name
            safe_name = model_name.replace("/", "_").replace("-", "_")
            symlink_path = self.models_dir / safe_name
            
            try:
                if symlink_path.exists():
                    symlink_path.unlink()
                
                symlink_path.symlink_to(model_info["path"])
                results[model_name] = True
                print(f"🔗 Linked: {safe_name} -> {model_name}")
                
            except Exception as e:
                results[model_name] = False
                print(f"❌ Failed to link {model_name}: {e}")
        
        return results
    
    def package_m1k3_bundle(self) -> Optional[Path]:
        """Create complete M1K3 bundle with pre-packaged models"""
        print("📦 Creating M1K3 Complete Bundle...")
        
        # Package key models
        models_to_package = [
            "google/gemma-2-2b-it",
            "TinyLlama/TinyLlama-1.1B-Chat-v1.0",
            "Qwen/Qwen2.5-0.5B-Instruct"
        ]
        
        packaged_models = []
        cached_models = self.find_cached_models()
        
        for model_name in models_to_package:
            if model_name in cached_models:
                package_path = self.package_model(model_name)
                if package_path:
                    packaged_models.append(package_path)
        
        if not packaged_models:
            print("❌ No models available for bundling")
            return None
        
        # Create bundle
        bundle_path = Path("m1k3_complete_bundle.tar.gz")
        
        try:
            with tarfile.open(bundle_path, "w:gz") as tar:
                # Add model packages
                for package_path in packaged_models:
                    tar.add(package_path, arcname=f"models/{package_path.name}")
                
                # Add setup script
                self._create_bundle_setup_script()
                setup_script = Path("setup_m1k3_bundle.py")
                if setup_script.exists():
                    tar.add(setup_script, arcname="setup_m1k3_bundle.py")
            
            bundle_size = bundle_path.stat().st_size / (1024 * 1024)
            print(f"✅ Bundle created: {bundle_path}")
            print(f"   Size: {bundle_size:.1f}MB")
            print(f"   Models: {len(packaged_models)}")
            
            return bundle_path
            
        except Exception as e:
            print(f"❌ Bundle creation failed: {e}")
            return None
    
    def _create_bundle_setup_script(self):
        """Create setup script for bundle installation"""
        setup_script = '''#!/usr/bin/env python3
"""
M1K3 Bundle Setup Script
Automatically extract and configure bundled models
"""

import tarfile
import shutil
from pathlib import Path

def setup_m1k3_bundle():
    print("🚀 Setting up M1K3 Complete Bundle...")
    
    models_dir = Path("models")
    models_dir.mkdir(exist_ok=True)
    
    # Extract model packages
    for package_file in Path(".").glob("models/*.tar.gz"):
        print(f"📂 Extracting {package_file.name}...")
        
        model_name = package_file.stem.replace(".tar", "")
        extract_dir = models_dir / model_name
        extract_dir.mkdir(exist_ok=True)
        
        with tarfile.open(package_file, "r:gz") as tar:
            tar.extractall(extract_dir)
    
    print("✅ M1K3 bundle setup complete!")
    print("🚀 Run: python m1k3.py")

if __name__ == "__main__":
    setup_m1k3_bundle()
'''
        
        with open("setup_m1k3_bundle.py", "w") as f:
            f.write(setup_script)
    
    def _calculate_checksum(self, file_path: Path) -> str:
        """Calculate SHA256 checksum of file"""
        sha256_hash = hashlib.sha256()
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                sha256_hash.update(chunk)
        return sha256_hash.hexdigest()
    
    def _update_manifest(self, package_info: Dict[str, Any]):
        """Update package manifest file"""
        manifest = []
        
        if self.package_manifest.exists():
            try:
                with open(self.package_manifest, 'r') as f:
                    manifest = json.load(f)
            except:
                manifest = []
        
        # Update or add package info
        for i, item in enumerate(manifest):
            if item.get("model_name") == package_info["model_name"]:
                manifest[i] = package_info
                break
        else:
            manifest.append(package_info)
        
        with open(self.package_manifest, 'w') as f:
            json.dump(manifest, f, indent=2)
    
    def list_packages(self) -> List[Dict[str, Any]]:
        """List all available packages"""
        if not self.package_manifest.exists():
            return []
        
        try:
            with open(self.package_manifest, 'r') as f:
                return json.load(f)
        except:
            return []
    
    def get_packaging_stats(self) -> Dict[str, Any]:
        """Get packaging system statistics"""
        cached_models = self.find_cached_models()
        packages = self.list_packages()
        
        total_cache_size = sum(model["size_mb"] for model in cached_models.values())
        total_package_size = sum(pkg["package_size_mb"] for pkg in packages)
        
        return {
            "cached_models": len(cached_models),
            "packaged_models": len(packages),
            "total_cache_size_mb": round(total_cache_size, 1),
            "total_package_size_mb": round(total_package_size, 1),
            "compression_ratio": round(total_package_size / total_cache_size * 100, 1) if total_cache_size > 0 else 0
        }

def main():
    """Main packaging interface"""
    packager = ModelPackager()
    
    print("🚀 M1K3 Model Packaging System")
    print("=" * 40)
    
    # Show cached models
    cached = packager.find_cached_models()
    print(f"📋 Found {len(cached)} cached models:")
    
    for model_name, info in cached.items():
        status = "✅ Complete" if info["complete"] else "⚠️ Incomplete"
        print(f"   {model_name}: {info['size_mb']}MB {status}")
    
    # Show packaging options
    print(f"\\n🎯 Quick Actions:")
    print("1. Package Gemma 2B: packager.package_model('google/gemma-2-2b-it')")
    print("2. Create symlinks: packager.create_model_symlinks()")
    print("3. Create bundle: packager.package_m1k3_bundle()")
    
    # Get stats
    stats = packager.get_packaging_stats()
    print(f"\\n📊 Stats: {stats['cached_models']} cached, {stats['total_cache_size_mb']}MB total")

if __name__ == "__main__":
    main()