#!/usr/bin/env python3
"""
M1K3 PWA Model API Server
Provides model metadata and deployment information for the frontend
"""

from flask import Flask, jsonify, request
from flask_cors import CORS
import os
import json
import logging
from pathlib import Path

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Model directory path
MODELS_DIR = Path("/usr/share/nginx/html/models")
BACKEND_DIR = Path("/app/backend")

def get_model_info():
    """Get information about available models"""
    models = {
        "tiny": {
            "name": "m1k3-tiny",
            "size_mb": 100,
            "description": "Lightweight model for low-end devices",
            "min_memory_gb": 2,
            "capabilities": ["basic_chat", "simple_qa"],
            "file": "tiny/model.onnx"
        },
        "small": {
            "name": "m1k3-small", 
            "size_mb": 350,
            "description": "Balanced model for mid-range devices",
            "min_memory_gb": 4,
            "capabilities": ["chat", "qa", "reasoning"],
            "file": "small/model.onnx"
        },
        "medium": {
            "name": "m1k3-medium",
            "size_mb": 800,
            "description": "Advanced model for high-end devices",
            "min_memory_gb": 8,
            "capabilities": ["advanced_chat", "complex_reasoning", "code_generation"],
            "file": "medium/model.onnx"
        }
    }
    
    # Check which models actually exist
    available_models = {}
    for tier, info in models.items():
        model_path = MODELS_DIR / info["file"]
        if model_path.exists():
            info["available"] = True
            info["size_bytes"] = model_path.stat().st_size
            available_models[tier] = info
        else:
            logger.warning(f"Model not found: {model_path}")
            info["available"] = False
            available_models[tier] = info
    
    return available_models

@app.route('/')
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "service": "m1k3-model-api",
        "version": "1.0.0"
    })

@app.route('/models')
def list_models():
    """List available models with metadata"""
    try:
        models = get_model_info()
        return jsonify({
            "models": models,
            "total_models": len(models),
            "available_models": len([m for m in models.values() if m["available"]])
        })
    except Exception as e:
        logger.error(f"Error listing models: {e}")
        return jsonify({"error": "Failed to list models"}), 500

@app.route('/models/<tier>')
def get_model(tier):
    """Get specific model information"""
    try:
        models = get_model_info()
        if tier not in models:
            return jsonify({"error": "Model not found"}), 404
        
        return jsonify(models[tier])
    except Exception as e:
        logger.error(f"Error getting model {tier}: {e}")
        return jsonify({"error": "Failed to get model"}), 500

@app.route('/deployment-manifest.json')
def deployment_manifest():
    """Deployment manifest for frontend consumption"""
    try:
        models = get_model_info()
        
        # Create deployment manifest
        manifest = {
            "version": "1.0.0",
            "models": models,
            "api_version": "v1",
            "features": {
                "device_detection": True,
                "progressive_loading": True,
                "offline_support": True,
                "webgpu_acceleration": True
            },
            "deployment": {
                "type": "docker",
                "platform": "web",
                "build_date": "2025-08-22",
                "environment": "production"
            }
        }
        
        return jsonify(manifest)
        
    except Exception as e:
        logger.error(f"Error creating deployment manifest: {e}")
        return jsonify({"error": "Failed to create manifest"}), 500

@app.route('/device-recommendations', methods=['POST'])
def device_recommendations():
    """Get model recommendations based on device capabilities"""
    try:
        capabilities = request.get_json()
        
        if not capabilities:
            return jsonify({"error": "Device capabilities required"}), 400
        
        memory_gb = capabilities.get('memory', 4)
        webgpu = capabilities.get('webgpu', {}).get('supported', False)
        mobile = capabilities.get('platform', {}).get('mobile', False)
        
        models = get_model_info()
        recommendations = []
        
        # Recommend models based on device capabilities
        for tier, model in models.items():
            if model['available'] and memory_gb >= model['min_memory_gb']:
                score = 100
                
                # Adjust score based on device capabilities
                if mobile and model['size_mb'] > 400:
                    score -= 30  # Penalize large models on mobile
                
                if not webgpu and tier == 'medium':
                    score -= 20  # Penalize advanced models without GPU
                
                if memory_gb >= model['min_memory_gb'] * 2:
                    score += 10  # Bonus for ample memory
                
                recommendations.append({
                    "tier": tier,
                    "model": model,
                    "score": max(0, score),
                    "recommended": score >= 70
                })
        
        # Sort by score
        recommendations.sort(key=lambda x: x['score'], reverse=True)
        
        return jsonify({
            "recommendations": recommendations,
            "optimal": recommendations[0] if recommendations else None
        })
        
    except Exception as e:
        logger.error(f"Error generating recommendations: {e}")
        return jsonify({"error": "Failed to generate recommendations"}), 500

if __name__ == '__main__':
    logger.info("Starting M1K3 Model API Server...")
    logger.info(f"Models directory: {MODELS_DIR}")
    logger.info(f"Models directory exists: {MODELS_DIR.exists()}")
    
    if MODELS_DIR.exists():
        logger.info(f"Available model files: {list(MODELS_DIR.rglob('*.onnx'))}")
    
    app.run(host='0.0.0.0', port=5000, debug=False)