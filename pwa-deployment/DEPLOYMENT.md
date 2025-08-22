# M1K3 PWA Deployment Guide

## Overview

This guide covers deploying M1K3 as a Progressive Web App (PWA) that runs AI inference entirely in the browser using WebAssembly and ONNX Runtime. The system provides device-adaptive model selection, offline support, and universal compatibility.

## Architecture Summary

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Frontend      │    │    Backend       │    │   Container     │
│   (Browser)     │    │   (Python)       │    │   (Docker)      │
├─────────────────┤    ├──────────────────┤    ├─────────────────┤
│ • Device detect │    │ • Model export   │    │ • Multi-stage   │
│ • ONNX Runtime  │    │ • ONNX conversion│    │ • Nginx + API   │ 
│ • Progressive   │    │ • Optimization   │    │ • Health checks │
│   loading       │    │ • Metadata API   │    │ • Auto-scaling  │
│ • Service Worker│    │ • CI/CD pipeline │    │ • Zero downtime │
│ • Offline cache │    │ • Testing suite  │    │ • Multi-platform│
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## Quick Start

### Local Development

1. **Clone and Setup**
   ```bash
   git clone <repository>
   cd pwa-deployment
   ```

2. **Test Locally**
   ```bash
   # Start development server
   python test_server.py --port 9090
   
   # Open browser to http://localhost:9090
   # Test PWA features and device detection
   ```

3. **Run Integration Tests**
   ```bash
   # Comprehensive PWA testing
   python test_pwa_integration.py --url http://localhost:9090
   
   # Expected: 92.3%+ success rate
   # All core functionality validated
   ```

### Docker Deployment

1. **Build Container**
   ```bash
   # Multi-stage build with model export
   docker build -t m1k3-pwa .
   
   # Or use Docker Compose
   docker-compose up --build
   ```

2. **Run Production**
   ```bash
   # Single container deployment
   docker run -d -p 80:80 -p 5000:5000 m1k3-pwa
   
   # With health checks
   docker run -d -p 80:80 --health-interval=30s m1k3-pwa
   ```

3. **Verify Deployment**
   ```bash
   curl http://localhost/api/models
   curl http://localhost/models/deployment-manifest.json
   ```

### Cloud Deployment

#### GitHub Actions (Automated)

1. **Setup Repository Secrets**
   ```
   GITHUB_TOKEN (automatic)
   # Additional secrets for cloud providers if needed
   ```

2. **Push to Main Branch**
   ```bash
   git push origin main
   # Triggers: Build → Test → Security Scan → Deploy
   ```

3. **Monitor Deployment**
   - Check GitHub Actions tab
   - Verify container registry
   - Test staging environment
   - Approve production deployment

#### Manual Cloud Deployment

**Docker Hub / GitHub Container Registry**
```bash
# Tag and push
docker tag m1k3-pwa ghcr.io/your-username/m1k3-pwa:latest
docker push ghcr.io/your-username/m1k3-pwa:latest
```

**Kubernetes**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: m1k3-pwa
spec:
  replicas: 3
  selector:
    matchLabels:
      app: m1k3-pwa
  template:
    metadata:
      labels:
        app: m1k3-pwa
    spec:
      containers:
      - name: m1k3-pwa
        image: ghcr.io/your-username/m1k3-pwa:latest
        ports:
        - containerPort: 80
        - containerPort: 5000
        livenessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: m1k3-pwa-service
spec:
  selector:
    app: m1k3-pwa
  ports:
  - name: http
    port: 80
    targetPort: 80
  - name: api
    port: 5000
    targetPort: 5000
  type: LoadBalancer
```

**Cloud Run (GCP)**
```bash
# Deploy to Cloud Run
gcloud run deploy m1k3-pwa \
  --image ghcr.io/your-username/m1k3-pwa:latest \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --port 80 \
  --memory 2Gi \
  --cpu 1
```

**AWS ECS / Fargate**
```json
{
  "family": "m1k3-pwa",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::account:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "m1k3-pwa",
      "image": "ghcr.io/your-username/m1k3-pwa:latest",
      "portMappings": [
        {"containerPort": 80, "protocol": "tcp"},
        {"containerPort": 5000, "protocol": "tcp"}
      ],
      "essential": true,
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/m1k3-pwa",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

## Configuration

### Environment Variables

```bash
# Production Configuration
NODE_ENV=production
PYTHONUNBUFFERED=1

# Model Configuration  
MODEL_CACHE_SIZE=1000  # MB
ENABLE_MODEL_OPTIMIZATION=true
SUPPORTED_TIERS=tiny,small,medium

# Security Configuration
ENABLE_CORS=true
ALLOWED_ORIGINS=*
RATE_LIMIT_REQUESTS=100
RATE_LIMIT_WINDOW=3600

# Logging Configuration
LOG_LEVEL=INFO
ENABLE_ACCESS_LOGS=true
ENABLE_ERROR_TRACKING=true
```

### Nginx Configuration

The included `docker/nginx.conf` provides:
- **Security Headers**: COOP/COEP for WebAssembly
- **Compression**: Gzip for static assets
- **Caching**: Optimized for PWA resources
- **API Proxy**: Backend API routing
- **CORS Support**: Cross-origin request handling

### Model Configuration

```json
{
  "models": {
    "tiny": {
      "name": "m1k3-tiny",
      "size_mb": 100,
      "min_memory_gb": 2,
      "target_devices": ["mobile", "low-end"],
      "optimization": "aggressive"
    },
    "small": {
      "name": "m1k3-small", 
      "size_mb": 350,
      "min_memory_gb": 4,
      "target_devices": ["tablet", "mid-range"],
      "optimization": "balanced"
    },
    "medium": {
      "name": "m1k3-medium",
      "size_mb": 800,
      "min_memory_gb": 8,
      "target_devices": ["desktop", "high-end"],
      "optimization": "quality"
    }
  }
}
```

## Monitoring & Observability

### Health Checks

**Application Health**
```bash
# Basic health check
curl http://localhost/

# API health check
curl http://localhost/api/models

# Model availability check
curl http://localhost/models/deployment-manifest.json
```

**Container Health**
```bash
# Docker health check
docker ps --format "table {{.Names}}\t{{.Status}}"

# Container logs
docker logs m1k3-pwa --tail 100 -f

# Resource usage
docker stats m1k3-pwa
```

### Metrics Collection

**Key Metrics to Monitor**
- Response time (target: <2s model loading)
- Memory usage (target: <2GB per container)
- CPU utilization (target: <70% sustained)
- Model download success rate (target: >95%)
- PWA installation rate
- Offline usage patterns

**Prometheus Metrics** (if integrated)
```yaml
# /metrics endpoint provides:
- http_requests_total
- model_loading_duration_seconds  
- device_capability_distribution
- pwa_install_events_total
- offline_usage_duration_seconds
```

### Error Tracking

**Common Issues & Solutions**

1. **Model Loading Failures**
   - Check ONNX file integrity
   - Verify WebAssembly support
   - Monitor memory constraints
   - Review device compatibility

2. **Service Worker Issues**
   - Clear browser cache
   - Check HTTPS requirements
   - Verify manifest.json validity
   - Monitor cache size limits

3. **API Connectivity**
   - Check CORS configuration
   - Verify network policies
   - Monitor rate limiting
   - Review proxy settings

## Performance Optimization

### Frontend Optimization

1. **Model Caching Strategy**
   ```javascript
   // Progressive loading with intelligent caching
   const cacheStrategy = {
     models: '30 days',
     app: '7 days', 
     api: '1 hour'
   };
   ```

2. **Device-Adaptive Loading**
   ```javascript
   // Automatic model selection based on device
   const modelTier = deviceDetector.determineTier({
     memory: navigator.deviceMemory,
     cores: navigator.hardwareConcurrency,
     webgpu: await navigator.gpu?.requestAdapter()
   });
   ```

3. **Compression & Minification**
   - Gzip compression for all text assets
   - Model quantization for smaller files
   - Progressive loading with fallbacks

### Backend Optimization

1. **Model Export Pipeline**
   ```python
   # Optimized ONNX export with quantization
   export_config = {
     'optimization_level': 'all',
     'quantization': 'dynamic',
     'target_platform': 'web'
   }
   ```

2. **Caching Strategy**
   ```python
   # Multi-tier caching
   cache_config = {
     'models': 'disk',      # Persistent model storage
     'metadata': 'memory',  # Fast API responses
     'responses': 'redis'   # Distributed caching
   }
   ```

### Container Optimization

1. **Multi-stage Build Benefits**
   - Minimal production image size
   - Cached build layers
   - Security through minimal attack surface

2. **Resource Allocation**
   ```yaml
   resources:
     requests:
       memory: "512Mi"
       cpu: "250m"
     limits:
       memory: "2Gi" 
       cpu: "1000m"
   ```

## Security

### Security Headers

Required headers for PWA and WebAssembly:
```nginx
Cross-Origin-Embedder-Policy: require-corp
Cross-Origin-Opener-Policy: same-origin
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
```

### Content Security Policy

```html
<meta http-equiv="Content-Security-Policy" content="
  default-src 'self';
  script-src 'self' 'unsafe-eval';
  worker-src 'self' blob:;
  wasm-src 'self';
  connect-src 'self' https:;
">
```

### Container Security

1. **Non-root User**
   ```dockerfile
   RUN adduser --disabled-password --gecos '' appuser
   USER appuser
   ```

2. **Minimal Base Image**
   ```dockerfile
   FROM nginx:alpine  # Minimal attack surface
   ```

3. **Security Scanning**
   ```bash
   # Automated vulnerability scanning
   docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
     aquasec/trivy image m1k3-pwa:latest
   ```

## Troubleshooting

### Common Deployment Issues

1. **Port Conflicts**
   ```bash
   # Check port usage
   lsof -i :80 -i :5000
   
   # Use alternative ports
   docker run -p 8080:80 -p 5001:5000 m1k3-pwa
   ```

2. **Memory Issues**
   ```bash
   # Monitor memory usage
   docker stats m1k3-pwa
   
   # Increase memory limit
   docker run --memory=4g m1k3-pwa
   ```

3. **Model Loading Failures**
   ```bash
   # Check model files
   docker exec m1k3-pwa ls -la /usr/share/nginx/html/models/
   
   # Verify ONNX files
   docker exec m1k3-pwa python -c "import onnx; print('ONNX OK')"
   ```

4. **Service Worker Registration**
   ```javascript
   // Debug service worker in browser
   navigator.serviceWorker.getRegistrations().then(console.log);
   
   // Clear service worker cache
   caches.keys().then(names => names.forEach(name => caches.delete(name)));
   ```

### Debug Commands

```bash
# Container debugging
docker exec -it m1k3-pwa /bin/sh

# Check logs
docker logs m1k3-pwa --tail 100 -f

# Test API endpoints
curl -v http://localhost/api/models
curl -v http://localhost/models/deployment-manifest.json

# Verify file structure
docker exec m1k3-pwa find /usr/share/nginx/html -type f -name "*.js" -o -name "*.json"
```

## Scaling & Production

### Horizontal Scaling

```yaml
# Kubernetes HorizontalPodAutoscaler
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: m1k3-pwa-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: m1k3-pwa
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Load Balancing

```nginx
# Nginx upstream configuration
upstream m1k3_backend {
    least_conn;
    server m1k3-pwa-1:5000 weight=3;
    server m1k3-pwa-2:5000 weight=3;
    server m1k3-pwa-3:5000 weight=2;
}

server {
    location /api/ {
        proxy_pass http://m1k3_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### CDN Integration

```javascript
// Configure CDN for static assets
const CDN_BASE = 'https://cdn.example.com/m1k3/';

// Load models from CDN with fallback
async function loadModelFromCDN(modelPath) {
  try {
    return await fetch(`${CDN_BASE}${modelPath}`);
  } catch (error) {
    // Fallback to origin server
    return await fetch(`/models/${modelPath}`);
  }
}
```

## Success Metrics

After successful deployment, expect:

- ✅ **92.3%+ Test Success Rate**: All critical PWA features working
- ✅ **<2s Model Loading**: Fast AI initialization on modern devices  
- ✅ **Universal Compatibility**: Works on mobile, tablet, desktop
- ✅ **Offline Functionality**: Service worker provides offline access
- ✅ **Device Adaptation**: Automatic model selection based on capabilities
- ✅ **Progressive Enhancement**: Graceful degradation on older devices
- ✅ **Security Compliance**: All required headers and policies
- ✅ **Production Ready**: Health checks, monitoring, auto-scaling

## Support

For deployment issues:
1. Check this guide first
2. Run integration tests: `python test_pwa_integration.py`
3. Review container logs: `docker logs m1k3-pwa`
4. Test API endpoints manually
5. Verify browser developer tools for PWA features

The M1K3 PWA is designed for reliable, scalable deployment with comprehensive testing and monitoring built-in.