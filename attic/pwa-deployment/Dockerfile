# M1K3 PWA Deployment - Multi-stage Docker Build
# Stage 1: Model Export & Preparation
FROM python:3.9-slim as model-builder

# Install system dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy backend requirements and install dependencies
COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy backend source
COPY backend/ ./backend/

# Create models directory
RUN mkdir -p models

# Export models to ONNX format
RUN python backend/scripts/model_exporter.py --all-tiers --optimize

# Stage 2: Frontend Build & Optimization
FROM node:18-alpine as frontend-builder

WORKDIR /app

# Copy frontend source
COPY frontend/ ./

# Install serve for production deployment
RUN npm install -g serve

# Verify all files are present
RUN ls -la && \
    test -f index.html && \
    test -f manifest.json && \
    test -f sw.js && \
    test -d src

# Stage 3: Production Runtime
FROM nginx:alpine as production

# Install Python for model serving API
RUN apk add --no-cache python3 py3-pip

# Copy exported models from builder stage
COPY --from=model-builder /app/models /usr/share/nginx/html/models

# Copy frontend files
COPY --from=frontend-builder /app /usr/share/nginx/html

# Copy custom nginx configuration
COPY docker/nginx.conf /etc/nginx/nginx.conf

# Copy backend API for model metadata
COPY --from=model-builder /app/backend /app/backend

# Install backend dependencies in production
WORKDIR /app
RUN pip3 install flask flask-cors

# Create startup script
RUN echo '#!/bin/sh' > /startup.sh && \
    echo 'python3 /app/backend/api/model_api.py &' >> /startup.sh && \
    echo 'nginx -g "daemon off;"' >> /startup.sh && \
    chmod +x /startup.sh

# Expose ports
EXPOSE 80 5000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost/ || exit 1

# Start both nginx and model API
CMD ["/startup.sh"]