#!/usr/bin/env python3
"""
M1K3 PWA RAG Models Builder
Complete build script for RAG-enabled PWA deployment
"""

import os
import sys
import subprocess
import logging
from pathlib import Path
import json
import time

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class RAGModelBuilder:
    """Builds complete RAG system for M1K3 PWA"""
    
    def __init__(self):
        self.project_root = Path(__file__).parent
        self.backend_dir = self.project_root / "backend"
        self.frontend_dir = self.project_root / "frontend"
        self.models_dir = self.frontend_dir / "models"
        self.models_dir.mkdir(parents=True, exist_ok=True)
        
        # Build status tracking
        self.build_status = {
            "distilgpt2_export": False,
            "embeddings_export": False,
            "knowledge_base": False,
            "total_size_mb": 0,
            "estimated_browser_size_mb": 0
        }
    
    def check_dependencies(self):
        """Check if required dependencies are available"""
        logger.info("🔍 Checking dependencies...")
        
        required_packages = [
            'torch', 'transformers', 'optimum', 'sentence_transformers', 
            'onnx', 'onnxruntime', 'numpy', 'psutil'
        ]
        
        missing_packages = []
        
        for package in required_packages:
            try:
                __import__(package)
                logger.info(f"  ✅ {package}")
            except ImportError:
                logger.error(f"  ❌ {package}")
                missing_packages.append(package)
        
        if missing_packages:
            logger.error(f"Missing packages: {missing_packages}")
            logger.info("Install with: pip install -r backend/requirements.txt")
            return False
        
        # Check for cached models
        cached_models_dir = Path("../../models")
        if cached_models_dir.exists():
            distilgpt2_path = cached_models_dir / "distilgpt2"
            if distilgpt2_path.exists():
                logger.info("  ✅ distilgpt2 cached model found")
            else:
                logger.warning("  ⚠️ distilgpt2 cached model not found")
        
        return True
    
    def export_distilgpt2_model(self):
        """Export distilgpt2 model to ONNX format"""
        logger.info("🤖 Exporting distilgpt2 model...")
        
        try:
            # Change to backend directory
            original_cwd = os.getcwd()
            os.chdir(self.backend_dir)
            
            # Run model exporter
            cmd = [sys.executable, "scripts/model_exporter.py", "--tier", "tiny", "--optimize"]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
            
            os.chdir(original_cwd)
            
            if result.returncode == 0:
                logger.info("✅ distilgpt2 model exported successfully")
                self.build_status["distilgpt2_export"] = True
                
                # Calculate size
                model_dir = self.models_dir / "m1k3-tiny"
                if model_dir.exists():
                    size_mb = sum(f.stat().st_size for f in model_dir.rglob('*')) / (1024 * 1024)
                    logger.info(f"📊 Model size: {size_mb:.1f}MB")
                    self.build_status["total_size_mb"] += size_mb
                
                return True
            else:
                logger.error(f"❌ Model export failed: {result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            logger.error("❌ Model export timed out (10 minutes)")
            return False
        except Exception as e:
            logger.error(f"❌ Model export error: {str(e)}")
            return False
    
    def export_embeddings_model(self):
        """Export embeddings model for RAG"""
        logger.info("🧠 Exporting embeddings model...")
        
        try:
            # Change to backend directory
            original_cwd = os.getcwd()
            os.chdir(self.backend_dir)
            
            # Run embeddings exporter
            cmd = [sys.executable, "scripts/embeddings_exporter.py", "--tier", "tiny"]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            
            os.chdir(original_cwd)
            
            if result.returncode == 0:
                logger.info("✅ Embeddings model exported successfully")
                self.build_status["embeddings_export"] = True
                
                # Calculate size
                embeddings_dir = self.models_dir / "embeddings-tiny"
                if embeddings_dir.exists():
                    size_mb = sum(f.stat().st_size for f in embeddings_dir.rglob('*')) / (1024 * 1024)
                    logger.info(f"📊 Embeddings size: {size_mb:.1f}MB")
                    self.build_status["total_size_mb"] += size_mb
                
                return True
            else:
                logger.error(f"❌ Embeddings export failed: {result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            logger.error("❌ Embeddings export timed out (5 minutes)")
            return False
        except Exception as e:
            logger.error(f"❌ Embeddings export error: {str(e)}")
            return False
    
    def build_knowledge_base(self):
        """Build knowledge base for RAG"""
        logger.info("📚 Building knowledge base...")
        
        try:
            # Change to backend directory
            original_cwd = os.getcwd()
            os.chdir(self.backend_dir)
            
            # Run knowledge base builder
            cmd = [sys.executable, "scripts/knowledge_base_builder.py", "--domains", "all", "--output", "../frontend/models"]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
            
            os.chdir(original_cwd)
            
            if result.returncode == 0:
                logger.info("✅ Knowledge base built successfully")
                self.build_status["knowledge_base"] = True
                
                # Calculate size
                kb_file = self.models_dir / "knowledge_base.json"
                if kb_file.exists():
                    size_mb = kb_file.stat().st_size / (1024 * 1024)
                    logger.info(f"📊 Knowledge base size: {size_mb:.1f}MB")
                    self.build_status["total_size_mb"] += size_mb
                
                return True
            else:
                logger.error(f"❌ Knowledge base build failed: {result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            logger.error("❌ Knowledge base build timed out (2 minutes)")
            return False
        except Exception as e:
            logger.error(f"❌ Knowledge base build error: {str(e)}")
            return False
    
    def create_deployment_manifest(self):
        """Create deployment manifest for the PWA"""
        logger.info("📋 Creating deployment manifest...")
        
        # Calculate estimated browser storage size
        estimated_browser_size = self.build_status["total_size_mb"] * 1.2  # 20% overhead
        self.build_status["estimated_browser_size_mb"] = estimated_browser_size
        
        manifest = {
            "version": "1.0.0-rag",
            "build_timestamp": int(time.time()),
            "build_date": time.strftime("%Y-%m-%d %H:%M:%S"),
            "features": {
                "rag_enabled": True,
                "models": {
                    "language_model": "distilgpt2-onnx-quantized",
                    "embeddings_model": "all-MiniLM-L6-v2-onnx",
                    "knowledge_base": "eco-health-multi-domain"
                },
                "storage": {
                    "total_size_mb": round(self.build_status["total_size_mb"], 1),
                    "estimated_browser_size_mb": round(estimated_browser_size, 1),
                    "indexeddb_required": True,
                    "webassembly_required": True
                }
            },
            "compatibility": {
                "min_memory_gb": 2,
                "recommended_memory_gb": 4,
                "browsers": ["Chrome", "Firefox", "Safari", "Edge"],
                "mobile_support": True,
                "offline_capable": True
            },
            "build_status": self.build_status
        }
        
        # Save manifest
        manifest_path = self.models_dir / "deployment-manifest.json"
        with open(manifest_path, 'w') as f:
            json.dump(manifest, f, indent=2)
        
        logger.info(f"📋 Deployment manifest created: {manifest_path}")
        return manifest
    
    def validate_build(self):
        """Validate the complete build"""
        logger.info("🔍 Validating build...")
        
        required_files = [
            "m1k3-tiny/model.onnx",
            "m1k3-tiny/tokenizer.json",
            "embeddings-tiny/model.onnx",
            "knowledge_base.json",
            "deployment-manifest.json"
        ]
        
        missing_files = []
        for file_path in required_files:
            full_path = self.models_dir / file_path
            if not full_path.exists():
                missing_files.append(file_path)
        
        if missing_files:
            logger.error(f"❌ Missing files: {missing_files}")
            return False
        
        # Check sizes
        total_size = sum(f.stat().st_size for f in self.models_dir.rglob('*') if f.is_file()) / (1024 * 1024)
        
        if total_size > 1000:  # 1GB limit
            logger.warning(f"⚠️ Build size ({total_size:.1f}MB) is quite large for browser deployment")
        elif total_size > 500:  # 500MB warning
            logger.warning(f"⚠️ Build size ({total_size:.1f}MB) may be large for mobile devices")
        else:
            logger.info(f"✅ Build size ({total_size:.1f}MB) is appropriate for browser deployment")
        
        return True
    
    def run_complete_build(self):
        """Run the complete RAG models build process"""
        logger.info("🚀 Starting M1K3 PWA RAG Models Build")
        logger.info("="*60)
        
        start_time = time.time()
        
        # Step 1: Check dependencies
        if not self.check_dependencies():
            logger.error("❌ Dependency check failed")
            return False
        
        # Step 2: Export distilgpt2 model
        logger.info("\n" + "="*30 + " STEP 1: LANGUAGE MODEL " + "="*30)
        if not self.export_distilgpt2_model():
            logger.error("❌ Language model export failed")
            return False
        
        # Step 3: Export embeddings model
        logger.info("\n" + "="*30 + " STEP 2: EMBEDDINGS MODEL " + "="*30)
        if not self.export_embeddings_model():
            logger.error("❌ Embeddings model export failed")
            return False
        
        # Step 4: Build knowledge base
        logger.info("\n" + "="*30 + " STEP 3: KNOWLEDGE BASE " + "="*30)
        if not self.build_knowledge_base():
            logger.error("❌ Knowledge base build failed")
            return False
        
        # Step 5: Create deployment manifest
        logger.info("\n" + "="*30 + " STEP 4: DEPLOYMENT " + "="*30)
        manifest = self.create_deployment_manifest()
        
        # Step 6: Validate build
        if not self.validate_build():
            logger.error("❌ Build validation failed")
            return False
        
        # Build complete
        build_time = time.time() - start_time
        
        logger.info("\n" + "="*60)
        logger.info("🎉 M1K3 PWA RAG BUILD COMPLETE!")
        logger.info("="*60)
        logger.info(f"⏱️  Build time: {build_time:.1f} seconds")
        logger.info(f"📦 Total size: {self.build_status['total_size_mb']:.1f}MB")
        logger.info(f"🌐 Estimated browser storage: {self.build_status['estimated_browser_size_mb']:.1f}MB")
        logger.info(f"📂 Models directory: {self.models_dir}")
        
        logger.info("\n🚀 Next steps:")
        logger.info("  1. Test locally: python test_server.py")
        logger.info("  2. Run integration tests: python test_pwa_integration.py")
        logger.info("  3. Deploy with Docker: docker-compose up --build")
        
        return True

def main():
    """Main build script"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Build M1K3 PWA RAG Models")
    parser.add_argument("--step", choices=["deps", "model", "embeddings", "knowledge", "all"], 
                       default="all", help="Build step to run")
    parser.add_argument("--validate", action="store_true", help="Only validate existing build")
    
    args = parser.parse_args()
    
    builder = RAGModelBuilder()
    
    if args.validate:
        success = builder.validate_build()
        return 0 if success else 1
    
    if args.step == "all":
        success = builder.run_complete_build()
    elif args.step == "deps":
        success = builder.check_dependencies()
    elif args.step == "model":
        success = builder.export_distilgpt2_model()
    elif args.step == "embeddings":
        success = builder.export_embeddings_model()
    elif args.step == "knowledge":
        success = builder.build_knowledge_base()
    
    return 0 if success else 1

if __name__ == "__main__":
    exit(main())